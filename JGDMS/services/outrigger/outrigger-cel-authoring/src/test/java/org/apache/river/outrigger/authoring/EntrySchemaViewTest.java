/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.outrigger.authoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.jini.core.entry.Entry;

import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.cel.verifier.DerSchemaChainView;
import au.net.zeus.jgdms.cel.verifier.SchemaView;
import au.net.zeus.jgdms.der.entry.EntrySchemaGenerator;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;

/**
 * The drift guard (SOW Part B, unit B1): {@link EntrySchemaView} must agree,
 * field-for-field and type-for-type, with {@link EntrySchemaGenerator} — the
 * single reflective view the wire body and the server admission seam also use.
 * A divergence here would silently break authoring-vs-wire type-checking.
 */
public class EntrySchemaViewTest {

    public static class Base implements Entry {
        public String a;
        public String b;
        public Base() {}
    }

    /** Adds subclass field c and a subclass-only field of a non-String type. */
    public static class Sub extends Base {
        public String c;
        public Long n;   // boxed Long -> not directly a CEL scalar token
        public Sub() {}
    }

    @Test
    public void agreesWithGeneratorFieldSetAndOrder() throws Exception {
        Class<Sub> type = Sub.class;
        EntrySchemaView view = EntrySchemaView.of(type);

        // Reference view built directly from the generator's own chain.
        EntrySchemaGenerator.EntrySchema schema = EntrySchemaGenerator.forClass(type);
        List<AtomicSerialSchemaRecord> chain = schema.chain().chain();
        SchemaView reference = new DerSchemaChainView(chain);

        // Same namespace chain (declaring classes, leaf-first).
        assertEquals(reference.namespaceChain(), view.namespaceChain());

        // Every field the generator considers usable is declared by the view,
        // with the identical CelType resolution.
        for (Field f : EntrySchemaGenerator.orderedUsableFields(type)) {
            String owner = f.getDeclaringClass().getName();
            String name = f.getName();
            assertTrue(view.declaresField(owner, name),
                    "view must declare " + owner + "." + name);
            assertEquals(reference.fieldType(owner, name), view.fieldType(owner, name),
                    "CelType must agree for " + owner + "." + name);
        }
    }

    @Test
    public void resolvesKnownScalarTypes() {
        EntrySchemaView view = EntrySchemaView.of(Base.class);
        String owner = Base.class.getName();
        assertEquals(CelType.STRING, view.fieldType(owner, "a").orElseThrow());
        assertEquals(CelType.STRING, view.fieldType(owner, "b").orElseThrow());
    }

    @Test
    public void rejectsNullEntryClass() {
        assertThrows(NullPointerException.class, () -> EntrySchemaView.of(null));
    }
}
