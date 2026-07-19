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
package org.apache.river.mercury;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.river.api.io.AtomicMarshalledInstance;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip tests for both {@link EventID} write sites converted to DER by
 * commit 221fe251c (JGDMS task #23 coverage). Both sites wrap the
 * {@code source} field in its own nested {@code net.jini.io.MarshalledInstance}
 * tagged {@code MarshallingFormat.ATOMIC_DER}:
 * <ul>
 *   <li>{@link EventID#serialize(org.apache.river.api.io.AtomicSerial.PutArg,
 *       EventID)} -- the {@code @AtomicSerial} write path. {@code EventID}
 *       itself is package-private, and the DER codec deliberately does
 *       <em>not</em> use {@code setAccessible} to reach non-public
 *       {@code @AtomicSerial} classes (see {@link DerFixtures}'s javadoc), so
 *       {@code EventID} can never be the top-level payload of a DER-tagged
 *       {@code MarshalledInstance} -- exactly matching production, where
 *       {@code EventID} is always encoded as a nested field (e.g. inside
 *       {@code ServiceRegistration.unknownEvents}) via the legacy
 *       JOSS/{@code @AtomicSerial} machinery, which does still use
 *       {@code setAccessible}. This test therefore drives
 *       {@code EventID.serialize} by wrapping an {@code EventID} instance in
 *       {@link AtomicMarshalledInstance} (JOSS format for the outer
 *       envelope); the {@code source} field inside it is still DER-encoded
 *       by the converted write site under test.</li>
 *   <li>{@link EventID#writeObject} (private, invoked via plain
 *       {@code ObjectOutputStream}) -- the dual-read fallback path used when
 *       {@code EventID} is serialized directly rather than through the
 *       {@code @AtomicSerial} machinery.</li>
 * </ul>
 *
 * <p>{@code EventID}'s own read-side recovery (both {@code readSource} for
 * the {@code @AtomicSerial} path and {@code readObject} for the plain-stream
 * path) already catches the fully general {@code Throwable} -- not just
 * {@code IOException}/{@code ClassNotFoundException} -- so it was already
 * resilient to {@code MarshalledInstance.get()}'s unchecked
 * {@code IllegalStateException} before commit 221fe251c; that commit's
 * catch-widening list does not include this file. No separate
 * catch-widening test is needed here (contrast
 * {@link ServiceRegistrationDerFormatTest}, which does add an explicit
 * {@code catch (IllegalStateException e)}).
 *
 * <p>Lives in the {@code org.apache.river.mercury} package to access
 * package-private {@link EventID}. Requires {@code jgdms-der} on the test
 * runtime classpath (declared test-scope in this module's pom.xml) for
 * {@code MarshalledInstance}'s {@code ServiceLoader}-based
 * {@code MarshalFactoryProvider} dispatch to encode/decode
 * {@code MarshallingFormat.ATOMIC_DER}-tagged payloads.
 */
public class EventIDDerFormatTest {

    // ── EventID.serialize(PutArg, EventID) -- @AtomicSerial write path ──────

    @Test
    public void atomicSerialPathRoundTripsSourceViaNestedDer() throws Exception {
        DerFixtures.Payload source = new DerFixtures.Payload("hello-source");
        EventID original = new EventID(source, 42L);

        AtomicMarshalledInstance mi = new AtomicMarshalledInstance(original);
        Object decoded = mi.get();

        assertTrue("decoded object must be an EventID", decoded instanceof EventID);
        assertEquals("EventID.serialize's DER-encoded source/id must round-trip",
                original, decoded);
    }

    // ── EventID.writeObject -- plain ObjectOutputStream dual-read path ──────

    @Test
    public void writeObjectPathRoundTripsViaPlainObjectStream() throws Exception {
        DerFixtures.Payload source = new DerFixtures.Payload("hello-source-2");
        EventID original = new EventID(source, 7L);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bout)) {
            oos.writeObject(original);
        }

        Object decoded;
        try (ObjectInputStream ois =
                new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray()))) {
            decoded = ois.readObject();
        }

        assertTrue("decoded object must be an EventID", decoded instanceof EventID);
        assertEquals("EventID.writeObject's DER-encoded source field must round-trip",
                original, decoded);
    }
}
