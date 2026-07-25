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

package au.net.zeus.jgdms.der.entry;

import au.net.zeus.jgdms.der.DerException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F3 drift guard (accepted by the board in lieu of extraction): pins that
 * {@link EntrySchemaGenerator}'s field-selection rules match the rules
 * {@code org.apache.river.outrigger.proxy.EntryRep} implements in release-8 outrigger-dl
 * ({@code getFields}/{@code usableField}/{@code FieldComparator}). The two MUST agree; if
 * either drifts, the encoded body and the decoded field positions diverge silently.
 *
 * <p>{@code EntryRep} lives in a different module (release 8) that jgdms-der cannot import,
 * so the expected outcomes are encoded here as the CONTRACT (super-first + alphabetical;
 * declaring-class namespace with no shadowing collapse; only public/non-static/non-final/
 * non-transient fields are usable; a public usable primitive field is illegal).
 */
public class EntrySchemaGeneratorFieldOrderTest {

    // Base with a mix of usable and non-usable fields.
    public static class Base {
        public String beta;                 // usable
        public String alpha;                // usable (alpha before beta within class)
        public static String ignoredStatic; // static -> not usable
        public final String ignoredFinal = "";     // final -> not usable
        public transient String ignoredTransient;   // transient -> not usable
        String ignoredPackagePrivate;       // non-public -> not usable
        public Base() {}
    }

    // Subclass: shadows 'alpha' (same name, different namespace) and adds 'gamma'.
    public static class Sub extends Base {
        public String alpha;   // shadows Base.alpha -- distinct position
        public String gamma;
        public Sub() {}
    }

    @Test
    public void orderIsSuperFirstThenAlphabeticalWithinClass() throws Exception {
        List<Field> f = EntrySchemaGenerator.orderedUsableFields(Sub.class);
        assertEquals(List.of("alpha", "beta", "alpha", "gamma"),
                f.stream().map(Field::getName).toList(),
                "Base fields (alpha,beta) first, then Sub fields (alpha,gamma) -- alpha within class");
        // The two 'alpha' fields are DISTINCT: different declaring classes / namespaces.
        assertEquals(Base.class, f.get(0).getDeclaringClass());
        assertEquals(Sub.class, f.get(2).getDeclaringClass());
    }

    @Test
    public void onlyPublicMutableInstanceFieldsAreUsable() throws Exception {
        List<Field> f = EntrySchemaGenerator.orderedUsableFields(Base.class);
        // static / final / transient / package-private are all excluded.
        assertEquals(List.of("alpha", "beta"), f.stream().map(Field::getName).toList());
    }

    @Test
    public void schemaChainRecordsCarryPerClassNamespaces() throws Exception {
        EntrySchemaGenerator.EntrySchema es = EntrySchemaGenerator.forClass(Sub.class);
        // leaf-first chain: [Sub, Base]. Sub record: alpha, gamma; Base record: alpha, beta.
        assertEquals("alpha", es.chain().chain().get(0).fields().get(0).wireName());
        assertEquals("gamma", es.chain().chain().get(0).fields().get(1).wireName());
        assertEquals("alpha", es.chain().chain().get(1).fields().get(0).wireName());
        assertEquals("beta", es.chain().chain().get(1).fields().get(1).wireName());
    }

    // A public usable field of primitive type is illegal in an Entry (EntryRep.usableField
    // throws IllegalArgumentException; here surfaced as a checked DerException).
    public static class BadPrimitive {
        public int notAllowed;
        public BadPrimitive() {}
    }

    @Test
    public void publicPrimitiveField_rejected() {
        DerException ex = assertThrows(DerException.class,
                () -> EntrySchemaGenerator.forClass(BadPrimitive.class));
        assertTrue(ex.getMessage().contains("primitive"),
                "a public primitive Entry field must be rejected: " + ex.getMessage());
    }

    @Test
    public void determinism_sameClassSameDigest() throws Exception {
        byte[] d1 = EntrySchemaGenerator.forClass(Sub.class).entrySchemaDigest();
        byte[] d2 = EntrySchemaGenerator.forClass(Sub.class).entrySchemaDigest();
        assertEquals(java.util.Arrays.toString(d1), java.util.Arrays.toString(d2),
                "entrySchemaDigest is deterministic per class");
    }
}
