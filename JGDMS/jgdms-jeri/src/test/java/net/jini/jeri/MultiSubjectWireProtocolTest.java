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

package net.jini.jeri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for the multi-Subject wire-protocol encoding added to
 * {@link BasicInvocationHandler#writeUserSubjects} and read by
 * {@link BasicInvocationDispatcher}.
 *
 * <p>The wire format for version 0x02 user-Subject block is:
 * <pre>
 *   subjectCount    : u16
 *   for each Subject:
 *     principalCount  : u16
 *     for each principal:
 *       classNameLength : u16
 *       classNameBytes  : UTF-8
 *       nameLength      : u16
 *       nameBytes       : UTF-8
 * </pre>
 */
public class MultiSubjectWireProtocolTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Reads a 2-byte big-endian unsigned short from {@code buf} at {@code off}. */
    private static int readU16(byte[] buf, int off) {
        return ((buf[off] & 0xFF) << 8) | (buf[off + 1] & 0xFF);
    }

    private static String readUtf8Prefixed(byte[] buf, int[] cursor) {
        int len = readU16(buf, cursor[0]);
        cursor[0] += 2;
        String s = new String(buf, cursor[0], len, java.nio.charset.StandardCharsets.UTF_8);
        cursor[0] += len;
        return s;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    public void testEmptySubjectArrayWritesZeroCount() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[0]);
        byte[] bytes = baos.toByteArray();
        Assert.assertEquals("should be exactly 2 bytes for count=0", 2, bytes.length);
        Assert.assertEquals(0, readU16(bytes, 0));
    }

    @Test
    public void testSingleSubjectSinglePrincipalRoundTrip() throws IOException {
        X500Principal p = new X500Principal("CN=alice");
        Set<Principal> principals = Collections.singleton(p);
        Subject s = new Subject(true, principals, Collections.emptySet(), Collections.emptySet());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[]{s});
        byte[] bytes = baos.toByteArray();

        int[] cursor = {0};

        // subjectCount = 1
        int subjectCount = readU16(bytes, cursor[0]); cursor[0] += 2;
        Assert.assertEquals(1, subjectCount);

        // principalCount = 1
        int principalCount = readU16(bytes, cursor[0]); cursor[0] += 2;
        Assert.assertEquals(1, principalCount);

        // className
        String className = readUtf8Prefixed(bytes, cursor);
        Assert.assertEquals(X500Principal.class.getName(), className);

        // name
        String name = readUtf8Prefixed(bytes, cursor);
        Assert.assertEquals(p.getName(), name);

        Assert.assertEquals("should have consumed all bytes", bytes.length, cursor[0]);
    }

    @Test
    public void testMultipleSubjectsRoundTrip() throws IOException {
        X500Principal pA = new X500Principal("CN=alice");
        X500Principal pB = new X500Principal("CN=bob");
        X500Principal pC = new X500Principal("CN=charlie");

        Subject s1 = new Subject(true,
            Collections.singleton((Principal) pA),
            Collections.emptySet(), Collections.emptySet());
        Subject s2 = new Subject(true,
            new HashSet<Principal>(Arrays.asList(pB, pC)),
            Collections.emptySet(), Collections.emptySet());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[]{s1, s2});
        byte[] bytes = baos.toByteArray();

        int[] cursor = {0};

        // subjectCount = 2
        int subjectCount = readU16(bytes, cursor[0]); cursor[0] += 2;
        Assert.assertEquals(2, subjectCount);

        // Subject 1: 1 principal
        int pc1 = readU16(bytes, cursor[0]); cursor[0] += 2;
        Assert.assertEquals(1, pc1);
        readUtf8Prefixed(bytes, cursor); // className
        readUtf8Prefixed(bytes, cursor); // name

        // Subject 2: 2 principals
        int pc2 = readU16(bytes, cursor[0]); cursor[0] += 2;
        Assert.assertEquals(2, pc2);
        for (int i = 0; i < 2; i++) {
            readUtf8Prefixed(bytes, cursor); // className
            readUtf8Prefixed(bytes, cursor); // name
        }

        Assert.assertEquals("should have consumed all bytes", bytes.length, cursor[0]);
    }

    @Test
    public void testSubjectWithNoPrincipals() throws IOException {
        // A Subject with no principals should encode principalCount=0.
        Subject empty = new Subject(true,
            Collections.emptySet(),
            Collections.emptySet(), Collections.emptySet());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[]{empty});
        byte[] bytes = baos.toByteArray();
        // 2 bytes subjectCount + 2 bytes principalCount = 4 bytes
        Assert.assertEquals(4, bytes.length);
        Assert.assertEquals(1, readU16(bytes, 0)); // subjectCount
        Assert.assertEquals(0, readU16(bytes, 2)); // principalCount
    }
}
