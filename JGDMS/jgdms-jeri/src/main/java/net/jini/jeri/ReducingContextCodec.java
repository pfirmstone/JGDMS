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
import net.jini.core.constraint.MarshallingFormat;
import net.jini.io.MarshalledInstance;

/**
 * A {@link net.jini.config.Configuration}-injectable factory for the marshalling
 * streams used to encode and decode a JERI <em>side-band</em> payload -- presently
 * the transmitted remote-caller {@link java.security.AccessControlContext} (the
 * reducing codebase set; see {@code RemoteContextCodec}), carried as a single
 * length-delimited block and decoded in its own isolated codec stream.
 *
 * <p>The codec is identified by a self-describing {@code payloadFormat} string --
 * the same identifier carried by {@link MarshallingFormat} and
 * {@link MarshalledInstance} (STD-008 sec.18.3), e.g. {@link MarshalledInstance#FORMAT_JOSS}
 * (Java-only legacy serialization) or {@code "JGDMS-STD-006/ATOMIC-DER"} (the canonical,
 * language-neutral DER wire format).  Using the published string id -- rather than a
 * private ordinal -- lets a non-Java peer (e.g. a Rust JERI implementation) encode and
 * decode the side-band block from the same documented format registry.  Cross-language
 * interop therefore requires the DER format; JOSS is not portable.
 *
 * <p>The format used for a given proxy is the proxy's own marshalling format
 * ({@code BasicInvocationHandler.marshallingFormat()}): the reducing-context side-band
 * travels over the same connection to the same peer, so it shares the invocation
 * payload's format.  The <em>server dispatcher</em> is given a {@code ReducingContextCodec}
 * (so a deployment can supply DoS {@code DerInputLimits} for the untrusted <em>decode</em>
 * side via a {@code Configuration}); the <em>client handler</em> does not carry one -- it
 * <em>derives</em> its <em>encode</em> codec from its marshalling format via
 * {@link #forFormat(String)}, so the codec choice survives proxy serialization without an
 * extra wire field.
 *
 * <p>Implementations must be safe for use by concurrent threads.
 *
 * @since 3.2
 */
public interface ReducingContextCodec {

    /**
     * Returns the self-describing {@code payloadFormat} identifier of the wire format
     * this codec encodes and decodes -- the same string used by {@link MarshallingFormat}
     * and {@link MarshalledInstance} (e.g. {@link MarshalledInstance#FORMAT_JOSS} or
     * {@code "JGDMS-STD-006/ATOMIC-DER"}).  Must equal the marshalling format of the handler /
     * dispatcher it serves.
     *
     * @return the payload-format identifier; never {@code null}
     */
    String formatName();

    /**
     * Returns the built-in {@code ReducingContextCodec} for a {@code payloadFormat}
     * identifier.  Used by the client invocation handler to derive its <em>encode</em>
     * codec from the proxy's marshalling format, so the side-band block is encoded in the
     * format the server decodes.  The DER codec is returned with
     * {@link au.net.zeus.jgdms.der.DerInputLimits#DEFAULT default} limits, which is
     * sufficient for the encode side (DoS limits bound decoding only).
     *
     * @param format the payload-format identifier (e.g. {@link MarshalledInstance#FORMAT_JOSS}
     *               or {@code MarshallingFormat.ATOMIC_DER.getFormat()})
     * @return a codec for {@code format}
     * @throws IllegalArgumentException if {@code format} is not a recognised built-in format
     * @throws NullPointerException if {@code format} is {@code null}
     */
    static ReducingContextCodec forFormat(String format) {
        if (format == null) {
            throw new NullPointerException("format");
        }
        if (MarshalledInstance.FORMAT_JOSS.equals(format)) {
            return new AtomicReducingContextCodec();
        }
        if (MarshallingFormat.ATOMIC_DER.getFormat().equals(format)) {
            return new DerReducingContextCodec();
        }
        throw new IllegalArgumentException(
                "no reducing-context codec for payload format: " + format);
    }

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
