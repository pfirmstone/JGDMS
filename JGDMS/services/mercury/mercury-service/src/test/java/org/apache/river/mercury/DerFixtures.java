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
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Objects;
import net.jini.core.entry.Entry;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Shared {@code @AtomicSerial} test fixtures for the mercury
 * AtomicMarshalledInstance-&gt;DER write-site round-trip tests (JGDMS task
 * #23, commit 221fe251c). Every write site converted by that commit wraps an
 * arbitrary {@code Object}/{@code RemoteEventListener}/{@code Entry} payload
 * in a {@code net.jini.io.MarshalledInstance} with
 * {@code MarshallingFormat.ATOMIC_DER}; the DER codec (jgdms-der, test-scope
 * dependency of this module -- see pom.xml) requires the wrapped payload
 * itself be a properly-declared {@code @AtomicSerial} type (plain
 * {@code java.io.Serializable} is rejected), so every fixture below follows
 * that contract -- the same convention used by
 * {@code org.apache.river.reggie.proxy.EntryRepDerFormatTest.Payload}.
 *
 * <p>Every nested fixture class is declared {@code public} (not just its
 * {@code @AtomicSerial} static members): the DER codec's schema generator
 * deliberately does <em>not</em> use {@code setAccessible} to reach
 * non-public {@code @AtomicSerial} classes (that reflection escape hatch
 * was removed -- see {@code org.apache.river.api.io.MarshalDelegate} and
 * memory {@code jgdms-marshal-delegate-built.md}), so a package-private
 * fixture used as a DER payload fails with "... is not reachable without
 * setAccessible, and no MarshalDelegate is registered for package
 * org.apache.river.mercury". Production write sites never hit this because
 * their {@code Object}/{@code RemoteEventListener}/{@code Entry} payloads
 * are always real public service/client classes at runtime.
 */
public final class DerFixtures {

    private DerFixtures() {}

    /**
     * Minimal {@code @AtomicSerial} payload, standing in for an arbitrary
     * event "source" object (EventID.source, RemoteEvent.source) or other
     * generic handback value.
     */
    @AtomicSerial
    public static class Payload implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final String VALUE = "value";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(VALUE, String.class) };
        }

        public static void serialize(PutArg arg, Payload p) throws IOException {
            arg.put(VALUE, p.value);
            arg.writeArgs();
        }

        final String value;

        public Payload(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(VALUE, null, String.class));
        }

        Payload(String value) { this.value = value; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Payload && Objects.equals(value, ((Payload) o).value);
        }
        @Override
        public int hashCode() { return Objects.hashCode(value); }
        @Override
        public String toString() { return "Payload[" + value + "]"; }
    }

    /**
     * Minimal {@code @AtomicSerial} {@link RemoteEventListener}, standing in
     * for a real client-supplied notification target wherever a write site
     * DER-encodes one (ServiceRegistration.setEventTarget,
     * MailboxImpl.RegistrationEnabledLogObj). {@link #notify} is never
     * actually invoked by these tests -- the fixture is only ever
     * marshalled/unmarshalled, never exported or called across a real
     * connection.
     */
    @AtomicSerial
    public static class Listener implements RemoteEventListener, Serializable {
        private static final long serialVersionUID = 1L;
        private static final String ID = "id";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(ID, String.class) };
        }

        public static void serialize(PutArg arg, Listener l) throws IOException {
            arg.put(ID, l.id);
            arg.writeArgs();
        }

        final String id;

        public Listener(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(ID, null, String.class));
        }

        Listener(String id) { this.id = id; }

        @Override
        public void notify(RemoteEvent theEvent)
            throws UnknownEventException, RemoteException {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Listener && Objects.equals(id, ((Listener) o).id);
        }
        @Override
        public int hashCode() { return Objects.hashCode(id); }
        @Override
        public String toString() { return "Listener[" + id + "]"; }
    }

    /**
     * Minimal plain (non-{@code @SerialEntry}) {@link Entry} with one
     * marshallable field, used for the
     * MailboxImpl.marshalAttributes/unmarshalAttributes round trip. Follows
     * the {@code Entry} contract: public no-arg constructor, public
     * non-final field.
     */
    @AtomicSerial
    public static class TestEntry implements Entry {
        private static final long serialVersionUID = 1L;
        private static final String VALUE = "value";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(VALUE, String.class) };
        }

        public static void serialize(PutArg arg, TestEntry e) throws IOException {
            arg.put(VALUE, e.value);
            arg.writeArgs();
        }

        public String value;

        public TestEntry() {}

        public TestEntry(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(VALUE, null, String.class));
        }

        TestEntry(String value) { this.value = value; }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestEntry && Objects.equals(value, ((TestEntry) o).value);
        }
        @Override
        public int hashCode() { return Objects.hashCode(value); }
        @Override
        public String toString() { return "TestEntry[" + value + "]"; }
    }
}
