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

package au.net.zeus.jgdms.der.schema.ruletypes;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Declares one field per element-derivation-rule edge case (memo §6, E1--E15). The
 * {@code ElementDerivationRuleTest} reflects each field's declared generic {@link java.lang.reflect.Type}
 * via {@code getDeclaredField(name).getGenericType()} and asserts {@code SchemaGenerator.toWireType(Type,..)}
 * yields the pinned outcome. The fields are never read at runtime; only their DECLARED signatures matter
 * (the whole point of the rule -- erasure erases instances, not declarations).
 *
 * <p>{@code @SuppressWarnings} is applied broadly because several of these declarations (raw
 * collections, unbounded/lower wildcards, {@code Object}-element collections) are intentionally the
 * "bad" declarations that resolve to {@code Any}, and the compiler rightly warns on them.
 */
@SuppressWarnings({"unused", "rawtypes"})
public final class RuleEdgeCases {

    // E1 -- Set<RuleFoo> (RuleFoo @AtomicSerial)     -> set:@AtomicSerial
    public Set<RuleFoo> e1;
    // E2 -- Set<String>                              -> set:java.lang.String
    public Set<String> e2;
    // E3 -- List<Integer>                            -> list:int
    public List<Integer> e3;
    // E4 -- Map<String,RuleFoo>                      -> map:{java.lang.String}{@AtomicSerial}
    public Map<String, RuleFoo> e4;
    // E5 -- Map<String,List<Set<RuleFoo>>>           -> map:{java.lang.String}{list:set:@AtomicSerial}
    public Map<String, List<Set<RuleFoo>>> e5;
    // E6 -- LinkedHashMap<String,HashSet<RuleFoo>>   -> orderedmap:{java.lang.String}{set:@AtomicSerial}
    public LinkedHashMap<String, HashSet<RuleFoo>> e6;
    // E7 -- Set<? extends Shape>                     -> set:@AtomicSerial  (upper bound, NOT Any)
    public Set<? extends Shape> e7;
    // E8 -- Set<RuleFoo[]>                           -> set:array:@AtomicSerial:...RuleFoo
    public Set<RuleFoo[]> e8;
    // E9 -- Set<RuleFoo>[]  (array field)            -> array:set:@AtomicSerial
    public Set<RuleFoo>[] e9;
    // E10 -- Set (raw)                               -> Any
    public Set e10;
    // E11 -- Set<?>                                  -> Any
    public Set<?> e11;
    // E12 -- Set<Object>                             -> Any
    public Set<Object> e12;
    // E13 -- Set<? super Integer>                    -> Any
    public Set<? super Integer> e13;
    // E15 -- RuleFoo[]  (plain array field)          -> array:@AtomicSerial:...RuleFoo
    public RuleFoo[] e15;

    /**
     * E14 -- a type variable in a generic class: {@code Box<T> { Set<T> vals; }}. The concrete
     * {@code T} is known only at the use site, which a per-class schema cannot see, so
     * {@code Set<T>} resolves to {@code Any} (the honest boundary; an annotation could not fix it).
     */
    public static final class Box<T> {
        public Set<T> vals;
    }

    private RuleEdgeCases() {}
}
