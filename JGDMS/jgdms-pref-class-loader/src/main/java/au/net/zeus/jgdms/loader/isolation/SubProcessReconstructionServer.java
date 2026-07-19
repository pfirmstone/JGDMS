/*
 * Copyright 2026 The Apache Software Foundation.
 *
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
package au.net.zeus.jgdms.loader.isolation;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.channels.ByteChannel;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import net.jini.core.constraint.MethodConstraints;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.io.context.IntegrityEnforcement;
import net.jini.io.context.ServerSubject;
import net.jini.loader.pref.PreferredProxyCodebaseProvider;

/**
 * The subprocess-side wire-handoff dispatcher (task&nbsp;T4): the
 * <strong>single concentrated object-reconstruction door</strong> for the
 * whole smart-proxy-isolation architecture.
 *
 * <p>Runs entirely <em>inside</em> the isolated subprocess, driven per
 * {@link SubProcessLauncher.Spawned#openWireChannel() connection}. Each
 * connection carries exactly one {@code REQUEST}/{@code REPLY} handoff
 * exchange (see {@link WireFraming}/{@link WireHandoffCodec} for the byte
 * layout), followed -- only on a successful handoff -- by that hosted
 * object's {@code INVOKE_REQUEST}/{@code INVOKE_REPLY} business-call
 * traffic for the connection's lifetime.
 *
 * <h2>Where each gate runs, and why here</h2>
 * <ul>
 *   <li><strong>Canonical pooling-key re-validation.</strong> The request
 *       carries the client's asserted {@link IsolationPoolingKey}; this
 *       dispatcher independently compares it against {@link #ownKey}
 *       (supplied at construction by whatever spawned this subprocess for a
 *       specific principal) and fails closed on mismatch -- defence in
 *       depth against a request replayed or misdirected onto the wrong
 *       subprocess (a confused-deputy / wrong-target hazard distinct from,
 *       and in addition to, the pool's own per-principal keying).</li>
 *   <li><strong>SCAP/BAE verdict gate, {@code DeSerializationPermission
 *       ("ATOMIC")}, and endpoint-assigned {@code ResolutionContext}.</strong>
 *       All three are enforced by literally reusing
 *       {@link PreferredProxyCodebaseProvider#resolve} -- the exact,
 *       already-reviewed legacy in-process reconstruction path -- called
 *       here, inside the subprocess, rather than re-implemented. This is a
 *       deliberate design choice: two independent implementations of the
 *       same security-critical gate stack can drift (G1/G8); reusing the
 *       one, existing implementation cannot. {@code resolve()}'s own
 *       verdict check, digest-grant issuance, download-permission grants,
 *       and the terminal {@code serviceProxy.get(loader, true, verifier,
 *       context)} call (where {@code DeSerializationPermission("ATOMIC")} /
 *       {@code "PROXY"} are actually enforced against each resolved class's
 *       protection domain, and where the DER/JOSS decode path constructs
 *       its endpoint-assigned {@code ResolutionContext}) all run exactly as
 *       they did before this architecture existed -- only the process
 *       boundary moved, not the gates.</li>
 *   <li><strong>{@code HostedProxyGuard} reject-on-load.</strong> Runs
 *       immediately after {@code resolve()} returns, <em>before</em> the
 *       resolved object's interfaces are ever handed back to a client or
 *       dispatched to -- exactly the point the wiring SOW names: "since
 *       this is where a hosted proxy's interface closure first becomes a
 *       resolved {@code Class} set."</li>
 * </ul>
 *
 * <h2>Required invariant on the hosting subprocess (not enforced by this
 * class alone)</h2>
 * The subprocess process must run with smart-proxy isolation routing
 * <strong>disabled</strong> (the
 * {@code net.jini.loader.pref.smartProxyIsolation.enabled} system property
 * unset/false), so the reused {@code resolve()} call takes the ordinary
 * in-process path rather than recursing into another isolation hand-off.
 * {@link #ownKey} construction defensively re-checks this property and
 * fails closed if it is set; the authoritative backstop is
 * {@code resolve()}'s own default {@code UnsupportedIsolationRouter}, which
 * fails closed regardless of this class's own check.
 *
 * @since 3.1.1
 */
final class SubProcessReconstructionServer {

    private static final Logger logger =
            Logger.getLogger(SubProcessReconstructionServer.class.getName());

    // Mirrors PreferredProxyCodebaseProvider.SMART_PROXY_ISOLATION_ENABLED_PROPERTY
    // (package-private there; duplicated here only for this class's own
    // defence-in-depth sanity check -- resolve()'s own fail-closed default
    // router remains the authoritative backstop regardless).
    private static final String SMART_PROXY_ISOLATION_ENABLED_PROPERTY =
            "net.jini.loader.pref.smartProxyIsolation.enabled";

    private final IsolationPoolingKey ownKey;
    private final ClassLoader trustedLoader;
    private final ClassLoader subprocessRootLoader;

    /**
     * @param ownKey this subprocess's own canonical pooling key, as supplied
     *        by the launcher that spawned it for a specific principal; must
     *        not be null
     * @param trustedLoader classloader used to decode the control-channel
     *        envelope itself (first-party types only: {@link
     *        MarshalledInstance}, {@link CodebaseAccessor}, {@link
     *        MethodConstraints}, {@code Principal[]}) -- never used to
     *        resolve the hosted smart proxy's own classes, which go through
     *        {@code resolve()}'s own endpoint-assigned loader
     * @param subprocessRootLoader the subprocess's own trusted root loader,
     *        used as {@code resolve()}'s {@code parent}/{@code verifier}
     *        arguments -- deliberately never a client-supplied loader
     *        reference, which is meaningless across the process boundary
     */
    SubProcessReconstructionServer(IsolationPoolingKey ownKey,
                                   ClassLoader trustedLoader,
                                   ClassLoader subprocessRootLoader) {
        if (ownKey == null) throw new NullPointerException("ownKey");
        if (Boolean.getBoolean(SMART_PROXY_ISOLATION_ENABLED_PROPERTY)) {
            throw new IllegalStateException(
                "Subprocess misconfiguration: " + ownKey + "'s process has"
                + " smart-proxy isolation routing ENABLED ("
                + SMART_PROXY_ISOLATION_ENABLED_PROPERTY + "=true). An"
                + " isolation subprocess must never itself route into"
                + " another isolation hand-off; refusing to start"
                + " (fail-closed).");
        }
        this.ownKey = ownKey;
        this.trustedLoader = trustedLoader;
        this.subprocessRootLoader = subprocessRootLoader;
    }

    /**
     * Services one connection to completion: the {@code REQUEST}/
     * {@code REPLY} handoff, then (only on success) the
     * {@code INVOKE_REQUEST}/{@code INVOKE_REPLY} loop until the channel is
     * closed by the peer. Never opens a new outbound connection back to the
     * client -- request origination on this channel is client-initiated
     * only, satisfying the wiring SOW's three-axes requirement structurally
     * (this method only ever reads-then-writes on the channel it was
     * given).
     */
    void serve(ByteChannel channel) {
        Object hosted;
        Set<Method> allowedMethods;
        String hostedId;
        try {
            WireFraming.Frame req = WireFraming.readFrame(channel);
            if (req.type != WireFraming.Type.REQUEST) {
                writeError(channel, "PROTOCOL",
                        "Expected REQUEST as the first frame on a new"
                        + " wire-handoff connection, got type " + req.type);
                return;
            }
            Reconstructed r = reconstruct(req.payload);
            hosted = r.object;
            allowedMethods = r.allowedMethods;
            hostedId = UUID.randomUUID().toString();
            byte[] reply = WireHandoffCodec.encodeReplyOk(r.interfaceNames, hostedId);
            WireFraming.writeFrame(channel, WireFraming.Type.REPLY_OK, reply);
        } catch (RejectedHandoffException rejected) {
            logger.log(Level.WARNING, "Wire-handoff request refused: "
                    + rejected.getMessage(), rejected);
            safeWriteError(channel, rejected.category, rejected.getMessage());
            return;
        } catch (EOFException eof) {
            // Peer closed before completing a handoff; nothing to reply to.
            return;
        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "Unexpected failure reconstructing wire-handoff request",
                    e);
            safeWriteError(channel, "INTERNAL",
                    "Unexpected failure reconstructing the request.");
            return;
        }

        // ---- Business-call loop for the now-hosted object -------------
        try {
            for (;;) {
                WireFraming.Frame frame;
                try {
                    frame = WireFraming.readFrame(channel);
                } catch (EOFException eof) {
                    // Peer closed the connection: the ordinary, expected end
                    // of this hosted reference's lifetime on this channel.
                    // NOT treated as proof of DGC reference-retirement by
                    // this class (S4) -- that decision is made client-side
                    // (SubProcessHandle.referenceRetired), independently of
                    // this connection's byte-level lifecycle.
                    return;
                }
                if (frame.type != WireFraming.Type.INVOKE_REQUEST) {
                    writeError(channel, "PROTOCOL",
                            "Expected INVOKE_REQUEST, got type " + frame.type);
                    return;
                }
                dispatchInvoke(channel, hosted, allowedMethods, frame.payload);
            }
        } catch (IOException io) {
            logger.log(Level.FINE,
                    "Wire-handoff business channel for {0} ended: {1}",
                    new Object[]{hostedId, io.getMessage()});
        }
    }

    // -------------------------------------------------------- reconstruct

    private static final class Reconstructed {
        final Object object;
        final String[] interfaceNames;
        final Set<Method> allowedMethods;

        Reconstructed(Object object, String[] interfaceNames,
                     Set<Method> allowedMethods) {
            this.object = object;
            this.interfaceNames = interfaceNames;
            this.allowedMethods = allowedMethods;
        }
    }

    private static final class RejectedHandoffException extends Exception {
        final String category;

        RejectedHandoffException(String category, String message) {
            super(message);
            this.category = category;
        }

        RejectedHandoffException(String category, String message, Throwable cause) {
            super(message, cause);
            this.category = category;
        }
    }

    private Reconstructed reconstruct(byte[] requestPayload)
            throws RejectedHandoffException {
        WireHandoffCodec.DecodedRequest req;
        try {
            req = WireHandoffCodec.decodeRequest(requestPayload, trustedLoader);
        } catch (Exception e) {
            throw new RejectedHandoffException("MALFORMED_REQUEST",
                    "Could not decode wire-handoff request envelope.", e);
        }

        // ---- Defence-in-depth: cross-check the client-asserted pooling key
        // ---- against this subprocess's own (compared as canonical
        // ---- strings; IsolationPoolingKey itself is not reconstructible
        // ---- from a bare string outside its own derive()/internal
        // ---- constructor, and does not need to be -- string equality on
        // ---- the canonical value is exactly what IsolationPoolingKey's
        // ---- own equals() reduces to).
        if (req.assertedPoolingKey == null
                || !ownKey.value().equals(req.assertedPoolingKey)) {
            throw new RejectedHandoffException("KEY_MISMATCH",
                    "Wire-handoff request asserts pooling key ["
                    + req.assertedPoolingKey + "] but this subprocess is"
                    + " provisioned for [" + ownKey + "]; refusing"
                    + " (fail-closed -- wrong-subprocess / replay guard).");
        }

        // ---- Build the Collection context resolve() expects, from the
        // ---- distilled fields the request carried (never a raw Subject
        // ---- reference from the client -- reconstructed locally from the
        // ---- Principal[] the client already derived from its own TLS
        // ---- ServerSubject at T1's choke point).
        List<Object> context = new ArrayList<Object>(3);
        context.add(new IntegrityEnforcement() {
            @Override
            public boolean integrityEnforced() {
                return req.verifyCodebaseIntegrity;
            }
        });
        if (req.methodConstraints != null) {
            context.add(req.methodConstraints);
        }
        if (req.serverPrincipals != null && req.serverPrincipals.length > 0) {
            final Subject serverSubject = new Subject(true,
                    new LinkedHashSet<Principal>(java.util.Arrays.asList(req.serverPrincipals)),
                    Collections.emptySet(), Collections.emptySet());
            context.add(new ServerSubject() {
                @Override
                public Subject getServerSubject() {
                    return serverSubject;
                }
            });
        }

        Object sp;
        try {
            // ---- THE reconstruction call: literally the same, already-
            // ---- reviewed legacy in-process gate stack (verdict check,
            // ---- digest grants, PreferredClassLoader creation,
            // ---- DeSerializationPermission("ATOMIC")/("PROXY") via the
            // ---- terminal serviceProxy.get(...), endpoint-assigned
            // ---- ResolutionContext deep in the DER/JOSS decode path) --
            // ---- now simply running inside the subprocess instead of the
            // ---- original client process.
            sp = new PreferredProxyCodebaseProvider().resolve(
                    req.bootstrapProxy, req.serviceProxy,
                    subprocessRootLoader, subprocessRootLoader, context);
        } catch (RuntimeException e) {
            throw new RejectedHandoffException("RECONSTRUCTION_REFUSED",
                    "Reconstruction refused: " + safeMessage(e), e);
        } catch (Exception e) {
            throw new RejectedHandoffException("RECONSTRUCTION_FAILED",
                    "Reconstruction failed: " + safeMessage(e), e);
        }

        if (sp == null) {
            throw new RejectedHandoffException("NULL_RESULT",
                    "Reconstructed proxy was null.");
        }

        // ---- HostedProxyGuard: reject-on-load, right where the resolved
        // ---- interface closure first exists, before export/dispatch.
        try {
            HostedProxyGuard.checkHostableClosure(sp.getClass());
        } catch (SecurityException e) {
            throw new RejectedHandoffException("HOSTING_REFUSED",
                    safeMessage(e), e);
        }

        Set<Class<?>> interfaces = publicInterfaceClosure(sp.getClass());
        if (interfaces.isEmpty()) {
            throw new RejectedHandoffException("NO_INTERFACES",
                    "Reconstructed proxy of type " + sp.getClass().getName()
                    + " implements no public interfaces; nothing to expose"
                    + " a thin client stub over.");
        }
        String[] names = new String[interfaces.size()];
        int i = 0;
        for (Class<?> c : interfaces) names[i++] = c.getName();

        Set<Method> allowed = new LinkedHashSet<Method>();
        for (Class<?> c : interfaces) {
            for (Method m : c.getMethods()) allowed.add(m);
        }
        return new Reconstructed(sp, names, allowed);
    }

    /**
     * The public interface closure of {@code type} -- its own interfaces,
     * their superinterfaces, and superclass interfaces -- restricted to
     * {@code public} interfaces only (a non-public interface cannot back a
     * {@code java.lang.reflect.Proxy} the client can implement). By the
     * time this runs {@link HostedProxyGuard} has already rejected any
     * closure containing a management-plane interface, so this closure is
     * guaranteed business-only.
     */
    private static Set<Class<?>> publicInterfaceClosure(Class<?> type) {
        Set<Class<?>> out = new LinkedHashSet<Class<?>>();
        collectPublicInterfaces(type, out);
        return out;
    }

    private static void collectPublicInterfaces(Class<?> t, Set<Class<?>> out) {
        if (t == null) return;
        if (t.isInterface() && Modifier.isPublic(t.getModifiers())) {
            if (!out.add(t)) return; // already visited
        }
        Class<?>[] ifaces = t.getInterfaces();
        for (Class<?> iface : ifaces) collectPublicInterfaces(iface, out);
        collectPublicInterfaces(t.getSuperclass(), out);
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null ? t.getClass().getName() : m;
    }

    // ------------------------------------------------------------ invoke

    private void dispatchInvoke(ByteChannel channel, Object hosted,
                                Set<Method> allowedMethods, byte[] payload) {
        WireHandoffCodec.DecodedInvokeRequest inv;
        try {
            inv = WireHandoffCodec.decodeInvokeRequest(
                    payload, hosted.getClass().getClassLoader());
        } catch (Exception e) {
            writeInvokeException(channel, IllegalArgumentException.class.getName(),
                    "Could not decode invocation request.");
            return;
        }
        Method target = findAllowedMethod(allowedMethods, inv.methodName,
                inv.parameterTypeNames);
        if (target == null) {
            // Fail closed: only methods declared on the resolved, guarded
            // business-interface closure are ever dispatched -- this is
            // what structurally prevents ever reaching a management-plane
            // method (HostedProxyGuard already guarantees no such method is
            // even in allowedMethods) or an arbitrary non-interface method.
            writeInvokeException(channel, NoSuchMethodException.class.getName(),
                    "No such business method: " + inv.methodName
                    + java.util.Arrays.toString(inv.parameterTypeNames));
            return;
        }
        try {
            Object result = target.invoke(hosted, inv.args);
            byte[] reply = WireHandoffCodec.encodeInvokeReplyResult(result);
            WireFraming.writeFrame(channel, WireFraming.Type.INVOKE_REPLY_RESULT, reply);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause() == null ? ite : ite.getCause();
            writeInvokeException(channel, cause.getClass().getName(),
                    safeMessage(cause));
        } catch (Exception e) {
            writeInvokeException(channel, e.getClass().getName(), safeMessage(e));
        }
    }

    private static Method findAllowedMethod(Set<Method> allowed, String name,
                                            String[] paramTypeNames) {
        outer:
        for (Method m : allowed) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != paramTypeNames.length) continue;
            for (int i = 0; i < params.length; i++) {
                if (!params[i].getName().equals(paramTypeNames[i])) continue outer;
            }
            return m;
        }
        return null;
    }

    private void writeInvokeException(ByteChannel channel, String className,
                                      String message) {
        try {
            byte[] reply = WireHandoffCodec.encodeInvokeReplyException(className, message);
            WireFraming.writeFrame(channel, WireFraming.Type.INVOKE_REPLY_EXCEPTION, reply);
        } catch (IOException io) {
            logger.log(Level.FINE, "Could not write INVOKE_REPLY_EXCEPTION", io);
        }
    }

    private void writeError(ByteChannel channel, String category, String message)
            throws IOException {
        byte[] payload = WireHandoffCodec.encodeReplyError(category, message);
        WireFraming.writeFrame(channel, WireFraming.Type.REPLY_ERROR, payload);
    }

    private void safeWriteError(ByteChannel channel, String category, String message) {
        try {
            writeError(channel, category, message);
        } catch (IOException io) {
            logger.log(Level.FINE, "Could not write REPLY_ERROR", io);
        }
    }
}
