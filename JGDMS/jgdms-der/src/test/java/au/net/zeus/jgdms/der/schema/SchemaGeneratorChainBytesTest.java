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
import au.net.zeus.jgdms.der.object.fixtures.Beta;
import au.net.zeus.jgdms.der.object.fixtures.Foo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SOW-DER-Codec-Performance T1: the per-class cached chain encoding must equal
 * a fresh re-encode of the chain, must never be mutable through a hand-out, and
 * failures must still throw.
 */
class SchemaGeneratorChainBytesTest {

    private static byte[] reencode(Class<?> cls) throws DerException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (AtomicSerialSchemaRecord r : SchemaGenerator.generateChain(cls).chain()) {
            byte[] e = r.encode();
            buf.write(e, 0, e.length);
        }
        return buf.toByteArray();
    }

    @Test
    void cachedBytesEqualFreshReencode() throws Exception {
        for (Class<?> cls : new Class<?>[] {Foo.class, Beta.class}) {
            assertArrayEquals(reencode(cls), SchemaGenerator.generateChainBytes(cls), cls.getName());
        }
    }

    @Test
    void multiLevelHierarchyChainHasEveryRecord() throws Exception {
        int records = SchemaGenerator.generateChain(Beta.class).chain().size();
        assertTrue(records >= 2, "fixture should be a multi-record hierarchy");
        byte[] bytes = SchemaGenerator.generateChainBytes(Beta.class);
        assertEquals(records, SchemaChain.decodeChain(bytes, "test").size());
    }

    @Test
    void handedOutArrayIsACopy() throws Exception {
        byte[] first = SchemaGenerator.generateChainBytes(Foo.class);
        byte[] snapshot = first.clone();
        Arrays.fill(first, (byte) 0);
        byte[] second = SchemaGenerator.generateChainBytes(Foo.class);
        assertNotSame(first, second);
        assertArrayEquals(snapshot, second, "mutating a returned array must not reach the cache");
    }

    @Test
    void leafDigestIsSha256OfFirstRecord() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(Beta.class);
        byte[] leaf = chain.chain().get(0).encode();
        byte[] bytes = SchemaGenerator.generateChainBytes(Beta.class);
        assertArrayEquals(leaf, Arrays.copyOf(bytes, leaf.length));
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(leaf), chain.leafDigest());
    }

    @Test
    void failureStillThrowsAndIsNotCached() {
        assertThrows(DerException.class, () -> SchemaGenerator.generateChainBytes(String.class));
        assertThrows(DerException.class, () -> SchemaGenerator.generateChainBytes(String.class));
        assertThrows(NullPointerException.class, () -> SchemaGenerator.generateChainBytes(null));
    }
}
