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
 * <p>Pair with {@link DerInvocationHandler} via {@link DerILFactory}.
 *
 * <p>The streams implement {@link ObjectOutput}/{@link ObjectInput} directly;
 * return types are the base interfaces (not the {@code ObjectOutputStream}/
 * {@code ObjectInputStream} subclasses).
 *
 * @author peter
 * @since 4.0
 */
public class DerInvocationDispatcher extends BasicInvocationDispatcher {

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
    public DerInvocationDispatcher(Collection methods,
                                   ServerCapabilities caps,
                                   MethodConstraints serverConstraints,
                                   Class permissionClass,
                                   ClassLoader loader)
            throws ExportException {
        super(methods, caps, serverConstraints, permissionClass, loader);
    }

    /**
     * Returns a {@link DerMarshalInputStream} reading from the request input
     * stream of {@code request}.
     *
     * <p>Override return type is {@link ObjectInput} (the base interface).
     * Integrity and loader are ignored -- DER carries the schema.
     */
    @Override
    protected ObjectInput createMarshalInputStream(Object impl,
                                                   InboundRequest request,
                                                   boolean integrity,
                                                   Collection context)
            throws IOException {
        return new DerMarshalInputStream(request.getRequestInputStream());
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
        return new DerMarshalOutputStream(request.getResponseOutputStream());
    }

    // Visible in stack traces.
    @Override
    public void dispatch(Remote impl,
                         InboundRequest request,
                         Collection context) {
        super.dispatch(impl, request, context);
    }
}
