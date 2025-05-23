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
// This file is copied from
// https://github.com/ClickHouse/ClickHouse/blob/master/src/DataTypes/IDataType.h
// and modified by Doris

#pragma once

#include <gen_cpp/Exprs_types.h>
#include <gen_cpp/Types_types.h>
#include <stddef.h>
#include <stdint.h>

#include <boost/core/noncopyable.hpp>
#include <memory>
#include <string>
#include <type_traits>
#include <vector>

#include "common/exception.h"
#include "common/status.h"
#include "runtime/define_primitive_type.h"
#include "vec/columns/column_const.h"
#include "vec/columns/column_string.h"
#include "vec/common/cow.h"
#include "vec/core/types.h"
#include "vec/data_types/serde/data_type_serde.h"

namespace doris {
class PColumnMeta;
enum PGenericType_TypeId : int;

namespace vectorized {
#include "common/compile_check_begin.h"
class IDataType;
class IColumn;
class BufferWritable;
class ReadBuffer;

using ColumnPtr = COW<IColumn>::Ptr;
using MutableColumnPtr = COW<IColumn>::MutablePtr;

class Field;

using DataTypePtr = std::shared_ptr<const IDataType>;
using DataTypes = std::vector<DataTypePtr>;
constexpr auto SERIALIZED_MEM_SIZE_LIMIT = 256;

template <typename T>
T upper_int32(T size) {
    static_assert(std::is_unsigned_v<T>);
    return T(static_cast<double>(3 + size) / 4.0);
}

/** Properties of data type.
  * Contains methods for serialization/deserialization.
  * Implementations of this interface represent a data type (example: UInt8)
  *  or parametric family of data types (example: Array(...)).
  *
  * DataType is totally immutable object. You can always share them.
  */
class IDataType : private boost::noncopyable {
public:
    IDataType();
    virtual ~IDataType();

    /// Name of data type (examples: UInt64, Array(String)).
    String get_name() const;

    /// Name of data type family (example: FixedString, Array).
    virtual const char* get_family_name() const = 0;

    /// Data type id. It's used for runtime type checks.
    virtual TypeIndex get_type_id() const = 0;

    virtual TypeDescriptor get_type_as_type_descriptor() const = 0;
    virtual doris::FieldType get_storage_field_type() const = 0;

    virtual void to_string(const IColumn& column, size_t row_num, BufferWritable& ostr) const;
    virtual std::string to_string(const IColumn& column, size_t row_num) const;

    virtual void to_string_batch(const IColumn& column, ColumnString& column_to) const;
    // only for compound type now.
    virtual Status from_string(ReadBuffer& rb, IColumn* column) const;

    // get specific serializer or deserializer
    virtual DataTypeSerDeSPtr get_serde(int nesting_level = 1) const = 0;

protected:
    virtual String do_get_name() const;

public:
    /** Create empty column for corresponding type.
      */
    virtual MutableColumnPtr create_column() const = 0;

    /** Create ColumnConst for corresponding type, with specified size and value.
      */
    ColumnPtr create_column_const(size_t size, const Field& field) const;
    ColumnPtr create_column_const_with_default_value(size_t size) const;

    /** Get default value of data type.
      * It is the "default" default, regardless the fact that a table could contain different user-specified default.
      */
    virtual Field get_default() const = 0;

    virtual Field get_field(const TExprNode& node) const = 0;

    /// Checks that two instances belong to the same type
    virtual bool equals(const IDataType& rhs) const = 0;

    /** The data type is dependent on parameters and at least one of them is another type.
      * Examples: Tuple(T1, T2), Nullable(T). But FixedString(N) is not.
      */
    virtual bool have_subtypes() const = 0;

    /** In text formats that render "pretty" tables,
      *  is it better to align value right in table cell.
      * Examples: numbers, even nullable.
      */
    virtual bool should_align_right_in_pretty_formats() const { return false; }

    /** Does formatted value in any text format can contain anything but valid UTF8 sequences.
      * Example: String (because it can contain arbitrary bytes).
      * Counterexamples: numbers, Date, DateTime.
      * For Enum, it depends.
      */
    virtual bool text_can_contain_only_valid_utf8() const { return false; }

    /** Is it possible to compare for less/greater, to calculate min/max?
      * Not necessarily totally comparable. For example, floats are comparable despite the fact that NaNs compares to nothing.
      * The same for nullable of comparable types: they are comparable (but not totally-comparable).
      */
    virtual bool is_comparable() const { return false; }

    /** Numbers, Enums, Date, DateTime. Not nullable.
      */
    virtual bool is_value_represented_by_number() const { return false; }

    /** Values are unambiguously identified by contents of contiguous memory region,
      *  that can be obtained by IColumn::get_data_at method.
      * Examples: numbers, Date, DateTime, String, FixedString,
      *  and Arrays of numbers, Date, DateTime, FixedString, Enum, but not String.
      *  (because Array(String) values became ambiguous if you concatenate Strings).
      * Counterexamples: Nullable, Tuple.
      */
    virtual bool is_value_unambiguously_represented_in_contiguous_memory_region() const {
        return false;
    }

    /** Example: numbers, Date, DateTime, FixedString, Enum... Nullable and Tuple of such types.
      * Counterexamples: String, Array.
      * It's Ok to return false for AggregateFunction despite the fact that some of them have fixed size state.
      */
    virtual bool have_maximum_size_of_value() const { return false; }

    /** Throws an exception if value is not of fixed size.
      */
    virtual size_t get_size_of_value_in_memory() const;

    virtual bool is_nullable() const { return false; }

    /* the data type create from type_null, NULL literal*/
    virtual bool is_null_literal() const { return false; }

    virtual bool low_cardinality() const { return false; }

    /// Strings, Numbers, Date, DateTime, Nullable
    virtual bool can_be_inside_low_cardinality() const { return false; }

    virtual int64_t get_uncompressed_serialized_bytes(const IColumn& column,
                                                      int be_exec_version) const = 0;
    virtual char* serialize(const IColumn& column, char* buf, int be_exec_version) const = 0;
    virtual const char* deserialize(const char* buf, MutableColumnPtr* column,
                                    int be_exec_version) const = 0;

    virtual void to_pb_column_meta(PColumnMeta* col_meta) const;

    static PGenericType_TypeId get_pdata_type(const IDataType* data_type);

    [[nodiscard]] virtual UInt32 get_precision() const {
        throw Exception(ErrorCode::INTERNAL_ERROR, "type {} not support get_precision", get_name());
    }
    [[nodiscard]] virtual UInt32 get_scale() const {
        throw Exception(ErrorCode::INTERNAL_ERROR, "type {} not support get_scale", get_name());
    }

private:
    friend class DataTypeFactory;
};

/// Some sugar to check data type of IDataType
struct WhichDataType {
    TypeIndex idx;

    WhichDataType(TypeIndex idx_ = TypeIndex::Nothing) : idx(idx_) {}

    WhichDataType(const IDataType& data_type) : idx(data_type.get_type_id()) {}

    WhichDataType(const IDataType* data_type) : idx(data_type->get_type_id()) {}

    WhichDataType(const DataTypePtr& data_type) : idx(data_type->get_type_id()) {}

    bool is_uint8() const { return idx == TypeIndex::UInt8; }
    bool is_uint16() const { return idx == TypeIndex::UInt16; }
    bool is_uint32() const { return idx == TypeIndex::UInt32; }
    bool is_uint64() const { return idx == TypeIndex::UInt64; }
    bool is_uint128() const { return idx == TypeIndex::UInt128; }
    bool is_uint() const {
        return is_uint8() || is_uint16() || is_uint32() || is_uint64() || is_uint128();
    }
    bool is_native_uint() const { return is_uint8() || is_uint16() || is_uint32() || is_uint64(); }

    bool is_int8() const { return idx == TypeIndex::Int8; }
    bool is_int16() const { return idx == TypeIndex::Int16; }
    bool is_int32() const { return idx == TypeIndex::Int32; }
    bool is_int64() const { return idx == TypeIndex::Int64; }
    bool is_int128() const { return idx == TypeIndex::Int128; }
    bool is_int() const {
        return is_int8() || is_int16() || is_int32() || is_int64() || is_int128();
    }
    bool is_int_or_uint() const { return is_int() || is_uint(); }
    bool is_native_int() const { return is_int8() || is_int16() || is_int32() || is_int64(); }

    bool is_decimal32() const { return idx == TypeIndex::Decimal32; }
    bool is_decimal64() const { return idx == TypeIndex::Decimal64; }
    bool is_decimal128v2() const { return idx == TypeIndex::Decimal128V2; }
    bool is_decimal128v3() const { return idx == TypeIndex::Decimal128V3; }
    bool is_decimal256() const { return idx == TypeIndex::Decimal256; }
    bool is_decimal() const {
        return is_decimal32() || is_decimal64() || is_decimal128v2() || is_decimal128v3() ||
               is_decimal256();
    }

    bool is_float32() const { return idx == TypeIndex::Float32; }
    bool is_float64() const { return idx == TypeIndex::Float64; }
    bool is_float() const { return is_float32() || is_float64(); }

    bool is_date() const { return idx == TypeIndex::Date; }
    bool is_date_time() const { return idx == TypeIndex::DateTime; }
    bool is_date_v2() const { return idx == TypeIndex::DateV2; }
    bool is_date_time_v2() const { return idx == TypeIndex::DateTimeV2; }
    bool is_date_or_datetime() const { return is_date() || is_date_time(); }
    bool is_date_v2_or_datetime_v2() const { return is_date_v2() || is_date_time_v2(); }
    bool is_time_v2() const { return idx == TypeIndex::TimeV2; }

    bool is_ipv4() const { return idx == TypeIndex::IPv4; }
    bool is_ipv6() const { return idx == TypeIndex::IPv6; }
    bool is_ip() const { return is_ipv4() || is_ipv6(); }

    bool is_string() const { return idx == TypeIndex::String; }
    bool is_fixed_string() const { return idx == TypeIndex::FixedString; }
    bool is_string_or_fixed_string() const { return is_string() || is_fixed_string(); }

    bool is_json() const { return idx == TypeIndex::JSONB; }
    bool is_bitmap() const { return idx == TypeIndex::BitMap; }
    bool is_hll() const { return idx == TypeIndex::HLL; }

    bool is_array() const { return idx == TypeIndex::Array; }
    bool is_tuple() const { return idx == TypeIndex::Tuple; }
    bool is_struct() const { return idx == TypeIndex::Struct; }
    bool is_map() const { return idx == TypeIndex::Map; }
    bool is_set() const { return idx == TypeIndex::Set; }
    bool is_fixed_length_object() const { return idx == TypeIndex::FixedLengthObject; }

    bool is_nothing() const { return idx == TypeIndex::Nothing; }
    bool is_nullable() const { return idx == TypeIndex::Nullable; }
    bool is_function() const { return idx == TypeIndex::Function; }
    bool is_aggregate_function() const { return idx == TypeIndex::AggregateFunction; }
    bool is_variant_type() const { return idx == TypeIndex::VARIANT; }
    bool is_simple() const { return is_int() || is_uint() || is_float() || is_string(); }
    // Compare datev2 and datetimev2 direct use the numric compare.
    bool is_num_can_compare() const {
        return is_int_or_uint() || is_float() || is_ip() || is_date_v2_or_datetime_v2();
    }
};

/// IDataType helpers (alternative for IDataType virtual methods with single point of truth)

#define IS_DATATYPE(name, method)                         \
    inline bool is_##name(const DataTypePtr& data_type) { \
        return WhichDataType(data_type).is_##method();    \
    }

IS_DATATYPE(uint8, uint8)
IS_DATATYPE(uint16, uint16)
IS_DATATYPE(uint32, uint32)
IS_DATATYPE(uint64, uint64)
IS_DATATYPE(uint128, uint128)
IS_DATATYPE(int8, int8)
IS_DATATYPE(int16, int16)
IS_DATATYPE(int32, int32)
IS_DATATYPE(int64, int64)
IS_DATATYPE(int128, int128)
IS_DATATYPE(date, date)
IS_DATATYPE(date_v2, date_v2)
IS_DATATYPE(date_time_v2, date_time_v2)
IS_DATATYPE(date_or_datetime, date_or_datetime)
IS_DATATYPE(date_v2_or_datetime_v2, date_v2_or_datetime_v2)
IS_DATATYPE(decimal, decimal)
IS_DATATYPE(decimal_v2, decimal128v2)
IS_DATATYPE(tuple, tuple)
IS_DATATYPE(array, array)
IS_DATATYPE(map, map)
IS_DATATYPE(struct, struct)
IS_DATATYPE(ipv4, ipv4)
IS_DATATYPE(ipv6, ipv6)
IS_DATATYPE(ip, ip)
IS_DATATYPE(nothing, nothing)

template <typename T>
bool is_uint8(const T& data_type) {
    return WhichDataType(data_type).is_uint8();
}

template <typename T>
bool is_unsigned_integer(const T& data_type) {
    return WhichDataType(data_type).is_uint();
}

template <typename T>
bool is_integer(const T& data_type) {
    WhichDataType which(data_type);
    return which.is_int() || which.is_uint();
}

template <typename T>
bool is_float(const T& data_type) {
    WhichDataType which(data_type);
    return which.is_float();
}

template <typename T>
bool is_native_number(const T& data_type) {
    WhichDataType which(data_type);
    return which.is_native_int() || which.is_native_uint() || which.is_float();
}

template <typename T>
bool is_number(const T& data_type) {
    WhichDataType which(data_type);
    return which.is_int() || which.is_uint() || which.is_float() || which.is_decimal();
}

template <typename T>
bool is_columned_as_number(const T& data_type) {
    WhichDataType which(data_type);
    return which.is_int() || which.is_uint() || which.is_float() || which.is_date_or_datetime() ||
           which.is_date_v2_or_datetime_v2();
}

template <typename T>
bool is_string(const T& data_type) {
    return WhichDataType(data_type).is_string();
}

template <typename T>
bool is_fixed_string(const T& data_type) {
    return WhichDataType(data_type).is_fixed_string();
}

template <typename T>
bool is_string_or_fixed_string(const T& data_type) {
    return WhichDataType(data_type).is_string_or_fixed_string();
}

template <typename T>
bool is_fixed_length_object(const T& data_type) {
    return WhichDataType(data_type).is_fixed_length_object();
}

inline bool is_not_decimal_but_comparable_to_decimal(const DataTypePtr& data_type) {
    WhichDataType which(data_type);
    return which.is_int() || which.is_uint();
}

inline bool is_complex_type(const DataTypePtr& data_type) {
    WhichDataType which(data_type);
    return which.is_array() || which.is_map() || which.is_struct();
}

inline bool is_variant_type(const DataTypePtr& data_type) {
    return WhichDataType(data_type).is_variant_type();
}

// write const_flag and row_num to buf, and return real_need_copy_num
char* serialize_const_flag_and_row_num(const IColumn** column, char* buf,
                                       size_t* real_need_copy_num);
const char* deserialize_const_flag_and_row_num(const char* buf, MutableColumnPtr* column,
                                               size_t* real_have_saved_num);
} // namespace vectorized

#include "common/compile_check_end.h"
} // namespace doris
