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
import au.net.zeus.jgdms.der.marshal.fixtures.VersionedRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A decode unit that has failed must not run its completion callbacks (the client DGC batched
 * {@code dirty} is registered through them): after a rejected read, {@code close()} used to flush the
 * registrations of the objects decoded before the failure, issuing leases for objects that were thrown
 * away. A unit that did not fail must still flush exactly once, before the underlying close.
 */
class DerMarshalInputStreamAbortTest {

    /** [good, good, item whose chainRef names a digest never sent]. */
    private static byte[] goodGoodBad() throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeObject(new VersionedRecord(1, "a", "x"));
        c.writeObject(new VersionedRecord(2, "b", "y"));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        List<byte[]> kids = new ArrayList<>();
        kids.add(DerWriter.writeOctetString(DerWriter.writeSequence(List.of())));
        kids.add(DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, 1), new byte[32]));
        kids.add(DerWriter.writeUtf8String("JGDMS-STD-006/ATOMIC-DER"));
        bos.write(DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, 1), DerWriter.writeSequence(kids)));
        return bos.toByteArray();
    }

    private static byte[] goodGood() throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeObject(new VersionedRecord(1, "a", "x"));
        c.writeObject(new VersionedRecord(2, "b", "y"));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    @Test
    void failedDecodeThenCloseRunsNoCompletionCallbacks() throws Exception {
        AtomicInteger fired = new AtomicInteger();
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(goodGoodBad()));
        in.registerValidation(fired::incrementAndGet, 0);
        assertNotNull(in.readObject());
        assertNotNull(in.readObject());
        assertThrows(IOException.class, in::readObject, "the third item must be rejected");
        in.close();
        assertEquals(0, fired.get(), "a failed decode unit must not run its completion callbacks on close");
    }

    @Test
    void failedDecodeThenEndDecodeUnitRunsNoCompletionCallbacks() throws Exception {
        AtomicInteger fired = new AtomicInteger();
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(goodGoodBad()));
        in.registerValidation(fired::incrementAndGet, 0);
        in.readObject();
        in.readObject();
        assertThrows(IOException.class, in::readObject);
        in.endDecodeUnit();
        in.close();
        assertEquals(0, fired.get());
    }

    @Test
    void failedTypedReadAlsoAbortsTheUnit() throws Exception {
        AtomicInteger fired = new AtomicInteger();
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(goodGood()));
        in.registerValidation(fired::incrementAndGet, 0);
        in.readObject();
        // the next item is an @AtomicSerial record, not a BOOLEAN: a malformed positional read
        assertThrows(IOException.class, in::readBoolean);
        in.close();
        assertEquals(0, fired.get());
    }

    @Test
    void registrationAfterAbortIsRefused() throws Exception {
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(goodGoodBad()));
        in.readObject();
        in.readObject();
        assertThrows(IOException.class, in::readObject);
        assertThrows(NotActiveException.class, () -> in.registerValidation(() -> { }, 0));
        in.close();
    }

    @Test
    void successfulUnitStillFlushesExactlyOnceAtEndDecodeUnit() throws Exception {
        AtomicInteger fired = new AtomicInteger();
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(goodGood()));
        in.registerValidation(fired::incrementAndGet, 0);
        in.readObject();
        in.readObject();
        in.endDecodeUnit();
        assertEquals(1, fired.get());
        in.close();
        assertEquals(1, fired.get(), "close() after endDecodeUnit() must not run them again");
    }

    @Test
    void successfulUnitWithoutEndDecodeUnitStillFlushesAtClose() throws Exception {
        AtomicInteger fired = new AtomicInteger();
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(goodGood()));
        in.registerValidation(fired::incrementAndGet, 0);
        in.readObject();
        in.readObject();
        in.close();
        assertEquals(1, fired.get(), "the defensive close() flush for a unit that did not fail is kept");
    }
}
