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

suite("load") {
    sql "drop table if exists agg_mv_test"
    sql """
        CREATE TABLE IF NOT EXISTS `agg_mv_test` (
            `id` int null,
            `kbool` boolean null,
            `ktint` tinyint(4) null,
            `ksint` smallint(6) null,
            `kint` int(11) null,
            `kbint` bigint(20) null,
            `klint` largeint(40) null,
            `kfloat` float null,
            `kdbl` double null,
            `kdcmls1` decimal(9, 3) null,
            `kdcmls2` decimal(15, 5) null,
            `kdcmls3` decimal(27, 9) null,
            `kdcmlv3s1` decimalv3(9, 3) null,
            `kdcmlv3s2` decimalv3(15, 5) null,
            `kdcmlv3s3` decimalv3(27, 9) null,
            `kchrs1` char(10) null,
            `kchrs2` char(20) null,
            `kchrs3` char(50) null,
            `kvchrs1` varchar(10) null,
            `kvchrs2` varchar(20) null,
            `kvchrs3` varchar(50) null,
            `kstr` string null,
            `kdt` date null,
            `kdtv2` datev2 null,
            `kdtm` datetime null,
            `kdtmv2s1` datetimev2(0) null,
            `kdtmv2s2` datetimev2(4) null,
            `kdtmv2s3` datetimev2(6) null,
            `kabool` array<boolean> null,
            `katint` array<tinyint(4)> null,
            `kasint` array<smallint(6)> null,
            `kaint` array<int> null,
            `kabint` array<bigint(20)> null,
            `kalint` array<largeint(40)> null,
            `kafloat` array<float> null,
            `kadbl` array<double> null,
            `kadt` array<date> null,
            `kadtm` array<datetime> null,
            `kadtv2` array<datev2> null,
            `kadtmv2` array<datetimev2(6)> null,
            `kachr` array<char(50)> null,
            `kavchr` array<varchar(50)> null,
            `kastr` array<string> null,
            `kadcml` array<decimal(27, 9)> null,
            `st_point_str` string null,
            `st_point_vc` varchar(50) null,
            `x_lng` double null,
            `x_lat` double null,
            `y_lng` double null,
            `y_lat` double null,
            `z_lng` double null,
            `z_lat` double null,
            `radius` double null,
            `linestring_wkt` varchar(50) null,
            `polygon_wkt` varchar(50) null,
            `km_bool_tint` map<boolean, tinyint> null,
            `km_tint_tint` map<tinyint, tinyint> null,
            `km_sint_tint` map<smallint, tinyint> null,
            `km_int_tint` map<int, tinyint> null,
            `km_bint_tint` map<bigint, tinyint> null,
            `km_lint_tint` map<largeint, tinyint> null,
            `km_float_tint` map<float, tinyint> null,
            `km_dbl_tint` map<double, tinyint> null,
            `km_dcml_tint` map<decimal(22,9), tinyint> null,
            `km_chr_tint` map<char(5), tinyint> null,
            `km_vchr_tint` map<varchar(50), tinyint> null,
            `km_str_tint` map<string, tinyint> null,
            `km_date_tint` map<date, tinyint> null,
            `km_dtm_tint` map<datetime, tinyint> null,
            `km_tint_bool` map<tinyint, boolean> null,
            `km_int_int` map<int, int> null,
            `km_tint_sint` map<tinyint, smallint> null,
            `km_tint_int` map<tinyint, int> null,
            `km_tint_bint` map<tinyint, bigint> null,
            `km_tint_lint` map<tinyint, largeint> null,
            `km_tint_float` map<tinyint, float> null,
            `km_tint_dbl` map<tinyint, double> null,
            `km_tint_dcml` map<tinyint, decimal(22,9)> null,
            `km_tint_chr` map<tinyint, char(5)> null,
            `km_tint_vchr` map<tinyint, varchar(50)> null,
            `km_tint_str` map<tinyint, string> null,
            `km_tint_date` map<tinyint, date> null,
            `km_tint_dtm` map<tinyint, datetime> null,
            `kjson` JSON null,
            `kstruct` STRUCT<id: int> null
        ) engine=olap
        DISTRIBUTED BY HASH(`id`) BUCKETS 4
        properties("replication_num" = "1")
    """

    streamLoad {
        table "agg_mv_test"
        db "regression_test_nereids_syntax_p1_mv"
        set 'column_separator', ';'
        set 'columns', '''
            id, kbool, ktint, ksint, kint, kbint, klint, kfloat, kdbl, kdcmls1, kdcmls2, kdcmls3,
            kdcmlv3s1, kdcmlv3s2, kdcmlv3s3, kchrs1, kchrs2, kchrs3, kvchrs1, kvchrs2, kvchrs3, kstr,
            kdt, kdtv2, kdtm, kdtmv2s1, kdtmv2s2, kdtmv2s3, kabool, katint, kasint, kaint,
            kabint, kalint, kafloat, kadbl, kadt, kadtm, kadtv2, kadtmv2, kachr, kavchr, kastr, kadcml,
            st_point_str, st_point_vc, x_lng, x_lat, y_lng, y_lat, z_lng, z_lat, radius, linestring_wkt, polygon_wkt,
            km_bool_tint, km_tint_tint, km_sint_tint, km_int_tint, km_bint_tint, km_lint_tint, km_float_tint,
            km_dbl_tint, km_dcml_tint, km_chr_tint, km_vchr_tint, km_str_tint, km_date_tint, km_dtm_tint,
            km_tint_bool, km_int_int, km_tint_sint, km_tint_int, km_tint_bint, km_tint_lint, km_tint_float,
            km_tint_dbl, km_tint_dcml, km_tint_chr, km_tint_vchr, km_tint_str, km_tint_date, km_tint_dtm, kjson, kstruct
            '''
        file "agg_mv_test.dat"
    }
}
