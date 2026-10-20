/*
 * Copyright (C) 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bench.serial;

import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.jeri.AtomicDerILFactory;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.InvocationLayerFactory;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;

/**
 * The two wire formats under comparison, each constructed exactly the way the
 * corresponding JERI invocation layer constructs it.
 *
 * <ul>
 *   <li>{@link #JOSS}: {@link AtomicMarshalOutputStream} /
 *       {@link AtomicMarshalInputStream}, as created by
 *       {@code AtomicInvocationHandler} and {@code AtomicInvocationDispatcher}
 *       (no compression, no codebase annotations).</li>
 *   <li>{@link #DER}: {@link DerMarshalOutputStream} /
 *       {@link DerMarshalInputStream}, as created by
 *       {@code AtomicDerInvocationHandler} and {@code AtomicDerInvocationDispatcher}
 *       (default {@link DerInputLimits}, no integrity verification).</li>
 * </ul>
 *
 * <p>A fresh stream is created per operation because that is what JERI does per
 * remote call: each request and each response is its own stream, so each call
 * pays the format's fixed cost (JOSS stream header and class descriptors, DER
 * schema) again. The benchmarks therefore measure what a call costs, not what
 * an amortised long-lived stream costs.
 */
public enum Format {

    /** Java Object Serialization Stream, read atomically. */
    JOSS {
        @Override
        public ObjectOutput output(OutputStream out, ClassLoader loader) throws IOException {
            return new AtomicMarshalOutputStream(out, loader, EMPTY_CONTEXT, false);
        }

        @Override
        public ObjectInput input(InputStream in, ClassLoader loader) throws IOException {
            return AtomicMarshalInputStream.create(in, loader, false, loader, EMPTY_CONTEXT, false);
        }

        @Override
        public InvocationLayerFactory ilFactory(ClassLoader loader, MethodConstraints constraints) {
            return new AtomicILFactory(constraints, null, loader);
        }
    },

    /** JGDMS-STD-006 canonical DER. */
    DER {
        @Override
        public ObjectOutput output(OutputStream out, ClassLoader loader) {
            return new DerMarshalOutputStream(out, EMPTY_CONTEXT, loader);
        }

        @Override
        public ObjectInput input(InputStream in, ClassLoader loader) throws IOException {
            return new DerMarshalInputStream(in,
                    new ResolutionContext(loader, false, loader), DerInputLimits.DEFAULT);
        }

        @Override
        public InvocationLayerFactory ilFactory(ClassLoader loader, MethodConstraints constraints) {
            return new AtomicDerILFactory(constraints, null, loader);
        }
    };

    private static final Collection<?> EMPTY_CONTEXT =
            Collections.unmodifiableCollection(Collections.emptyList());

    /** A marshal output stream over {@code out}, as the JERI handler would build it. */
    public abstract ObjectOutput output(OutputStream out, ClassLoader loader) throws IOException;

    /** A marshal input stream over {@code in}, as the JERI dispatcher would build it. */
    public abstract ObjectInput input(InputStream in, ClassLoader loader) throws IOException;

    /** The JERI invocation layer factory that uses this format. */
    public abstract InvocationLayerFactory ilFactory(ClassLoader loader, MethodConstraints constraints);

    /** The invocation layer factory with no server constraints. */
    public final InvocationLayerFactory ilFactory(ClassLoader loader) {
        return ilFactory(loader, null);
    }

    /** Encodes {@code obj} into a fresh stream and returns the bytes. */
    public byte[] encode(Object obj, ClassLoader loader) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(1024);
        ObjectOutput out = output(bos, loader);
        out.writeObject(obj);
        out.flush();
        out.close();
        return bos.toByteArray();
    }

    /** Decodes one object from {@code bytes} with a fresh stream. */
    public Object decode(byte[] bytes, ClassLoader loader) throws IOException, ClassNotFoundException {
        ObjectInput in = input(new java.io.ByteArrayInputStream(bytes), loader);
        Object o = in.readObject();
        in.close();
        return o;
    }
}
