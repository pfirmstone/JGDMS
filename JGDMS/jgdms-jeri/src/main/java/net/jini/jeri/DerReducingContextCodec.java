/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
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
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.util.Collection;
import net.jini.core.constraint.MarshallingFormat;

/**
 * A {@link ReducingContextCodec} that produces STD-006 DER codec streams
 * ({@link DerMarshalOutputStream} / {@link DerMarshalInputStream}).  The input side
 * applies per-deployment {@link DerInputLimits} for denial-of-service defence.
 *
 * <p>Suitable for use as a {@link net.jini.config.Configuration}-constructed
 * argument to {@link AtomicDerILFactory}; both constructors are public.
 *
 * @since 3.2
 */
public final class DerReducingContextCodec implements ReducingContextCodec {

    private final DerInputLimits limits;

    /** Creates a factory using {@link DerInputLimits#DEFAULT}. */
    public DerReducingContextCodec() {
        this(DerInputLimits.DEFAULT);
    }

    /**
     * Creates a factory with the given input-stream DoS limits.
     *
     * @param limits the limits applied while decoding a side-band block; must not
     *               be {@code null}
     * @throws NullPointerException if {@code limits} is {@code null}
     */
    public DerReducingContextCodec(DerInputLimits limits) {
        if (limits == null) {
            throw new NullPointerException("limits");
        }
        this.limits = limits;
    }

    @Override
    public String formatName() {
        return MarshallingFormat.ATOMIC_DER.getFormat();
    }

    @Override
    public ObjectOutput createMarshalOutputStream(OutputStream out,
                                                  Collection context,
                                                  ClassLoader loader) throws IOException {
        return new DerMarshalOutputStream(out, context, loader);
    }

    @Override
    public ObjectInput createMarshalInputStream(InputStream in,
                                                ClassLoader loader,
                                                boolean integrity,
                                                Collection context) throws IOException {
        return new DerMarshalInputStream(
                in, new ResolutionContext(loader, integrity, loader), limits);
    }
}
