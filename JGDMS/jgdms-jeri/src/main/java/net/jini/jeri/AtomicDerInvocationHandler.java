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
import java.lang.reflect.Proxy;
import java.util.Collection;
import net.jini.core.constraint.MethodConstraints;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * An {@link InvocationHandler} that uses DER (JGDMS-STD-006) encoding for
 * the JERI call wire format (JGDMS-STD-008 sec.14, A0).
 *
 * <p>Drop-in replacement for {@link AtomicInvocationHandler}: swap the
 * {@link InvocationLayerFactory} to {@link AtomicDerILFactory} and every
 * {@code @AtomicSerial} object in the call graph is encoded in DER --
 * no service code change required.
 *
 * <p>DER has no compression and carries no codebase annotations. The streams
 * implement {@link ObjectOutput}/{@link ObjectInput} directly (not the
 * {@code ObjectOutputStream}/{@code ObjectInputStream} subclasses).
 *
 * @author peter
 * @since 4.0
 */
@AtomicSerial
public class AtomicDerInvocationHandler extends BasicInvocationHandler {

    private static final long serialVersionUID = 1L;

    // No new fields beyond BasicInvocationHandler.

    public static SerialForm[] serialForm() {
        return new SerialForm[0];
    }

    public static void serialize(PutArg arg, AtomicDerInvocationHandler h)
            throws IOException {
        arg.writeArgs();
    }

    /**
     * Deserialization constructor (AtomicSerial contract).
     */
    public AtomicDerInvocationHandler(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        super(arg);
    }

    /**
     * Constructs a handler with the given object endpoint and server
     * constraints.
     *
     * @param oe the object endpoint for the remote object
     * @param serverConstraints the server constraints, or {@code null}
     */
    public AtomicDerInvocationHandler(ObjectEndpoint oe,
                                MethodConstraints serverConstraints) {
        super(oe, serverConstraints);
    }

    /**
     * Copy constructor used when applying client constraints.
     *
     * @param other the existing handler
     * @param clientConstraints the client constraints to apply
     */
    public AtomicDerInvocationHandler(AtomicDerInvocationHandler other,
                                MethodConstraints clientConstraints) {
        super(other, clientConstraints);
    }

    /**
     * Returns a {@link DerMarshalOutputStream} writing to the request output
     * stream of {@code request}.
     *
     * <p>Override return type is {@link ObjectOutput} (the base interface)
     * because {@link DerMarshalOutputStream} implements the interface directly
     * and does not extend {@code ObjectOutputStream}.
     *
     * @throws NullPointerException if {@code proxy} or {@code method} is null
     */
    @Override
    protected ObjectOutput createMarshalOutputStream(Object proxy,
                                                     Method method,
                                                     OutboundRequest request,
                                                     Collection context)
            throws IOException {
        if (proxy == null || method == null) {
            throw new NullPointerException();
        }
        return new DerMarshalOutputStream(request.getRequestOutputStream());
    }

    /**
     * Returns a {@link DerMarshalInputStream} reading from the response input
     * stream of {@code request}.
     *
     * <p>Override return type is {@link ObjectInput} (the base interface).
     * Integrity and loader are ignored -- DER carries the schema, not codebase
     * annotations.
     *
     * @throws IllegalArgumentException if {@code proxy}'s invocation handler
     *         is not this handler
     */
    @Override
    protected ObjectInput createMarshalInputStream(Object proxy,
                                                   Method method,
                                                   OutboundRequest request,
                                                   boolean integrity,
                                                   Collection context)
            throws IOException {
        if (method == null) {
            throw new NullPointerException();
        }
        if (Proxy.getInvocationHandler(proxy) != this) {
            throw new IllegalArgumentException("not proxy for this");
        }
        return new DerMarshalInputStream(request.getResponseInputStream());
    }

    /**
     * DER handler equality is based on the object endpoint and server
     * constraints only (client constraints are excluded, as with
     * {@link AtomicInvocationHandler}).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtomicDerInvocationHandler)) return false;
        AtomicDerInvocationHandler other = (AtomicDerInvocationHandler) o;
        return org.apache.river.jeri.internal.runtime.Util.sameClassAndEquals(
                       getObjectEndpoint(), other.getObjectEndpoint())
               && org.apache.river.jeri.internal.runtime.Util.equals(
                       getServerConstraints(), other.getServerConstraints());
    }

    @Override
    public int hashCode() {
        ObjectEndpoint oe = getObjectEndpoint();
        return oe == null ? 0 : oe.hashCode();
    }

    // Visible in stack traces.
    @Override
    public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
        return super.invoke(proxy, method, args);
    }

    /**
     * This handler's codec produces the JGDMS-STD-006/DER wire format, so it satisfies a
     * {@link net.jini.core.constraint.MarshallingFormat#DER} requirement (STD-008 sec.18.3).
     */
    @Override
    protected String marshallingFormat() {
        return net.jini.core.constraint.MarshallingFormat.DER.getFormat();
    }
}
