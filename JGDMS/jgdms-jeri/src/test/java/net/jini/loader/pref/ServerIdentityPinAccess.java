/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.loader.pref;

import java.io.IOException;
import java.net.URL;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;

/**
 * Test-only door to the package-private {@link ServerIdentityPin}, so that it
 * can be exercised against real SSL endpoints, whose test credentials live in
 * this module.  It is in the provider's package only to reach the class; it
 * is not shipped.
 */
public final class ServerIdentityPinAccess {

    private ServerIdentityPinAccess() {}

    public static Principal probe(Object bootstrapProxy) throws IOException {
        return ServerIdentityPin.probe(bootstrapProxy);
    }

    public static CodebaseAccessor pin(CodebaseAccessor proxy, Principal principal)
            throws IOException {
        return ServerIdentityPin.pin(proxy, principal);
    }

    /** The system property that turns smart-proxy isolation routing on. */
    public static final String ISOLATION_PROPERTY =
            PreferredProxyCodebaseProvider.SMART_PROXY_ISOLATION_ENABLED_PROPERTY;

    /** What one resolve handed to the isolation router. */
    public static final class Route {
        public final Principal[] serverPrincipals;
        public final CodebaseAccessor bootstrapProxy;
        public final Collection<?> context;

        Route(Principal[] serverPrincipals, CodebaseAccessor bootstrapProxy, Collection<?> context) {
            this.serverPrincipals = serverPrincipals;
            this.bootstrapProxy = bootstrapProxy;
            this.context = context;
        }
    }

    /**
     * Installs an isolation router that records each route in {@code sink} and
     * returns {@code result}.  Closing the returned handle restores the router
     * that was installed before.
     */
    public static AutoCloseable captureRoutes(final List<Route> sink, final Object result) {
        final PreferredProxyCodebaseProvider.SmartProxyIsolationRouter previous =
                PreferredProxyCodebaseProvider.getIsolationRouter();
        PreferredProxyCodebaseProvider.setIsolationRouter(
                new PreferredProxyCodebaseProvider.SmartProxyIsolationRouter() {
                    @Override
                    public Object route(Principal[] serverPrincipals, CodebaseAccessor bootstrapProxy,
                            MarshalledInstance serviceProxy, URL[] codebase, String path,
                            ClassLoader parent, ClassLoader verifier, Collection context) {
                        sink.add(new Route(serverPrincipals, bootstrapProxy, context));
                        return result;
                    }
                });
        return new AutoCloseable() {
            @Override
            public void close() {
                PreferredProxyCodebaseProvider.setIsolationRouter(previous);
            }
        };
    }
}
