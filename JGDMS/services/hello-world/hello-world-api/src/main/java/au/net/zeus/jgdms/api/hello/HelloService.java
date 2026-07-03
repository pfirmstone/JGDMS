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
package au.net.zeus.jgdms.api.hello;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Client-facing remote service interface for the Hello World example service.
 *
 * <p>This interface demonstrates the minimal contract that a JGDMS service
 * must expose to clients.  It extends {@link Remote} so that JERI can
 * generate a remote stub for it, and every method declares
 * {@link RemoteException} to communicate transport failures to callers.
 *
 * <p>It deliberately carries <em>only</em> the service operation.  The
 * infrastructure capabilities a JGDMS service stub must also expose
 * (bootstrap accessors, {@code Administrable}, {@code JoinAdmin},
 * {@code DestroyAdmin}) are appended to the exported dynamic-proxy stub at
 * export time by the framework-default invocation-layer factory
 * {@link net.jini.jeri.DynamicILFactory}, not declared here — so a client sees the
 * clean service contract, exactly as Reggie clients see
 * {@link net.jini.core.lookup.ServiceRegistrar} rather than the internal
 * {@code Registrar} backend.
 *
 * <h2>Generated boilerplate ({@code @JiniService})</h2>
 * The {@code @JiniService} annotation that drives service-proxy generation lives
 * on the service <em>implementation</em>
 * ({@link au.net.zeus.jgdms.hello.HelloWorldServiceImpl}), <em>not</em> on this
 * interface — proxy type, codebase, and config component are deployment concerns
 * of the implementor, so this interface stays a pure {@link Remote} contract.
 * There the annotation reads {@code @JiniService(api = HelloService.class,
 * proxy = ProxyType.DYNAMIC)}, and because the proxy type is
 * {@code DYNAMIC} (and this service's protocol equals its API), the
 * service-proxy annotation processor
 * ({@code au.net.zeus.jgdms.tool.serviceproxy.ServiceProxyProcessor}) emits
 * <em>nothing at all</em>: no backend interface, no proxy class, and no
 * invocation-layer factory.
 * <ul>
 *   <li>The non-{@link Remote} admin interfaces
 *       ({@code Administrable}/{@code JoinAdmin}/{@code DestroyAdmin}) are appended
 *       to the exported stub's interface AND server dispatch sets by the reusable
 *       framework factory {@link net.jini.jeri.DynamicILFactory} — a
 *       {@link net.jini.jeri.AtomicILFactory} subclass whose
 *       {@code getRemoteInterfaces} override appends its extra interfaces, mirroring
 *       {@code net.jini.jeri.ProxyTrustILFactory}'s {@code ProxyTrust} append.  The
 *       JGDMS service support installs it as the default exporter, supplying the
 *       admin interfaces, so a DYNAMIC service gets full admin dispatch with
 *       neither codegen nor config.  The {@link Remote} bootstrap accessors
 *       ({@code ServiceProxyAccessor}/{@code ServiceIDAccessor}/
 *       {@code ServiceAttributesAccessor}/{@code CodebaseAccessor}) are picked
 *       up automatically by {@code super.getRemoteInterfaces}.</li>
 * </ul>
 * There is <em>no</em> generated backend interface and <em>no</em> generated
 * proxy class: the client proxy is the JERI-exported
 * {@link java.lang.reflect.Proxy} dynamic stub itself, returned to clients
 * directly.  Nothing is written by hand.
 *
 * <p>{@code proxy = DYNAMIC} selects JGDMS-STD-009 §6 shape 1 (the exported
 * dynamic-proxy stub <em>is</em> the client proxy — one fat
 * {@code java.lang.reflect.Proxy} implementing the API, the appended admin
 * interfaces, the {@link Remote} accessors, and
 * {@link net.jini.core.constraint.RemoteMethodControl}).  {@code codebase = false}
 * because there is no downloaded smart-proxy jar to ship.
 *
 * <h2>Usage</h2>
 * Clients discover an implementation of this interface via
 * {@link net.jini.lookup.ServiceDiscoveryManager}:
 * <pre>{@code
 * ServiceTemplate template = new ServiceTemplate(
 *         null, new Class[]{ HelloService.class }, null);
 * ServiceItem[] items = sdm.lookup(template, 1, null);
 * HelloService svc = (HelloService) items[0].service;
 * System.out.println(svc.sayHello("World"));
 * }</pre>
 *
 * <h2>Security</h2>
 * When exported over an SSL/TLS endpoint the service requires callers to
 * satisfy the constraints declared in the server-side configuration
 * ({@code hello-world-service.config}):
 * <ul>
 *   <li>{@link net.jini.core.constraint.Integrity#YES} — all messages are
 *       integrity-protected</li>
 *   <li>{@link net.jini.core.constraint.ClientAuthentication#YES} — callers
 *       must present a valid X.509 certificate</li>
 * </ul>
 *
 * @see au.net.zeus.jgdms.hello.HelloWorldServiceImpl
 * @see au.net.zeus.jgdms.service.annotation.JiniService
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public interface HelloService extends Remote {

    /**
     * Returns a greeting message for the given name.
     *
     * @param name the name to greet; must not be {@code null}
     * @return a non-null greeting string, e.g. {@code "Hello, World!"}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws RemoteException      if a communication failure occurs
     */
    String sayHello(String name) throws RemoteException;
}
