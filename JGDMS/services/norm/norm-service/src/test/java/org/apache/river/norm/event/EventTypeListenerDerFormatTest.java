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
package org.apache.river.norm.event;

import java.io.IOException;
import java.io.Serializable;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.io.MarshalledInstance;
import net.jini.security.ProxyPreparer;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.norm.DerFormatTestSupport;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip tests for {@link EventType}'s listener persistence write site
 * ({@code setListener}'s {@code marshalledListener} assignment, converted
 * from {@code AtomicMarshalledInstance} (JOSS) to
 * {@code MarshallingFormat.ATOMIC_DER} by commit 221fe251c), and for the
 * {@code IllegalStateException} catch that commit added to
 * {@code getListener()}'s recovery path.
 *
 * <p>{@code getListener()} is a {@code private} instance method -- accessed
 * here via {@link DerFormatTestSupport#invokePrivate} -- and the transient
 * in-memory {@code listener} cache is cleared via reflection before each
 * decode assertion, so the tests actually exercise
 * {@code marshalledListener.get(false)} rather than short-circuiting on the
 * live reference (the same "just recovered from persistent storage, only the
 * marshalled field survived" state {@code getListener()} sees for real after
 * a restart).
 */
public class EventTypeListenerDerFormatTest {

    /** Minimal @AtomicSerial RemoteEventListener fixture -- ATOMIC_DER is
     *  @AtomicSerial-restricted, so the listener payload must be a
     *  properly-declared @AtomicSerial type. */
    @AtomicSerial
    public static class TestListener implements RemoteEventListener, Serializable {
        private static final long serialVersionUID = 1L;
        private static final String ID = "id";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(ID, String.class) };
        }

        public static void serialize(PutArg arg, TestListener o) throws IOException {
            arg.put(ID, o.id);
            arg.writeArgs();
        }

        public final String id;

        public TestListener(String id) { this.id = id; }

        public TestListener(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(ID, null, String.class));
        }

        @Override
        public void notify(RemoteEvent theEvent) throws UnknownEventException, java.rmi.RemoteException {
            // no-op: never actually invoked by these tests
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestListener && id.equals(((TestListener) o).id);
        }

        @Override
        public int hashCode() { return id.hashCode(); }
    }

    private static final SendMonitor NOOP_MONITOR = new SendMonitor() {
        @Override
        public void definiteException(EventType type, RemoteEvent ev,
                long registrationNumber, Throwable t) { }
        @Override
        public boolean isCurrent() { return true; }
    };

    private static final ProxyPreparer IDENTITY_PREPARER = new ProxyPreparer() {
        @Override
        public Object prepareProxy(Object proxy) { return proxy; }
    };

    /** Simulates "just recovered from persistent storage": drops the transient
     *  in-memory listener cache so getListener() must actually decode
     *  marshalledListener, and restores the transient state getListener()
     *  needs (recoveredListenerPreparer) via the real public API. */
    private static void simulateRecoveredState(EventType et) {
        DerFormatTestSupport.setField(et, "listener", null);
        et.restoreTransientState(new EventTypeGenerator(), NOOP_MONITOR, IDENTITY_PREPARER, null);
    }

    private static RemoteEventListener getListener(EventType et) {
        return (RemoteEventListener) DerFormatTestSupport.invokePrivate(
            et, "getListener", new Class<?>[0]);
    }

    // ── (a) the write site actually uses ATOMIC_DER, and decodes correctly ──

    @Test
    public void setListenerWritesMarshalledListenerUsingAtomicDerFormat() throws Exception {
        EventTypeGenerator generator = new EventTypeGenerator();
        TestListener listener = new TestListener("l1");
        EventType et = new EventType(generator, NOOP_MONITOR, 1L, listener, null, null);

        MarshalledInstance mi = (MarshalledInstance)
            DerFormatTestSupport.getField(et, "marshalledListener");

        assertEquals("EventType.setListener must use MarshallingFormat.ATOMIC_DER "
            + "(not the legacy JOSS default)",
            MarshallingFormat.ATOMIC_DER.getFormat(),
            DerFormatTestSupport.payloadFormatOf(mi));
    }

    @Test
    public void getListenerDecodesRealPersistedListener() throws Exception {
        EventTypeGenerator generator = new EventTypeGenerator();
        TestListener listener = new TestListener("l2");
        EventType et = new EventType(generator, NOOP_MONITOR, 2L, listener, null, null);

        simulateRecoveredState(et);

        RemoteEventListener recovered = getListener(et);
        assertEquals("getListener() must decode the DER-encoded marshalledListener "
            + "back to an equivalent listener",
            listener, recovered);
    }

    // ── (b) the widened IllegalStateException catch in getListener() ────────

    @Test
    public void getListenerReturnsNullGracefullyWhenFormatIsUnresolvable() throws Exception {
        EventTypeGenerator generator = new EventTypeGenerator();
        TestListener listener = new TestListener("l3");
        EventType et = new EventType(generator, NOOP_MONITOR, 3L, listener, null, null);

        MarshalledInstance mi = (MarshalledInstance)
            DerFormatTestSupport.getField(et, "marshalledListener");
        // Same failure mode getListener() would hit in production if jgdms-der
        // were missing from the runtime classpath (see DerFormatTestSupport
        // javadoc): MarshalledInstance.get() throws IllegalStateException,
        // unchecked, from factoryForFormat.
        DerFormatTestSupport.makeFormatUnresolvable(mi);

        simulateRecoveredState(et);

        // Must not throw -- this is exactly the regression the commit fixed:
        // an uncaught IllegalStateException here would previously have
        // escaped a doPrivileged block (which only wraps checked exceptions),
        // silently killing the event-retry task.
        RemoteEventListener recovered = getListener(et);
        assertNull("an unresolvable payloadFormat must be caught and treated as "
            + "'listener not yet available', not propagate", recovered);

        // haveListener() must still report true -- the registration itself is
        // still there (marshalledListener != null); it just couldn't be
        // unpacked this attempt, so the caller (SendTask.tryOnce) schedules a
        // retry rather than dropping the registration outright.
        assertTrue(et.haveListener());
    }
}
