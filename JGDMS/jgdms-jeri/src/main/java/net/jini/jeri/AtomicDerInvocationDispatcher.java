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
        // MarshallingFormat.DER requirement at export + dispatch (STD-008 sec.18.3).
        super(methods, caps, serverConstraints, permissionClass, loader,
                net.jini.core.constraint.MarshallingFormat.DER.getFormat());
        this.limits = java.util.Objects.requireNonNull(limits, "limits");
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

    // Visible in stack traces.
    @Override
    public void dispatch(Remote impl,
                         InboundRequest request,
                         Collection context) {
        super.dispatch(impl, request, context);
    }
}
