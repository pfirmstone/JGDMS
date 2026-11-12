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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Invariant: a bootstrap proxy without a codebase annotation needs no
 * downloaded code, so its service proxy is resolved in the parent loader:
 * no class loader is created and nothing is routed to a sub-process, also
 * when isolation is on.  (A null annotation used to throw a
 * NullPointerException.)  The identity rules for such proxies over real SSL
 * are tested in jgdms-identity-it.
 */
public class CodebaseFreeProxyTest {

    private static final ClassLoader LOADER = CodebaseFreeProxyTest.class.getClassLoader();

    private PreferredProxyCodebaseProvider.SmartProxyIsolationRouter previousRouter;
    private String previousIsolation;
    private final List<Object> routed = new ArrayList<Object>();

    @Before
    public void captureRoutes() {
        previousIsolation = System.getProperty(
                PreferredProxyCodebaseProvider.SMART_PROXY_ISOLATION_ENABLED_PROPERTY);
        previousRouter = PreferredProxyCodebaseProvider.getIsolationRouter();
        PreferredProxyCodebaseProvider.setIsolationRouter(
                new PreferredProxyCodebaseProvider.SmartProxyIsolationRouter() {
                    public Object route(Principal[] serverPrincipals, CodebaseAccessor bootstrapProxy,
                            MarshalledInstance serviceProxy, URL[] codebase, String path,
                            ClassLoader parent, ClassLoader verifier, Collection context) {
                        routed.add(path);
                        return "routed";
                    }
                });
    }

    @After
    public void restore() {
        PreferredProxyCodebaseProvider.setIsolationRouter(previousRouter);
        if (previousIsolation == null) {
            System.clearProperty(PreferredProxyCodebaseProvider.SMART_PROXY_ISOLATION_ENABLED_PROPERTY);
        } else {
            System.setProperty(PreferredProxyCodebaseProvider.SMART_PROXY_ISOLATION_ENABLED_PROPERTY,
                    previousIsolation);
        }
    }

    /** A bootstrap proxy with no codebase; its handler is not a platform JERI handler, so no identity. */
    private static CodebaseAccessor codebaseFreeBootstrap() {
        return (CodebaseAccessor) Proxy.newProxyInstance(LOADER,
                new Class<?>[]{CodebaseAccessor.class, RemoteMethodControl.class},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "setConstraints":
                                return proxy;
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            default:
                                return null; // getClassAnnotation: no codebase
                        }
                    }
                });
    }

    private static Object resolve() throws Exception {
        return new PreferredProxyCodebaseProvider().resolve(codebaseFreeBootstrap(),
                new MarshalledInstance("a local service object"), LOADER, LOADER, new ArrayList<Object>());
    }

    @Test
    public void aServiceProxyWithoutACodebaseIsResolvedInTheParentLoader() throws Exception {
        assertEquals("a local service object", resolve());
    }

    @Test
    public void aServiceProxyWithoutACodebaseIsNotRoutedWhenIsolating() throws Exception {
        System.setProperty(PreferredProxyCodebaseProvider.SMART_PROXY_ISOLATION_ENABLED_PROPERTY, "true");
        assertEquals("there is no downloaded code to isolate", "a local service object", resolve());
        assertTrue(routed.isEmpty());
    }
}
