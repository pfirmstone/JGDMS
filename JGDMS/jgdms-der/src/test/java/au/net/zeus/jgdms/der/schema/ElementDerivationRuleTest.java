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

package au.net.zeus.jgdms.der.schema;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.schema.ruletypes.RuleEdgeCases;
import au.net.zeus.jgdms.der.schema.ruletypes.RuleFoo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Element-derivation RULE conformance tests (memo §3, §6 edge-case table E1--E15). Each case
 * reflects a declared field's generic {@link Type} and asserts {@link SchemaGenerator#toWireType(Type, Class)}
 * produces the pinned wire-type. The five unresolvable cases (E10--E14) MUST resolve to
 * {@link SchemaGenerator#ANY}; the resolvable cases MUST NOT.
 */
class ElementDerivationRuleTest {

    private static final String FOO = RuleFoo.class.getName();

    private static String rule(String fieldName) throws Exception {
        Field f = RuleEdgeCases.class.getDeclaredField(fieldName);
        return SchemaGenerator.toWireType(f.getGenericType(), RuleEdgeCases.class);
    }

    // --- E1--E9, E15: resolvable, fully typed (NOT Any) -----------------------

    @Test void e1_setOfAtomicSerial() throws Exception {
        assertEquals("set:@AtomicSerial", rule("e1"), "E1 Set<RuleFoo> must stay typed");
    }

    @Test void e2_setOfString() throws Exception {
        assertEquals("set:java.lang.String", rule("e2"));
    }

    @Test void e3_listOfInt() throws Exception {
        // Integer -> scalar "int"; List preserve -> list:
        assertEquals("list:int", rule("e3"));
    }

    @Test void e4_mapStringToFoo() throws Exception {
        assertEquals("map:{java.lang.String}{@AtomicSerial}", rule("e4"),
                "E4 map K,V resolved independently");
    }

    @Test void e5_deepNesting() throws Exception {
        assertEquals("map:{java.lang.String}{list:set:@AtomicSerial}", rule("e5"),
                "E5 deep nested generics fully recovered");
    }

    @Test void e6_disciplinePerLevel() throws Exception {
        // Outer LinkedHashMap -> orderedmap (preserve); inner HashSet -> set (canonicalise).
        assertEquals("orderedmap:{java.lang.String}{set:@AtomicSerial}", rule("e6"),
                "E6 discipline and element type compose independently per level");
    }

    @Test void e7_boundedWildcard_staysTyped_notAny() throws Exception {
        // The subtle WIN: Set<? extends Shape> -> set:@AtomicSerial (upper bound), NOT Any.
        assertEquals("set:@AtomicSerial", rule("e7"),
                "E7 bounded wildcard must stay typed (upper bound), never Any");
        assertNotEquals(SchemaGenerator.ANY, SchemaGenerator.toWireType(
                RuleEdgeCases.class.getDeclaredField("e7").getGenericType(), RuleEdgeCases.class));
    }

    @Test void e8_setOfReifiedArrayElement() throws Exception {
        // Set<RuleFoo[]>: element is the reified array RuleFoo[] -> set:array:@AtomicSerial:<class>
        assertEquals("set:array:@AtomicSerial:" + FOO, rule("e8"),
                "E8 array element is reified through the Class door");
    }

    @Test void e9_arrayOfCollection() throws Exception {
        // Set<RuleFoo>[]: GenericArrayType comp Set<RuleFoo> -> array:set:@AtomicSerial
        assertEquals("array:set:@AtomicSerial", rule("e9"),
                "E9 array-of-collection recurses into the generic component");
    }

    @Test void e15_plainAtomicSerialArray() throws Exception {
        assertEquals("array:@AtomicSerial:" + FOO, rule("e15"),
                "E15 plain reified array unchanged");
    }

    // --- E10--E14: the ONLY paths to Any, each genuinely unresolvable ---------
    //
    // The FIELD token is set:any (a Set whose ELEMENT is the Any form); "any" is the element
    // wire-type the rule selects. The set: discipline still comes from the declared class (a raw
    // Set/HashSet is canonicalise). Assert the element is Any via CollectionWireTypes.elementWireType.

    private static String elementOf(String setToken) {
        return au.net.zeus.jgdms.der.getarg.CollectionWireTypes.elementWireType(setToken);
    }

    @Test void e10_rawCollection_toAny() throws Exception {
        // A raw Set field -- getGenericType() is a plain Class, no element type -> element Any.
        assertEquals("set:any", rule("e10"), "E10 raw Set -> set:any");
        assertEquals(SchemaGenerator.ANY, elementOf(rule("e10")), "E10 element is Any");
    }

    @Test void e11_unboundedWildcard_toAny() throws Exception {
        assertEquals(SchemaGenerator.ANY, elementOf(rule("e11")), "E11 Set<?> element -> Any");
    }

    @Test void e12_objectElement_toAny() throws Exception {
        assertEquals(SchemaGenerator.ANY, elementOf(rule("e12")), "E12 Set<Object> element -> Any");
    }

    @Test void e13_lowerBoundedWildcard_toAny() throws Exception {
        assertEquals(SchemaGenerator.ANY, elementOf(rule("e13")),
                "E13 Set<? super Integer> element -> Any (lower bound gives no element type)");
    }

    @Test void e14_typeVariable_toAny() throws Exception {
        Field vals = RuleEdgeCases.Box.class.getDeclaredField("vals");
        String wt = SchemaGenerator.toWireType(vals.getGenericType(), RuleEdgeCases.Box.class);
        assertEquals("set:any", wt, "E14 Set<T> field -> set:any");
        assertEquals(SchemaGenerator.ANY, elementOf(wt),
                "E14 Set<T> in a generic class element -> Any (honest boundary; annotation can't fix it)");
    }

    // --- Totality: every field resolves, none throws for 'can't tell' ---------

    @Test void ruleIsTotal_neverThrowsForUnresolvable() throws Exception {
        for (String f : new String[]{"e10", "e11", "e12", "e13"}) {
            assertDoesNotThrow(() -> rule(f), "'can't tell' is the well-defined Any outcome, not an error");
        }
    }

    // --- The raw-Class overload still rejects a genuinely unsupported type ----

    @Test void rawClassOverload_stillRejectsUnsupported() {
        assertThrows(DerException.class,
                () -> SchemaGenerator.toWireType(Thread.class, RuleEdgeCases.class),
                "a concrete non-@AtomicSerial, non-scalar Class is still a hard error (not Any)");
    }
}
