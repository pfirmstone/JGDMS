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

package au.net.zeus.jgdms.cel.verifier;

import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.cel.testsupport.TestDer;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DerSchemaChainView}: the adapter from a real {@code jgdms-der}
 * {@code AtomicSerialSchemaRecord} chain to {@link SchemaView}, including
 * its wire-type -> {@link CelType} mapping table ({@code
 * SchemaGenerator}'s documented token vocabulary) and its documented
 * nested-schema limitation.
 */
class DerSchemaChainViewTest {

    @Test
    void wireTypeMapping_resolvableTokens() {
        assertEquals(Optional.of(CelType.BOOL), DerSchemaChainView.celTypeOfWireType("boolean"));
        assertEquals(Optional.of(CelType.INT), DerSchemaChainView.celTypeOfWireType("byte"));
        assertEquals(Optional.of(CelType.INT), DerSchemaChainView.celTypeOfWireType("short"));
        assertEquals(Optional.of(CelType.INT), DerSchemaChainView.celTypeOfWireType("int"));
        assertEquals(Optional.of(CelType.INT), DerSchemaChainView.celTypeOfWireType("long"));
        assertEquals(Optional.of(CelType.DOUBLE), DerSchemaChainView.celTypeOfWireType("double"));
        assertEquals(Optional.of(CelType.DOUBLE), DerSchemaChainView.celTypeOfWireType("float"));
        assertEquals(Optional.of(CelType.STRING), DerSchemaChainView.celTypeOfWireType("java.lang.String"));
        assertEquals(Optional.of(CelType.BYTES), DerSchemaChainView.celTypeOfWireType("byte[]"));
        assertEquals(Optional.of(CelType.OBJECT), DerSchemaChainView.celTypeOfWireType("@AtomicSerial"));
    }

    @Test
    void wireTypeMapping_unresolvableTokensDeferToUnknown() {
        assertEquals(Optional.empty(), DerSchemaChainView.celTypeOfWireType("char"));
        assertEquals(Optional.empty(), DerSchemaChainView.celTypeOfWireType("enum:com.example.Color"));
        assertEquals(Optional.empty(), DerSchemaChainView.celTypeOfWireType("array:int"));
        assertEquals(Optional.empty(), DerSchemaChainView.celTypeOfWireType("set:int"));
        assertEquals(Optional.empty(), DerSchemaChainView.celTypeOfWireType("list:java.lang.String"));
        assertEquals(Optional.empty(), DerSchemaChainView.celTypeOfWireType("any"));
        assertEquals(Optional.empty(), DerSchemaChainView.celTypeOfWireType("java.lang.Class"));
    }

    @Test
    void namespaceChainAndFieldLookup_leafFirst() {
        AtomicSerialSchemaRecord root = new AtomicSerialSchemaRecord("com.example.Base",
                List.of(new AtomicSerialFieldDef("id", "long")));
        AtomicSerialSchemaRecord leaf = new AtomicSerialSchemaRecord("com.example.Leaf", (byte[]) null,
                List.of(new AtomicSerialFieldDef("name", "java.lang.String"),
                        new AtomicSerialFieldDef("payload", "any")));

        DerSchemaChainView view = new DerSchemaChainView(List.of(leaf, root));

        assertEquals(List.of("com.example.Leaf", "com.example.Base"), view.namespaceChain());
        assertTrue(view.declaresField("com.example.Leaf", "name"));
        assertTrue(view.declaresField("com.example.Base", "id"));
        assertFalse(view.declaresField("com.example.Base", "name"));
        assertFalse(view.declaresField("com.example.Leaf", "nonexistent"));

        assertEquals(Optional.of(CelType.STRING), view.fieldType("com.example.Leaf", "name"));
        assertEquals(Optional.of(CelType.INT), view.fieldType("com.example.Base", "id"));
        assertEquals(Optional.empty(), view.fieldType("com.example.Leaf", "payload"), "\"any\" defers, never guesses");

        assertTrue(view.nestedSchema("com.example.Leaf", "name").isEmpty(),
                "documented limitation: no nested schema is ever resolvable through this adapter");
    }

    @Test
    void endToEnd_verifyAgainstRealSchemaChain() {
        AtomicSerialSchemaRecord record = new AtomicSerialSchemaRecord("com.example.Reading",
                List.of(new AtomicSerialFieldDef("temperatureC", "double")));
        DerSchemaChainView view = new DerSchemaChainView(List.of(record));

        byte[] expr = TestDer.gt(TestDer.fieldRef("temperatureC"), TestDer.litInt(0)); // int vs double field -- allowed cross-pair
        byte[] wire = TestDer.wrapAsPredicateRecord(expr);

        VerificationResult result = CelVerifier.verify(wire, view);
        assertTrue(result.accepted(), () -> "expected acceptance, got: " + result);
    }

    @Test
    void constructorRejectsEmptyChain() {
        assertThrows(IllegalArgumentException.class, () -> new DerSchemaChainView(List.of()));
    }
}
