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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
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
 * <p>The outcomes of {@link #probe}:
 * <ul>
 * <li>a principal: the server authenticated as exactly one SPIFFE principal,
 *     else exactly one Kerberos principal, else exactly one X.500 principal
 *     (a distinguished name alone does not pin a workload, but it is what the
 *     stream's subject carried before, so such servers keep principal-scoped
 *     grants); the proxy must be {@linkplain #pin pinned} to it before any
 *     further call;</li>
 * <li>{@code null}: no identity can be established because the handler is not
 *     a trusted class, or the transport or endpoint cannot authenticate a
 *     server.  The caller proceeds on the unverified path;</li>
 * <li>an {@link IOException} or {@link SecurityException}: the transport can
 *     authenticate but the attempt failed (including a constraint that cannot
 *     be met, such as a client that is not logged on), nothing recognisable
 *     was authenticated, or the identity is ambiguous.  The caller must refuse.</li>
 * </ul>
 *
 * @since 4.0
 */
final class ServerIdentityPin {

    private static final Logger logger = Logger.getLogger(ServerIdentityPin.class.getName());

    /** In jgdms-lib-dl, which this module does not depend on: matched by exact class name. */
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
            logger.log(Level.WARNING,
                    "No server identity for a bootstrap proxy whose invocation handler is {0}:"
                    + " not a platform JERI handler class, so the codebase is treated as unverified",
                    handler.getClass().getName());
            return null;
        }
        final Method accessorMethod;
        try {
            accessorMethod = CodebaseAccessor.class.getMethod("getClassAnnotation");
        } catch (NoSuchMethodException e) {
            throw new IOException("CodebaseAccessor.getClassAnnotation not found", e);
        }
        // The handshake is made as the caller's logged-on subject: doPrivileged
        // would otherwise drop it and the probe would fail or use another identity.
        final Subject caller = currentSubject();
        Set<?> principals;
        try {
            principals = AccessController.doPrivileged(
                new PrivilegedExceptionAction<Set<?>>() {
                    @Override
                    public Set<?> run() throws Exception {
                        return runAs(caller, new Callable<Set<?>>() {
                            @Override
                            public Set<?> call() throws Exception {
                                Method m = handlerClass.getMethod("authenticateServer", Method.class);
                                return (Set<?>) m.invoke(handler, accessorMethod);
                            }
                        });
                    }
                });
        } catch (PrivilegedActionException pae) {
            Throwable t = pae.getException();
            while ((t instanceof InvocationTargetException || t instanceof CompletionException)
                    && t.getCause() != null) {
                t = t.getCause();
            }
            // An UnsupportedConstraintException here means the constraints cannot be
            // met on a transport that does authenticate, for example a client that is
            // not logged on: an error, never "no identity".
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            if (t instanceof SecurityException) {
                throw (SecurityException) t;
            }
            throw new IOException("could not authenticate the codebase server", t);
        }
        if (principals.isEmpty()) {
            return null; // the transport or endpoint cannot authenticate a server
        }
        return select(principals);
    }

    private static Subject currentSubject() {
        try {
            Method current = Subject.class.getMethod("current"); // Java 18 and later
            return (Subject) current.invoke(null);
        } catch (NoSuchMethodException e) {
            return AccessController.doPrivileged(new java.security.PrivilegedAction<Subject>() {
                @Override
                public Subject run() {
                    return Subject.getSubject(AccessController.getContext());
                }
            });
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private static <T> T runAs(Subject subject, final Callable<T> action) throws Exception {
        if (subject == null) {
            return action.call();
        }
        try {
            Method callAs = Subject.class.getMethod("callAs", Subject.class, Callable.class);
            try {
                return (T) callAs.invoke(null, subject, action);
            } catch (InvocationTargetException e) {
                Throwable t = e.getCause();
                if (t instanceof CompletionException && t.getCause() != null) {
                    t = t.getCause();
                }
                if (t instanceof Exception) {
                    throw (Exception) t;
                }
                throw new IllegalStateException(t);
            }
        } catch (NoSuchMethodException e) {
            try {
                return Subject.doAs(subject, new PrivilegedExceptionAction<T>() {
                    @Override
                    public T run() throws Exception {
                        return action.call();
                    }
                });
            } catch (PrivilegedActionException pae) {
                throw pae.getException();
            }
        }
    }

    /** Whether the proxy's invocation handler is exactly one of the trusted JERI handler classes. */
    static boolean hasTrustedHandler(Object proxy) {
        return proxy != null && Proxy.isProxyClass(proxy.getClass())
                && trustedHandlerClass(Proxy.getInvocationHandler(proxy)) != null;
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

    /** Whether {@code p} is a SPIFFE principal, recognised by exact class name. */
    static boolean isSpiffe(Principal p) {
        return p != null && p.getClass().getName().equals(SPIFFE_PRINCIPAL_CLASS);
    }

    /**
     * Exactly one SPIFFE principal, else exactly one Kerberos principal, else
     * exactly one X.500 principal.  More than one of the kind that decides, or
     * nothing recognisable, is refused.
     */
    static Principal select(Set<?> principals) throws IOException {
        List<Principal> spiffe = new ArrayList<Principal>();
        List<Principal> kerberos = new ArrayList<Principal>();
        List<Principal> x500 = new ArrayList<Principal>();
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
            } else if (c == X500Principal.class) {
                x500.add(p);
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
        if (kerberos.size() == 1) {
            return kerberos.get(0);
        }
        if (x500.size() > 1) {
            throw new IOException("the server authenticated as more than one X.500"
                    + " principal; refusing an ambiguous identity: " + x500);
        }
        if (x500.size() == 1) {
            return x500.get(0);
        }
        throw new IOException("the server authenticated, but as no SPIFFE, Kerberos or"
                + " X.500 principal: " + principals);
    }

    /**
     * A connection has one server principal, so two different
     * {@link ServerMinPrincipal} requirements cannot both be met.  The one the
     * probe satisfied is replaced by the pin.
     */
    private static InvocationConstraints withoutServerMinPrincipal(InvocationConstraints c) {
        List<InvocationConstraint> requirements = new ArrayList<InvocationConstraint>();
        for (Object o : c.requirements()) {
            if (!(o instanceof ServerMinPrincipal)) {
                requirements.add((InvocationConstraint) o);
            }
        }
        List<InvocationConstraint> preferences = new ArrayList<InvocationConstraint>();
        for (Object o : c.preferences()) {
            preferences.add((InvocationConstraint) o);
        }
        return new InvocationConstraints(
                requirements.toArray(new InvocationConstraint[0]),
                preferences.toArray(new InvocationConstraint[0]));
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
                    m.getName(), InvocationConstraints.combine(
                            withoutServerMinPrincipal(current), pinReq)));
        }
        // Anything not named above still requires the pin.
        descs.add(new BasicMethodConstraints.MethodDesc(pinReq));
        MethodConstraints pinned = new BasicMethodConstraints(
                descs.toArray(new BasicMethodConstraints.MethodDesc[0]));
        return (CodebaseAccessor) ((RemoteMethodControl) proxy).setConstraints(pinned);
    }
}
