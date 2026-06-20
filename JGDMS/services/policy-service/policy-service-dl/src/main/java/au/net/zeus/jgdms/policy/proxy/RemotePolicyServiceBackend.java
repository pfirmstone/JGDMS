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
package au.net.zeus.jgdms.policy.proxy;

import java.rmi.Remote;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.export.CodebaseAccessor;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import org.apache.river.admin.DestroyAdmin;
import org.apache.river.api.security.RemotePolicyService;

/**
 * Server-side <b>backend</b> remote interface for the policy service — the
 * single interface the exported server stub satisfies.
 *
 * <p>This is the analogue of Reggie's {@code Registrar}: one {@link Remote}
 * interface that aggregates the client-facing service contract
 * ({@link RemotePolicyService}) <em>and</em> every infrastructure capability a
 * JGDMS service stub must expose.  Because it extends {@link Remote}, a JERI
 * exporter generates a stub that — being a proxy for
 * {@code RemotePolicyServiceBackend} — transitively implements all of the
 * aggregated interfaces, including the ones that are not themselves
 * {@code Remote} ({@link Administrable}, {@link JoinAdmin},
 * {@link DestroyAdmin}).  That is what lets
 * {@link au.net.zeus.jgdms.proxy.AbstractSmartProxy} validate the deserialized
 * stub and makes admin operations invocable over the wire.
 *
 * <p>The split mirrors Reggie: {@link RemotePolicyService} stays clean and
 * client-facing (the {@code ServiceRegistrar} role); this backend carries the
 * infrastructure (the {@code Registrar} role) and lives in the downloadable
 * {@code -dl} jar.  {@code ActivatableInMemoryPolicyServiceImpl} implements this
 * interface; the infrastructure members all come from
 * {@link au.net.zeus.jgdms.service.support.AbstractJiniService}.
 *
 * @see RemotePolicyService
 * @see au.net.zeus.jgdms.proxy.AbstractSmartProxy
 * @since 3.1.1
 */
public interface RemotePolicyServiceBackend
        extends Remote,
                RemotePolicyService,
                ServiceProxyAccessor,
                ServiceAttributesAccessor,
                ServiceIDAccessor,
                CodebaseAccessor,
                Administrable,
                JoinAdmin,
                DestroyAdmin {
}
