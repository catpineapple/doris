// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.nereids.trees.plans.physical;

import org.apache.doris.nereids.memo.GroupExpression;
import org.apache.doris.nereids.properties.DataTrait;
import org.apache.doris.nereids.properties.LogicalProperties;
import org.apache.doris.nereids.properties.PhysicalProperties;
import org.apache.doris.nereids.trees.expressions.Add;
import org.apache.doris.nereids.trees.expressions.Alias;
import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.NamedExpression;
import org.apache.doris.nereids.trees.expressions.Slot;
import org.apache.doris.nereids.trees.expressions.SlotReference;
import org.apache.doris.nereids.trees.expressions.functions.NoneMovableFunction;
import org.apache.doris.nereids.trees.expressions.functions.scalar.Uuid;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.PlanType;
import org.apache.doris.nereids.trees.plans.algebra.Project;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;
import org.apache.doris.nereids.util.ExpressionUtils;
import org.apache.doris.nereids.util.Utils;
import org.apache.doris.qe.ConnectContext;
import org.apache.doris.statistics.Statistics;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Physical project plan.
 */
public class PhysicalProject<CHILD_TYPE extends Plan> extends PhysicalUnary<CHILD_TYPE> implements Project {

    private final List<NamedExpression> projects;
    private final Supplier<Set<NamedExpression>> projectsSet;
    //multiLayerProjects is used to extract common expressions
    // projects: (A+B) * 2, (A+B) * 3
    // multiLayerProjects:
    //            L1: A+B as x
    //            L2: x*2, x*3
    private List<List<NamedExpression>> multiLayerProjects = Lists.newArrayList();

    public PhysicalProject(List<NamedExpression> projects, LogicalProperties logicalProperties, CHILD_TYPE child) {
        this(projects, Optional.empty(), logicalProperties, child);
    }

    public PhysicalProject(List<NamedExpression> projects, Optional<GroupExpression> groupExpression,
            LogicalProperties logicalProperties, CHILD_TYPE child) {
        super(PlanType.PHYSICAL_PROJECT, groupExpression, logicalProperties, child);
        this.projects = Utils.fastToImmutableList(
                Objects.requireNonNull(projects, "projects can not be null")
        );
        this.projectsSet = Suppliers.memoize(() -> Utils.fastToImmutableSet(this.projects));
    }

    /** PhysicalProject */
    public PhysicalProject(List<NamedExpression> projects, Optional<GroupExpression> groupExpression,
            LogicalProperties logicalProperties, PhysicalProperties physicalProperties,
            Statistics statistics, CHILD_TYPE child) {
        super(PlanType.PHYSICAL_PROJECT, groupExpression, logicalProperties, physicalProperties, statistics, child);
        this.projects = Utils.fastToImmutableList(
                Objects.requireNonNull(projects, "projects can not be null")
        );
        this.projectsSet = Suppliers.memoize(() -> Utils.fastToImmutableSet(this.projects));
    }

    public List<NamedExpression> getProjects() {
        return projects;
    }

    @Override
    public PhysicalProject<Plan> withProjects(List<NamedExpression> projects) {
        return withProjectionsAndChild(projects, child());
    }

    @Override
    public String toString() {
        StringBuilder cse = new StringBuilder();
        for (int i = 0; i < multiLayerProjects.size(); i++) {
            List<NamedExpression> layer = multiLayerProjects.get(i);
            cse.append("l").append(i).append("(").append(layer).append(")");
        }
        return Utils.toSqlString("PhysicalProject[" + id.asInt() + "]" + getGroupIdWithPrefix(),
                "stats", statistics, "projects", projects, "multi_proj", cse.toString()
        );
    }

    @Override
    public String shapeInfo() {
        ConnectContext context = ConnectContext.get();
        if (context != null
                && context.getSessionVariable().getDetailShapePlanNodesSet().contains(getClass().getSimpleName())) {
            StringBuilder builder = new StringBuilder();
            builder.append(getClass().getSimpleName());
            // the internal project list's order may be unstable, especial for join tables,
            // so sort the projects to make it stable
            builder.append(projects.stream().map(Expression::shapeInfo).sorted()
                    .collect(Collectors.joining(", ", "[", "]")));
            return builder.toString();
        } else {
            return super.shapeInfo();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PhysicalProject<?> that = (PhysicalProject<?>) o;
        return projectsSet.get().equals(that.projectsSet.get());
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectsSet.get());
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitPhysicalProject(this, context);
    }

    @Override
    public List<? extends Expression> getExpressions() {
        return projects;
    }

    @Override
    public PhysicalProject<Plan> withChildren(List<Plan> children) {
        Preconditions.checkArgument(children.size() == 1);
        return new PhysicalProject<>(projects,
                groupExpression,
                getLogicalProperties(),
                physicalProperties,
                statistics,
                children.get(0)
        );
    }

    @Override
    public PhysicalProject<CHILD_TYPE> withGroupExpression(Optional<GroupExpression> groupExpression) {
        return new PhysicalProject<>(projects, groupExpression, getLogicalProperties(), child());
    }

    @Override
    public Plan withGroupExprLogicalPropChildren(Optional<GroupExpression> groupExpression,
            Optional<LogicalProperties> logicalProperties, List<Plan> children) {
        Preconditions.checkArgument(children.size() == 1);
        return new PhysicalProject<>(projects, groupExpression, logicalProperties.get(), children.get(0));
    }

    @Override
    public PhysicalProject<CHILD_TYPE> withPhysicalPropertiesAndStats(PhysicalProperties physicalProperties,
            Statistics statistics) {
        return new PhysicalProject<>(projects, groupExpression, getLogicalProperties(), physicalProperties,
                statistics, child());
    }

    /**
     * replace projections and child, it is used for merge consecutive projections.
     * @param projections new projections
     * @param child new child
     * @return new project
     */
    public PhysicalProject<Plan> withProjectionsAndChild(List<NamedExpression> projections, Plan child) {
        return new PhysicalProject<>(Utils.fastToImmutableList(projections),
                groupExpression,
                getLogicalProperties(),
                physicalProperties,
                statistics,
                child
        );
    }

    @Override
    public List<Slot> computeOutput() {
        List<NamedExpression> output = projects;
        if (!multiLayerProjects.isEmpty()) {
            int layers = multiLayerProjects.size();
            output = multiLayerProjects.get(layers - 1);
        }

        Builder<Slot> slots = ImmutableList.builderWithExpectedSize(output.size());
        for (NamedExpression project : output) {
            slots.add(project.toSlot());
        }
        return slots.build();
    }

    @Override
    public PhysicalProject<CHILD_TYPE> resetLogicalProperties() {
        return new PhysicalProject<>(projects, groupExpression, null, physicalProperties,
                statistics, child());
    }

    /**
     * extract common expr, set multi layer projects
     */
    public void computeMultiLayerProjectsForCommonExpress() {
        // hard code: select (s_suppkey + s_nationkey), 1+(s_suppkey + s_nationkey), s_name from supplier;
        if (projects.size() == 3) {
            if (projects.get(2) instanceof SlotReference) {
                SlotReference sName = (SlotReference) projects.get(2);
                if (sName.getName().equals("s_name")) {
                    Alias a1 = (Alias) projects.get(0); // (s_suppkey + s_nationkey)
                    Alias a2 = (Alias) projects.get(1); // 1+(s_suppkey + s_nationkey)
                    // L1: (s_suppkey + s_nationkey) as x, s_name
                    multiLayerProjects.add(Lists.newArrayList(projects.get(0), projects.get(2)));
                    List<NamedExpression> l2 = Lists.newArrayList();
                    l2.add(a1.toSlot());
                    Alias a3 = new Alias(a2.getExprId(), new Add(a1.toSlot(), a2.child().child(1)), a2.getName());
                    l2.add(a3);
                    l2.add(sName);
                    // L2: x, (1+x) as y, s_name
                    multiLayerProjects.add(l2);
                }
            }
        }
        // hard code:
        // select (s_suppkey + n_regionkey) + 1 as x, (s_suppkey + n_regionkey) + 2 as y
        // from supplier join nation on s_nationkey=n_nationkey
        // projects: x, y
        // multi L1: s_suppkey, n_regionkey, (s_suppkey + n_regionkey) as z
        //       L2: z +1 as x, z+2 as y
        if (projects.size() == 2 && projects.get(0) instanceof Alias && projects.get(1) instanceof Alias
                && ((Alias) projects.get(0)).getName().equals("x")
                && ((Alias) projects.get(1)).getName().equals("y")) {
            Alias a0 = (Alias) projects.get(0);
            Alias a1 = (Alias) projects.get(1);
            Add common = (Add) a0.child().child(0); // s_suppkey + n_regionkey
            List<NamedExpression> l1 = Lists.newArrayList();
            common.children().stream().forEach(child -> l1.add((SlotReference) child));
            Alias aliasOfCommon = new Alias(common);
            l1.add(aliasOfCommon);
            multiLayerProjects.add(l1);
            Add add1 = new Add(common, a0.child().child(0).child(1));
            Alias aliasOfAdd1 = new Alias(a0.getExprId(), add1, a0.getName());
            Add add2 = new Add(common, a1.child().child(0).child(1));
            Alias aliasOfAdd2 = new Alias(a1.getExprId(), add2, a1.getName());
            List<NamedExpression> l2 = Lists.newArrayList(aliasOfAdd1, aliasOfAdd2);
            multiLayerProjects.add(l2);
        }
    }

    public boolean hasMultiLayerProjection() {
        return !multiLayerProjects.isEmpty();
    }

    public List<List<NamedExpression>> getMultiLayerProjects() {
        return multiLayerProjects;
    }

    public void setMultiLayerProjects(List<List<NamedExpression>> multiLayers) {
        this.multiLayerProjects = multiLayers;
    }

    @Override
    public void computeUnique(DataTrait.Builder builder) {
        builder.addUniqueSlot(child(0).getLogicalProperties().getTrait());
        for (NamedExpression proj : getProjects()) {
            if (proj.children().isEmpty()) {
                continue;
            }
            if (proj.child(0) instanceof Uuid) {
                builder.addUniqueSlot(proj.toSlot());
            } else if (ExpressionUtils.isInjective(proj.child(0))) {
                ImmutableSet<Slot> inputs = Utils.fastToImmutableSet(proj.getInputSlots());
                if (child(0).getLogicalProperties().getTrait().isUnique(inputs)) {
                    builder.addUniqueSlot(proj.toSlot());
                }
            }
        }
    }

    @Override
    public void computeUniform(DataTrait.Builder builder) {
        builder.addUniformSlot(child(0).getLogicalProperties().getTrait());
        for (NamedExpression proj : getProjects()) {
            if (!(proj instanceof Alias)) {
                continue;
            }
            if (proj.child(0).isConstant()) {
                builder.addUniformSlotAndLiteral(proj.toSlot(), proj.child(0));
            } else if (proj.child(0) instanceof Slot) {
                Slot slot = (Slot) proj.child(0);
                DataTrait childTrait = child(0).getLogicalProperties().getTrait();
                if (childTrait.isUniformAndHasConstValue(slot)) {
                    builder.addUniformSlotAndLiteral(proj.toSlot(),
                            child(0).getLogicalProperties().getTrait().getUniformValue(slot).get());
                } else if (childTrait.isUniform(slot)) {
                    builder.addUniformSlot(proj.toSlot());
                }
            }
        }
    }

    @Override
    public void computeEqualSet(DataTrait.Builder builder) {
        Map<Expression, NamedExpression> aliasMap = new HashMap<>();
        builder.addEqualSet(child().getLogicalProperties().getTrait());
        for (NamedExpression expr : getProjects()) {
            if (expr instanceof Alias) {
                if (aliasMap.containsKey(expr.child(0))) {
                    builder.addEqualPair(expr.toSlot(), aliasMap.get(expr.child(0)).toSlot());
                }
                aliasMap.put(expr.child(0), expr);
                if (expr.child(0).isSlot()) {
                    builder.addEqualPair(expr.toSlot(), (Slot) expr.child(0));
                }
            }
        }
    }

    @Override
    public void computeFd(DataTrait.Builder builder) {
        builder.addFuncDepsDG(child().getLogicalProperties().getTrait());
        for (NamedExpression expr : getProjects()) {
            if (!expr.isSlot()) {
                builder.addDeps(expr.getInputSlots(), ImmutableSet.of(expr.toSlot()));
            }
        }
    }

    @Override
    public List<NamedExpression> getOutputs() {
        return projects;
    }

    @Override
    public Plan pruneOutputs(List<NamedExpression> prunedOutputs) {
        List<NamedExpression> allProjects = new ArrayList<>(prunedOutputs);
        for (NamedExpression expression : projects) {
            if (expression.containsType(NoneMovableFunction.class)) {
                if (!prunedOutputs.contains(expression)) {
                    allProjects.add(expression);
                }
            }
        }
        return withProjects(allProjects);
    }

    @Override
    public boolean canProcessProject(List<NamedExpression> parentProjects) {
        return true;
    }
}
