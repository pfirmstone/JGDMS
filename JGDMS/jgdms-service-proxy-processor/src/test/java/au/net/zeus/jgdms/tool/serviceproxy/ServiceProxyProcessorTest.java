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
 * backend-interface generation (P1).  Each test drives the processor over
 * in-memory sources via {@link ProcessorHarness}.
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
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name);"   // missing throws RemoteException
                + " }")
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
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + " }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
    }

    @Test
    public void jiniServiceOnClassIsError() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public class HelloService {}")
            .run();
        assertTrue(r.allMessages(),
            r.hasError("must annotate the public API interface"));
    }

    @Test
    public void apiNotExtendingRemoteIsError() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService {"
                + "   String greet(String name) throws RemoteException;"
                + " }")
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
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + " }")
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
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + " }")
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
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + " }")
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
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException;"
                + " }")
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

    // ------------------------------------------------------------------ generation

    @Test
    public void validateOnlyGeneratesNothing() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .option("-Aserviceproxy.validateOnly")
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .run();
        assertTrue("validate-only must generate nothing: " + r.generated.keySet(),
            r.generated.isEmpty());
    }

    @Test
    public void generatesBackendInterface() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
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
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
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
        ProcessorHarness h = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService extends Remote {"
                + "   String sayHello(String name) throws RemoteException; }");
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
        ProcessorHarness h = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface HelloService extends Remote {"
                + "   String sayHello(String name) throws RemoteException; }");
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
        ProcessorHarness h = new ProcessorHarness()
            .add("svc.Svc",
                "package svc; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " import java.util.List;"
                + " @au.net.zeus.jgdms.service.annotation.JiniService"
                + " public interface Svc extends Remote {"
                + "   <T> List<T> pick(T[] items) throws RemoteException;"
                + "   void log(String fmt, Object... args) throws RemoteException;"
                + "   int count() throws RemoteException; }");
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
    public void doesNotGenerateBackendWhenNotRequested() {
        ProcessorHarness.Result r = new ProcessorHarness()
            .add("hello.HelloService",
                "package hello; import java.rmi.Remote; import java.rmi.RemoteException;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService;"
                + " import au.net.zeus.jgdms.service.annotation.JiniService.Generate;"
                + " @JiniService(generate = { Generate.PROXY })"
                + " public interface HelloService extends Remote {"
                + "   String greet(String name) throws RemoteException; }")
            .run();
        assertFalse(r.allMessages(), r.hasAnyError());
        assertFalse("BACKEND not requested",
            r.generated.containsKey("hello.HelloServiceBackend"));
    }
}
