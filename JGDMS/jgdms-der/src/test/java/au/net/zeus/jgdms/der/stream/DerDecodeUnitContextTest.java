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

import au.net.zeus.jgdms.der.marshal.fixtures.CompletionFixture;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test, through the real DER codec, that the decode-unit completion token is
 * threaded from {@link DerMarshalInputStream} into a decoded {@code @AtomicSerial}
 * object's {@code GetArg} (via {@code getObjectStreamContext()}) and fired by
 * {@link DerMarshalInputStream#endDecodeUnit()} -- the mechanism the client DGC
 * ({@code net.jini.jeri.BasicObjectEndpoint}) relies on to issue its batched
 * {@code dirty} before the decode unit is acknowledged (JGDMS-STD-008 sec.6;
 * SRC&nbsp;RR-116 sec.2.1).
 *
 * @see CompletionFixture
 */
class DerDecodeUnitContextTest {

    private static byte[] encodeTwo(int idA, int idB) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(baos)) {
            out.writeObject(new CompletionFixture(idA));
            out.writeObject(new CompletionFixture(idB));
        }
        return baos.toByteArray();
    }

    @Test
    void completionTokenReachesGetArg_sharedPerUnit_firesOnceOnEndDecodeUnit() throws Exception {
        byte[] bytes = encodeTwo(1, 2);

        // Manage the stream manually so we can observe state before/after endDecodeUnit.
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes));

        CompletionFixture a = in.readObject(CompletionFixture.class);
        CompletionFixture b = in.readObject(CompletionFixture.class);

        assertEquals(1, a.getId());
        assertEquals(2, b.getId());

        // (a) The token reached each constructor via getObjectStreamContext().
        assertNotNull(a.observedToken(), "decode-unit token must reach the GetArg");
        assertNotNull(b.observedToken());

        // (b) Both objects in one decode unit observe the SAME token -> the DGC dirty batches.
        assertSame(a.observedToken(), b.observedToken(),
                "all objects of one decode unit must share one completion token");

        // (c) Callbacks have NOT fired during construction -- only on decode-unit completion.
        assertEquals(0, a.fireCount(), "callback must not fire during construction");
        assertEquals(0, b.fireCount());

        in.endDecodeUnit();

        assertEquals(1, a.fireCount(), "callback must fire exactly once on endDecodeUnit");
        assertEquals(1, b.fireCount());

        // Idempotent: a redundant endDecodeUnit and the close-time safety net do not re-fire.
        in.endDecodeUnit();
        in.close();
        assertEquals(1, a.fireCount());
        assertEquals(1, b.fireCount());
    }
}
