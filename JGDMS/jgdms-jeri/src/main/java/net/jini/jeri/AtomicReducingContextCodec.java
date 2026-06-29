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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.util.Collection;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;

/**
 * A {@link ReducingContextCodec} that produces atomic Java-serialization (JOSS)
 * codec streams ({@link AtomicMarshalOutputStream} / {@link AtomicMarshalInputStream}),
 * the hardened atomic-validation streams.
 *
 * <p>Suitable for use as a {@link net.jini.config.Configuration}-constructed
 * argument to {@link AtomicILFactory}; the no-argument constructor is public.
 *
 * @since 3.2
 */
public final class AtomicReducingContextCodec implements ReducingContextCodec {

    /** Creates a JOSS marshal-stream factory. */
    public AtomicReducingContextCodec() {
    }

    @Override
    public String formatName() {
        return MarshalledInstance.FORMAT_JOSS;
    }

    @Override
    public ObjectOutput createMarshalOutputStream(OutputStream out,
                                                  Collection context,
                                                  ClassLoader loader) throws IOException {
        return new AtomicMarshalOutputStream(out, context);
    }

    @Override
    public ObjectInput createMarshalInputStream(InputStream in,
                                                ClassLoader loader,
                                                boolean integrity,
                                                Collection context) throws IOException {
        // readAnnotations = false: a RemoteContextCodec side-band block carries no
        // codebase annotations -- it is primitives plus value-semantic byte arrays.
        return AtomicMarshalInputStream.create(in, loader, integrity, loader, context, false);
    }
}
