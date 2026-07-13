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
import static org.junit.Assert.assertEquals;
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
        // The delegate named by @JiniService.smartProxy() must implement every api()
        // interface (the check now lives on the @JiniService side, not @SmartProxy).
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String n) throws RemoteException; }")
            .add("hello.HelloSmartLogic",
                "package hello;"
                + " @au.net.zeus.jgdms.service.annotation.SmartProxy"
                + " public final class HelloSmartLogic {"   // does not implement HelloService
                + "   public HelloSmartLogic(HelloService server) {} }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = HelloService.class, proxy = ProxyType.SMART,"
                + "     smartProxy = HelloSmartLogic.class)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String n) throws RemoteException { return n; } }")
            .run();
        assertTrue(r.allMessages(), r.hasError("must implement the api interface hello.HelloService"));
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
        // distinct HelloProtocol interface.  The generated backend aggregates the
        // internal wire PROTOCOL + infra under Remote, NOT the public api: the
        // exported server stub (and hence this backend it carries) implements only
        // the wire interface; the disjoint public api is advertised via the
        // downloaded smart proxy (JGDMS-STD-009 §4, "Reggie Registrar vs
        // ServiceRegistrar").  (A do-nothing proxy with protocol == api gets no
        // backend -- see dynamicProtocolIsApiGeneratesNothing.)
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
        assertTrue(backend, backend.contains("interface HelloServiceBackend"));
        // The extends clause aggregates Remote, the wire PROTOCOL, and every infra
        // interface -- and NOT the public api.  Isolate the extends clause so the
        // backend's own type name ("HelloServiceBackend") does not confound the
        // api-absence check.
        String ext = backend.substring(backend.indexOf("extends"), backend.indexOf(" {"));
        assertTrue(ext, ext.contains("java.rmi.Remote"));
        assertTrue("backend must aggregate the wire protocol: " + ext,
            ext.contains("hello.HelloProtocol"));
        assertFalse("backend must aggregate the wire protocol, NOT the public api: " + ext,
            ext.contains("hello.HelloService"));
        assertTrue(ext, ext.contains("net.jini.lookup.ServiceProxyAccessor"));
        assertTrue(ext, ext.contains("net.jini.lookup.ServiceAttributesAccessor"));
        assertTrue(ext, ext.contains("net.jini.lookup.ServiceIDAccessor"));
        assertTrue(ext, ext.contains("net.jini.export.CodebaseAccessor"));
        assertTrue(ext, ext.contains("net.jini.admin.Administrable"));
        assertTrue(ext, ext.contains("net.jini.admin.JoinAdmin"));
        assertTrue(ext, ext.contains("org.apache.river.admin.DestroyAdmin"));
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
        //
        // Here the wire protocol carries the SAME method signatures as the api (a
        // renamed/parallel wire interface -- the direct-forwarding shape the current
        // generator supports), so the generated proxy must also COMPILE.  This is the
        // anti-CCE/anti-compile-error regression: create()/ctor and setConstraints
        // must both be keyed off the protocol.  Before the fix, create()/ctor took
        // the api while setConstraints cast `(protocol) server` -- for a disjoint api
        // != protocol that does not compile (and at runtime would ClassCastException,
        // since the exported stub is a protocol instance, never an api instance).
        ProcessorHarness h = new ProcessorHarness()
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
                + "   public String greet(String name) throws RemoteException { return name; } }");
        ProcessorHarness.Result r = h.run();
        assertFalse(r.allMessages(), r.hasAnyError());
        assertTrue("SMART + protocol!=api generates the backend: " + r.generated.keySet(),
            r.generated.containsKey("hello.HelloServiceBackend"));
        String proxy = r.generated.get("hello.ConstrainableHelloServiceProxy");
        assertTrue("SMART generates the constrainable proxy class: " + r.generated.keySet(),
            proxy != null);
        // create() and the (server, uuid, constraints) ctor are keyed off the wire
        // PROTOCOL (matching the exported stub and the @SmartProxy delegate ctor),
        // not the api.
        assertTrue("create() must take the wire protocol:\n" + proxy,
            proxy.contains("create(hello.HelloProtocol server, net.jini.id.Uuid proxyID)"));
        assertTrue("ctor must take the wire protocol:\n" + proxy,
            proxy.contains("public ConstrainableHelloServiceProxy(hello.HelloProtocol server,"));
        // The proxy still IMPLEMENTS the public api and forwards through the protocol.
        assertTrue("proxy implements the public api:\n" + proxy,
            proxy.contains("implements hello.HelloService"));
        assertTrue("forwards through the wire protocol:\n" + proxy,
            proxy.contains("((hello.HelloProtocol) server).greet(name)"));
        // And the whole translating chain must now COMPILE (the fix's payoff).
        ProcessorHarness.Result compiled = h.compileGenerated(r);
        assertTrue("translating proxy (protocol-keyed create/ctor/setConstraints) must"
                + " compile:\n" + compiled.allMessages() + "\n--- proxy ---\n" + proxy,
            compiled.success);
    }

    @Test
    public void smartDisjointTranslationKeysProxyAndBackendOffProtocol() {
        // Shape 3 translating case, DISJOINT api != protocol (the canonical Reggie
        // "Registrar vs ServiceRegistrar" model, JGDMS-STD-009 §4): the public api
        // (HelloService.greet) and the wire protocol (HelloProtocol.greetInternal)
        // share NO method, and the impl implements ONLY the wire interface.  The
        // generator must key the generated artifacts off the protocol:
        //   - the backend aggregates the wire PROTOCOL + infra (never the api), and
        //   - the proxy's create()/ctor take the PROTOCOL (matching the exported stub
        //     and the @SmartProxy delegate ctor), while still IMPLEMENTING the api.
        //
        // This is a STRUCTURE (golden-source) test, not a compile test: with a truly
        // disjoint method set the generated direct-forwarding body
        // `((HelloProtocol) server).greet(name)` cannot compile -- HelloProtocol has
        // no greet(String).  Bridging a disjoint method translation is the job of the
        // developer @SmartProxy delegate whose shell generation is the deferred P3
        // work (no delegate exists in the tree yet); a compile-and-load round trip is
        // therefore a P3 follow-up.  See smartWithDistinctProtocolGeneratesBackendAndProxy
        // for the name-compatible translating shape that DOES compile today.
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
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = HelloService.class, proxy = ProxyType.SMART,"
                + "     protocol = HelloProtocol.class)"
                // The impl implements only the WIRE interface (Registrar-style); the
                // disjoint public api is advertised via the downloaded smart proxy.
                + " public class HelloServiceImpl implements HelloProtocol {"
                + "   public String greetInternal(String name) throws RemoteException { return name; } }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());

        // Backend aggregates the wire protocol + infra, NOT the public api.
        String backend = r.generated.get("hello.HelloServiceBackend");
        assertTrue("backend must be generated; got " + r.generated.keySet(), backend != null);
        String ext = backend.substring(backend.indexOf("extends"), backend.indexOf(" {"));
        assertTrue("backend aggregates the wire protocol: " + ext,
            ext.contains("hello.HelloProtocol"));
        assertFalse("backend must NOT aggregate the disjoint public api: " + ext,
            ext.contains("hello.HelloService"));

        // Proxy: create()/ctor keyed off the protocol; implements the public api.
        String proxy = r.generated.get("hello.ConstrainableHelloServiceProxy");
        assertTrue("proxy must be generated; got " + r.generated.keySet(), proxy != null);
        assertTrue("create() takes the wire protocol:\n" + proxy,
            proxy.contains("create(hello.HelloProtocol server, net.jini.id.Uuid proxyID)"));
        assertTrue("ctor takes the wire protocol:\n" + proxy,
            proxy.contains("public ConstrainableHelloServiceProxy(hello.HelloProtocol server,"));
        assertTrue("proxy implements the (disjoint) public api:\n" + proxy,
            proxy.contains("implements hello.HelloService"));
        assertTrue("setConstraints re-wraps via the protocol cast:\n" + proxy,
            proxy.contains("(hello.HelloProtocol) server"));
        // The forwarding body casts the server to the wire protocol (the translating
        // chain is consistent on protocol); bridging the disjoint method name is the
        // P3 delegate's job, so this source is asserted, not compiled.
        assertTrue("forwards through the wire protocol:\n" + proxy,
            proxy.contains("((hello.HelloProtocol) server).greet(name)"));
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
    public void dynamicWithCodebaseGeneratesNothingShape2() {
        // Shape 2 (JGDMS-STD-009 §6 shape 2 + §6.5 / Unit-3 scope item 1): a
        // DYNAMIC service with codebase = true still generates NOTHING -- no proxy
        // class and no backend interface.  The downloadable artifact is the
        // service's own interfaces-only *-api.jar served as the codebase (fetched
        // by the receiver via net.jini.export.DynamicProxyCodebaseAccessor when it
        // lacks an interface it must resolve), NOT a generated proxy.  The codebase
        // flag gates PACKAGING (whether the api.jar is served for download), never
        // codegen; only shape 3 (SMART) emits a proxy class.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("hello.HelloServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = HelloService.class, proxy = ProxyType.DYNAMIC,"
                + "     codebase = true)"
                + " public class HelloServiceImpl implements HelloService {"
                + "   public String greet(String name) throws RemoteException { return name; } }")
            .run();
        assertFalse("DYNAMIC + codebase=true (shape 2) must not error:\n" + r.allMessages(),
            r.hasAnyError());
        assertFalse("DYNAMIC + codebase=true (shape 2) must not warn:\n" + r.allMessages(),
            r.hasWarning("codebase"));
        assertTrue("shape 2 downloads the interfaces-only api.jar, NOT a generated"
                + " proxy -- the processor generates NOTHING: " + r.generated.keySet(),
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
        // A multi-element wire set has no single interface able to name it, so the
        // framework aggregates it: the COMBINING backend <PrimaryApi>Backend is
        // generated (extends Remote + every api + infra) and the proxy's create()/ctor
        // take that aggregate type.
        String backend = gen.generated.get("multi.FooBackend");
        assertTrue("combining backend must be generated for a multi-api SMART service; got "
                + gen.generated.keySet(), backend != null);
        String bext = backend.substring(backend.indexOf("extends"), backend.indexOf(" {"));
        assertTrue("backend aggregates BOTH api interfaces (thin: api IS the wire): " + bext,
            bext.contains("multi.Foo") && bext.contains("multi.Bar"));
        String proxy = gen.generated.get("multi.ConstrainableFooProxy");
        assertTrue("proxy is named after the PRIMARY api; got " + gen.generated.keySet(),
            proxy != null);
        assertTrue("implements BOTH api interfaces:\n" + proxy,
            proxy.contains("implements multi.Foo, multi.Bar"));
        assertTrue("create() takes the aggregate backend:\n" + proxy,
            proxy.contains("create(multi.FooBackend server, net.jini.id.Uuid proxyID)"));
        assertTrue("ctor takes the aggregate backend:\n" + proxy,
            proxy.contains("public ConstrainableFooProxy(multi.FooBackend server,"));
        assertTrue("forwards foo() through Foo:\n" + proxy,
            proxy.contains("return ((multi.Foo) server).foo(s);"));
        assertTrue("forwards bar() through Bar:\n" + proxy,
            proxy.contains("return ((multi.Bar) server).bar(n);"));
        ProcessorHarness.Result compiled = h.compileGenerated(gen);
        assertTrue("multi-interface proxy must compile:\n" + compiled.allMessages()
                + "\n" + proxy, compiled.success);
    }

    @Test
    public void smartMultiProtocolTranslatingAggregatesAllProtocols() {
        // A SMART service that TRANSLATES its public api into MULTIPLE distinct wire
        // protocols: the framework aggregates the whole protocol[] under the generated
        // <Api>Backend (extends Remote + every protocol + infra, and NOT the api), and
        // the proxy's create()/ctor take that aggregate backend.  The proxy still
        // IMPLEMENTS the public api and forwards through the aggregate wire type.
        ProcessorHarness h = new ProcessorHarness()
            .add("multi.WireA",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface WireA extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("multi.WireB",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface WireB extends Remote {"
                + "   int ping() throws RemoteException; }")
            .add("multi.HelloService",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + "   int ping() throws RemoteException; }")
            // impl implements only the WIRE interfaces (Registrar-style); the disjoint
            // public api is advertised via the downloaded smart proxy.
            .add("multi.HelloServiceImpl",
                "package multi; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = HelloService.class, proxy = ProxyType.SMART,"
                + "     protocol = { WireA.class, WireB.class })"
                + " public class HelloServiceImpl implements WireA, WireB {"
                + "   public String greet(String name) throws RemoteException { return name; }"
                + "   public int ping() throws RemoteException { return 0; } }");
        ProcessorHarness.Result r = h.run();
        assertFalse(r.allMessages(), r.hasAnyError());
        String backend = r.generated.get("multi.HelloServiceBackend");
        assertTrue("backend must aggregate the multi-protocol wire set; got "
                + r.generated.keySet(), backend != null);
        String ext = backend.substring(backend.indexOf("extends"), backend.indexOf(" {"));
        assertTrue("backend aggregates BOTH wire protocols: " + ext,
            ext.contains("multi.WireA") && ext.contains("multi.WireB"));
        assertFalse("backend must NOT aggregate the disjoint public api: " + ext,
            ext.contains("multi.HelloService"));
        String proxy = r.generated.get("multi.ConstrainableHelloServiceProxy");
        assertTrue("proxy must be generated; got " + r.generated.keySet(), proxy != null);
        assertTrue("create() takes the aggregate backend (no single wire type names the set):\n"
                + proxy, proxy.contains("create(multi.HelloServiceBackend server, net.jini.id.Uuid proxyID)"));
        assertTrue("ctor takes the aggregate backend:\n" + proxy,
            proxy.contains("public ConstrainableHelloServiceProxy(multi.HelloServiceBackend server,"));
        assertTrue("proxy implements the public api:\n" + proxy,
            proxy.contains("implements multi.HelloService"));
        assertTrue("setConstraints re-wraps via the aggregate backend cast:\n" + proxy,
            proxy.contains("(multi.HelloServiceBackend) server"));
        // The whole translating chain compiles: the aggregate backend extends both
        // wire interfaces, so ((HelloServiceBackend) server).greet / .ping resolve.
        ProcessorHarness.Result compiled = h.compileGenerated(r);
        assertTrue("multi-protocol translating proxy must compile:\n"
                + compiled.allMessages() + "\n--- backend ---\n" + backend
                + "\n--- proxy ---\n" + proxy, compiled.success);
    }

    @Test
    public void smartProxyMultiProtocolDelegateAcceptsRemoteInCtor() {
        // The generated aggregate backend (multi.HelloServiceBackend) is a
        // processor-synthesized name the delegate author cannot know in advance --
        // it does not exist until this same compilation generates it.  Declaring
        // the delegate's first ctor parameter as java.rmi.Remote (and casting
        // internally to whichever protocol interface(s) it needs) must validate and
        // compile, without the delegate ever naming the generated backend.
        ProcessorHarness h = new ProcessorHarness()
            .add("multi.WireA",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface WireA extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("multi.WireB",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface WireB extends Remote {"
                + "   int ping() throws RemoteException; }")
            .add("multi.HelloService",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + "   int ping() throws RemoteException; }")
            .add("multi.HelloSmartLogic",
                "package multi; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.SmartProxy"
                + " public final class HelloSmartLogic implements HelloService {"
                + "   private final WireA a;"
                + "   private final WireB b;"
                + "   public HelloSmartLogic(java.rmi.Remote server) {"
                + "     this.a = (WireA) server;"
                + "     this.b = (WireB) server; }"
                + "   public String greet(String name) throws RemoteException { return a.greet(name); }"
                + "   public int ping() throws RemoteException { return b.ping(); } }")
            .add("multi.HelloServiceImpl",
                "package multi; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = HelloService.class, proxy = ProxyType.SMART,"
                + "     protocol = { WireA.class, WireB.class }, smartProxy = HelloSmartLogic.class)"
                + " public class HelloServiceImpl implements WireA, WireB {"
                + "   public String greet(String name) throws RemoteException { return name; }"
                + "   public int ping() throws RemoteException { return 0; } }");
        ProcessorHarness.Result r = h.run();
        assertFalse(r.allMessages(), r.hasAnyError());
        String proxy = r.generated.get("multi.ConstrainableHelloServiceProxy");
        assertTrue("proxy must be generated; got " + r.generated.keySet(), proxy != null);
        assertTrue("delegate rebuilt from the aggregate backend, cast for the delegate's Remote ctor:\n"
                + proxy, proxy.contains("new multi.HelloSmartLogic((multi.HelloServiceBackend) this.server)"));
        ProcessorHarness.Result compiled = h.compileGenerated(r);
        assertTrue("Remote-ctor delegate shell must compile:\n" + compiled.allMessages()
                + "\n" + proxy, compiled.success);
    }

    @Test
    public void delegateCtorMismatchInMultiProtocolRecommendsRemoteNotGeneratedBackend() {
        // The diagnostic must recommend the ONE signature a delegate author can
        // actually write (java.rmi.Remote) rather than the processor-generated
        // backend name (multi.HelloServiceBackend), which does not exist until this
        // compilation generates it and so cannot be known in advance.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("multi.WireA",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface WireA extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .add("multi.WireB",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface WireB extends Remote {"
                + "   int ping() throws RemoteException; }")
            .add("multi.HelloService",
                "package multi; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + "   int ping() throws RemoteException; }")
            .add("multi.HelloSmartLogic",
                "package multi; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.SmartProxy"
                + " public final class HelloSmartLogic implements HelloService {"
                + "   public HelloSmartLogic() {}"   // no server ctor at all
                + "   public String greet(String name) throws RemoteException { return name; }"
                + "   public int ping() throws RemoteException { return 0; } }")
            .add("multi.HelloServiceImpl",
                "package multi; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = HelloService.class, proxy = ProxyType.SMART,"
                + "     protocol = { WireA.class, WireB.class }, smartProxy = HelloSmartLogic.class)"
                + " public class HelloServiceImpl implements WireA, WireB {"
                + "   public String greet(String name) throws RemoteException { return name; }"
                + "   public int ping() throws RemoteException { return 0; } }")
            .run();
        assertTrue(r.allMessages(),
            r.hasError("must declare a constructor (java.rmi.Remote)"));
        assertFalse("must not ask the delegate author to name the generated backend:\n"
                + r.allMessages(),
            r.allMessages().contains("must declare a constructor (multi.HelloServiceBackend)"));
    }

    @Test
    public void smartProxyDelegateMissingOneOfMultipleApisIsError() {
        // The delegate must implement EVERY api() interface named by @JiniService.
        // Implementing only one of two -> fail-closed.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.Foo",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   String foo(String s) throws RemoteException; }")
            .add("hello.Bar",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   int bar(int n) throws RemoteException; }")
            .add("hello.Wire",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Wire extends Remote {"
                + "   void wire() throws RemoteException; }")
            .add("hello.HelloSmartLogic",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.SmartProxy"
                + " public final class HelloSmartLogic implements Foo {"  // missing Bar
                + "   public HelloSmartLogic(Wire server) {}"
                + "   public String foo(String s) throws RemoteException { return s; } }")
            .add("hello.MultiImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = { Foo.class, Bar.class }, protocol = Wire.class,"
                + "     proxy = ProxyType.SMART, smartProxy = HelloSmartLogic.class)"
                + " public class MultiImpl implements Wire {"
                + "   public void wire() throws RemoteException {} }")
            .run();
        assertTrue(r.allMessages(),
            r.hasError("must implement the api interface hello.Bar"));
    }

    @Test
    public void smartProxyDelegateImplementingAllApisIsClean() {
        // The dual of the above: a delegate implementing BOTH declared api interfaces
        // (and declaring the (Wire) ctor) validates cleanly and generates the shell.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.Foo",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   String foo(String s) throws RemoteException; }")
            .add("hello.Bar",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   int bar(int n) throws RemoteException; }")
            .add("hello.Wire",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Wire extends Remote {"
                + "   void wire() throws RemoteException; }")
            .add("hello.HelloSmartLogic",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.SmartProxy"
                + " public final class HelloSmartLogic implements Foo, Bar {"
                + "   public HelloSmartLogic(Wire server) {}"
                + "   public String foo(String s) throws RemoteException { return s; }"
                + "   public int bar(int n) throws RemoteException { return n; } }")
            .add("hello.MultiImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = { Foo.class, Bar.class }, protocol = Wire.class,"
                + "     proxy = ProxyType.SMART, smartProxy = HelloSmartLogic.class)"
                + " public class MultiImpl implements Wire {"
                + "   public void wire() throws RemoteException {} }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
    }

    @Test
    public void smartMultiInterfaceEmptyApiIsError() {
        // Ratified: api() inference is DYNAMIC-only.  A SMART service with an empty
        // api() -- even when multiple service interfaces could be inferred -- must
        // fail closed (a translating impl implements the wire interface, not the
        // public api).  The multi-interface SMART GENERATION path (with explicit
        // api()) is covered by smartMultiInterfaceGeneratesProxyForAll.
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
                + " @JiniService(proxy = ProxyType.SMART)"  // no api() -> error for SMART
                + " public class MultiImpl implements Foo, Bar, net.jini.admin.Administrable {"
                + "   public String foo(String s) throws RemoteException { return s; }"
                + "   public int bar(int n) throws RemoteException { return n; }"
                + "   public Object getAdmin() { return null; } }");
        ProcessorHarness.Result gen = h.run();
        assertTrue(gen.allMessages(), gen.hasError("SMART proxy with an empty api()"));
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

    // ------------------------------------------------- @SmartProxy delegate shell (P3)

    @Test
    public void delegateForwardingShellForwardsToDelegateNotServer() {
        // P3.1: a genuinely DISJOINT delegate -- public api Foo.currentCelsius(region)
        // over wire protocol Bar.rawCelsius(region).  The generated shell must forward
        // the api method to the DELEGATE (which implements Foo), not cast the wire
        // server to Foo (Bar has no currentCelsius -- that would not compile).  The
        // delegate is transient behaviour, rebuilt from the deserialized server in
        // BOTH constructors.
        ProcessorHarness h = new ProcessorHarness()
            .add("hello.Foo",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   double currentCelsius(String region) throws RemoteException; }")
            .add("hello.Bar",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   double rawCelsius(String region) throws RemoteException; }")
            .add("hello.FooLogic",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.SmartProxy"
                + " public final class FooLogic implements Foo {"
                + "   private final Bar server;"
                + "   public FooLogic(Bar server) { this.server = server; }"
                + "   public double currentCelsius(String region) throws RemoteException {"
                + "     return server.rawCelsius(region); } }")
            .add("hello.FooServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = Foo.class, protocol = Bar.class, proxy = ProxyType.SMART,"
                + "     smartProxy = FooLogic.class)"
                + " public class FooServiceImpl implements Bar {"
                + "   public double rawCelsius(String region) throws RemoteException { return 0; } }");
        ProcessorHarness.Result gen = h.run();
        assertFalse(gen.allMessages(), gen.hasAnyError());
        String proxy = gen.generated.get("hello.ConstrainableFooProxy");
        assertTrue("shell must be generated; got " + gen.generated.keySet(), proxy != null);
        // Exactly ONE shell (no direct-forwarding proxy in addition to the delegate one).
        long shells = gen.generated.keySet().stream()
                .filter(k -> k.startsWith("hello.Constrainable") && k.endsWith("Proxy")).count();
        assertEquals("exactly one Constrainable*Proxy shell:\n" + gen.generated.keySet(),
                1, shells);
        assertTrue("transient final delegate field:\n" + proxy,
            proxy.contains("private transient final hello.FooLogic delegate;"));
        // BLOCKER A: the delegate is built from the POST-super `this.server` (the
        // constraint-transformed field), NOT the bare ctor parameter -- otherwise a
        // later setConstraints would leave the delegate forwarding under the old stub.
        assertTrue("delegate built from this.server (post-super constrained field):\n" + proxy,
            proxy.contains("this.delegate = new hello.FooLogic((hello.Bar) this.server);"));
        assertFalse("delegate must NOT be built from the bare ctor parameter:\n" + proxy,
            proxy.contains("new hello.FooLogic((hello.Bar) server)"));
        assertTrue("forwards currentCelsius to the DELEGATE:\n" + proxy,
            proxy.contains("return delegate.currentCelsius(region);"));
        assertFalse("must NOT cast the wire server to the api (would not compile):\n" + proxy,
            proxy.contains("((hello.Foo) server).currentCelsius"));
        // The whole disjoint chain compiles ONLY because the shell forwards through
        // the delegate -- this is the payoff the direct generator could not express.
        ProcessorHarness.Result compiled = h.compileGenerated(gen);
        assertTrue("delegate-forwarding shell must compile:\n" + compiled.allMessages()
                + "\n--- proxy ---\n" + proxy, compiled.success);
    }

    @Test
    public void noDelegateStillEmitsByteIdenticalDirectShell() {
        // Regression guard for decision #1: a SMART service with NO @SmartProxy
        // delegate takes the byte-identical direct-forwarding path (no delegate field,
        // casts the server directly), exactly as before P3.
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
        assertTrue(proxy != null);
        assertFalse("no-delegate shell must NOT carry a delegate field:\n" + proxy,
            proxy.contains("delegate"));
        assertTrue("no-delegate shell stays @Stateless:\n" + proxy,
            proxy.contains("@org.apache.river.api.io.AtomicSerial.Stateless"));
        assertTrue("no-delegate shell forwards through the server cast:\n" + proxy,
            proxy.contains("((hello.HelloService) server).sayHello(name)"));
    }

    @Test
    public void smartProxyInfersSmartWhenProxyOmitted() {
        // Ratified: smartProxy() implies proxy=SMART, so proxy= need not be declared.
        // A @JiniService(api, protocol, smartProxy) with NO proxy= generates the
        // delegate-forwarding shell.
        ProcessorHarness h = new ProcessorHarness()
            .add("hello.Foo",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   double currentCelsius(String region) throws RemoteException; }")
            .add("hello.Bar",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   double rawCelsius(String region) throws RemoteException; }")
            .add("hello.FooLogic",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.SmartProxy"
                + " public final class FooLogic implements Foo {"
                + "   public FooLogic(Bar server) {}"
                + "   public double currentCelsius(String region) throws RemoteException { return 0; } }")
            .add("hello.FooServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " @JiniService(api = Foo.class, protocol = Bar.class,"  // NO proxy=
                + "     smartProxy = FooLogic.class)"
                + " public class FooServiceImpl implements Bar {"
                + "   public double rawCelsius(String region) throws RemoteException { return 0; } }");
        ProcessorHarness.Result gen = h.run();
        assertFalse(gen.allMessages(), gen.hasAnyError());
        assertTrue("smartProxy() alone must infer SMART and generate the shell: "
                + gen.generated.keySet(),
            gen.generated.containsKey("hello.ConstrainableFooProxy"));
    }

    @Test
    public void smartProxyWithProxyDynamicIsError() {
        // smartProxy() implies SMART; an explicit proxy=DYNAMIC contradicts it.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.Foo",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   double currentCelsius(String region) throws RemoteException; }")
            .add("hello.Bar",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   double rawCelsius(String region) throws RemoteException; }")
            .add("hello.FooLogic",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.SmartProxy"
                + " public final class FooLogic implements Foo {"
                + "   public FooLogic(Bar server) {}"
                + "   public double currentCelsius(String region) throws RemoteException { return 0; } }")
            .add("hello.FooServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = Foo.class, protocol = Bar.class, proxy = ProxyType.DYNAMIC,"
                + "     smartProxy = FooLogic.class)"
                + " public class FooServiceImpl implements Bar {"
                + "   public double rawCelsius(String region) throws RemoteException { return 0; } }")
            .run();
        // This fixture's protocol (Bar) also differs from api (Foo), so BOTH
        // smartProxy() and the translating protocol() independently imply SMART;
        // the combined diagnostic names both causes.
        assertTrue(r.allMessages(),
            r.hasError("smartProxy() and a translating protocol() implies a SMART proxy"));
    }

    @Test
    public void translatingProtocolWithProxyDynamicIsError() {
        // protocol() differs from api() (translating) but NO smartProxy(). DYNAMIC
        // has no codegen to bridge api()!=protocol() -- its exported
        // java.lang.reflect.Proxy only ever carries the interfaces the impl
        // actually implements -- so this must fail closed exactly like the
        // smartProxy()-vs-DYNAMIC contradiction above.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.Foo",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   double currentCelsius(String region) throws RemoteException; }")
            .add("hello.Bar",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   double currentCelsius(String region) throws RemoteException; }")
            .add("hello.FooServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = Foo.class, protocol = Bar.class, proxy = ProxyType.DYNAMIC)"
                + " public class FooServiceImpl implements Bar {"
                + "   public double currentCelsius(String region) throws RemoteException { return 0; } }")
            .run();
        assertTrue(r.allMessages(),
            r.hasError("a translating protocol() implies a SMART proxy"));
    }

    @Test
    public void translatingProtocolWithNoExplicitProxyInfersSmartAndGenerates() {
        // No proxy= at all -- a translating protocol() alone must infer SMART and
        // generate the direct-forwarding constrainable proxy, exactly as if
        // proxy=ProxyType.SMART had been written explicitly.  (Foo/Bar share the
        // same method name so direct forwarding -- no @SmartProxy delegate --
        // compiles.)
        ProcessorHarness h = new ProcessorHarness()
            .add("hello.Foo",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   double currentCelsius(String region) throws RemoteException; }")
            .add("hello.Bar",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   double currentCelsius(String region) throws RemoteException; }")
            .add("hello.FooServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " @JiniService(api = Foo.class, protocol = Bar.class)"
                + " public class FooServiceImpl implements Bar {"
                + "   public double currentCelsius(String region) throws RemoteException { return 0; } }");
        ProcessorHarness.Result r = h.run();
        assertFalse(r.allMessages(), r.hasAnyError());
        String proxy = r.generated.get("hello.ConstrainableFooProxy");
        assertTrue("proxy must be generated (protocol()!=api() alone must infer SMART); got "
                + r.generated.keySet(), proxy != null);
        assertTrue("proxy forwards through the wire type Bar:\n" + proxy,
            proxy.contains("((hello.Bar) server).currentCelsius(region)"));
        ProcessorHarness.Result compiled = h.compileGenerated(r);
        assertTrue("inferred-SMART translating proxy must compile:\n" + compiled.allMessages()
                + "\n" + proxy, compiled.success);
    }

    @Test
    public void smartProxyReferencingNonMarkerIsError() {
        // The smartProxy() class must carry the @SmartProxy marker.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.Foo",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   double currentCelsius(String region) throws RemoteException; }")
            .add("hello.Bar",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   double rawCelsius(String region) throws RemoteException; }")
            .add("hello.FooLogic",
                "package hello; import java.rmi.RemoteException;"
                + " public final class FooLogic implements Foo {"   // NOT @SmartProxy
                + "   public FooLogic(Bar server) {}"
                + "   public double currentCelsius(String region) throws RemoteException { return 0; } }")
            .add("hello.FooServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = Foo.class, protocol = Bar.class, proxy = ProxyType.SMART,"
                + "     smartProxy = FooLogic.class)"
                + " public class FooServiceImpl implements Bar {"
                + "   public double rawCelsius(String region) throws RemoteException { return 0; } }")
            .run();
        assertTrue(r.allMessages(),
            r.hasError("must be annotated @au.net.zeus.jgdms.service.annotation.SmartProxy"));
    }

    @Test
    public void smartWithEmptyApiIsError() {
        // Ratified: api() inference is DYNAMIC-only.  A SMART service with an empty
        // api() (here via smartProxy() implying SMART) must fail closed -- a
        // translating impl implements the wire interface, not the public api.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.Bar",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   double rawCelsius(String region) throws RemoteException; }")
            .add("hello.FooLogic",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.SmartProxy"
                + " public final class FooLogic {"
                + "   public FooLogic(Bar server) {} }")
            .add("hello.FooServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " @JiniService(protocol = Bar.class, smartProxy = FooLogic.class)"  // no api()
                + " public class FooServiceImpl implements Bar {"
                + "   public double rawCelsius(String region) throws RemoteException { return 0; } }")
            .run();
        assertTrue(r.allMessages(),
            r.hasError("SMART proxy with an empty api()"));
    }

    @Test
    public void delegateMissingServerCtorIsError() {
        // Fail-closed: the shell reconstructs the delegate via new Delegate((wire) server);
        // a delegate lacking that ctor cannot be built.
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.Foo",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   double currentCelsius(String region) throws RemoteException; }")
            .add("hello.Bar",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   double rawCelsius(String region) throws RemoteException; }")
            .add("hello.FooLogic",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.SmartProxy"
                + " public final class FooLogic implements Foo {"
                + "   public FooLogic() {}"   // no (Bar) ctor
                + "   public double currentCelsius(String region) throws RemoteException { return 0; } }")
            .add("hello.FooServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = Foo.class, protocol = Bar.class, proxy = ProxyType.SMART,"
                + "     smartProxy = FooLogic.class)"
                + " public class FooServiceImpl implements Bar {"
                + "   public double rawCelsius(String region) throws RemoteException { return 0; } }")
            .run();
        assertTrue(r.allMessages(),
            r.hasError("must declare a constructor (hello.Bar)"));
    }

    @Test
    public void statefulDelegateShellSerializesStateAndPassesToDelegate() {
        // P3.2: a @State declaration makes the shell STATEFUL -- non-@Stateless, with
        // its OWN serial form for the durable field, serialize() writing it, and a
        // (GetArg) ctor reading it from its own frame and passing it to the delegate
        // ctor AFTER server.  (The {server, proxyID} state stays in the @Stateless
        // base's frame -- the frame-scoped GetArg namespace trap.)
        ProcessorHarness h = new ProcessorHarness()
            .add("hello.Foo",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   double currentCelsius(String region) throws RemoteException; }")
            .add("hello.Bar",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   double rawCelsius(String region) throws RemoteException; }")
            .add("hello.FooLogic",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.SmartProxy;"
                + " @SmartProxy"
                + " @SmartProxy.State(name = \"unit\", type = String.class)"
                + " public final class FooLogic implements Foo {"
                + "   private final Bar server; private final String unit;"
                + "   public FooLogic(Bar server, String unit) { this.server = server; this.unit = unit; }"
                + "   public double currentCelsius(String region) throws RemoteException {"
                + "     return server.rawCelsius(region); } }")
            .add("hello.FooServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = Foo.class, protocol = Bar.class, proxy = ProxyType.SMART,"
                + "     smartProxy = FooLogic.class)"
                + " public class FooServiceImpl implements Bar {"
                + "   public double rawCelsius(String region) throws RemoteException { return 0; } }");
        ProcessorHarness.Result gen = h.run();
        assertFalse(gen.allMessages(), gen.hasAnyError());
        String proxy = gen.generated.get("hello.ConstrainableFooProxy");
        assertTrue("shell must be generated; got " + gen.generated.keySet(), proxy != null);
        assertTrue("stateful shell is @AtomicSerial:\n" + proxy,
            proxy.contains("@org.apache.river.api.io.AtomicSerial\n"));
        assertFalse("stateful shell must NOT be @Stateless:\n" + proxy,
            proxy.contains("@org.apache.river.api.io.AtomicSerial.Stateless"));
        assertTrue("durable state field declared:\n" + proxy,
            proxy.contains("private final java.lang.String unit;"));
        assertTrue("serial form declares the durable field:\n" + proxy,
            proxy.contains("new org.apache.river.api.io.AtomicSerial.SerialForm(\"unit\", java.lang.String.class)"));
        assertTrue("serialize writes the durable field:\n" + proxy,
            proxy.contains("arg.put(\"unit\", obj.unit);"));
        assertTrue("(GetArg) reads the durable field from its own frame:\n" + proxy,
            proxy.contains("this.unit = arg.get(\"unit\", null, java.lang.String.class);"));
        assertTrue("state passed to the delegate ctor AFTER this.server:\n" + proxy,
            proxy.contains("this.delegate = new hello.FooLogic((hello.Bar) this.server, unit);"));
        // And the whole stateful shell must compile against the serial stubs.
        ProcessorHarness.Result compiled = h.compileGenerated(gen);
        assertTrue("stateful delegate shell must compile:\n" + compiled.allMessages()
                + "\n--- proxy ---\n" + proxy, compiled.success);
    }

    @Test
    public void primitiveStateShellUsesPrimitiveGetIdiom() {
        // BLOCKER B: a primitive @State (int) must serialize/deserialize via the
        // primitive get/put overloads.  The reference-type path
        // get("port", null, int.class) throws every deserialize (int.class.isInstance
        // of a boxed Integer is always false); the fix emits get("port", 0).
        ProcessorHarness h = new ProcessorHarness()
            .add("hello.Foo",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Foo extends Remote {"
                + "   double currentCelsius(String region) throws RemoteException; }")
            .add("hello.Bar",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " public interface Bar extends Remote {"
                + "   double rawCelsius(String region) throws RemoteException; }")
            .add("hello.FooLogic",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.SmartProxy;"
                + " @SmartProxy"
                + " @SmartProxy.State(name = \"port\", type = int.class)"
                + " public final class FooLogic implements Foo {"
                + "   private final Bar server; private final int port;"
                + "   public FooLogic(Bar server, int port) { this.server = server; this.port = port; }"
                + "   public double currentCelsius(String region) throws RemoteException {"
                + "     return server.rawCelsius(region); } }")
            .add("hello.FooServiceImpl",
                "package hello; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.ProxyType;"
                + " @JiniService(api = Foo.class, protocol = Bar.class, proxy = ProxyType.SMART,"
                + "     smartProxy = FooLogic.class)"
                + " public class FooServiceImpl implements Bar {"
                + "   public double rawCelsius(String region) throws RemoteException { return 0; } }");
        ProcessorHarness.Result gen = h.run();
        assertFalse(gen.allMessages(), gen.hasAnyError());
        String proxy = gen.generated.get("hello.ConstrainableFooProxy");
        assertTrue("shell must be generated; got " + gen.generated.keySet(), proxy != null);
        assertTrue("primitive state field is primitive-typed:\n" + proxy,
            proxy.contains("private final int port;"));
        assertTrue("serial form declares the primitive field:\n" + proxy,
            proxy.contains("new org.apache.river.api.io.AtomicSerial.SerialForm(\"port\", int.class)"));
        assertTrue("serialize writes the primitive field (overloaded put):\n" + proxy,
            proxy.contains("arg.put(\"port\", obj.port);"));
        assertTrue("(GetArg) reads via the PRIMITIVE get overload, not the 3-arg typed get:\n" + proxy,
            proxy.contains("this.port = arg.get(\"port\", 0);"));
        assertFalse("must NOT use the reference-type 3-arg get for a primitive:\n" + proxy,
            proxy.contains("arg.get(\"port\", null, int.class)"));
        assertTrue("primitive state passed to the delegate ctor:\n" + proxy,
            proxy.contains("this.delegate = new hello.FooLogic((hello.Bar) this.server, port);"));
        ProcessorHarness.Result compiled = h.compileGenerated(gen);
        assertTrue("primitive @State shell must compile:\n" + compiled.allMessages()
                + "\n--- proxy ---\n" + proxy, compiled.success);
    }
}
