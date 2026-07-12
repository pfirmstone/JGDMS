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
package au.net.zeus.jgdms.service.support;

import au.net.zeus.jgdms.service.annotation.JiniService;
import au.net.zeus.jgdms.service.annotation.ProxyType;
import au.net.zeus.jgdms.service.support.smartfixture.ConstrainableDummyServiceApiProxy;
import au.net.zeus.jgdms.service.support.smartfixture.ConstrainableStatefulServiceApiProxy;
import au.net.zeus.jgdms.service.support.smartfixture.DummyServiceApi;
import au.net.zeus.jgdms.service.support.smartfixture.StatefulServiceApi;
import java.rmi.RemoteException;
import net.jini.config.EmptyConfiguration;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies {@link AbstractJiniService}'s default {@code createProxy(Object, Uuid)}:
 * unchanged for {@code DYNAMIC}, reflectively resolves and invokes the generated
 * {@code Constrainable<Api>Proxy.create(...)} factory for {@code SMART} (stateless
 * and, via {@link AbstractJiniService#smartProxyStateArgs()}, stateful), with no
 * override and no generated class name in the service's own source.
 */
public class CreateProxyDefaultTest {

    private static DefaultJiniServiceParameters params(Class<?> serviceInterface) throws Exception {
        return new DefaultJiniServiceParameters(
                EmptyConfiguration.INSTANCE, "test.component", null, serviceInterface);
    }

    // --- DYNAMIC: default createProxy returns the stub unchanged -------------

    @JiniService(api = DummyServiceApi.class)
    static final class DynamicService extends AbstractJiniService implements DummyServiceApi {
        DynamicService() throws Exception { super(params(DummyServiceApi.class), null); }
        @Override public String ping() { return "pong"; }
        Object callCreateProxy(Object stub, Uuid uuid) { return createProxy(stub, uuid); }
    }

    @Test
    public void dynamicServiceReturnsStubUnchanged() throws Exception {
        DynamicService svc = new DynamicService();
        Object stub = new Object();
        Uuid uuid = UuidFactory.generate();
        assertSame("DYNAMIC default createProxy must return the stub unchanged",
                stub, svc.callCreateProxy(stub, uuid));
    }

    // --- SMART, stateless: auto-resolved with no override ---------------------

    @JiniService(api = DummyServiceApi.class, proxy = ProxyType.SMART)
    static final class SmartStatelessService extends AbstractJiniService
            implements DummyServiceApi {
        SmartStatelessService() throws Exception { super(params(DummyServiceApi.class), null); }
        @Override public String ping() { return "pong"; }
        Object callCreateProxy(Object stub, Uuid uuid) { return createProxy(stub, uuid); }
    }

    @Test
    public void smartStatelessServiceResolvesGeneratedFactoryWithNoOverride() throws Exception {
        SmartStatelessService svc = new SmartStatelessService();
        Object stub = new Object();
        Uuid uuid = UuidFactory.generate();
        Object proxy = svc.callCreateProxy(stub, uuid);
        assertTrue("must be the generated fixture proxy",
                proxy instanceof ConstrainableDummyServiceApiProxy);
        ConstrainableDummyServiceApiProxy p = (ConstrainableDummyServiceApiProxy) proxy;
        assertSame(stub, p.server);
        assertSame(uuid, p.proxyID);
    }

    // --- SMART, stateful: smartProxyStateArgs() supplies the extra ctor arg ---

    @JiniService(api = StatefulServiceApi.class, proxy = ProxyType.SMART)
    static final class SmartStatefulService extends AbstractJiniService
            implements StatefulServiceApi {
        SmartStatefulService() throws Exception { super(params(StatefulServiceApi.class), null); }
        @Override public String describe() { return "n/a"; }
        @Override protected Object[] smartProxyStateArgs() { return new Object[]{ "degC" }; }
        Object callCreateProxy(Object stub, Uuid uuid) { return createProxy(stub, uuid); }
    }

    @Test
    public void smartStatefulServiceThreadsStateArgsThroughSmartProxyStateArgs() throws Exception {
        SmartStatefulService svc = new SmartStatefulService();
        Object stub = new Object();
        Uuid uuid = UuidFactory.generate();
        Object proxy = svc.callCreateProxy(stub, uuid);
        assertTrue(proxy instanceof ConstrainableStatefulServiceApiProxy);
        ConstrainableStatefulServiceApiProxy p = (ConstrainableStatefulServiceApiProxy) proxy;
        assertSame(stub, p.server);
        assertSame(uuid, p.proxyID);
        assertTrue("state arg from smartProxyStateArgs() must reach the generated factory",
                "degC".equals(p.label));
    }

    // --- SMART, no matching generated class: fails loudly at start, not silently

    @JiniService(proxy = ProxyType.SMART)
    static final class SmartServiceMissingGeneratedProxy extends AbstractJiniService
            implements NoGeneratedProxyApi {
        SmartServiceMissingGeneratedProxy() throws Exception {
            super(params(NoGeneratedProxyApi.class), null);
        }
        @Override public void noop() { }
        Object callCreateProxy(Object stub, Uuid uuid) { return createProxy(stub, uuid); }
    }

    public interface NoGeneratedProxyApi extends java.rmi.Remote {
        void noop() throws RemoteException;
    }

    @Test
    public void smartServiceWithNoGeneratedProxyFailsLoud() throws Exception {
        SmartServiceMissingGeneratedProxy svc = new SmartServiceMissingGeneratedProxy();
        try {
            svc.callCreateProxy(new Object(), UuidFactory.generate());
            fail("expected IllegalStateException: no generated proxy class for"
                    + " NoGeneratedProxyApi");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("ConstrainableNoGeneratedProxyApiProxy"));
        }
    }
}
