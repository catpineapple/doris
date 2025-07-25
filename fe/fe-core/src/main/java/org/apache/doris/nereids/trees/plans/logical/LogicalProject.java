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

package org.apache.doris.nereids.trees.plans.logical;

import org.apache.doris.nereids.analyzer.Unbound;
import org.apache.doris.nereids.memo.GroupExpression;
import org.apache.doris.nereids.properties.DataTrait;
import org.apache.doris.nereids.properties.LogicalProperties;
import org.apache.doris.nereids.trees.expressions.Alias;
import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.NamedExpression;
import org.apache.doris.nereids.trees.expressions.Slot;
import org.apache.doris.nereids.trees.expressions.functions.NoneMovableFunction;
import org.apache.doris.nereids.trees.expressions.functions.scalar.Uuid;
import org.apache.doris.nereids.trees.expressions.literal.TinyIntLiteral;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.PlanType;
import org.apache.doris.nereids.trees.plans.algebra.Project;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;
import org.apache.doris.nereids.util.ExpressionUtils;
import org.apache.doris.nereids.util.Utils;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Logical project plan.
 */
public class LogicalProject<CHILD_TYPE extends Plan> extends LogicalUnary<CHILD_TYPE>
        implements Project, OutputPrunable, ProjectMergeable {

    private final List<NamedExpression> projects;
    private final Supplier<Set<NamedExpression>> projectsSet;
    private final boolean isDistinct;

    public LogicalProject(List<NamedExpression> projects, CHILD_TYPE child) {
        this(projects, false, ImmutableList.of(child));
    }

    public LogicalProject(List<NamedExpression> projects, boolean isDistinct, List<Plan> child) {
        this(projects, isDistinct, Optional.empty(), Optional.empty(), child);
    }

    public LogicalProject(List<NamedExpression> projects, boolean isDistinct, Plan child) {
        this(projects, isDistinct,
                Optional.empty(), Optional.empty(), ImmutableList.of(child));
    }

    private LogicalProject(List<NamedExpression> projects, boolean isDistinct,
            Optional<GroupExpression> groupExpression, Optional<LogicalProperties> logicalProperties,
            List<Plan> child) {
        super(PlanType.LOGICAL_PROJECT, groupExpression, logicalProperties, child);
        Preconditions.checkArgument(projects != null, "projects can not be null");
        // only ColumnPrune rule may produce empty projects, this happens in rewrite phase
        // so if projects is empty, all plans have been bound already.
        Preconditions.checkArgument(!projects.isEmpty() || !(child instanceof Unbound),
                "projects can not be empty when child plan is unbound");
        this.projects = projects.isEmpty()
                ? ImmutableList.of(new Alias(new TinyIntLiteral((byte) 1)))
                : projects;
        this.projectsSet = Suppliers.memoize(() -> Utils.fastToImmutableSet(this.projects));
        this.isDistinct = isDistinct;
    }

    /**
     * Get project list.
     *
     * @return all project of this node.
     */
    @Override
    public List<NamedExpression> getProjects() {
        return projects;
    }

    @Override
    public List<Slot> getOutput() {
        return Lists.transform(this.projects, NamedExpression::toSlot);
    }

    @Override
    public List<Slot> computeOutput() {
        Builder<Slot> slots = ImmutableList.builderWithExpectedSize(projects.size());
        for (NamedExpression project : projects) {
            slots.add(project.toSlot());
        }
        return slots.build();
    }

    @Override
    public String toString() {
        return Utils.toSqlStringSkipNull("LogicalProject[" + id.asInt() + "]",
                "distinct", isDistinct,
                "projects", projects,
                "stats", statistics
        );
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitLogicalProject(this, context);
    }

    @Override
    public List<? extends Expression> getExpressions() {
        return projects;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LogicalProject<?> that = (LogicalProject<?>) o;
        return projectsSet.get().equals(that.projectsSet.get()) && isDistinct == that.isDistinct;
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectsSet.get(), isDistinct);
    }

    @Override
    public LogicalProject<Plan> withChildren(List<Plan> children) {
        Preconditions.checkArgument(children.size() == 1);
        return new LogicalProject<>(projects, isDistinct, Utils.fastToImmutableList(children));
    }

    @Override
    public LogicalProject<Plan> withGroupExpression(Optional<GroupExpression> groupExpression) {
        return new LogicalProject<>(projects, isDistinct,
                groupExpression, Optional.of(getLogicalProperties()), children);
    }

    @Override
    public Plan withGroupExprLogicalPropChildren(Optional<GroupExpression> groupExpression,
            Optional<LogicalProperties> logicalProperties, List<Plan> children) {
        Preconditions.checkArgument(children.size() == 1);
        return new LogicalProject<>(projects, isDistinct,
                groupExpression, logicalProperties, children);
    }

    public LogicalProject<Plan> withProjects(List<NamedExpression> projects) {
        return new LogicalProject<>(projects, isDistinct, children);
    }

    public LogicalProject<Plan> withProjectsAndChild(List<NamedExpression> projects, Plan child) {
        return new LogicalProject<>(projects, isDistinct, ImmutableList.of(child));
    }

    public LogicalProject<Plan> withDistinct(boolean isDistinct) {
        return new LogicalProject<>(projects, isDistinct, children);
    }

    public boolean isDistinct() {
        return isDistinct;
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
    public JSONObject toJson() {
        JSONObject logicalProject = super.toJson();
        JSONObject properties = new JSONObject();
        properties.put("Projects", projects.toString());
        properties.put("IsDistinct", isDistinct);
        logicalProject.put("Properties", properties);
        return logicalProject;
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
                ImmutableSet<Slot> inputs = ImmutableSet.copyOf(proj.getInputSlots());
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
            if (!(expr instanceof Alias)) {
                continue;
            }
            // a+random(1,10) should continue, otherwise the a(determinant), a+random(1,10) (dependency) will be added.
            if (expr.containsNonfoldable()) {
                continue;
            }
            builder.addDeps(expr.getInputSlots(), ImmutableSet.of(expr.toSlot()));
        }
    }

    @Override
    public boolean canProcessProject(List<NamedExpression> parentProjects) {
        return canMergeParentProjections(parentProjects);
    }
}
