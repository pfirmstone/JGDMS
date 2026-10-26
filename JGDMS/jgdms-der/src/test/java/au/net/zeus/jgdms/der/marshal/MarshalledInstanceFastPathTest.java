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

package au.net.zeus.jgdms.der.marshal;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.marshal.fixtures.VersionedRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SOW-DER-Codec-Performance T4: the decode-side schema fast path may only accept
 * what the full path accepts. Equal-to-local chains decode identically on both
 * paths; any difference (tampered chain bytes, wrong schemaDigest field, evolved
 * sender) must still take the full path and still be rejected / handled as before.
 */
class MarshalledInstanceFastPathTest {

    private static final VersionedRecord OBJ = new VersionedRecord(7, "label", "extra");

    private static MarshalledInstanceRecord genuine() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(VersionedRecord.class);
        byte[] payload = ObjectCodec.encodeHierarchy(OBJ, chain);
        return MarshalledInstanceRecord.fromChain(chain, payload);
    }

    @Test
    void genuineRecordMatchesLocalAndDecodesAsCaseA() throws Exception {
        MarshalledInstanceRecord rec = genuine();
        assertTrue(rec.schemaBytesMatchLocal(VersionedRecord.class));
        MarshalledInstanceCodec.Result<VersionedRecord> r =
                MarshalledInstanceCodec.decodeMarshalledInstance(rec, VersionedRecord.class);
        assertEquals(OBJ, r.object());
        assertEquals(MarshalledInstanceCodec.SchemaCase.A_MATCH, r.schemaCase());
    }

    /** Both paths on the same input: the fast-path result equals a full-path decode. */
    @Test
    void fastPathResultEqualsFullPathResult() throws Exception {
        MarshalledInstanceRecord rec = genuine();
        VersionedRecord viaCodec =
                MarshalledInstanceCodec.decodeMarshalledInstance(rec, VersionedRecord.class).object();
        // full path, spelled out: parse + verify the embedded chain, decode with it
        SchemaChain.Result embedded = rec.decodeSchemaChainAsResult();
        VersionedRecord viaFull = ObjectCodec.decodeHierarchy(
                VersionedRecord.class, embedded, rec.payloadBytes());
        assertEquals(viaFull, viaCodec);
        assertEquals(OBJ, viaCodec);
    }

    @Test
    void tamperedChainBytesWithSameDigestFieldAreRejected() throws Exception {
        MarshalledInstanceRecord rec = genuine();
        byte[] chain = rec.schemaBytes();
        // a second copy of the (root) record appended: digest field and leaf are unchanged,
        // the bytes are not the canonical chain, and the full path rejects it.
        byte[] doubled = new byte[chain.length * 2];
        System.arraycopy(chain, 0, doubled, 0, chain.length);
        System.arraycopy(chain, 0, doubled, chain.length, chain.length);
        MarshalledInstanceRecord bad = new MarshalledInstanceRecord(
                rec.payloadBytes(), doubled, rec.schemaDigest(), rec.payloadFormat());
        assertFalse(bad.schemaBytesMatchLocal(VersionedRecord.class));
        assertThrows(DerException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(bad, VersionedRecord.class));
    }

    @Test
    void singleFlippedBitInChainIsNotFastPathed() throws Exception {
        MarshalledInstanceRecord rec = genuine();
        byte[] chain = rec.schemaBytes();
        for (int i = 0; i < chain.length; i++) {
            byte[] mutated = chain.clone();
            mutated[i] ^= 0x01;
            MarshalledInstanceRecord bad = new MarshalledInstanceRecord(
                    rec.payloadBytes(), mutated, rec.schemaDigest(), rec.payloadFormat());
            assertFalse(bad.schemaBytesMatchLocal(VersionedRecord.class), "bit flip at " + i);
        }
    }

    @Test
    void wrongSchemaDigestFieldIsRejected() throws Exception {
        MarshalledInstanceRecord rec = genuine();
        byte[] wrong = rec.schemaDigest();
        wrong[0] ^= (byte) 0xFF;
        MarshalledInstanceRecord bad = new MarshalledInstanceRecord(
                rec.payloadBytes(), rec.schemaBytes(), wrong, rec.payloadFormat());
        assertThrows(DerException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(bad, VersionedRecord.class));
    }

    @Test
    void emptyAndTruncatedChainAreNotFastPathedAndRejected() throws Exception {
        MarshalledInstanceRecord rec = genuine();
        byte[] chain = rec.schemaBytes();
        for (byte[] bytes : new byte[][] {new byte[0], Arrays.copyOf(chain, chain.length - 1)}) {
            MarshalledInstanceRecord bad = new MarshalledInstanceRecord(
                    rec.payloadBytes(), bytes, rec.schemaDigest(), rec.payloadFormat());
            assertFalse(bad.schemaBytesMatchLocal(VersionedRecord.class));
            assertThrows(DerException.class,
                    () -> MarshalledInstanceCodec.decodeMarshalledInstance(bad, VersionedRecord.class));
        }
    }

    @Test
    void chainForAnotherClassIsNotAMatch() throws Exception {
        MarshalledInstanceRecord rec = genuine();
        assertFalse(rec.schemaBytesMatchLocal(au.net.zeus.jgdms.der.object.fixtures.Foo.class));
        assertFalse(rec.schemaBytesMatchLocal(String.class));   // no local chain: never a match
    }

    // ---- verified-chain overload (stream layer hands on the chain it already verified) ----

    @Test
    void verifiedChainOverloadDecodesLikeSelfParsingPath() throws Exception {
        MarshalledInstanceRecord rec = genuine();
        SchemaChain.Result verified = rec.decodeSchemaChainAsResult();
        MarshalledInstanceCodec.Result<VersionedRecord> r = MarshalledInstanceCodec.decodeMarshalledInstance(
                rec, VersionedRecord.class, verified, null, au.net.zeus.jgdms.der.getarg.ResolutionContext.NONE);
        assertEquals(OBJ, r.object());
        assertEquals(MarshalledInstanceCodec.SchemaCase.A_MATCH, r.schemaCase());
    }

    @Test
    void verifiedChainWithDisagreeingDigestFieldIsRejected() throws Exception {
        MarshalledInstanceRecord rec = genuine();
        SchemaChain.Result verified = rec.decodeSchemaChainAsResult();
        byte[] wrong = rec.schemaDigest();
        wrong[5] ^= 0x7F;
        MarshalledInstanceRecord bad = new MarshalledInstanceRecord(
                rec.payloadBytes(), rec.schemaBytes(), wrong, rec.payloadFormat());
        assertThrows(DerException.class, () -> MarshalledInstanceCodec.decodeMarshalledInstance(
                bad, VersionedRecord.class, verified, null,
                au.net.zeus.jgdms.der.getarg.ResolutionContext.NONE));
    }

    // ---- the fast path must not skip the chain ceilings (independent review) ----

    /** A class whose locally generated chain exceeds maxChainBytes (65536): ~3000 fields with long names. */
    @org.apache.river.api.io.AtomicSerial
    public static final class OversizedChain {
        public static org.apache.river.api.io.AtomicSerial.SerialForm[] serialForm() {
            org.apache.river.api.io.AtomicSerial.SerialForm[] f =
                    new org.apache.river.api.io.AtomicSerial.SerialForm[3000];
            for (int i = 0; i < f.length; i++) {
                f[i] = new org.apache.river.api.io.AtomicSerial.SerialForm(
                        "field" + String.format("%05d", i) + "_padding_to_make_each_name_long", int.class);
            }
            return f;
        }
        public static void serialize(org.apache.river.api.io.AtomicSerial.PutArg arg, OversizedChain o)
                throws java.io.IOException {
            arg.writeArgs();
        }
        public OversizedChain() {}
        public OversizedChain(org.apache.river.api.io.AtomicSerial.GetArg arg)
                throws java.io.IOException, ClassNotFoundException {}
    }

    @Test
    void localChainBreachingACeilingIsRejectedOnBothPaths() throws Exception {
        SchemaChain.Result local = SchemaGenerator.generateChain(OversizedChain.class);
        byte[] chainBytes = SchemaGenerator.generateChainBytes(OversizedChain.class);
        assertTrue(chainBytes.length > 65536, "fixture must breach maxChainBytes, got " + chainBytes.length);
        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                new byte[0], chainBytes, local.leafDigest(), MarshalledInstanceRecord.PAYLOAD_FORMAT);

        // slow path, spelled out
        DerException slow = assertThrows(DerException.class, rec::decodeSchemaChainAsResult);
        assertTrue(slow.getMessage().contains("maxChainBytes"), slow.getMessage());

        // the codec (fast path candidate): same bytes as the local chain and a matching digest field,
        // yet it must still be rejected by the chain ceiling, not accepted because the chain is "local"
        DerException fast = assertThrows(DerException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(rec, OversizedChain.class));
        assertTrue(fast.getMessage().contains("maxChainBytes"),
                "fast path must apply the same ceiling as the slow path, got: " + fast.getMessage());
    }
}
