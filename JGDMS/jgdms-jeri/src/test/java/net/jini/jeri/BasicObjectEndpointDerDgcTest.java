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
package net.jini.jeri;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputValidation;
import java.util.Collection;
import java.util.Collections;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.context.DeserializationCompletion;
import net.jini.jeri.tcp.TcpEndpoint;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.ReadObject;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for the client-DGC-over-DER fix in {@link BasicObjectEndpoint}'s
 * {@code @AtomicSerial} constructor (JGDMS-STD-008 sec.6).
 *
 * <p>On the JOSS/atomic path the constructor obtains a {@code ReadObject} reader and
 * registers its batched {@code dirty} on the reader's {@code ObjectInputStream}. On the
 * DER path {@code getReader()} returns {@code null}; the old code dereferenced it and
 * threw {@link NullPointerException}. The fix branches on the reader: when it is
 * {@code null} the live reference registers on the {@link DeserializationCompletion}
 * context element instead.
 *
 * <p>These tests drive the constructor directly with a fake {@code GetArg} that models the
 * DER path (no reader; a recording {@code DeserializationCompletion} in the object-stream
 * context), and do not fire the batched dirty ({@code endDecodeUnit}/{@code close}), which
 * would attempt a real network {@code registerRefs}. A fake {@code GetArg} is used because a
 * full DER round-trip of a real {@code BasicObjectEndpoint} is not yet possible: its
 * interface-typed {@code ep} field now encodes, but its {@code id} field's runtime type
 * {@code net.jini.id.UuidFactory$Impl} lacks a {@code serialize(PutArg)} DER write contract.
 * The interface-typed-field mechanism is covered by
 * {@code au.net.zeus.jgdms.der.object.InterfaceFieldRoundTripTest}; the end-of-decode-unit
 * firing by {@code au.net.zeus.jgdms.der.stream.DerDecodeUnitContextTest}.
 */
public class BasicObjectEndpointDerDgcTest {

    private static final Endpoint EP = TcpEndpoint.getInstance("localhost", 7777);
    private static final Uuid ID = UuidFactory.create(0x0123456789abcdefL, 0xfedcba9876543210L);

    @Test
    public void dgcEnabled_derPath_registersBatchedCompletion_noNpe() throws Exception {
        RecordingCompletion completion = new RecordingCompletion();
        GetArg arg = new FakeDerGetArg(EP, ID, true, completion);

        // Previously threw NullPointerException on the DER path (getReader() == null).
        BasicObjectEndpoint boe = new BasicObjectEndpoint(arg);

        Assert.assertEquals(EP, boe.getEndpoint());
        Assert.assertEquals(ID, boe.getObjectIdentifier());
        Assert.assertEquals("DGC live ref must register exactly one batched completion",
                1, completion.registrationCount);
        Assert.assertNotNull("a completion callback must have been registered",
                completion.lastCallback);
        Assert.assertEquals("DGC registers at priority 0", 0, completion.lastPriority);
    }

    @Test(expected = InvalidObjectException.class)
    public void dgcEnabled_derPath_missingCompletion_throwsInvalidObjectException() throws Exception {
        // No reader and no DeserializationCompletion -> cannot batch the dirty -> fail-secure.
        GetArg arg = new FakeDerGetArg(EP, ID, true, null);
        new BasicObjectEndpoint(arg);
    }

    @Test
    public void dgcDisabled_derPath_doesNotRegister() throws Exception {
        RecordingCompletion completion = new RecordingCompletion();
        GetArg arg = new FakeDerGetArg(EP, ID, false, completion);

        BasicObjectEndpoint boe = new BasicObjectEndpoint(arg);

        Assert.assertEquals(EP, boe.getEndpoint());
        Assert.assertEquals("a non-DGC endpoint must not register a completion",
                0, completion.registrationCount);
    }

    // NOTE: a full real DER round-trip of a BasicObjectEndpoint is not yet possible. The
    // interface-typed 'ep' field now encodes (SchemaGenerator polymorphic @AtomicSerial
    // slot), but its 'id' field's runtime type net.jini.id.UuidFactory$Impl is @AtomicSerial
    // WITHOUT a serialize(PutArg) DER write contract (and TcpEndpoint's chain is unverified),
    // so encode fails deeper in the graph. Making the JERI proxy graph fully DER-serializable
    // is a separate workstream; the interface-field step is proven by
    // au.net.zeus.jgdms.der.object.InterfaceFieldRoundTripTest.

    /** Records {@code registerCompletion} calls; never fires (no network). */
    private static final class RecordingCompletion implements DeserializationCompletion {
        int registrationCount;
        ObjectInputValidation lastCallback;
        int lastPriority;

        @Override
        public void registerCompletion(ObjectInputValidation action, int priority) {
            registrationCount++;
            lastCallback = action;
            lastPriority = priority;
        }
    }

    /**
     * Minimal {@code GetArg} modelling the DER decode path: returns the endpoint/uuid/dgc
     * fields by name, {@code null} from {@code getReader()}, and a context holding the
     * (optional) {@link DeserializationCompletion}. All other accessors are unused here.
     */
    private static final class FakeDerGetArg extends GetArg {
        private final Endpoint ep;
        private final Uuid id;
        private final boolean dgc;
        private final DeserializationCompletion completion;

        FakeDerGetArg(Endpoint ep, Uuid id, boolean dgc, DeserializationCompletion completion) {
            super();
            this.ep = ep;
            this.id = id;
            this.dgc = dgc;
            this.completion = completion;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String name, T val, Class<T> type) {
            if ("ep".equals(name)) return (T) ep;
            if ("id".equals(name)) return (T) id;
            return val;
        }

        @Override
        public boolean get(String name, boolean val) {
            return "dgc".equals(name) ? dgc : val;
        }

        @Override
        public ReadObject getReader() {
            return null; // DER path: no @ReadInput reader
        }

        @Override
        public Collection getObjectStreamContext() {
            return completion == null
                    ? Collections.emptyList()
                    : Collections.singletonList(completion);
        }

        // ---- unused accessors -------------------------------------------------
        @Override public boolean defaulted(String name) { throw uoe(); }
        @Override public byte get(String name, byte val) { throw uoe(); }
        @Override public char get(String name, char val) { throw uoe(); }
        @Override public short get(String name, short val) { throw uoe(); }
        @Override public int get(String name, int val) { throw uoe(); }
        @Override public long get(String name, long val) { throw uoe(); }
        @Override public float get(String name, float val) { throw uoe(); }
        @Override public double get(String name, double val) { throw uoe(); }
        @Override public Object get(String name, Object val) { throw uoe(); }
        @Override public Class[] serialClasses() { throw uoe(); }
        @Override public GetArg validateInvariants(String[] f, Class[] t, boolean[] n) { throw uoe(); }

        private static UnsupportedOperationException uoe() {
            return new UnsupportedOperationException("not used by this test");
        }
    }
}
