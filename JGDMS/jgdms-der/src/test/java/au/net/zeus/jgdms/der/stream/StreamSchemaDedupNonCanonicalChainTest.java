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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A schema chain must be strictly canonical (STD-006 Appendix C sec.C.5.1: "strict canonical DER inside
 * fullChain"; G1: one encoding per value). A chain whose field name carries invalid UTF-8 parses to the
 * same record as the genuine one (the string decoder substitutes U+FFFD) but is a DIFFERENT byte string,
 * so it must be rejected, not accepted with a different identity. Pins the review finding that the
 * verified-chain hand-off must not turn a digest-over-received-bytes into the only integrity check.
 */
class StreamSchemaDedupNonCanonicalChainTest {

    private static byte[] encode(Object... objs) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        for (Object o : objs) c.writeObject(o);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    /** Replaces the single-character field name 'y' inside the first fullChain (tag 0x80) by 0xFF. */
    private static byte[] corruptFieldName(byte[] stream) {
        // locate the fullChain arm: tag 0x80 followed by a DER length, whose content starts a SEQUENCE (0x30)
        for (int i = 0; i + 4 < stream.length; i++) {
            if (stream[i] == (byte) 0x80 && (stream[i + 1] & 0xFF) < 0x80 && stream[i + 2] == 0x30) {
                int len = stream[i + 1] & 0xFF;
                byte[] out = stream.clone();
                for (int j = i + 2; j < i + 2 + len; j++) {
                    // UTF8String tag 0x0C, length 1, content 'y'
                    if (out[j] == 0x0C && out[j + 1] == 0x01 && out[j + 2] == 'y') {
                        out[j + 2] = (byte) 0xFF;
                        return out;
                    }
                }
            }
        }
        fail("could not locate the field name 'y' inside the fullChain");
        return null;
    }

    @Test
    void invalidUtf8InAFullChainIsRejected() throws Exception {
        byte[] good = encode(new DerGoldenBytesTest.Point(7, 9));
        byte[] bad = corruptFieldName(good);

        DerObjectStreamCodec ok = new DerObjectStreamCodec();
        ok.initReader(good);
        assertNotNull(ok.readObject(), "the genuine stream still decodes");

        DerObjectStreamCodec in = new DerObjectStreamCodec();
        in.initReader(bad);
        Exception e = assertThrows(Exception.class, in::readObject,
                "a chain with an invalid-UTF-8 field name is not canonical and must be rejected");
        assertNotNull(e.getMessage());
    }

    @Test
    void twoDifferentMisspellingsOfOneChainAreNotBothAccepted() throws Exception {
        byte[] good = encode(new DerGoldenBytesTest.Point(7, 9));
        byte[] bad1 = corruptFieldName(good);
        byte[] bad2 = bad1.clone();
        int idx = -1;
        for (int i = 0; i < bad1.length; i++) if (bad1[i] != good[i]) { idx = i; break; }
        bad2[idx] = (byte) 0xFE;
        for (byte[] bad : new byte[][] {bad1, bad2}) {
            DerObjectStreamCodec in = new DerObjectStreamCodec();
            in.initReader(bad);
            assertThrows(Exception.class, in::readObject);
        }
    }
}
