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
package org.apache.river.outrigger.proxy;

import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.entry.AbstractEntry;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SOW-Outrigger-DER-Only-JOSS-Rejection sec.3 item 6 (U1b): {@link SnapshotRep}
 * is {@code @AtomicSerial} (its only field, the {@link EntryRep}, already was)
 * and its java.io (JOSS) path is fenced off. A snapshot is JVM-local per the
 * JavaSpaces specification, so this is impl-only hygiene: if a snapshot is
 * ever marshalled it travels atomically, never via JOSS.
 */
public class SnapshotRepAtomicSerialTest {

    /** Minimal Entry to snapshot. */
    public static class Note extends AbstractEntry {
        public String text;
        public Note() { }
        public Note(String text) { this.text = text; }
    }

    @Test
    public void derRoundTripPreservesRep() throws Exception {
        SnapshotRep original =
            new SnapshotRep(new Note("snap"), MarshallingFormat.ATOMIC_DER);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DerMarshalOutputStream out = new DerMarshalOutputStream(baos);
        out.writeObject(original);
        out.flush();

        DerMarshalInputStream in = new DerMarshalInputStream(
            new ByteArrayInputStream(baos.toByteArray()));
        Object decoded = in.readObject();
        assertTrue("decode must reconstruct a SnapshotRep, got "
            + decoded.getClass(), decoded instanceof SnapshotRep);
        EntryRep rep = ((SnapshotRep) decoded).rep();
        assertNotNull("the rep must survive the round trip", rep);
        assertEquals(Note.class.getName(), rep.classFor());
    }

    @Test
    public void jossSerializationIsFenced() throws Exception {
        SnapshotRep snap =
            new SnapshotRep(new Note("fenced"), MarshallingFormat.ATOMIC_DER);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        try {
            oos.writeObject(snap);
            fail("java.io serialization of SnapshotRep must throw"
                 + " NotSerializableException");
        } catch (NotSerializableException expected) {
            assertTrue(expected.getMessage()
                .contains(SnapshotRep.class.getName()));
        }
    }
}
