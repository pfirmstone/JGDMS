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

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.object.fixtures.Chain;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CUMULATIVE nested-decode depth (DoS) guard, STD-008 sec.16 / sec.15.3.
 *
 * <p>The nested-object recursion flows through the constructor: an object's
 * {@code (GetArg)} ctor calls {@code arg.get("next")}, which decodes the next nested
 * record. The depth guard is therefore only effective if the depth is threaded through
 * {@code DerGetArg} into {@code ObjectCodec.decodeNested}; a naive implementation that
 * passes {@code 0} each time resets the counter every level, so the guard never trips and
 * a hostile deeply-nested payload overflows the stack.
 *
 * <p>The encoder caps nesting at {@code MAX_NESTING}, so an over-deep payload cannot be
 * produced through the codec; this test hand-builds the DER (as a hostile sender could) to
 * prove the DECODER rejects it. A {@code MAX_NESTING}-deep payload still decodes; one level
 * deeper is rejected with a checked exception (not a {@code StackOverflowError}).
 */
class NestedDepthGuardTest {

    /** The nested-record schema bytes for {@link Chain} (leaf-first concat, single class). */
    private static byte[] chainSchemaBytes() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(Chain.class);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (AtomicSerialSchemaRecord r : chain.chain()) {
            buf.write(r.encode());
        }
        return buf.toByteArray();
    }

    /**
     * Builds the nested-record TLV (as produced by {@code ObjectCodec.encodeNested}) for a
     * {@code Chain} of {@code n} links (innermost {@code next == null}).
     * Record shape: {@code SEQUENCE { OCTET STRING(schema), OCTET STRING(payload) }} where
     * {@code payload = SEQUENCE { SEQUENCE { <next field TLV> } }} (outer hierarchy SEQUENCE
     * wrapping the single Chain class SEQUENCE whose sole field is {@code next}).
     */
    private static byte[] buildNestedChain(int n, byte[] schema) {
        byte[] nextTlv = new byte[]{0x05, 0x00}; // innermost next == null (DER NULL)
        byte[] record = null;
        for (int i = 0; i < n; i++) {
            byte[] chainSeq = DerWriter.writeSequence(List.of(nextTlv));   // Chain class SEQUENCE
            byte[] payload  = DerWriter.writeSequence(List.of(chainSeq));  // outer hierarchy SEQUENCE
            record = DerWriter.writeSequence(List.of(
                    DerWriter.writeOctetString(schema),
                    DerWriter.writeOctetString(payload)));
            nextTlv = record; // next level's "next" field is this record
        }
        return record;
    }

    @Test
    void atMaxNestingDecodes() throws Exception {
        byte[] schema = chainSchemaBytes();
        // ObjectCodec.MAX_NESTING links: the deepest decodeHierarchy is exactly MAX_NESTING.
        byte[] record = buildNestedChain(ObjectCodec.MAX_NESTING, schema);
        Object decoded = ObjectCodec.decodeNested(record, 0);
        assertNotNull(decoded);
        assertEquals(ObjectCodec.MAX_NESTING, ((Chain) decoded).length(),
                "a MAX_NESTING-deep chain must decode fully");
    }

    @Test
    void overMaxNestingIsRejected() throws Exception {
        byte[] schema = chainSchemaBytes();
        byte[] record = buildNestedChain(ObjectCodec.MAX_NESTING + 1, schema);
        // Must be a checked exception from the cumulative depth guard -- NOT a
        // StackOverflowError, and NOT a successful decode (which is what happens if the
        // depth resets to 0 each level).
        assertThrows(IOException.class, () -> ObjectCodec.decodeNested(record, 0),
                "a payload one level deeper than MAX_NESTING must be rejected fail-secure");
    }
}
