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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.security.auth.kerberos.KerberosPrincipal;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.export.CodebaseAccessor;
import net.jini.io.UnsupportedConstraintException;

/**
 * Establishes who a bootstrap proxy's server is, from the proxy's own
 * authenticated connection, and pins the proxy to that single principal.
 *
 * <p>The identity is never taken from the stream that delivered the proxy: a
 * proxy relayed inside another service's stream arrives with that service's
 * identity.  It is taken by asking the proxy's own invocation handler to
 * authenticate the server ({@code BasicInvocationHandler.authenticateServer}).
 * That handler is asked only when its class is exactly one of the JERI
 * handler classes the provider's own class loader sees, because a handler of
 * any other class could return whatever it liked.
 *
 * <p>The three outcomes of {@link #probe}:
 * <ul>
 * <li>a principal: the server authenticated as exactly one SPIFFE principal
 *     (or, failing that, exactly one Kerberos principal); the proxy must be
 *     {@linkplain #pin pinned} to it before any further call;</li>
 * <li>{@code null}: no identity can be established (the handler is not a
 *     trusted class, the transport cannot authenticate a server, or the server
 *     authenticated only as an X.500 name, which does not pin a workload).
 *     The caller proceeds on the unverified path;</li>
 * <li>an {@link IOException} or {@link SecurityException}: the transport can
 *     authenticate but the attempt failed or the identity is ambiguous.  The
 *     caller must refuse.</li>
 * </ul>
 *
 * @since 4.0
 */
final class ServerIdentityPin {

    /** Resolved reflectively: jgdms-lib-dl depends on the JERI module, not the reverse. */
    static final String SPIFFE_PRINCIPAL_CLASS = "au.net.zeus.jgdms.spiffe.SpiffePrincipal";

    private static final String[] TRUSTED_HANDLERS = {
        "net.jini.jeri.BasicInvocationHandler",
        "net.jini.jeri.AtomicInvocationHandler",
        "net.jini.jeri.AtomicDerInvocationHandler"
    };

    private ServerIdentityPin() {}

    /**
     * Asks the proxy's own connection who the server is.
     *
     * @return the single principal to pin to, or {@code null} for no identity
     * @throws IOException if the server could not be authenticated, or
     *         authenticated as more than one SPIFFE or Kerberos principal
     * @throws SecurityException if the probe was refused for lack of permission
     */
    static Principal probe(Object bootstrapProxy) throws IOException {
        if (bootstrapProxy == null || !Proxy.isProxyClass(bootstrapProxy.getClass())) {
            return null;
        }
        final InvocationHandler handler = Proxy.getInvocationHandler(bootstrapProxy);
        final Class<?> handlerClass = trustedHandlerClass(handler);
        if (handlerClass == null) {
            return null;
        }
        final Method accessorMethod;
        try {
            accessorMethod = CodebaseAccessor.class.getMethod("getClassAnnotation");
        } catch (NoSuchMethodException e) {
            throw new IOException("CodebaseAccessor.getClassAnnotation not found", e);
        }
        Set<?> principals;
        try {
            // Privileged: the identity is used inside the provider and never
            // returned to the caller, so the caller's own grants are not the
            // question; the provider must hold the ContextPermission.
            principals = AccessController.doPrivileged(
                new PrivilegedExceptionAction<Set<?>>() {
                    @Override
                    public Set<?> run() throws Exception {
                        Method m = handlerClass.getMethod("authenticateServer", Method.class);
                        return (Set<?>) m.invoke(handler, accessorMethod);
                    }
                });
        } catch (PrivilegedActionException pae) {
            Throwable t = pae.getException();
            if (t instanceof InvocationTargetException && t.getCause() != null) {
                t = t.getCause();
            }
            if (t instanceof UnsupportedConstraintException) {
                return null; // a transport that cannot authenticate a server
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            if (t instanceof SecurityException) {
                throw (SecurityException) t;
            }
            throw new IOException("could not authenticate the codebase server", t);
        }
        return select(principals);
    }

    /** The handler's class if it is exactly a trusted JERI handler class, else null. */
    private static Class<?> trustedHandlerClass(InvocationHandler handler) {
        Class<?> hc = handler.getClass();
        ClassLoader own = ServerIdentityPin.class.getClassLoader();
        for (String name : TRUSTED_HANDLERS) {
            try {
                // Class identity, not the name: a class of the same name from
                // another loader is not the trusted class.
                if (hc == Class.forName(name, false, own)) {
                    return hc;
                }
            } catch (ClassNotFoundException e) {
                // That handler class is not present in this deployment, so no
                // handler can be an instance of it; keep looking.
                continue;
            }
        }
        return null;
    }

    /**
     * Exactly one SPIFFE principal, else exactly one Kerberos principal, else
     * none.  More than one of the kind that decides is an ambiguous identity
     * and is refused.
     */
    static Principal select(Set<?> principals) throws IOException {
        List<Principal> spiffe = new ArrayList<Principal>();
        List<Principal> kerberos = new ArrayList<Principal>();
        for (Object o : principals) {
            if (!(o instanceof Principal)) {
                continue;
            }
            Principal p = (Principal) o;
            Class<?> c = p.getClass();
            if (c.getName().equals(SPIFFE_PRINCIPAL_CLASS)) {
                spiffe.add(p);
            } else if (c == KerberosPrincipal.class) {
                kerberos.add(p);
            }
        }
        if (spiffe.size() > 1) {
            throw new IOException("the server authenticated as more than one SPIFFE"
                    + " identity; refusing an ambiguous identity: " + spiffe);
        }
        if (spiffe.size() == 1) {
            return spiffe.get(0);
        }
        if (kerberos.size() > 1) {
            throw new IOException("the server authenticated as more than one Kerberos"
                    + " principal; refusing an ambiguous identity: " + kerberos);
        }
        return kerberos.isEmpty() ? null : kerberos.get(0);
    }

    /**
     * Returns the proxy with every {@link CodebaseAccessor} call additionally
     * requiring server authentication as {@code principal}.  The constraints
     * the proxy already carries are kept per method.
     *
     * @throws IOException if the proxy cannot carry constraints
     */
    static CodebaseAccessor pin(CodebaseAccessor proxy, Principal principal)
            throws IOException {
        if (!(proxy instanceof RemoteMethodControl)) {
            throw new IOException("bootstrap proxy must be a RemoteMethodControl to be"
                    + " pinned to the authenticated server");
        }
        InvocationConstraints pinReq = new InvocationConstraints(
                new InvocationConstraint[]{
                    ServerAuthentication.YES, new ServerMinPrincipal(principal)},
                (InvocationConstraint[]) null);
        MethodConstraints existing = ((RemoteMethodControl) proxy).getConstraints();
        Method[] methods = CodebaseAccessor.class.getMethods();
        List<BasicMethodConstraints.MethodDesc> descs =
                new ArrayList<BasicMethodConstraints.MethodDesc>();
        for (Method m : methods) {
            InvocationConstraints current = existing == null
                    ? InvocationConstraints.EMPTY : existing.getConstraints(m);
            descs.add(new BasicMethodConstraints.MethodDesc(
                    m.getName(), InvocationConstraints.combine(current, pinReq)));
        }
        // Anything not named above still requires the pin.
        descs.add(new BasicMethodConstraints.MethodDesc(pinReq));
        MethodConstraints pinned = new BasicMethodConstraints(
                descs.toArray(new BasicMethodConstraints.MethodDesc[0]));
        return (CodebaseAccessor) ((RemoteMethodControl) proxy).setConstraints(pinned);
    }
}
