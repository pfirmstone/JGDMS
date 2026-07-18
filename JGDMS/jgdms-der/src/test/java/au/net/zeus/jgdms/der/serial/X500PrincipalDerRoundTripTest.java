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

package au.net.zeus.jgdms.der.serial;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Arrays;
import javax.security.auth.x500.X500Principal;

import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import au.net.zeus.jgdms.der.serial.fixtures.X500Holder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI-4 / WI-5 / WI-6(e): the closed production registry substitutes
 * {@code X500PrincipalSerializer} for a {@link X500Principal}, and:
 * <ul>
 *   <li>two independently constructed byte-identical {@code X500Principal}s encode to
 *       byte-identical DER (canonical wire; the verbatim {@code getEncoded()} DN is
 *       carried unchanged, never re-canonicalized);</li>
 *   <li>a hand-mangled encoding is rejected on decode as a <em>checked</em>
 *       {@link InvalidObjectException} (WI-5), not an unchecked
 *       {@code IllegalArgumentException}.</li>
 * </ul>
 * Runs against the real PRODUCTION registry (no test seam) since X500Principal is the
 * one registered production type.
 */
class X500PrincipalDerRoundTripTest {

    private static final String DN = "CN=Alice,O=Acme,C=AU";

    @Test
    void byteIdenticalPrincipalsEncodeToIdenticalWire() throws Exception {
        X500Principal p1 = new X500Principal(DN);
        X500Principal p2 = new X500Principal(DN);
        assertNotSame(p1, p2, "two independently constructed principals");
        assertArrayEquals(p1.getEncoded(), p2.getEncoded(),
                "same DN => byte-identical getEncoded()");

        SchemaChain.Result chain = SchemaGenerator.generateChain(X500Holder.class);
        byte[] w1 = ObjectCodec.encodeHierarchy(new X500Holder(p1), chain);
        byte[] w2 = ObjectCodec.encodeHierarchy(new X500Holder(p2), chain);
        assertArrayEquals(w1, w2,
                "canonical wire: equal X500Principal values must encode to identical DER");
    }

    @Test
    void x500PrincipalRoundTripsThroughProductionRegistry() throws Exception {
        X500Principal p = new X500Principal(DN);
        SchemaChain.Result chain = SchemaGenerator.generateChain(X500Holder.class);
        byte[] wire = ObjectCodec.encodeHierarchy(new X500Holder(p), chain);

        X500Holder back = ObjectCodec.decodeHierarchy(X500Holder.class, chain, wire);
        assertEquals(p, back.name(), "X500Principal must round-trip by value");
        assertArrayEquals(p.getEncoded(), back.name().getEncoded(),
                "the verbatim DER of the DN must survive the round-trip unchanged");
    }

    @Test
    void mangledEncodingIsRejectedAsCheckedInvalidObjectException() throws Exception {
        X500Principal p = new X500Principal(DN);
        SchemaChain.Result chain = SchemaGenerator.generateChain(X500Holder.class);
        byte[] wire = ObjectCodec.encodeHierarchy(new X500Holder(p), chain);

        // Locate the verbatim DN octets carried inside the serializer's "encoded" field
        // and corrupt the outer DER tag (0x30 SEQUENCE -> 0x31). This keeps the OCTET
        // STRING framing (same length) intact, so decode reaches new X500Principal(bytes)
        // with malformed DN content -- the WI-5 path.
        byte[] dn = p.getEncoded();
        int at = indexOf(wire, dn);
        assertTrue(at >= 0, "expected the verbatim DN octets to appear in the wire");
        byte[] mangled = wire.clone();
        mangled[at] = (byte) (mangled[at] ^ 0x01); // 0x30 -> 0x31: no longer a SEQUENCE

        Throwable thrown = assertThrows(Throwable.class,
                () -> ObjectCodec.decodeHierarchy(X500Holder.class, chain, mangled));
        assertTrue(thrown instanceof IOException,
                "malformed DN must surface as a CHECKED IOException (WI-5), not an "
                + "unchecked IllegalArgumentException; got " + thrown);
        assertTrue(hasInvalidObjectException(thrown),
                "WI-5: the failure must be an InvalidObjectException (or carry one); got " + thrown);
    }

    private static boolean hasInvalidObjectException(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof InvalidObjectException) {
                return true;
            }
        }
        return false;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    // Guard against a spurious pass if the DN appears more than once in the payload.
    @Test
    void dnAppearsExactlyOnceInWire() throws Exception {
        X500Principal p = new X500Principal(DN);
        SchemaChain.Result chain = SchemaGenerator.generateChain(X500Holder.class);
        byte[] wire = ObjectCodec.encodeHierarchy(new X500Holder(p), chain);
        byte[] dn = p.getEncoded();
        int count = 0;
        for (int i = 0; i + dn.length <= wire.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(wire, i, i + dn.length), dn)) {
                count++;
            }
        }
        assertEquals(1, count, "the verbatim DN must appear exactly once (the encoded field)");
    }
}
