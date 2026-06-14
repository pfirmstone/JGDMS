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

package au.net.zeus.jgdms.der.evolution;

import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.evolution.fixtures.LeakLeaf;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * §3.9 namespace-isolation regression for the §11.6 "inserted @AtomicSerial class"
 * evolution path.
 *
 * <p>Scenario: the current code's hierarchy is {@code LeakLeaf -> LeakMid -> LeakRoot}
 * (all @AtomicSerial). The OLD wire data predates {@code LeakMid}, so its embedded
 * schema chain is {@code [LeakLeaf, LeakRoot]} with NO {@code LeakMid} SEQUENCE.
 * {@code LeakLeaf} and {@code LeakMid} both declare a field named {@code "shared"} in
 * their (independent, §3.9) namespaces.
 *
 * <p>When such old data is decoded against the new hierarchy, {@code LeakMid}'s
 * constructor calls {@code arg.get("shared", "MID_DEFAULT")}. Because {@code LeakMid}'s
 * SEQUENCE is absent, the correct result is the DEFAULT — {@code LeakMid}'s namespace
 * is simply absent. It must NOT resolve to {@code LeakLeaf}'s store (which DOES have a
 * "shared" value), which would be a namespace leak.
 *
 * <p>Before the fix, {@code DerGetArg.callerClass()} returned the first stack frame
 * whose class was a key in the store map; since {@code LeakMid} was absent from the map,
 * its frame was skipped and {@code LeakLeaf}'s store was used — leaking
 * {@code LeakLeaf.shared} into {@code LeakMid.shared}. This test asserts the default,
 * so it FAILS on the buggy code and PASSES once every class in the receiver hierarchy
 * has a (possibly empty) store entry.
 */
class NamespaceLeakRegressionTest {

    @Test
    void test_insertedClass_absentFromOldData_doesNotLeakNeighbourNamespace() throws Exception {
        String leafName = LeakLeaf.class.getName();
        String rootName = au.net.zeus.jgdms.der.evolution.fixtures.LeakRoot.class.getName();

        // OLD embedded chain: [LeakLeaf, LeakRoot] — LeakMid did not exist yet.
        AtomicSerialSchemaRecord leafRec = new AtomicSerialSchemaRecord(
                leafName, (byte[]) null,
                List.of(new AtomicSerialFieldDef("shared", "java.lang.String")));
        AtomicSerialSchemaRecord rootRec = new AtomicSerialSchemaRecord(
                rootName, (byte[]) null,
                List.of(new AtomicSerialFieldDef("rootName", "java.lang.String")));
        SchemaChain.Result embeddedChain =
                SchemaChain.linkAndGetLeafDigest(List.of(leafRec, rootRec)); // leaf-first

        // Payload in superclass-first (root-first) wire order: [LeakRoot SEQ, LeakLeaf SEQ]
        byte[] rootSeq = DerWriter.writeSequence(List.of(DerWriter.writeUtf8String("the-root")));
        byte[] leafSeq = DerWriter.writeSequence(List.of(DerWriter.writeUtf8String("LEAF_VAL")));
        byte[] payload = DerWriter.writeSequence(List.of(rootSeq, leafSeq));

        LeakLeaf decoded = ObjectCodec.decodeHierarchy(LeakLeaf.class, embeddedChain, payload);

        // LeakLeaf reads its OWN "shared":
        assertEquals("LEAF_VAL", decoded.getLeafShared(),
                "LeakLeaf.shared must come from LeakLeaf's own SEQUENCE");
        // LeakRoot reads its own field:
        assertEquals("the-root", decoded.getRootName(),
                "LeakRoot.rootName must come from LeakRoot's own SEQUENCE");
        // CRUX: LeakMid was absent from old data → its "shared" must DEFAULT, not leak
        // LeakLeaf's "shared".
        assertEquals("MID_DEFAULT", decoded.getMidShared(),
                "LeakMid.shared must be its DEFAULT — old data had no LeakMid SEQUENCE; "
                + "resolving to LeakLeaf's store would be a §3.9 namespace leak");
    }
}
