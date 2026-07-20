/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.cel;

/**
 * The closed error set of JGDMS-STD-011 §9.2. Evaluation is total: it yields
 * a typed value or exactly one of these eight codes, never anything else.
 * The set is closed by spec -- no implementation may add codes, and every
 * abnormal condition in the standard is mapped to one of these.
 */
public enum CelError {

    /**
     * An operand/argument/receiver has a type outside the operator's or
     * function's definition (§6, §7), including operations on {@code null}
     * beyond {@code ==}/{@code !=}, on {@code object}, on {@code list} beyond
     * §4.7, and a non-{@code bool} predicate result (§9.4).
     */
    TYPE_MISMATCH,

    /**
     * Checked {@code int} arithmetic out of range (§4.3.1), {@code abs(-2^63)},
     * or {@code int(x)} out of range (§7.2 row 4).
     */
    OVERFLOW,

    /** {@code int} {@code /} or {@code %} with a zero divisor. */
    DIVISION_BY_ZERO,

    /**
     * A value reference to an absent field, or through an absent/null
     * intermediate (§4.6, §8.4).
     */
    ABSENT_FIELD,

    /**
     * An unqualified name declared by two or more namespaces in the relevant
     * schema chain (§8.2).
     */
    AMBIGUOUS_FIELD,

    /**
     * A platform-function argument outside its domain, including NaN, and
     * ±Inf for finite-only functions (§7.1, §7.2).
     */
    DOMAIN,

    /**
     * The statically computed cost bound was exceeded. Per §10.5 this cannot
     * occur for a verified (T6-accepted) expression; an implementation MAY
     * still meter cost at runtime as defense in depth and raise this if the
     * meter is exhausted.
     */
    COST_BOUND,

    /** The candidate failed fail-closed projection (§8.1). */
    CANDIDATE_UNDECODABLE
}
