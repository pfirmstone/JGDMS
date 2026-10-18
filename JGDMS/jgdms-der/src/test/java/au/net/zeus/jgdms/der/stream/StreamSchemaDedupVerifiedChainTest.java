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

package au.net.zeus.jgdms.der.stream;

import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SOW-DER-Codec-Performance T3: {@link StreamSchemaDedup#verifiedChain} may only vouch for
 * chain bytes this stream already verified, byte-for-byte; and the verified top-level chain
 * handed back by reconstitution must be the chain of the record it reconstituted.
 */
class StreamSchemaDedupVerifiedChainTest {

    private static final Tag FULL = new Tag(Tag.CLASS_CONTEXT, false, 0);
    private static final Tag REF  = new Tag(Tag.CLASS_CONTEXT, false, 1);

    private static byte[] linked(String prefix, int n) {
        List<AtomicSerialSchemaRecord> records = new ArrayList<>();
        for (int i = 0; i < n; i++) records.add(new AtomicSerialSchemaRecord(prefix + i, List.of()));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (AtomicSerialSchemaRecord r : SchemaChain.linkAndGetLeafDigest(records).chain()) {
            buf.writeBytes(r.encode());
        }
        return buf.toByteArray();
    }

    private static byte[] hierarchy(int classCount) {
        List<byte[]> seqs = new ArrayList<>();
        for (int i = 0; i < classCount; i++) seqs.add(DerWriter.writeSequence(List.of()));
        return DerWriter.writeSequence(seqs);
    }

    private static byte[] p1(Tag arm, byte[] armContent, int classCount) {
        List<byte[]> kids = new ArrayList<>();
        kids.add(DerWriter.writeOctetString(hierarchy(classCount)));
        kids.add(DerWriter.writeTlv(arm, armContent));
        kids.add(DerWriter.writeUtf8String("JGDMS-STD-006/ATOMIC-DER"));
        return DerWriter.writeSequence(kids);
    }

    private static byte[] leafDigest(byte[] chain) throws Exception {
        // first complete SEQUENCE TLV of the chain
        int hdr = 2;
        int len = chain[1] & 0xFF;
        if (len >= 0x80) {
            int n = len & 0x7F;
            len = 0;
            for (int i = 0; i < n; i++) len = (len << 8) | (chain[2 + i] & 0xFF);
            hdr = 2 + n;
        }
        return MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(chain, hdr + len));
    }

    @Test
    void tabledChainIsFoundByContentAndOnlyByContent() throws Exception {
        byte[] chain = linked("vc.A", 3);
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        StreamSchemaDedup.Reconstituted rc = d.reconstituteTopLevelAtomicVerified(p1(FULL, chain, 3));

        SchemaChain.Result hit = d.verifiedChain(chain.clone());
        assertNotNull(hit, "a chain verified at a fullChain site must be found by its bytes");
        assertEquals(3, hit.chain().size());
        assertArrayEquals(leafDigest(chain), hit.leafDigest());

        assertNull(d.verifiedChain(new byte[0]), "empty bytes are never a tabled chain");
        assertNull(d.verifiedChain(linked("vc.B", 3)), "a different chain is not vouched for");

        byte[] flipped = chain.clone();
        flipped[flipped.length - 1] ^= 0x01;
        assertNull(d.verifiedChain(flipped), "one flipped bit in the tail is not vouched for");

        // same LEAF record, shorter tail: the leaf digest agrees with a tabled chain, the bytes do not
        byte[] truncated = Arrays.copyOf(chain, chain.length - 10);
        assertNull(d.verifiedChain(truncated), "a truncated chain sharing the leaf is not vouched for");
        byte[] extended = Arrays.copyOf(chain, chain.length + 4);
        assertNull(d.verifiedChain(extended), "trailing bytes after a tabled chain are not vouched for");

        // The table's digest (over the RECEIVED leaf bytes, sec.C.7.3) must equal the digest nested
        // decode used to compute by re-encoding the parsed leaf record: canonical decode makes
        // the two coincide, and the lookup relies on it.
        for (int i = 0; i < hit.chain().size(); i++) {
            assertArrayEquals(leafDigest(hit.chain().get(i).encode()), hit.chain().get(i).schemaDigest());
        }
        assertArrayEquals(hit.chain().get(0).schemaDigest(), hit.leafDigest(),
                "received-bytes digest must equal the re-encoded leaf digest");

        assertEquals(hit.chain(), rc.topChain().chain(), "reconstitution hands back the tabled chain");
        assertArrayEquals(hit.leafDigest(), rc.topChain().leafDigest());
    }

    @Test
    void topChainMatchesTheReconstitutedRecordBothArms() throws Exception {
        byte[] chain = linked("vc.C", 2);
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        StreamSchemaDedup.Reconstituted viaFull = d.reconstituteTopLevelAtomicVerified(p1(FULL, chain, 2));
        StreamSchemaDedup.Reconstituted viaRef =
                d.reconstituteTopLevelAtomicVerified(p1(REF, leafDigest(chain), 2));
        assertArrayEquals(viaFull.recordBytes(), viaRef.recordBytes(),
                "full and ref arms reconstitute byte-identical canonical records");
        assertEquals(viaFull.topChain().chain(), viaRef.topChain().chain());
        assertArrayEquals(viaFull.topChain().leafDigest(), viaRef.topChain().leafDigest());
        assertEquals("vc.C0", viaFull.topChain().chain().get(0).className());
    }

    @Test
    void handedOutDigestIsACopy() throws Exception {
        byte[] chain = linked("vc.D", 1);
        StreamSchemaDedup d = new StreamSchemaDedup(false);
        d.reconstituteTopLevelAtomicVerified(p1(FULL, chain, 1));
        byte[] first = d.verifiedChain(chain).leafDigest();
        Arrays.fill(first, (byte) 0);
        assertArrayEquals(leafDigest(chain), d.verifiedChain(chain).leafDigest(),
                "mutating a handed-out digest must not reach the table");
    }

    @Test
    void encodeDirectionNeverFeedsTheDecodeIndex() throws Exception {
        StreamSchemaDedup enc = new StreamSchemaDedup(true);
        byte[] chain = linked("vc.E", 1);
        List<byte[]> canonical = new ArrayList<>();
        canonical.add(DerWriter.writeOctetString(hierarchy(1)));
        canonical.add(DerWriter.writeOctetString(chain));
        canonical.add(DerWriter.writeOctetString(leafDigest(chain)));
        canonical.add(DerWriter.writeUtf8String("JGDMS-STD-006/ATOMIC-DER"));
        enc.dedupTopLevelAtomic(DerWriter.writeSequence(canonical));
        assertNull(enc.verifiedChain(chain), "verifiedChain is a decode-direction facility");
    }
}
