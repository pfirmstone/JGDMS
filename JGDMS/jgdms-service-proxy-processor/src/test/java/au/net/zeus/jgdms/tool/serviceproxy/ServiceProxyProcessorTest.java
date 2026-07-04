/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package au.net.zeus.jgdms.tool.serviceproxy;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link ServiceProxyProcessor}: validate-only diagnostics (P0) and
 * shape-dispatched generation (JGDMS-STD-009 §6 + §14).  Each test drives the
 * processor over in-memory sources via {@link ProcessorHarness}.
 *
 * <p>{@code @JiniService} lives on the service <em>implementation</em> class and
 * names its API interface(s) with {@code api()} (or leaves {@code api()} empty for
 * inference); the processor reads it off the impl, resolves the API interface, and
 * generates against that.  Generation is gated per shape:
 * <ul>
 *   <li>the backend (wire) interface only when {@code protocol() != api()} (a
 *       translating smart proxy); a do-nothing proxy ({@code protocol == api})
 *       gets none;</li>
 *   <li>a {@code DYNAMIC} service (with {@code protocol == api}) generates
 *       NOTHING -- no backend, no proxy class, and no ILFactory: its admin
 *       interfaces are supplied at export by the reusable framework factory
 *       {@code net.jini.jeri.DynamicILFactory};</li>
 *   <li>a {@code SMART} service gets the constrainable proxy class.</li>
 * </ul>
 */
public class ServiceProxyProcessorTest {

    // ---------------------------------------------------------------- validation

    @Test
    public void apiMethodMissingRemoteExceptionIsError() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello;"
                + " import java.rmi.Remote;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name);"   // missing throws RemoteException
                + " }")
            .add("hello.HelloServiceImpl",
                "package hello;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService(api = HelloService.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) { return name; } }")
            .run();
        assertTrue(r.allMessages(),
            r.hasError("must declare 'throws java.rmi.RemoteException'"));
    }

    @Test
    public void apiMethodWithRemoteExceptionIsClean() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello;"
                + " import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + " }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService(api = HelloService.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
    }

    @Test
    public void jiniServiceOnInterfaceIsError() {
        // @JiniService belongs on the implementation, not the API interface.
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .run();
        assertTrue(r.allMessages(),
            r.hasError("belongs on the service implementation, not the interface"));
    }

    @Test
    public void apiNotExtendingRemoteIsError() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello; import java.rmi.RemoteException;"
                + " public interface HelloService {"
                + "   String greet(String name) throws RemoteException;"
                + " }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService(api = HelloService.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .run();
        assertTrue(r.allMessages(), r.hasError("must extend java.rmi.Remote"));
    }

    @Test
    public void handWrittenBackendMissingInfraIsError() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello;"
                + " import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + " }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService(api = HelloService.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .add("hello.HelloServiceBackend",
                "package hello; import java.rmi.Remote;"
                + " public interface HelloServiceBackend extends Remote, HelloService,"
                + "   net.jini.lookup.ServiceProxyAccessor {"  // missing the rest
                + " }")
            .run();
        assertTrue(r.allMessages(),
            r.hasError("missing required infrastructure interface"));
    }

    @Test
    public void handWrittenBackendCompleteIsClean() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello;"
                + " import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + " }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService(api = HelloService.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .add("hello.HelloServiceBackend",
                "package hello; import java.rmi.Remote;"
                + " public interface HelloServiceBackend extends Remote, HelloService,"
                + "   net.jini.lookup.ServiceProxyAccessor,"
                + "   net.jini.lookup.ServiceAttributesAccessor,"
                + "   net.jini.lookup.ServiceIDAccessor,"
                + "   net.jini.export.CodebaseAccessor,"
                + "   net.jini.admin.Administrable,"
                + "   net.jini.admin.JoinAdmin,"
                + "   org.apache.river.admin.DestroyAdmin {"
                + " }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
    }

    @Test
    public void concreteNonConstrainableProxyIsError() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello;"
                + " import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + " }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService(api = HelloService.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            // concrete proxy extending AbstractSmartProxy directly -> fail-open plain variant
            .add("hello.proxy.HelloServiceProxy",
                "package hello.proxy;"
                + " public class HelloServiceProxy"
                + "   extends au.net.zeus.jgdms.proxy.AbstractSmartProxy {}")
            .run();
        assertTrue(r.allMessages(), r.hasError("non-constrainable smart proxy"));
    }

    @Test
    public void constrainableProxyIsClean() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello;"
                + " import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + " }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService(api = HelloService.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .add("hello.proxy.ConstrainableHelloServiceProxy",
                "package hello.proxy;"
                + " public class ConstrainableHelloServiceProxy"
                + "   extends au.net.zeus.jgdms.proxy.AbstractSmartProxy.ConstrainableSmartProxy {}")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
    }

    @Test
    public void smartProxyDelegateNotImplementingApiIsError() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String n) throws RemoteException; }")
            .add("hello.HelloSmartLogic",
                "package hello;"
                + " @au.net.zeus.jgdms.service.annotation.SmartProxy(api = HelloService.class)"
                + " public final class HelloSmartLogic {}")  // does not implement HelloService
            .run();
        assertTrue(r.allMessages(), r.hasError("must implement its declared api"));
    }

    @Test
    public void emptyApiInferredFromImplementedInterface() {
        // api() left empty -> the processor infers HelloService from the impl's
        // implemented interfaces (minus the infrastructure set) and validates it.
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"  // no api() -> inferred
                + " public class HelloServiceImpl implements HelloService,"
                + "   net.jini.admin.Administrable {"  // an infra iface, must be ignored
                + "   public String greet(String name) throws RemoteException { return name; }"
                + "   public Object getAdmin() { return null; } }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
    }

    @Test
    public void emptyApiWithMultipleServiceInterfacesInfersAllNoAmbiguity() {
        // Multi-interface convergence (design decision D2): an empty api() with TWO
        // Remote service interfaces is NO LONGER "ambiguous" -- both are inferred and
        // registered via the shared allowlist (extends Remote, minus the four
        // accessors), exactly as the runtime AbstractJiniService.classify does.  Being
        // DYNAMIC with protocol == api, it still generates nothing.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.ByeService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface ByeService extends Remote {"
                + "   String bye(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"  // empty api() -> infer BOTH
                + " public class HelloServiceImpl implements HelloService, ByeService {"
                + "   public String greet(String name) throws RemoteException { return name; }"
                + "   public String bye(String name) throws RemoteException { return name; } }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
        assertTrue("DYNAMIC multi-interface must generate NOTHING: " + r.generated.keySet(),
            r.generated.isEmpty());
    }

    @Test
    public void emptyApiIgnoresNonRemoteCustomAdminInterface() {
        // Allowlist convergence: a non-Remote custom admin-style interface declared
        // directly on the impl is NOT part of the inferred api (it is the
        // administrative contract, reached via getAdmin()).  Only HelloService (the
        // Remote interface) is the api, so there is no "must extend Remote" error.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.FooAdmin",
                "package hello; import java.rmi.RemoteException;"
                + " public interface FooAdmin {"       // NOT Remote -> admin, not api
                + "   void foo() throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"  // empty api()
                + " public class HelloServiceImpl implements HelloService, FooAdmin {"
                + "   public String greet(String name) throws RemoteException { return name; }"
                + "   public void foo() throws RemoteException {} }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
        assertTrue("DYNAMIC must generate NOTHING: " + r.generated.keySet(),
            r.generated.isEmpty());
    }

    @Test
    public void emptyApiWithNoServiceInterfaceIsError() {
        // api() empty and the impl implements only infrastructure interfaces:
        // nothing to infer -> fail-closed.
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloServiceImpl",
                "package hello;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public class HelloServiceImpl implements net.jini.admin.Administrable {"
                + "   public Object getAdmin() { return null; } }")
            .run();
        assertTrue(r.allMessages(),
            r.hasError("no service interface could be inferred"));
    }

    // ------------------------------------------------------------------ generation

    @Test
    public void validateOnlyGeneratesNothing() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService(api = HelloService.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .run();
        assertTrue("validate-only must generate nothing: " + r.generated.keySet(),
            r.generated.isEmpty());
    }

    @Test
    public void generatesBackendInterface() {
        // The backend (wire) interface is generated only for a TRANSLATING smart
        // proxy -- protocol() != api().  Here HelloService's internal protocol is a
        // distinct HelloProtocol interface, so a backend that aggregates the API +
        // infra under Remote IS emitted (§6 shape-3 translating case).  (A
        // do-nothing proxy with protocol == api gets no backend -- see
        // dynamicProtocolIsApiGeneratesNothing.)
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloProtocol",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloProtocol extends Remote {"
                + "   String greetInternal(String name) throws RemoteException; }")
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService(api = HelloService.class,"
                + "     protocol = HelloProtocol.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
        String backend = r.generated.get("hello.HelloServiceBackend");
        assertTrue("backend must be generated; got " + r.generated.keySet(),
            backend != null);
        // Aggregates Remote, the API, and every infra interface.
        assertTrue(backend, backend.contains("interface HelloServiceBackend"));
        assertTrue(backend, backend.contains("java.rmi.Remote"));
        assertTrue(backend, backend.contains("hello.HelloService"));
        assertTrue(backend, backend.contains("net.jini.lookup.ServiceProxyAccessor"));
        assertTrue(backend, backend.contains("net.jini.lookup.ServiceAttributesAccessor"));
        assertTrue(backend, backend.contains("net.jini.lookup.ServiceIDAccessor"));
        assertTrue(backend, backend.contains("net.jini.export.CodebaseAccessor"));
        assertTrue(backend, backend.contains("net.jini.admin.Administrable"));
        assertTrue(backend, backend.contains("net.jini.admin.JoinAdmin"));
        assertTrue(backend, backend.contains("org.apache.river.admin.DestroyAdmin"));
    }

    @Test
    public void doesNotGenerateBackendWhenHandWritten() {
        // protocol != api so a backend WOULD be generated -- but it is hand-written,
        // so the processor must validate and NOT regenerate it.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloProtocol",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloProtocol extends Remote {"
                + "   String greetInternal(String name) throws RemoteException; }")
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService(api = HelloService.class,"
                + "     protocol = HelloProtocol.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .add("hello.HelloServiceBackend",
                "package hello; import java.rmi.Remote;"
                + " public interface HelloServiceBackend extends Remote, HelloService,"
                + "   net.jini.lookup.ServiceProxyAccessor,"
                + "   net.jini.lookup.ServiceAttributesAccessor,"
                + "   net.jini.lookup.ServiceIDAccessor,"
                + "   net.jini.export.CodebaseAccessor,"
                + "   net.jini.admin.Administrable,"
                + "   net.jini.admin.JoinAdmin,"
                + "   org.apache.river.admin.DestroyAdmin {}")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
        assertFalse("must not regenerate a hand-written backend",
            r.generated.containsKey("hello.HelloServiceBackend"));
    }

    @Test
    public void generatesConstrainableProxy() {
        // A hand-written constrainable proxy IS a SMART proxy, so the golden-diff
        // fixture that drives this shape declares proxy = ProxyType.SMART (§6
        // shape 3 is the only shape that emits a proxy class).
        ProcessorHarness h = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String sayHello(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = HelloService.class, proxy = ProxyType.SMART)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String sayHello(String name) throws RemoteException { return name; } }");
        ProcessorHarness.Result r = h.run();
        assertFalse(r.allMessages(), r.hasAnyError());
        String proxy = r.generated.get("hello.ConstrainableHelloServiceProxy");
        assertTrue("proxy must be generated; got " + r.generated.keySet(), proxy != null);
        // Shape: constrainable, @AtomicSerial @Stateless, fail-closed create, forwarding.
        assertTrue(proxy, proxy.contains("extends au.net.zeus.jgdms.proxy.AbstractSmartProxy.ConstrainableSmartProxy"));
        assertTrue(proxy, proxy.contains("implements hello.HelloService"));
        assertTrue(proxy, proxy.contains("@org.apache.river.api.io.AtomicSerial"));
        assertTrue(proxy, proxy.contains("@org.apache.river.api.io.AtomicSerial.Stateless"));
        assertTrue(proxy, proxy.contains("public static au.net.zeus.jgdms.proxy.AbstractSmartProxy create("));
        assertTrue("create must fail closed on non-RemoteMethodControl:\n" + proxy,
            proxy.contains("if (!(server instanceof net.jini.core.constraint.RemoteMethodControl))"));
        assertTrue("must not degrade to plain proxy (throws):\n" + proxy,
            proxy.contains("throw new IllegalArgumentException"));
        assertTrue(proxy, proxy.contains("setConstraints"));
        assertTrue("forwards sayHello to server:\n" + proxy,
            proxy.contains("((hello.HelloService) server).sayHello(name)"));
    }

    @Test
    public void generatedProxyCompiles() {
        // SMART: the golden proxy-class fixture (only shape 3 emits a proxy class).
        ProcessorHarness h = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String sayHello(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = HelloService.class, proxy = ProxyType.SMART)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String sayHello(String name) throws RemoteException { return name; } }");
        ProcessorHarness.Result gen = h.run();
        assertFalse(gen.allMessages(), gen.hasAnyError());
        // The generated backend + proxy must compile against the stubs.
        ProcessorHarness.Result compiled = h.compileGenerated(gen);
        assertTrue("generated sources must compile:\n" + compiled.allMessages()
                + "\n--- proxy ---\n" + gen.generated.get("hello.ConstrainableHelloServiceProxy"),
            compiled.success);
    }

    @Test
    public void generatedProxyPreservesGenericsVarargsAndThrows() {
        // SMART: signature-fidelity is checked on the generated proxy class.
        ProcessorHarness h = new ProcessorHarness()
            .add("svc.Svc",
                "package svc; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " import java.util.List;"
                + " public interface Svc extends Remote {"
                + "   <T> List<T> pick(T[] items) throws RemoteException;"
                + "   void log(String fmt, Object... args) throws RemoteException;"
                + "   int count() throws RemoteException; }")
            .add("svc.SvcImpl",
                "package svc; import java.rmi.RemoteException; import java.util.List;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = Svc.class, proxy = ProxyType.SMART)"
                + " public class SvcImpl implements Svc {"
                + "   public <T> List<T> pick(T[] items) throws RemoteException { return null; }"
                + "   public void log(String fmt, Object... args) throws RemoteException {}"
                + "   public int count() throws RemoteException { return 0; } }");
        ProcessorHarness.Result gen = h.run();
        assertFalse(gen.allMessages(), gen.hasAnyError());
        String proxy = gen.generated.get("svc.ConstrainableSvcProxy");
        assertTrue(proxy != null);
        assertTrue("type parameter preserved:\n" + proxy, proxy.contains("<T> java.util.List<T> pick(T[] items)"));
        assertTrue("varargs preserved:\n" + proxy, proxy.contains("void log(java.lang.String fmt, java.lang.Object... args)"));
        assertTrue("void method forwards without return:\n" + proxy,
            proxy.contains("((svc.Svc) server).log(fmt, args);"));
        assertTrue("non-void returns:\n" + proxy, proxy.contains("return ((svc.Svc) server).count();"));
        // And it must all compile.
        ProcessorHarness.Result compiled = h.compileGenerated(gen);
        assertTrue("generated proxy with generics/varargs must compile:\n"
                + compiled.allMessages() + "\n" + proxy, compiled.success);
    }

    @Test
    public void smartWithDefaultProtocolGeneratesProxyButNoBackend() {
        // Shape 3 (JGDMS-STD-009 §6): a SMART service with the DEFAULT protocol
        // (protocol == api -- a one-to-one forwarding smart proxy) generates the
        // constrainable proxy class but NO backend interface: the API interface is
        // already the wire interface, so there is nothing to translate.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = HelloService.class, proxy = ProxyType.SMART, codebase = true)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
        assertFalse("SMART + protocol==api generates NO backend: " + r.generated.keySet(),
            r.generated.containsKey("hello.HelloServiceBackend"));
        assertTrue("SMART generates the constrainable proxy class: " + r.generated.keySet(),
            r.generated.containsKey("hello.ConstrainableHelloServiceProxy"));
    }

    @Test
    public void smartWithDistinctProtocolGeneratesBackendAndProxy() {
        // Shape 3 translating case: a SMART service whose protocol() differs from
        // its api() (the proxy translates the API into a distinct internal wire
        // protocol) generates BOTH the backend wire interface AND the proxy class.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloProtocol",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloProtocol extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = HelloService.class, proxy = ProxyType.SMART,"
                + "     codebase = true, protocol = HelloProtocol.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
        assertTrue("SMART + protocol!=api generates the backend: " + r.generated.keySet(),
            r.generated.containsKey("hello.HelloServiceBackend"));
        assertTrue("SMART generates the constrainable proxy class: " + r.generated.keySet(),
            r.generated.containsKey("hello.ConstrainableHelloServiceProxy"));
    }

    @Test
    public void dynamicProtocolIsApiGeneratesNothing() {
        // Shape 1 (JGDMS-STD-009 §6 + §14 [RESOLVED]): a DYNAMIC service with
        // protocol == api (a do-nothing dynamic proxy) generates NOTHING:
        //   - NO backend interface   (the API interface is already the wire type),
        //   - NO constrainable proxy class (only shape 3 does),
        //   - NO ILFactory: the non-Remote admin interfaces are supplied to the
        //     exported stub at export by the reusable framework factory
        //     net.jini.jeri.DynamicILFactory, which the JGDMS service support
        //     installs by default (neither codegen nor config required).
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = HelloService.class, proxy = ProxyType.DYNAMIC)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
        assertTrue("DYNAMIC + protocol==api must generate NOTHING: " + r.generated.keySet(),
            r.generated.isEmpty());
    }

    @Test
    public void smartWithCodebaseFalseIsFullySupported() {
        // JGDMS-STD-009 §6 / Unit-3 scope item 3: SMART + codebase=false is a
        // legitimate shape (proxy served from a SHARED codebase; proxy identity is
        // endpoint + codebase, not an identity smell).  The processor MUST emit the
        // constrainable proxy with NO error and NO warning -- codebase only gates
        // -dl-vs-shared PACKAGING, never codegen.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = HelloService.class, proxy = ProxyType.SMART,"
                + "     codebase = false)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .run();
        assertFalse("SMART + codebase=false must not error:\n" + r.allMessages(),
            r.hasAnyError());
        assertFalse("SMART + codebase=false must not warn:\n" + r.allMessages(),
            r.hasWarning("codebase"));
        assertTrue("SMART still generates the constrainable proxy class: "
                + r.generated.keySet(),
            r.generated.containsKey("hello.ConstrainableHelloServiceProxy"));
    }

    // -------------------------------------------------- multi-interface services

    @Test
    public void smartMultiInterfaceGeneratesProxyForAll() {
        // JGDMS-STD-009 §3.1/§4: a SMART service declaring MULTIPLE api interfaces
        // generates a single proxy that implements ALL of them and forwards every
        // interface's methods -- each dispatched through the interface that
        // declares it (casting every method to a single api would not compile when
        // a method belongs to a sibling interface).
        ProcessorHarness h = new ProcessorHarness()
            .add("multi.Foo",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   String foo(String s) throws RemoteException; }")
            .add("multi.Bar",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   int bar(int n) throws RemoteException; }")
            .add("multi.MultiImpl",
                "package multi; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = { Foo.class, Bar.class }, proxy = ProxyType.SMART)"
                + " public class MultiImpl implements Foo, Bar {"
                + "   public String foo(String s) throws RemoteException { return s; }"
                + "   public int bar(int n) throws RemoteException { return n; } }");
        ProcessorHarness.Result gen = h.run();
        assertFalse(gen.allMessages(), gen.hasAnyError());
        String proxy = gen.generated.get("multi.ConstrainableFooProxy");
        assertTrue("proxy is named after the PRIMARY api; got " + gen.generated.keySet(),
            proxy != null);
        assertTrue("implements BOTH api interfaces:\n" + proxy,
            proxy.contains("implements multi.Foo, multi.Bar"));
        assertTrue("forwards foo() through Foo:\n" + proxy,
            proxy.contains("return ((multi.Foo) server).foo(s);"));
        assertTrue("forwards bar() through Bar:\n" + proxy,
            proxy.contains("return ((multi.Bar) server).bar(n);"));
        ProcessorHarness.Result compiled = h.compileGenerated(gen);
        assertTrue("multi-interface proxy must compile:\n" + compiled.allMessages()
                + "\n" + proxy, compiled.success);
    }

    @Test
    public void smartMultiInterfaceInferredFromImpl() {
        // api() empty: BOTH service interfaces are inferred from the impl (minus the
        // infrastructure Administrable) -- multiple inferred interfaces are the
        // multi-interface case, NOT an ambiguity error.
        ProcessorHarness h = new ProcessorHarness()
            .add("multi.Foo",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   String foo(String s) throws RemoteException; }")
            .add("multi.Bar",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   int bar(int n) throws RemoteException; }")
            .add("multi.MultiImpl",
                "package multi; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(proxy = ProxyType.SMART)"  // no api() -> inferred
                + " public class MultiImpl implements Foo, Bar, net.jini.admin.Administrable {"
                + "   public String foo(String s) throws RemoteException { return s; }"
                + "   public int bar(int n) throws RemoteException { return n; }"
                + "   public Object getAdmin() { return null; } }");
        ProcessorHarness.Result gen = h.run();
        assertFalse(gen.allMessages(), gen.hasAnyError());
        String proxy = gen.generated.get("multi.ConstrainableFooProxy");
        assertTrue("proxy generated for inferred multi-api; got " + gen.generated.keySet(),
            proxy != null);
        assertTrue("implements both inferred api interfaces (infra excluded):\n" + proxy,
            proxy.contains("implements multi.Foo, multi.Bar"));
        ProcessorHarness.Result compiled = h.compileGenerated(gen);
        assertTrue("inferred multi-interface proxy must compile:\n"
                + compiled.allMessages() + "\n" + proxy, compiled.success);
    }

    @Test
    public void emptyApiMultipleInferredIsNotAmbiguous() {
        // Regression: an impl implementing MORE THAN ONE service interface with an
        // empty api() previously failed with an "ambiguous" error; it is now the
        // legitimate multi-interface case (symmetric with the runtime
        // AbstractJiniService.getServiceInterfaces()).
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("multi.Foo",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   String foo(String s) throws RemoteException; }")
            .add("multi.Bar",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   int bar(int n) throws RemoteException; }")
            .add("multi.MultiImpl",
                "package multi; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public class MultiImpl implements Foo, Bar {"
                + "   public String foo(String s) throws RemoteException { return s; }"
                + "   public int bar(int n) throws RemoteException { return n; } }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
    }

    @Test
    public void dynamicMultiInterfaceGeneratesNothing() {
        // A DYNAMIC multi-interface service with protocol == api is still shape 1:
        // the exported java.lang.reflect.Proxy carries every api interface, so the
        // processor generates nothing (no proxy class, no backend).
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("multi.Foo",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   String foo(String s) throws RemoteException; }")
            .add("multi.Bar",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   int bar(int n) throws RemoteException; }")
            .add("multi.MultiImpl",
                "package multi; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = { Foo.class, Bar.class }, proxy = ProxyType.DYNAMIC)"
                + " public class MultiImpl implements Foo, Bar {"
                + "   public String foo(String s) throws RemoteException { return s; }"
                + "   public int bar(int n) throws RemoteException { return n; } }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
        assertTrue("DYNAMIC multi + protocol==api generates NOTHING: " + r.generated.keySet(),
            r.generated.isEmpty());
    }

    @Test
    public void defaultProxyTypeIsDynamicGeneratesNothing() {
        // The default @JiniService (no proxy element, default protocol) is DYNAMIC
        // with protocol == api, so it emits nothing at all -- no backend, no proxy
        // class, and no ILFactory.  Admin dispatch comes from the framework's
        // default net.jini.jeri.DynamicILFactory (the shape-1 default).
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService(api = HelloService.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
        assertTrue("default (DYNAMIC) must generate NOTHING: " + r.generated.keySet(),
            r.generated.isEmpty());
    }
}
