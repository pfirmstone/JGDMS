/*
 * Copyright 2026 peter.
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
package net.jini.jeri;

import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.MethodConstraints;

/**
 * An {@link InvocationDispatcher} that uses DER (JGDMS-STD-006) encoding for
 * the JERI call wire format on the server side (JGDMS-STD-008 sec.14, A0).
 *
 * <p>Pair with {@link AtomicDerInvocationHandler} via {@link AtomicDerILFactory}.
 *
 * <p>The streams implement {@link ObjectOutput}/{@link ObjectInput} directly;
 * return types are the base interfaces (not the {@code ObjectOutputStream}/
 * {@code ObjectInputStream} subclasses).
 *
 * @author peter
 * @since 4.0
 */
public class AtomicDerInvocationDispatcher extends BasicInvocationDispatcher {

    /** Per-deployment DoS limits for the client-argument input stream (see {@link DerInputLimits}). */
    private final DerInputLimits limits;

    /**
     * Creates a dispatcher for the given set of remote methods.
     *
     * @param methods the remote methods to dispatch
     * @param caps the server capabilities
     * @param serverConstraints the server constraints, or {@code null}
     * @param permissionClass the permission class for access control,
     *        or {@code null}
     * @param loader the class loader, or {@code null}
     * @throws ExportException if the dispatcher cannot be created
     */
    public AtomicDerInvocationDispatcher(Collection methods,
                                   ServerCapabilities caps,
                                   MethodConstraints serverConstraints,
                                   Class permissionClass,
                                   ClassLoader loader)
            throws ExportException {
        this(methods, caps, serverConstraints, permissionClass, loader, DerInputLimits.DEFAULT);
    }

    /**
     * As {@link #AtomicDerInvocationDispatcher(Collection, ServerCapabilities, MethodConstraints,
     * Class, ClassLoader)}, with explicit DoS limits for reading client invocation arguments -- the
     * per-deployment input cap supplied by {@link AtomicDerILFactory} from the service's
     * {@code net.jini.config.Configuration}.
     *
     * @param limits the DoS limits for the argument stream (must not be {@code null};
     *               {@link DerInputLimits#DEFAULT} for the JVM-wide default)
     * @throws ExportException if the dispatcher cannot be created
     */
    public AtomicDerInvocationDispatcher(Collection methods,
                                   ServerCapabilities caps,
                                   MethodConstraints serverConstraints,
                                   Class permissionClass,
                                   ClassLoader loader,
                                   DerInputLimits limits)
            throws ExportException {
        // Pass the DER payload format so the superclass verifies/strips a
        // MarshallingFormat.ATOMIC_DER requirement at export + dispatch (STD-008 sec.18.3),
        // and a DER reducing-context codec so the untrusted §7.3 reducing-context
        // block is decoded as DER -- matching the DER client handler, which derives
        // the same codec from its marshallingFormat() -- under the same per-deployment
        // DoS limits as the argument stream.
        super(methods, caps, serverConstraints, permissionClass, loader,
                net.jini.core.constraint.MarshallingFormat.ATOMIC_DER.getFormat(),
                new DerReducingContextCodec(java.util.Objects.requireNonNull(limits, "limits")));
        this.limits = limits;
    }

    /**
     * Returns a {@link DerMarshalInputStream} reading from the request input
     * stream of {@code request}.
     *
     * <p>Override return type is {@link ObjectInput} (the base interface). DER carries no codebase
     * annotation, so class names resolve against the dispatcher's stream loader
     * ({@code getStreamLoader(impl)}) -- the endpoint-assigned loader, NOT the thread-context
     * loader (the Warres discipline) -- carried in a {@link ResolutionContext}.
     */
    @Override
    protected ObjectInput createMarshalInputStream(Object impl,
                                                   InboundRequest request,
                                                   boolean integrity,
                                                   Collection context)
            throws IOException {
        ClassLoader streamLoader = getStreamLoader(impl);
        return new DerMarshalInputStream(request.getRequestInputStream(),
                new ResolutionContext(streamLoader, integrity, streamLoader), limits);
    }

    /**
     * Returns a {@link DerMarshalOutputStream} writing to the response output
     * stream of {@code request}.
     *
     * <p>Override return type is {@link ObjectOutput} (the base interface).
     *
     * @throws NullPointerException if {@code impl} is null
     */
    @Override
    protected ObjectOutput createMarshalOutputStream(Object impl,
                                                     Method method,
                                                     InboundRequest request,
                                                     Collection context)
            throws IOException {
        if (impl == null) {
            throw new NullPointerException();
        }
        // Substitute a downloadable proxy return value with a DerProxySerializer carrier; the
        // dispatcher's stream loader gates the ProxyCodebaseSpi.substitute() check.
        return new DerMarshalOutputStream(request.getResponseOutputStream(),
                context, getStreamLoader(impl));
    }

    /**
     * Marshals a remote fault over the pure DER response stream (U1b finding 8a).
     *
     * <p>The DER stream admits only {@code @AtomicSerial} objects at top level
     * (and the closed DER registry deliberately defers {@code Throwable}), so the
     * inherited {@code out.writeObject(throwable)} fails for every
     * non-{@code @AtomicSerial} fault -- including the {@code ServerException} /
     * {@code ServerError} wrappers this class's superclass applies before calling
     * this method -- degrading the fault to a {@code request.abort()} and a
     * generic connection failure at the client. This override applies the
     * STD-006 sec.7.6 {@code ThrowableRecord} safe-subset carrier
     * ({@link DerThrowableForm}) as an <em>explicit top-level replacement at this
     * dispatcher seam</em>; the closed DER registry is NOT involved.
     *
     * <h4>Dispatch rule (pinned)</h4>
     * <ul>
     *   <li>An {@code @AtomicSerial} throwable whose declared serial form is
     *       DER-SCHEMA-TYPABLE ({@code SchemaGenerator.generateChain} succeeds --
     *       memoised, so the per-fault cost is a cache hit) travels NATIVELY:
     *       unchanged wire form, and its {@code (GetArg)} invariant-checked
     *       reconstruction is higher-fidelity than the carrier's constructor
     *       matching. This also preserves the serializer-vs-annotation
     *       precedence pinned by {@code ThrowableDerDispatchProbeTest}.</li>
     *   <li>Everything else is captured into a {@link DerThrowableForm} carrier
     *       at this seam. That includes {@code @AtomicSerial} throwables whose
     *       form is NOT schema-typable: notably every
     *       {@code org.apache.river.api.io.AtomicException} subclass (e.g.
     *       {@code TransactionException}), whose inherited serial form declares
     *       {@code cause: Throwable} / {@code suppressed: Throwable[]} /
     *       {@code stack: StackTraceElement[]} -- types the declaration-driven
     *       DER schema rejects. Pre-carrier, such faults did not travel natively
     *       either: they failed schema generation mid-marshal and degraded to
     *       {@code request.abort()}; the carrier upgrade is strict.</li>
     *   <li>Within a carried tree the conversion is carrier-for-all: the
     *       declaration-driven schema cannot express a polymorphic
     *       {@code Throwable} slot, so mixed native/carrier trees are not
     *       representable.</li>
     *   <li>Belt-and-braces: if a native-eligible fault still fails to encode
     *       (a value-level failure, e.g. a polymorphic slot holding a
     *       non-{@code @AtomicSerial} value), it falls back to the carrier
     *       rather than aborting -- the DER stream buffers each object item
     *       whole before emitting, so a failed {@code writeObject} leaves the
     *       stream clean.</li>
     * </ul>
     */
    @Override
    protected void marshalThrow(Remote impl,
                                Method method,
                                Throwable throwable,
                                java.io.ObjectOutput out,
                                Collection context)
        throws IOException {
        if (impl == null || throwable == null || context == null) {
            throw new NullPointerException();
        }
        if (nativeDerEligible(throwable.getClass())) {
            try {
                out.writeObject(throwable);
                return;
            } catch (IOException | RuntimeException e) {
                logger.log(Level.FINE,
                        "native DER encode of @AtomicSerial fault {0} failed;"
                        + " falling back to the DerThrowableForm carrier: {1}",
                        new Object[]{throwable.getClass().getName(), e});
            }
        }
        out.writeObject(org.apache.river.api.io.DerThrowableForm.capture(throwable));
    }

    /** Fault-path logger. */
    private static final Logger logger =
            Logger.getLogger("net.jini.jeri.AtomicDerInvocationDispatcher");

    /**
     * Whether {@code cls} may travel natively as a top-level DER fault:
     * {@code @AtomicSerial} AND its declared serial form is DER-schema-typable.
     * Both checks are per-class deterministic ({@code generateChain} is
     * memoised, caching failures too).
     */
    private static boolean nativeDerEligible(Class<?> cls) {
        if (!cls.isAnnotationPresent(org.apache.river.api.io.AtomicSerial.class)) {
            return false;
        }
        try {
            au.net.zeus.jgdms.der.schema.SchemaGenerator.generateChain(cls);
            return true;
        } catch (au.net.zeus.jgdms.der.DerException e) {
            return false;
        }
    }

    // Visible in stack traces.
    @Override
    public void dispatch(Remote impl,
                         InboundRequest request,
                         Collection context) {
        super.dispatch(impl, request, context);
    }
}
