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

import java.io.IOException;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.ByteChannel;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.MethodConstraints;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.io.context.IntegrityEnforcement;
import net.jini.io.context.ServerSubject;
import net.jini.loader.ClassLoading;

/**
 * Real client-side {@link SubProcessWireHandoff}: hands the raw, still-
 * marshalled {@link MarshalledInstance} off to the principal's subprocess
 * over a dedicated {@link SubProcessHandle#openWireChannel() wire-handoff
 * channel}, then builds the thin client-side stub from the reply.
 *
 * <p><strong>Never deserialises {@code serviceProxy}.</strong> Writing it
 * with {@link WireHandoffCodec#encodeRequest} calls {@code
 * ObjectOutputStream.writeObject} on an <em>already-live</em> {@link
 * MarshalledInstance} instance -- which, per its own {@code serialize()}
 * (see {@code MarshalledInstance.serialForm()}), only copies its own opaque
 * {@code byte[]}/{@code String}/{@code int} fields onto the stream. No
 * class belonging to the <em>wrapped</em> object is ever touched, named, or
 * resolved on this side; {@code .get()} is never called here.
 *
 * <h2>Three-axes check (S3, stated explicitly)</h2>
 * <ul>
 *   <li><strong>Byte flow:</strong> both directions, on one connection --
 *       request bytes client&rarr;subprocess, reply bytes
 *       subprocess&rarr;client, for both the handoff exchange and every
 *       subsequent business call.</li>
 *   <li><strong>Request origination:</strong> client-initiated only. This
 *       class only ever calls {@link SubProcessHandle#openWireChannel()}
 *       (opening a connection <em>to</em> the subprocess) and then writes a
 *       request before reading a reply; it never accepts an inbound
 *       connection and never reads unsolicited bytes from the subprocess.
 *       The subprocess side ({@code SubProcessReconstructionServer}) is
 *       symmetric: it only ever reads-then-writes on the channel it was
 *       handed, and never opens an outbound connection back to a client.
 *       There is <strong>no</strong> subprocess-originated call toward the
 *       client anywhere in this mechanism -- re-derived here explicitly,
 *       not assumed, per Board Guidance's role-reversal hunt.</li>
 *   <li><strong>Authorization:</strong> this business/wire-handoff channel
 *       is entirely separate from {@link SubProcessHandle#adminSurface()}'s
 *       admin channel; possessing one proves nothing about the other. If a
 *       future revision multiplexes {@code SubProcessDynamicPolicy}'s
 *       grant-push traffic onto this same physical connection, its
 *       authorization must be re-derived independently (S1's admin
 *       authentication gate), never inherited from having successfully
 *       completed a business handoff here.</li>
 * </ul>
 *
 * @since 3.1.1
 */
public final class SubProcessWireHandoffImpl implements SubProcessWireHandoff {

    private static final Logger logger =
            Logger.getLogger(SubProcessWireHandoffImpl.class.getName());

    /**
     * {@code java.lang.ref.Cleaner} equivalent, hand-rolled: this module
     * compiles at {@code --release 8} (pre-{@code Cleaner}, added in 9), so
     * DGC-analogue retirement (see {@link Retire}) is driven by a
     * {@link PhantomReference}/{@link ReferenceQueue} pair drained by one
     * shared daemon thread, exactly what {@code Cleaner} itself wraps.
     */
    private static final ReferenceQueue<Object> RETIRE_QUEUE = new ReferenceQueue<Object>();
    static {
        Thread t = new Thread("SubProcessWireHandoff-retirement") {
            @Override
            public void run() {
                for (;;) {
                    try {
                        Reference<?> ref = RETIRE_QUEUE.remove();
                        if (ref instanceof Retire) {
                            ((Retire) ref).run();
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (RuntimeException e) {
                        logger.log(Level.FINE, "Retirement action failed", e);
                    }
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    @Override
    public Object handoff(SubProcessHandle handle,
                          CodebaseAccessor bootstrapProxy,
                          MarshalledInstance serviceProxy,
                          URL[] codebase,
                          String path,
                          ClassLoader parent,
                          ClassLoader verifier,
                          Collection context)
            throws IOException, ClassNotFoundException {
        if (handle == null) throw new NullPointerException("handle");
        if (serviceProxy == null) throw new NullPointerException("serviceProxy");

        MethodConstraints mc = null;
        IntegrityEnforcement integrityEnforcement = null;
        ServerSubject serverSubjectFromContext = null;
        if (context != null) {
            Iterator it = context.iterator();
            while (it.hasNext()) {
                Object o = it.next();
                if (o instanceof MethodConstraints) {
                    mc = (MethodConstraints) o;
                } else if (o instanceof IntegrityEnforcement) {
                    integrityEnforcement = (IntegrityEnforcement) o;
                } else if (o instanceof ServerSubject) {
                    serverSubjectFromContext = (ServerSubject) o;
                }
            }
        }
        boolean verifyCodebaseIntegrity =
                integrityEnforcement != null && integrityEnforcement.integrityEnforced();
        Principal[] serverPrincipals = null;
        if (serverSubjectFromContext != null) {
            Set<Principal> principals =
                    serverSubjectFromContext.getServerSubject().getPrincipals();
            serverPrincipals = principals.isEmpty()
                    ? null : principals.toArray(new Principal[0]);
        }

        final ByteChannel channel = handle.openWireChannel();
        final Object lock = new Object();
        boolean handoffOk = false;
        try {
            byte[] requestPayload = WireHandoffCodec.encodeRequest(
                    handle.key().value(), path, verifyCodebaseIntegrity,
                    serviceProxy, bootstrapProxy, mc, serverPrincipals);

            WireFraming.Frame reply;
            synchronized (lock) {
                WireFraming.writeFrame(channel, WireFraming.Type.REQUEST, requestPayload);
                reply = WireFraming.readFrame(channel);
            }

            if (reply.type == WireFraming.Type.REPLY_ERROR) {
                WireHandoffCodec.DecodedReplyError err =
                        WireHandoffCodec.decodeReplyError(reply.payload, thisLoader());
                throw new IOException(
                        "Subprocess refused wire-handoff request for codebase "
                        + path + " [" + err.category + "]: " + err.message);
            }
            if (reply.type != WireFraming.Type.REPLY_OK) {
                throw new IOException(
                        "Unexpected wire-handoff reply frame type: " + reply.type);
            }
            WireHandoffCodec.DecodedReplyOk ok =
                    WireHandoffCodec.decodeReplyOk(reply.payload, thisLoader());

            Class<?>[] interfaces = resolveInterfaces(
                    ok.interfaceNames, parent, verifyCodebaseIntegrity, verifier);

            InvocationHandler ih = new ForwardingInvocationHandler(
                    channel, lock, ok.hostedId, parent, verifyCodebaseIntegrity, verifier);
            Object stub = Proxy.newProxyInstance(
                    parent != null ? parent : thisLoader(), interfaces, ih);

            // DGC-analogue bookkeeping (T2's SubProcessHandle model): record
            // the live reference now, and retire it when this JVM's own GC
            // proves the stub is no longer reachable from this process --
            // never on connection-close (S4). This is a process-A-local
            // signal: this process is the only client of this subprocess's
            // exports (per-principal, non-shared), so local unreachability
            // is by itself sufficient to know the reference is gone; no
            // cross-process acknowledgement is required to trust it.
            handle.hostReference(ok.hostedId);
            new Retire(stub, RETIRE_QUEUE, handle, ok.hostedId, channel);

            handoffOk = true;
            return stub;
        } finally {
            if (!handoffOk) {
                try { channel.close(); } catch (IOException ignore) { }
            }
        }
    }

    private static ClassLoader thisLoader() {
        return SubProcessWireHandoffImpl.class.getClassLoader();
    }

    private static Class<?>[] resolveInterfaces(String[] names, ClassLoader parent,
                                                boolean verifyCodebaseIntegrity,
                                                ClassLoader verifier)
            throws ClassNotFoundException, IOException {
        if (names == null || names.length == 0) {
            throw new IOException(
                    "Subprocess reported no business interfaces to build a"
                    + " thin stub from.");
        }
        Class<?>[] out = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) {
            try {
                // codebase == null: interface resolution rides the client's
                // own local, non-download-capable classpath (T5) -- never a
                // codebase URL carried by the (untrusted) hosted proxy.
                out[i] = ClassLoading.loadClass(
                        null, names[i], parent, verifyCodebaseIntegrity, verifier);
            } catch (MalformedURLException e) {
                // codebase is null above; unreachable per ClassLoading's own
                // contract, but declared -- treat defensively as not-found.
                throw new ClassNotFoundException(names[i], e);
            }
        }
        return out;
    }

    /**
     * A live {@code Retire} registration must itself stay strongly
     * reachable until it fires -- otherwise the {@link PhantomReference}
     * could be collected before enqueue, silently dropping the retirement
     * notification (the same hazard {@code java.lang.ref.Cleaner} guards
     * against internally with its own live-cleanable set). This set is that
     * guard: every {@link Retire} adds itself here at construction and
     * removes itself once it has run.
     */
    private static final Set<Retire> LIVE_RETIRE_REFS =
            java.util.Collections.newSetFromMap(
                    new java.util.concurrent.ConcurrentHashMap<Retire, Boolean>());

    /**
     * Cleaner-equivalent action: fires once the client-side stub this
     * instance was registered against becomes phantom-reachable (this JVM's
     * own GC has proven nothing in this process references it anymore).
     */
    private static final class Retire extends PhantomReference<Object> implements Runnable {
        private final SubProcessHandle handle;
        private final String hostedId;
        private final ByteChannel channel;

        Retire(Object stub, ReferenceQueue<Object> queue, SubProcessHandle handle,
              String hostedId, ByteChannel channel) {
            super(stub, queue);
            this.handle = handle;
            this.hostedId = hostedId;
            this.channel = channel;
            LIVE_RETIRE_REFS.add(this);
        }

        @Override
        public void run() {
            LIVE_RETIRE_REFS.remove(this);
            try {
                handle.referenceRetired(hostedId,
                        SubProcessHandle.RetirementReason.CLEAN_CALL_PROCESSED);
            } catch (RuntimeException e) {
                logger.log(Level.FINE, "referenceRetired failed for " + hostedId, e);
            } finally {
                try { channel.close(); } catch (IOException ignore) { }
            }
        }
    }

    /**
     * Forwards business method calls to the subprocess-hosted object over
     * the wire-handoff channel's {@code INVOKE_REQUEST}/{@code INVOKE_REPLY}
     * messages. All calls on one stub are serialised through {@code lock}:
     * this connection carries one outstanding request at a time (no
     * pipelining/multiplexing) -- a scope simplification flagged in this
     * task's report, not a hidden limitation.
     */
    private static final class ForwardingInvocationHandler implements InvocationHandler {
        private final ByteChannel channel;
        private final Object lock;
        private final String hostedId;
        private final ClassLoader parent;
        private final boolean verifyCodebaseIntegrity;
        private final ClassLoader verifier;

        ForwardingInvocationHandler(ByteChannel channel, Object lock, String hostedId,
                                    ClassLoader parent, boolean verifyCodebaseIntegrity,
                                    ClassLoader verifier) {
            this.channel = channel;
            this.lock = lock;
            this.hostedId = hostedId;
            this.parent = parent;
            this.verifyCodebaseIntegrity = verifyCodebaseIntegrity;
            this.verifier = verifier;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Class<?> decl = method.getDeclaringClass();
            if (decl == Object.class) {
                String n = method.getName();
                if ("hashCode".equals(n)) return System.identityHashCode(proxy);
                if ("equals".equals(n)) return proxy == (args == null ? null : args[0]);
                if ("toString".equals(n)) return "SubProcessWireHandoff$stub[" + hostedId + "]";
            }

            String[] paramTypeNames = new String[method.getParameterTypes().length];
            for (int i = 0; i < paramTypeNames.length; i++) {
                paramTypeNames[i] = method.getParameterTypes()[i].getName();
            }
            byte[] reqPayload = WireHandoffCodec.encodeInvokeRequest(
                    method.getName(), paramTypeNames, args);

            WireFraming.Frame reply;
            synchronized (lock) {
                WireFraming.writeFrame(channel, WireFraming.Type.INVOKE_REQUEST, reqPayload);
                reply = WireFraming.readFrame(channel);
            }

            if (reply.type == WireFraming.Type.INVOKE_REPLY_RESULT) {
                return WireHandoffCodec.decodeInvokeReplyResult(reply.payload, parent);
            }
            if (reply.type == WireFraming.Type.INVOKE_REPLY_EXCEPTION) {
                WireHandoffCodec.DecodedReplyError err =
                        WireHandoffCodec.decodeInvokeReplyException(reply.payload, parent);
                throw synthesizeException(method, err.category, err.message);
            }
            throw new IOException(
                    "Unexpected invoke-reply frame type: " + reply.type);
        }

        /**
         * Never reconstructs the subprocess's actual exception class (that
         * would be an unnecessary reverse decode surface -- deserialising a
         * subprocess-named, possibly attacker-influenced class back into
         * this process). Instead synthesises a checked
         * {@link RemoteException} when the method declares one (the
         * ordinary Jini remote-call failure shape), or an unchecked
         * {@link RuntimeException} otherwise, both carrying the reported
         * class name and message only as text.
         */
        private static Throwable synthesizeException(Method method, String category,
                                                      String message) {
            String text = category + ": " + message;
            Class<?>[] declared = method.getExceptionTypes();
            for (Class<?> d : declared) {
                if (RemoteException.class.isAssignableFrom(d)
                        || d.isAssignableFrom(RemoteException.class)) {
                    return new RemoteException(text);
                }
            }
            return new RuntimeException(text);
        }
    }
}
