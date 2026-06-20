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
package au.net.zeus.jgdms.hello.proxy;

import au.net.zeus.jgdms.api.hello.HelloService;
import java.rmi.Remote;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.export.CodebaseAccessor;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import org.apache.river.admin.DestroyAdmin;

/**
 * Server-side <b>backend</b> remote interface for the Hello World service —
 * the single interface the exported server stub satisfies.
 *
 * <p>This is the Hello World analogue of Reggie's {@code Registrar}: it is a
 * single {@link Remote} interface that aggregates the client-facing service
 * contract ({@link HelloService}) <em>and</em> every infrastructure capability
 * a JGDMS service stub must expose.  Because this interface extends
 * {@link Remote}, a JERI exporter ({@code AtomicILFactory}) generates a stub
 * that — being a proxy for {@code HelloServiceBackend} — transitively
 * implements all of the aggregated interfaces, including the ones that are not
 * themselves {@code Remote} ({@link Administrable}, {@link JoinAdmin},
 * {@link DestroyAdmin}).  That is what allows
 * {@link au.net.zeus.jgdms.proxy.AbstractSmartProxy} to validate the
 * deserialized stub by a single cast/interface check, and what makes admin
 * operations genuinely invocable over the wire.
 *
 * <p><b>Why this exists.</b> A bare {@code interface HelloService extends
 * Remote} carries only {@code sayHello}; an impl that separately implements
 * {@code Administrable}/{@code JoinAdmin}/{@code DestroyAdmin} does <em>not</em>
 * expose them on its exported stub, because those interfaces are not reachable
 * through any {@code Remote} interface.  Aggregating them here — exactly as
 * {@code Registrar extends Remote, ServiceProxyAccessor, Administrable,
 * DiscoveryAdmin, JoinAdmin, DestroyAdmin} — is the convention that makes the
 * stub carry them.
 *
 * <p>The split mirrors Reggie:
 * <ul>
 *   <li>{@link HelloService} — clean, client-facing (the {@code
 *       ServiceRegistrar} role), in {@code hello-world-api}.</li>
 *   <li>{@code HelloServiceBackend} — infrastructure-laden, server/proxy
 *       facing (the {@code Registrar} role), in the downloadable
 *       {@code hello-world-dl} jar.</li>
 * </ul>
 *
 * <p>The service implementation ({@code HelloWorldServiceImpl}) implements this
 * interface; all members other than {@link HelloService#sayHello} are supplied
 * by {@link au.net.zeus.jgdms.service.support.AbstractJiniService}.
 *
 * @see HelloService
 * @see au.net.zeus.jgdms.proxy.AbstractSmartProxy
 * @since 3.1.1
 * @author Peter Firmstone
 */
public interface HelloServiceBackend
        extends Remote,
                HelloService,
                ServiceProxyAccessor,
                ServiceAttributesAccessor,
                ServiceIDAccessor,
                CodebaseAccessor,
                Administrable,
                JoinAdmin,
                DestroyAdmin {
}
