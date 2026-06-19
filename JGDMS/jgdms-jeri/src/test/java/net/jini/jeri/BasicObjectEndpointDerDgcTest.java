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

import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
 * <p>The first three tests drive the constructor with a fake {@code GetArg} that models the
 * DER path (no reader; a recording {@code DeserializationCompletion} in the object-stream
 * context); the last does a real DER encode/decode round-trip of a DGC-enabled
 * {@code BasicObjectEndpoint} (now possible: its interface-typed {@code ep} field travels by
 * runtime concrete type, and its {@code @Stateless} {@code Uuid} encodes as an empty leaf).
 * None fire the batched dirty ({@code endDecodeUnit}/{@code close}), which would attempt a
 * real network {@code registerRefs}; the end-of-decode-unit firing is covered, network-free,
 * by {@code au.net.zeus.jgdms.der.stream.DerDecodeUnitContextTest}.
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

    /**
     * End-to-end: a DGC-enabled BasicObjectEndpoint encodes and decodes over the real DER
     * wire without the NPE -- its interface-typed Endpoint 'ep' field travels by runtime
     * concrete @AtomicSerial type (TcpEndpoint), and its @Stateless Uuid id
     * (net.jini.id.UuidFactory$Impl) now encodes as an empty leaf over the Uuid base. The
     * decode registers the batched dirty on the stream's decode-unit token; the stream is
     * left unclosed so the dirty is not fired (which would hit the network).
     */
    @Test
    public void dgcEnabled_realDerRoundTrip_noNpe() throws Exception {
        BasicObjectEndpoint original = new BasicObjectEndpoint(EP, ID, true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(baos)) {
            out.writeObject(original);
        }

        // Not closed on purpose: close()/endDecodeUnit() would flush the batched dirty and
        // attempt a real network registerRefs. readObject() exercises the (formerly NPEing)
        // DGC registration path end-to-end.
        DerMarshalInputStream in =
                new DerMarshalInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Object decoded = in.readObject();

        Assert.assertTrue("decoded object must be a BasicObjectEndpoint",
                decoded instanceof BasicObjectEndpoint);
        Assert.assertEquals("DGC endpoint must round-trip over DER", original, decoded);
        Assert.assertNotSame("decoded endpoint must be a distinct instance", original, decoded);
    }

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

        // Inc2 GetArg contract: the base owns the final typed get(...)/defaulted accessors
        // and caller resolution (via serialClasses() + StackWalker); subclasses supply the
        // boxed value via lookup() and presence via isDefaulted().
        @Override
        protected Object lookup(Class<?> callerClass, String name) {
            switch (name) {
                case "ep":  return ep;
                case "id":  return id;
                case "dgc": return Boolean.valueOf(dgc);
                default:    return ABSENT;
            }
        }

        @Override
        protected boolean isDefaulted(Class<?> callerClass, String name) {
            return false; // all three fields are present
        }

        @Override
        public Class[] serialClasses() {
            // The base resolves the caller against this set (single class -> safe fallback).
            return new Class[] { BasicObjectEndpoint.class };
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
    }
}
