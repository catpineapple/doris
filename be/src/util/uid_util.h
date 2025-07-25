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

#pragma once

#include <gen_cpp/Types_types.h>
#include <gen_cpp/types.pb.h>
#include <stdint.h>

#include <boost/random/mersenne_twister.hpp>
#include <boost/uuid/random_generator.hpp>
#include <boost/uuid/uuid.hpp>
#include <boost/uuid/uuid_io.hpp>
#include <cstring>
#include <ostream>
#include <string>
#include <string_view>

#include "util/uuid_generator.h"

namespace doris {

// convert int to a hex format string, buf must enough to hold converted hex string
template <typename T>
void to_hex(T val, char* buf) {
    static const char* digits = "0123456789abcdef";
    for (int i = 0; i < 2 * sizeof(T); ++i) {
        buf[2 * sizeof(T) - 1 - i] = digits[val & 0x0F];
        val >>= 4;
    }
}

template <typename T>
void from_hex(T* ret, std::string_view buf) {
    T val = 0;
    for (char i : buf) {
        int buf_val = 0;
        if (i >= '0' && i <= '9') {
            buf_val = i - '0';
        } else {
            buf_val = i - 'a' + 10;
        }
        val <<= 4;
        val = val | buf_val;
    }
    *ret = val;
}

struct UniqueId {
    int64_t hi = 0;
    int64_t lo = 0;

    UniqueId() = default;
    UniqueId(int64_t hi_, int64_t lo_) : hi(hi_), lo(lo_) {}
    UniqueId(const UniqueId& uid) : hi(uid.hi), lo(uid.lo) {}
    UniqueId(const TUniqueId& tuid) : hi(tuid.hi), lo(tuid.lo) {}
    UniqueId(const PUniqueId& puid) : hi(puid.hi()), lo(puid.lo()) {}
    UniqueId(const std::string& hi_str, const std::string& lo_str) {
        from_hex(&hi, hi_str);
        from_hex(&lo, lo_str);
    }

    bool initialized() const { return hi != 0 || lo != 0; }

    // currently, the implementation is uuid, but it may change in the future
    static UniqueId gen_uid() {
        UniqueId uid(0, 0);
        auto uuid = UUIDGenerator::instance()->next_uuid();
        memcpy(&uid.hi, uuid.data, sizeof(int64_t));
        memcpy(&uid.lo, uuid.data + sizeof(int64_t), sizeof(int64_t));
        return uid;
    }

    ~UniqueId() noexcept {}

    std::string to_string() const {
        char buf[33];
        to_hex(hi, buf);
        buf[16] = '-';
        to_hex(lo, buf + 17);
        return {buf, 33};
    }

    UniqueId& operator=(const UniqueId uid) {
        hi = uid.hi;
        lo = uid.lo;
        return *this;
    }

    UniqueId& operator=(const PUniqueId puid) {
        hi = puid.hi();
        lo = puid.lo();
        return *this;
    }

    UniqueId& operator=(const TUniqueId tuid) {
        hi = tuid.hi;
        lo = tuid.lo;
        return *this;
    }
    //compare PUniqueId and UniqueId
    bool operator==(const PUniqueId& rhs) const { return hi == rhs.hi() && lo == rhs.lo(); }

    bool operator!=(const PUniqueId& rhs) const { return hi != rhs.hi() || lo != rhs.lo(); }

    // std::map std::set needs this operator
    bool operator<(const UniqueId& right) const {
        if (hi != right.hi) {
            return hi < right.hi;
        } else {
            return lo < right.lo;
        }
    }

    // std::unordered_map need this api
    size_t hash(size_t seed = 0) const;

    // std::unordered_map need this api
    bool operator==(const UniqueId& rhs) const { return hi == rhs.hi && lo == rhs.lo; }

    bool operator!=(const UniqueId& rhs) const { return hi != rhs.hi || lo != rhs.lo; }

    TUniqueId to_thrift() const {
        TUniqueId tid;
        tid.__set_hi(hi);
        tid.__set_lo(lo);
        return tid;
    }

    PUniqueId to_proto() const {
        PUniqueId pid;
        pid.set_hi(hi);
        pid.set_lo(lo);
        return pid;
    }
};

// This function must be called 'hash_value' to be picked up by boost.
std::size_t hash_value(const doris::TUniqueId& id);

/// generates a 16 byte UUID
inline std::string generate_uuid_string() {
    return boost::uuids::to_string(boost::uuids::basic_random_generator<boost::mt19937>()());
}

/// generates a 16 byte UUID
inline TUniqueId generate_uuid() {
    auto uuid = boost::uuids::basic_random_generator<boost::mt19937>()();
    TUniqueId uid;
    memcpy(&uid.hi, uuid.data, sizeof(int64_t));
    memcpy(&uid.lo, uuid.data + sizeof(int64_t), sizeof(int64_t));
    return uid;
}

std::ostream& operator<<(std::ostream& os, const UniqueId& uid);

std::string print_id(const UniqueId& id);
std::string print_id(const TUniqueId& id);
std::string print_id(const PUniqueId& id);

// Parse 's' into a TUniqueId object.  The format of s needs to be the output format
// from PrintId.  (<hi_part>:<low_part>)
// Returns true if parse succeeded.
bool parse_id(const std::string& s, TUniqueId* id);

} // namespace doris

template <>
struct std::hash<doris::UniqueId> {
    size_t operator()(const doris::UniqueId& uid) const { return uid.hash(); }
};
