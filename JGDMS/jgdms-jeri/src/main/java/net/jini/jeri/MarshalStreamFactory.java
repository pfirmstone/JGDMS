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

/**
 * A {@link net.jini.config.Configuration}-injectable factory for the marshalling
 * streams used to encode and decode a JERI <em>side-band</em> payload -- presently
 * the transmitted remote-caller {@link java.security.AccessControlContext} (the
 * reducing codebase set; see {@code RemoteContextCodec}), carried as a single
 * length-delimited block and decoded in its own isolated codec stream.
 *
 * <p>An invocation-layer factory accepts a {@code MarshalStreamFactory} as a
 * constructor argument -- defaulting to the codec matching its wire format -- and
 * threads it to the invocation handler and dispatcher, which use it to build the
 * codec stream over the block.  Because the factory is a plain constructed object,
 * a deployment selects or supplies one through a {@code Configuration}, which can
 * call constructors and static methods, for example:
 * <pre>
 *   ilf = new net.jini.jeri.AtomicDerILFactory(
 *             null, Svc.class,
 *             new net.jini.jeri.DerMarshalStreamFactory(myLimits));
 * </pre>
 * A new stream format is therefore a new {@code MarshalStreamFactory}
 * implementation -- it requires no new invocation-layer-factory, handler, or
 * dispatcher subclasses.
 *
 * <p>Implementations must be safe for use by concurrent threads.
 *
 * @since 3.2
 */
public interface MarshalStreamFactory {

    /**
     * Returns an {@link ObjectOutput} that writes a side-band payload to
     * {@code out}.
     *
     * @param out     the underlying byte sink (typically a
     *                {@link java.io.ByteArrayOutputStream})
     * @param context the marshalling context (e.g. constraints, codebase policy);
     *                may be empty but not {@code null}
     * @param loader  the stream/codebase loader; may be {@code null}
     * @return an {@code ObjectOutput} over {@code out}
     * @throws IOException if the stream cannot be created
     */
    ObjectOutput createMarshalOutputStream(OutputStream out,
                                           Collection context,
                                           ClassLoader loader) throws IOException;

    /**
     * Returns an {@link ObjectInput} that reads a side-band payload from
     * {@code in}.  The caller has already bounded the number of bytes available in
     * {@code in} (the block is length-delimited on the wire), but the
     * implementation should still apply its own DoS limits.
     *
     * @param in        the underlying byte source (the already length-bounded block)
     * @param loader    the stream/codebase loader; may be {@code null}
     * @param integrity whether codebase-integrity verification is required
     * @param context   the marshalling context; may be empty but not {@code null}
     * @return an {@code ObjectInput} over {@code in}
     * @throws IOException if the stream cannot be created
     */
    ObjectInput createMarshalInputStream(InputStream in,
                                         ClassLoader loader,
                                         boolean integrity,
                                         Collection context) throws IOException;
}
