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

import java.io.IOException;
import java.lang.reflect.Field;
import java.rmi.RemoteException;
import net.jini.core.event.RemoteEventListener;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.security.ProxyPreparer;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip and catch-widening tests for {@link ServiceRegistration}'s DER
 * write site, converted by commit 221fe251c (JGDMS task #23 coverage):
 * <ul>
 *   <li><b>Write site</b>: {@code setEventTarget(RemoteEventListener)} DER-
 *       encodes the client-supplied notification target into the
 *       {@code marshalledEventTarget} field.</li>
 *   <li><b>Widened read-side catch</b>: {@code restoreTransientState(
 *       ProxyPreparer)} is the read-side recovery method, and is the
 *       <em>only</em> mercury site where commit 221fe251c added a new
 *       explicit {@code catch (IllegalStateException e)} -- converting the
 *       unchecked exception {@code MarshalledInstance.get()} throws when
 *       its {@code payloadFormat} cannot be resolved (e.g. {@code jgdms-der}
 *       missing from the runtime classpath) into the checked
 *       {@code IOException} this method already declares and documents.
 *       Every other mercury write site's read-side recovery already caught
 *       the fully general {@code Throwable} before this commit (see
 *       {@link EventIDDerFormatTest} and
 *       {@link MailboxImplDerFormatTest}'s javadoc) -- this is the one
 *       case in this module where an uncaught, undocumented unchecked
 *       exception was a real, if lower-severity, risk (per commit
 *       221fe251c's own message).</li>
 * </ul>
 *
 * <p>{@link #restoreTransientStateWrapsUnresolvablePayloadFormatAsIOException}
 * is the key test: it does not just compile a
 * {@code catch (IllegalStateException e)} block, it drives a real
 * {@code IllegalStateException} out of {@code MarshalledInstance.get()}
 * (by corrupting a genuinely DER-encoded instance's {@code payloadFormat}
 * to an unregistered value -- the same failure {@code factoryForFormat}
 * throws for a missing {@code jgdms-der}) and asserts
 * {@code restoreTransientState} converts it to an {@code IOException}
 * with the original {@code IllegalStateException} as its cause, rather
 * than letting it escape.
 *
 * <p>Lives in the {@code org.apache.river.mercury} package to access
 * package-private {@link ServiceRegistration}. Requires {@code jgdms-der}
 * on the test runtime classpath (declared test-scope in this module's
 * pom.xml).
 */
public class ServiceRegistrationDerFormatTest {

    /** Pass-through preparer: no real proxy preparation needed for these tests. */
    private static final ProxyPreparer IDENTITY_PREPARER = new ProxyPreparer() {
        @Override
        public Object prepareProxy(Object proxy) throws RemoteException {
            return proxy;
        }
    };

    private static MarshalledInstance marshalledEventTargetOf(ServiceRegistration reg)
            throws Exception {
        Field f = ServiceRegistration.class.getDeclaredField("marshalledEventTarget");
        f.setAccessible(true);
        return (MarshalledInstance) f.get(reg);
    }

    // ── setEventTarget write site + normal restoreTransientState decode ─────

    @Test
    public void setEventTargetRoundTripsThroughRestoreTransientState() throws Exception {
        Uuid cookie = UuidFactory.generate();
        ServiceRegistration reg = new ServiceRegistration(cookie, null, null);

        DerFixtures.Listener listener = new DerFixtures.Listener("target-1");
        reg.setEventTarget(listener);

        MarshalledInstance mi = marshalledEventTargetOf(reg);
        assertNotNull("setEventTarget must have DER-encoded the target", mi);

        // A fresh ServiceRegistration standing in for "after recovery from
        // persistent storage": only marshalledEventTarget survives
        // (de)serialization, preparedEventTarget is transient and must be
        // rebuilt by restoreTransientState.
        ServiceRegistration recovered = new ServiceRegistration(cookie, null, null);
        Field f = ServiceRegistration.class.getDeclaredField("marshalledEventTarget");
        f.setAccessible(true);
        f.set(recovered, mi);

        recovered.restoreTransientState(IDENTITY_PREPARER);

        assertEquals("restoreTransientState must decode the DER-encoded target back",
                listener, recovered.getEventTarget());
    }

    // ── the widened catch: IllegalStateException -> documented IOException ──

    @Test
    public void restoreTransientStateWrapsUnresolvablePayloadFormatAsIOException()
            throws Exception {
        Uuid cookie = UuidFactory.generate();
        ServiceRegistration reg = new ServiceRegistration(cookie, null, null);
        reg.setEventTarget(new DerFixtures.Listener("target-2"));

        MarshalledInstance mi = marshalledEventTargetOf(reg);

        // Simulate exactly the failure mode commit 221fe251c's catch-widening
        // targets: MarshalledInstance.get()'s factoryForFormat cannot resolve
        // payloadFormat (e.g. jgdms-der missing from the classpath at read
        // time), which throws an unchecked IllegalStateException.
        Field payloadFormatField = MarshalledInstance.class.getDeclaredField("payloadFormat");
        payloadFormatField.setAccessible(true);
        payloadFormatField.set(mi, "BOGUS/UNRESOLVABLE-FORMAT");

        // Sanity: confirm the corruption is effective on its own before
        // going through ServiceRegistration at all.
        try {
            mi.get(false);
            fail("corrupted payloadFormat should not resolve to a MarshalFactoryProvider");
        } catch (IllegalStateException expected) {
            // expected
        }

        try {
            reg.restoreTransientState(IDENTITY_PREPARER);
            fail("restoreTransientState must not silently succeed with an "
                    + "unresolvable payloadFormat");
        } catch (IOException e) {
            assertTrue("the widened catch must preserve the original "
                    + "IllegalStateException as the cause, not swallow it",
                    e.getCause() instanceof IllegalStateException);
        }
        // Explicitly NOT catching IllegalStateException above: if the widened
        // catch in ServiceRegistration.restoreTransientState regressed (e.g.
        // reverted to only catching IOException/ClassNotFoundException), the
        // IllegalStateException would propagate uncaught here and fail this
        // test with the wrong exception type, exactly as it would have
        // escaped the doPrivileged-adjacent caller in production.
    }
}
