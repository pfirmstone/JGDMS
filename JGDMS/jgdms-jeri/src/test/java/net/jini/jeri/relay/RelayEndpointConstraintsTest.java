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

package net.jini.jeri.relay;

import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import java.io.ByteArrayInputStream;
import net.jini.core.constraint.AtomicInputValidation;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.jeri.OutboundRequest;
import org.junit.Assert;
import org.junit.Test;

/**
 * The unfulfilled constraints of a relay request are the invocation-layer
 * ones only, while the relay still receives all of them.
 */
public class RelayEndpointConstraintsTest {

    private static InvocationConstraints ic(InvocationConstraint[] req,
                                            InvocationConstraint[] pref)
    {
        return new InvocationConstraints(req, pref);
    }

    private static InvocationConstraint[] of(InvocationConstraint... c) {
        return c;
    }

    @Test
    public void requiredIntegrityIsUnfulfilledAndRelayGetsEverything()
        throws Exception
    {
        InvocationConstraints c = ic(
            of(Integrity.YES, ServerAuthentication.YES),
            of(Confidentiality.YES));
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.hold()) {
            OutboundRequest r = RelayEndpoint.getInstance("d")
                .newRequest(c, relay.channels()).next();
            Assert.assertEquals(new InvocationConstraints(Integrity.YES, null),
                r.getUnfulfilledConstraints());
            RelayEndpointTestRelay.Received got = relay.nextReceived();
            r.abort();
            Object sent = new DerMarshalInputStream(
                new ByteArrayInputStream(got.constraintBytes)).readObject();
            Assert.assertEquals(c, sent);
        }
    }

    @Test
    public void transportOnlyConstraintsAreNotReported() {
        InvocationConstraints c = ic(
            of(ServerAuthentication.YES, Confidentiality.YES,
               new ConnectionRelativeTime(1000), Integrity.NO),
            of(ServerAuthentication.YES));
        Assert.assertEquals(InvocationConstraints.EMPTY,
            RelayOutboundRequest.invocationLayer(c));
    }

    @Test
    public void preferencesStayPreferences() {
        InvocationConstraints c = ic(
            of(ServerAuthentication.YES),
            of(Integrity.YES, AtomicInputValidation.YES,
               MarshallingFormat.ATOMIC_DER, Confidentiality.YES));
        InvocationConstraints u = RelayOutboundRequest.invocationLayer(c);
        Assert.assertTrue(u.requirements().isEmpty());
        Assert.assertEquals(ic(of(),
            of(Integrity.YES, AtomicInputValidation.YES,
               MarshallingFormat.ATOMIC_DER)), u);
    }

    @Test
    public void requirementsOfEveryInvocationLayerKind() {
        InvocationConstraints c = ic(
            of(AtomicInputValidation.YES, MarshallingFormat.ATOMIC_DER,
               Integrity.YES, ServerAuthentication.YES),
            of(Integrity.YES));
        InvocationConstraints u = RelayOutboundRequest.invocationLayer(c);
        Assert.assertEquals(ic(
            of(AtomicInputValidation.YES, MarshallingFormat.ATOMIC_DER,
               Integrity.YES), of()), u);
    }

    @Test
    public void emptyConstraintsGiveEmpty() {
        Assert.assertEquals(InvocationConstraints.EMPTY,
            RelayOutboundRequest.invocationLayer(InvocationConstraints.EMPTY));
    }
}
