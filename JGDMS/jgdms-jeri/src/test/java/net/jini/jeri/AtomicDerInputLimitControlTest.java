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

import au.net.zeus.jgdms.der.DerInputLimitControl;
import au.net.zeus.jgdms.der.DerInputLimits;
import net.jini.core.constraint.InvocationConstraints;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The RemoteMethodControl-style {@link DerInputLimitControl} on a DER proxy: getInputLimits /
 * setInputLimits are handled locally by {@link AtomicDerInvocationHandler}, returning a NEW proxy
 * (no remote call, no transport needed -- the stub endpoint is never used).
 */
public class AtomicDerInputLimitControlTest {

    public interface Greeter { String greet(); }

    /** The control methods are handled locally, so the endpoint is never invoked. */
    static final class StubObjectEndpoint implements ObjectEndpoint {
        @Override public OutboundRequestIterator newCall(InvocationConstraints constraints) {
            throw new UnsupportedOperationException("stub");
        }
        @Override public RemoteException executeCall(OutboundRequest call) throws IOException {
            throw new UnsupportedOperationException("stub");
        }
    }

    private static Object newProxy(AtomicDerInvocationHandler h) {
        return Proxy.newProxyInstance(
                AtomicDerInputLimitControlTest.class.getClassLoader(),
                new Class<?>[]{ Greeter.class, DerInputLimitControl.class },
                h);
    }

    @Test
    public void getInputLimitsDefaultsToDefault() {
        Object proxy = newProxy(new AtomicDerInvocationHandler(new StubObjectEndpoint(), null));
        assertSame(DerInputLimits.DEFAULT, ((DerInputLimitControl) proxy).getInputLimits());
    }

    @Test
    public void setInputLimitsReturnsNewProxyAndDoesNotMutateOriginal() {
        Object proxy = newProxy(new AtomicDerInvocationHandler(new StubObjectEndpoint(), null));
        DerInputLimits custom = DerInputLimits.maxBytes(8 * 1024 * 1024);

        Object proxy2 = ((DerInputLimitControl) proxy).setInputLimits(custom);

        assertNotSame("setInputLimits must return a NEW proxy", proxy, proxy2);
        assertSame("new proxy carries the cap", custom, ((DerInputLimitControl) proxy2).getInputLimits());
        assertSame("original proxy is unchanged",
                DerInputLimits.DEFAULT, ((DerInputLimitControl) proxy).getInputLimits());
        assertTrue("new proxy still implements the service interface", proxy2 instanceof Greeter);
    }

    @Test
    public void setInputLimitsRejectsNull() {
        Object proxy = newProxy(new AtomicDerInvocationHandler(new StubObjectEndpoint(), null));
        assertThrows(NullPointerException.class,
                () -> ((DerInputLimitControl) proxy).setInputLimits(null));
    }
}
