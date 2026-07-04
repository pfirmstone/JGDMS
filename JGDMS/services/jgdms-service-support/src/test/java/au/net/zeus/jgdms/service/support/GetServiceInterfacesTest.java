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

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.config.EmptyConfiguration;
import net.jini.export.ProxyAccessor;
import org.apache.river.admin.DestroyAdmin;
import au.net.zeus.jgdms.service.annotation.JiniService;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies that {@link AbstractJiniService#getServiceInterfaces()} resolves the
 * service API from the {@link JiniService @JiniService} annotation on the concrete
 * implementation class: it returns {@code api()} when declared, infers the API
 * from the implemented interfaces (minus the infrastructure set) when {@code api()}
 * is empty, and fails fast when {@code @JiniService} is absent.
 */
public class GetServiceInterfacesTest {

    // --- test service API interfaces -----------------------------------------

    public interface FooService extends Remote {
        String foo(String s) throws RemoteException;
    }

    public interface BarService extends Remote {
        String bar(String s) throws RemoteException;
    }

    private static DefaultJiniServiceParameters params() throws Exception {
        return new DefaultJiniServiceParameters(
                EmptyConfiguration.INSTANCE, "test.component", null, FooService.class);
    }

    // --- api() declared explicitly -------------------------------------------

    @JiniService(api = FooService.class)
    static final class ExplicitSingle extends AbstractJiniService implements FooService {
        ExplicitSingle() throws Exception { super(params(), null); }
        @Override public String foo(String s) { return s; }
        // getServiceInterfaces() is package-visible to this test via a bridge.
        Class<?>[] serviceInterfaces() { return getServiceInterfaces(); }
    }

    @JiniService(api = { FooService.class, BarService.class })
    static final class ExplicitMulti extends AbstractJiniService
            implements FooService, BarService {
        ExplicitMulti() throws Exception { super(params(), null); }
        @Override public String foo(String s) { return s; }
        @Override public String bar(String s) { return s; }
        Class<?>[] serviceInterfaces() { return getServiceInterfaces(); }
    }

    // --- api() empty -> inferred, minus infrastructure interfaces ------------

    @JiniService
    static final class InferredWithInfra extends AbstractJiniService
            implements FooService, Administrable {
        InferredWithInfra() throws Exception { super(params(), null); }
        @Override public String foo(String s) { return s; }
        @Override public Object getAdmin() { return null; }
        Class<?>[] serviceInterfaces() { return getServiceInterfaces(); }
    }

    // --- api() empty, MULTIPLE service interfaces -> all inferred (multi) -----

    @JiniService
    static final class InferredMulti extends AbstractJiniService
            implements FooService, BarService {
        InferredMulti() throws Exception { super(params(), null); }
        @Override public String foo(String s) { return s; }
        @Override public String bar(String s) { return s; }
        Class<?>[] serviceInterfaces() { return getServiceInterfaces(); }
    }

    // --- no @JiniService -> fail fast at construction -------------------------

    static final class Unannotated extends AbstractJiniService implements FooService {
        Unannotated() throws Exception { super(params(), null); }
        @Override public String foo(String s) { return s; }
    }

    // --- @JiniService inherited from an annotated superclass ------------------

    static class Subclassed extends ExplicitSingleBase {
        Subclassed() throws Exception { super(); }
        Class<?>[] serviceInterfaces() { return getServiceInterfaces(); }
    }

    @JiniService(api = BarService.class)
    static class ExplicitSingleBase extends AbstractJiniService implements BarService {
        ExplicitSingleBase() throws Exception { super(params(), null); }
        @Override public String bar(String s) { return s; }
    }

    // --- tests ---------------------------------------------------------------

    @Test
    public void returnsDeclaredApiSingle() throws Exception {
        assertArrayEquals(new Class<?>[]{ FooService.class },
                new ExplicitSingle().serviceInterfaces());
    }

    @Test
    public void returnsDeclaredApiMultiInOrder() throws Exception {
        assertArrayEquals(new Class<?>[]{ FooService.class, BarService.class },
                new ExplicitMulti().serviceInterfaces());
    }

    @Test
    public void infersApiFromImplementedInterfacesMinusInfra() throws Exception {
        Class<?>[] got = new InferredWithInfra().serviceInterfaces();
        assertArrayEquals("inferred set must be the service API only, no infra: "
                + Arrays.toString(got),
                new Class<?>[]{ FooService.class }, got);
    }

    @Test
    public void infersAllServiceInterfacesWhenApiEmpty() throws Exception {
        // Multi-interface convergence (design decision D2): an empty api() with
        // several Remote service interfaces infers and registers them ALL, in
        // declaration order — it is NOT ambiguous.
        Class<?>[] got = new InferredMulti().serviceInterfaces();
        assertArrayEquals("both Remote service interfaces must be inferred: "
                + Arrays.toString(got),
                new Class<?>[]{ FooService.class, BarService.class }, got);
    }

    @Test
    public void classifyPutsAdminInterfacesInAdminSetNotProxyAccessor() {
        // The derived admin set must be exactly {JoinAdmin, DestroyAdmin}:
        // ProxyAccessor (non-Remote, implemented by AbstractJiniService itself) and
        // Administrable must NOT leak into it, or the common case would be forced off
        // the stable ConstrainableAdminProxy wire form.
        List<Class<?>> admin = Arrays.asList(
                AbstractJiniService.classify(InferredMulti.class).admin);
        assertTrue("admin set must contain JoinAdmin: " + admin,
                admin.contains(JoinAdmin.class));
        assertTrue("admin set must contain DestroyAdmin: " + admin,
                admin.contains(DestroyAdmin.class));
        assertFalse("ProxyAccessor must NOT be classified as an admin interface: " + admin,
                admin.contains(ProxyAccessor.class));
        assertFalse("Administrable must NOT be classified as an admin interface: " + admin,
                admin.contains(Administrable.class));
        assertEquals("admin set must be exactly {JoinAdmin, DestroyAdmin}: " + admin,
                2, admin.size());
    }

    @Test
    public void classifyApiExcludesAdminAndAccessors() {
        // The api set is the client contract only: the Remote service interfaces,
        // never the admin interfaces or the Remote bootstrap accessors.
        List<Class<?>> api = Arrays.asList(
                AbstractJiniService.classify(InferredMulti.class).api);
        assertTrue(api.contains(FooService.class));
        assertTrue(api.contains(BarService.class));
        assertFalse("JoinAdmin is admin, not api: " + api, api.contains(JoinAdmin.class));
        assertEquals("api must be exactly the two service interfaces: " + api, 2, api.size());
    }

    @Test
    public void missingAnnotationFailsFast() {
        try {
            new Unannotated();
            fail("construction must fail when @JiniService is absent");
        } catch (Exception e) {
            // The cause chain (or the exception itself) must be the IllegalStateException.
            Throwable t = e;
            boolean found = false;
            while (t != null) {
                if (t instanceof IllegalStateException
                        && t.getMessage() != null
                        && t.getMessage().contains("@au.net.zeus.jgdms.service.annotation.JiniService")) {
                    found = true;
                    break;
                }
                t = t.getCause();
            }
            if (!found) {
                fail("expected IllegalStateException about missing @JiniService, got " + e);
            }
        }
    }

    @Test
    public void findsAnnotationOnSuperclass() throws Exception {
        assertArrayEquals(new Class<?>[]{ BarService.class },
                new Subclassed().serviceInterfaces());
    }

    @Test
    public void resultIsADefensiveCopy() throws Exception {
        ExplicitSingle svc = new ExplicitSingle();
        Class<?>[] first = svc.serviceInterfaces();
        first[0] = BarService.class; // mutate the returned array
        assertEquals("mutating the returned array must not corrupt the cache",
                FooService.class, svc.serviceInterfaces()[0]);
    }
}
