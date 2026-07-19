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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.jini.core.entry.Entry;
import net.jini.core.event.RemoteEventListener;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip tests for the two {@link MailboxImpl} write sites converted to
 * DER by commit 221fe251c (JGDMS task #23 coverage):
 * <ul>
 *   <li>{@code MailboxImpl.RegistrationEnabledLogObj.writeObject} -- a
 *       private static nested {@code LogRecord} class recorded to the
 *       mailbox's persistent log whenever a registration is enabled, DER-
 *       encoding its {@code RemoteEventListener target} field.</li>
 *   <li>{@code MailboxImpl.marshalAttributes(Entry[])} -- a private static
 *       method that DER-encodes each element of an {@code Entry[]} for
 *       persistent storage (used by the {@code AttrsAddedLogObj}/
 *       {@code AttrsModifiedLogObj} log records); its read-side counterpart
 *       is {@code MailboxImpl.unmarshalAttributes(Object[])}.</li>
 * </ul>
 *
 * <p>Both {@code RegistrationEnabledLogObj} and {@code marshalAttributes}/
 * {@code unmarshalAttributes} are {@code private} members of {@code public
 * class MailboxImpl} -- not merely package-private -- so this test reaches
 * them via reflection ({@code setAccessible(true)}) rather than direct
 * same-package access (contrast {@link EventIDDerFormatTest}, where
 * {@code EventID} itself is package-private and directly usable). This
 * still exercises the real production write sites: the DER encode/decode
 * happens inside the actual {@code writeObject}/{@code marshalAttributes}
 * bodies, invoked via real Java serialization / a real method call, not
 * reimplemented in the test.
 *
 * <p>Neither of these two sites gained a new explicit
 * {@code catch (IllegalStateException e)} from commit 221fe251c -- both
 * already caught the fully general {@code Throwable} beforehand (see
 * {@code RegistrationEnabledLogObj.readObject}'s
 * {@code catch (Throwable e)} and {@code unmarshalAttributes}'s
 * per-element {@code catch (Throwable e)}), which already subsumes
 * {@code IllegalStateException}. The
 * {@link #unmarshalAttributesDropsEntryWithUnresolvablePayloadFormat} test
 * below confirms that pre-existing resilience actually holds for exactly
 * the failure mode this migration introduced (an unresolvable DER
 * {@code payloadFormat}), not just that it compiles -- the closest
 * mercury analog to the "5 read-side recovery loops" the commit describes
 * widening in other modules, even though {@code unmarshalAttributes}
 * itself was not one of the methods that commit modified.
 *
 * <p>Requires {@code jgdms-der} on the test runtime classpath (declared
 * test-scope in this module's pom.xml).
 */
public class MailboxImplDerFormatTest {

    private static Class<?> registrationEnabledLogObjClass() throws ClassNotFoundException {
        return Class.forName(MailboxImpl.class.getName() + "$RegistrationEnabledLogObj");
    }

    // ── MailboxImpl.RegistrationEnabledLogObj.writeObject ───────────────────

    @Test
    public void registrationEnabledLogObjRoundTripsListenerViaDer() throws Exception {
        Class<?> logObjClass = registrationEnabledLogObjClass();
        Constructor<?> ctor = logObjClass.getDeclaredConstructor(Uuid.class, RemoteEventListener.class);
        ctor.setAccessible(true);

        Uuid regID = UuidFactory.generate();
        DerFixtures.Listener listener = new DerFixtures.Listener("listener-1");
        Object original = ctor.newInstance(regID, listener);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bout)) {
            oos.writeObject(original);
        }

        Object decoded;
        try (ObjectInputStream ois =
                new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray()))) {
            decoded = ois.readObject();
        }

        assertTrue("decoded object must be a RegistrationEnabledLogObj",
                logObjClass.isInstance(decoded));

        Field targetField = logObjClass.getDeclaredField("target");
        targetField.setAccessible(true);
        Object decodedTarget = targetField.get(decoded);

        assertEquals("RegistrationEnabledLogObj's DER-encoded target must round-trip",
                listener, decodedTarget);
    }

    // ── MailboxImpl.marshalAttributes / unmarshalAttributes ─────────────────

    private static MarshalledInstance[] marshalAttributes(Entry[] attrs) throws Exception {
        Method m = MailboxImpl.class.getDeclaredMethod("marshalAttributes", Entry[].class);
        m.setAccessible(true);
        return (MarshalledInstance[]) m.invoke(null, (Object) attrs);
    }

    private static Entry[] unmarshalAttributes(Object[] marshalledAttrs) throws Exception {
        Method m = MailboxImpl.class.getDeclaredMethod("unmarshalAttributes", Object[].class);
        m.setAccessible(true);
        return (Entry[]) m.invoke(null, (Object) marshalledAttrs);
    }

    @Test
    public void marshalAttributesRoundTripsViaDer() throws Exception {
        Entry[] attrs = new Entry[]{
            new DerFixtures.TestEntry("attr-1"),
            new DerFixtures.TestEntry("attr-2")
        };

        MarshalledInstance[] marshalled = marshalAttributes(attrs);
        assertEquals(2, marshalled.length);

        Entry[] recovered = unmarshalAttributes(marshalled);

        assertArrayEquals("marshalAttributes' DER-encoded attributes must round-trip",
                attrs, recovered);
    }

    @Test
    public void unmarshalAttributesDropsEntryWithUnresolvablePayloadFormat() throws Exception {
        Entry[] attrs = new Entry[]{
            new DerFixtures.TestEntry("good-1"),
            new DerFixtures.TestEntry("good-2")
        };
        MarshalledInstance[] marshalled = marshalAttributes(attrs);
        assertEquals(2, marshalled.length);

        // Simulate exactly the failure mode commit 221fe251c's catch-widening
        // targets: MarshalledInstance.get()'s factoryForFormat cannot resolve
        // payloadFormat (e.g. jgdms-der missing from the classpath at read
        // time), which throws an unchecked IllegalStateException. Corrupt
        // just the first element's payloadFormat to an unregistered value.
        Field payloadFormatField = MarshalledInstance.class.getDeclaredField("payloadFormat");
        payloadFormatField.setAccessible(true);
        payloadFormatField.set(marshalled[0], "BOGUS/UNRESOLVABLE-FORMAT");

        // Sanity: confirm the corrupted instance really does throw
        // IllegalStateException on decode (proves the corruption is
        // effective, not a no-op).
        try {
            marshalled[0].get(false);
            fail("corrupted payloadFormat should not resolve to a MarshalFactoryProvider");
        } catch (IllegalStateException expected) {
            // expected
        }

        Entry[] recovered = unmarshalAttributes(marshalled);

        assertEquals("unmarshalAttributes must drop only the unresolvable entry, "
                + "not fail the whole recovery loop", 1, recovered.length);
        assertEquals(new DerFixtures.TestEntry("good-2"), recovered[0]);
    }
}
