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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Principal;
import net.jini.core.constraint.MethodConstraints;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;

/**
 * The single, shared definition of the wire-handoff payload field order
 * (task&nbsp;T4).  Both {@code SubProcessWireHandoffImpl} (client side) and
 * {@code SubProcessReconstructionServer} (subprocess side) encode/decode
 * through this class exclusively, so the two ends of the protocol cannot
 * silently drift apart (G1/G8: one canonical definition, not two
 * independently-hand-written mirrors).
 *
 * <p>All payload encoding uses {@link AtomicMarshalOutputStream} /
 * {@link AtomicMarshalInputStream} -- the same, already-hardened codec
 * {@code AtomicMarshalInputStream} carries the allocate-after-validate fix
 * for reference arrays / block data / long strings -- rather than a
 * hand-rolled decoder, so this new wire path inherits that hardening
 * instead of risking reintroducing the same class of bug.
 *
 * <h2>Per-field independent sub-streams (deliberate, not incidental)</h2>
 * Each logical field of a message is marshalled in its own,
 * <strong>completely independent</strong> {@code AtomicMarshalOutputStream}
 * (and decoded from its own independent {@code AtomicMarshalInputStream}),
 * length-prefixed inside the payload -- never multiple fields sharing one
 * stream's object graph. This mirrors an existing, deliberate JGDMS pattern
 * for exactly this hazard class (see the historical commit note on {@code
 * ProxySerializer}: "marshal a proxy separately from the stream, such that
 * it has no shared state with the stream"). It is not merely defensive
 * style here: adversarial testing while building this codec found a real,
 * reproducible defect in {@code AtomicMarshalInputStream.readNewArray} --
 * an {@code Externalizable} array element that is <em>not</em> the last
 * element in a reference array corrupted the shared stream's block-data
 * state, and the resulting {@code StreamCorruptedException} hit a
 * pre-existing null-{@code exceptions}-list bug in that method's own
 * catch block (a {@code NullPointerException} instead of a clean decode
 * error).
 *
 * <p><strong>Correction (2026-07-20 board review): per-field independent
 * streams do NOT "sidestep the defect entirely."</strong> An earlier
 * revision of this javadoc claimed that; it was wrong, and two board seats
 * independently disproved it by calling {@link #encodeInvokeRequest}/
 * {@link #decodeInvokeRequest} and {@link #encodeInvokeReplyResult}/
 * {@link #decodeInvokeReplyResult} directly with a 2-element array
 * containing a non-last {@code Externalizable} element and reproducing the
 * exact {@code NullPointerException}. Per-field isolation only protects
 * <em>distinct top-level fields</em> from sharing corrupted stream state
 * with each other; it does nothing for a <em>single</em> field whose own
 * value is itself a multi-element reference array -- exactly the shape of
 * {@code INVOKE_REQUEST.args} and {@code INVOKE_REPLY_RESULT}. The actual
 * fixes: (1) the root-cause null-guard landed directly in {@code
 * AtomicMarshalInputStream} (a separate, minimal jgdms-platform commit --
 * closes the {@code NullPointerException}, not the underlying stream-desync
 * itself: after the fix, this exact shape fails with a clean {@code
 * StreamCorruptedException} rather than round-tripping successfully or
 * crashing -- see {@code SubProcessWireHandoffEndToEndTest
 * #nonLastExternalizableArrayElement_failsCleanly_notWithRawNpe_throughFullStack}
 * for the verified, current behaviour); (2) {@link DecodeDepthGuard}, a
 * genuinely independent depth-nesting pre-check (unrelated to this specific
 * defect, added for Finding 2); (3) every {@code unmarshalOneField} caller
 * now wraps the call in {@code catch(Throwable)} and synthesises a clean
 * exception, so whatever this decoder's residual failure modes are, they
 * never propagate raw into calling application code.
 *
 * <p>Each field's length prefix is validated against
 * {@link WireFraming#MAX_PAYLOAD_LEN} before allocation, the same
 * allocate-after-validate discipline as the outer frame.
 *
 * <h2>{@code REQUEST} payload (client&rarr;subprocess)</h2>
 * 7 fields, in order:
 * <ol>
 *   <li>{@code String} {@code assertedPoolingKey} -- the client's own
 *       {@code IsolationPoolingKey.value()}; the subprocess re-derives its
 *       <em>own</em> key independently and fails closed on mismatch
 *       (defence in depth against a request replayed/misdirected onto the
 *       wrong subprocess).</li>
 *   <li>{@code String} {@code path} -- the codebase annotation string
 *       (diagnostic only: {@code resolve()} re-derives its own authoritative
 *       {@code path} from {@code bootstrapProxy.getClassAnnotation()} and
 *       never trusts this field as an input to reconstruction).</li>
 *   <li>{@code Boolean} {@code verifyCodebaseIntegrity}.</li>
 *   <li>{@link MarshalledInstance} {@code serviceProxy} (still-marshalled --
 *       the client never calls {@code .get()} on it; writing it only
 *       serialises its own opaque {@code byte[]} fields).</li>
 *   <li>{@link CodebaseAccessor} {@code bootstrapProxy} (a trusted,
 *       first-party JERI proxy type, not hosted mobile code; safe to move
 *       between trusted processes like any other remote reference).</li>
 *   <li>{@link MethodConstraints} {@code methodConstraints} (or
 *       {@code null}).</li>
 *   <li>{@code Principal[]} {@code serverPrincipals} (or {@code null}).</li>
 * </ol>
 *
 * <h2>{@code REPLY_OK} payload (subprocess&rarr;client)</h2>
 * 2 fields: {@code String[] interfaceNames}, {@code String hostedId}.
 *
 * <h2>{@code REPLY_ERROR} payload (subprocess&rarr;client)</h2>
 * 2 fields: {@code String category}, {@code String message}.
 * Deliberately never serialises the actual {@link Throwable}: reconstructing
 * an arbitrary exception object on the client would reopen a decode surface
 * this whole mechanism exists to close on the request side; the reply side
 * gets the same discipline.
 *
 * <h2>{@code INVOKE_REQUEST} payload (client&rarr;subprocess)</h2>
 * 3 fields: {@code String methodName}, {@code String[] parameterTypeNames},
 * {@code Object[] args} (nullable elements).
 *
 * <h2>{@code INVOKE_REPLY_RESULT} payload (subprocess&rarr;client)</h2>
 * 1 field: the result.
 *
 * <h2>{@code INVOKE_REPLY_EXCEPTION} payload (subprocess&rarr;client)</h2>
 * 2 fields: {@code String exceptionClassName} (name only, not resolved),
 * {@code String message}.
 *
 * @since 3.1.1
 */
final class WireHandoffCodec {

    private WireHandoffCodec() { }

    // ------------------------------------------------- per-field sub-stream I/O

    private static byte[] marshalOneField(Object value) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new AtomicMarshalOutputStream(bos, null);
        out.writeObject(value);
        out.flush();
        return bos.toByteArray();
    }

    private static Object unmarshalOneField(byte[] bytes, ClassLoader loader)
            throws IOException, ClassNotFoundException {
        // Board-required (2026-07-20 review): AtomicMarshalInputStream has no
        // recursion-depth ceiling of its own (unlike the DER Any/collection
        // codec) -- a deeply-nested-but-narrow payload (e.g. a chain of
        // single-element Object[] wrappers) drives a live
        // StackOverflowError deep in its recursive readObject/readNewArray/
        // readNewObject call chain, well within MAX_PAYLOAD_LEN. Reject a
        // confidently-parsed excessive-depth structure BEFORE attempting the
        // real, expensive/dangerous recursive decode. See DecodeDepthGuard's
        // class javadoc for exactly what this check does and does not cover
        // -- it is deliberately best-effort, not the sole safety boundary;
        // callers must still handle Throwable around unmarshalOneField.
        DecodeDepthGuard.bestEffortCheck(bytes);
        ObjectInputStream in = AtomicMarshalInputStream.create(
                new ByteArrayInputStream(bytes), loader, false, null, null, false);
        return in.readObject();
    }

    private static void writeLenPrefixed(ByteArrayOutputStream bos, byte[] field) {
        int len = field.length;
        bos.write((len >>> 24) & 0xff);
        bos.write((len >>> 16) & 0xff);
        bos.write((len >>> 8) & 0xff);
        bos.write(len & 0xff);
        bos.write(field, 0, field.length);
    }

    /** Simple bounded cursor over an in-memory byte[] for reading fields back. */
    private static final class Cursor {
        final byte[] buf;
        int pos;
        Cursor(byte[] buf) { this.buf = buf; }

        int readInt() throws IOException {
            if (pos + 4 > buf.length) {
                throw new IOException("Truncated wire-handoff envelope (length prefix)");
            }
            int v = ((buf[pos] & 0xff) << 24) | ((buf[pos + 1] & 0xff) << 16)
                  | ((buf[pos + 2] & 0xff) << 8) | (buf[pos + 3] & 0xff);
            pos += 4;
            return v;
        }

        byte[] readField() throws IOException {
            int len = readInt();
            // Allocate-after-validate: reject before allocating/copying.
            if (len < 0 || len > WireFraming.MAX_PAYLOAD_LEN) {
                throw new IOException(
                        "Wire-handoff envelope field declares an out-of-bounds"
                        + " length (" + len + "); refusing before allocation"
                        + " (fail-closed, denial of service guard).");
            }
            if (pos + len > buf.length) {
                throw new IOException("Truncated wire-handoff envelope (field body)");
            }
            byte[] field = java.util.Arrays.copyOfRange(buf, pos, pos + len);
            pos += len;
            return field;
        }
    }

    private static byte[] encodeFields(Object... fields) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (Object field : fields) {
            writeLenPrefixed(bos, marshalOneField(field));
        }
        return bos.toByteArray();
    }

    private static Object[] decodeFields(byte[] payload, ClassLoader loader, int expected)
            throws IOException, ClassNotFoundException {
        Cursor c = new Cursor(payload);
        Object[] out = new Object[expected];
        for (int i = 0; i < expected; i++) {
            out[i] = unmarshalOneField(c.readField(), loader);
        }
        return out;
    }

    // ------------------------------------------------------------ REQUEST

    static byte[] encodeRequest(String assertedPoolingKey,
                                String path,
                                boolean verifyCodebaseIntegrity,
                                MarshalledInstance serviceProxy,
                                CodebaseAccessor bootstrapProxy,
                                MethodConstraints methodConstraints,
                                Principal[] serverPrincipals)
            throws IOException {
        return encodeFields(
            assertedPoolingKey,
            path == null ? "" : path,
            Boolean.valueOf(verifyCodebaseIntegrity),
            serviceProxy,
            bootstrapProxy,
            methodConstraints,
            serverPrincipals);
    }

    /** Decoded {@code REQUEST} payload. */
    static final class DecodedRequest {
        final String assertedPoolingKey;
        final String path;
        final boolean verifyCodebaseIntegrity;
        final MarshalledInstance serviceProxy;
        final CodebaseAccessor bootstrapProxy;
        final MethodConstraints methodConstraints;
        final Principal[] serverPrincipals;

        DecodedRequest(String assertedPoolingKey, String path,
                      boolean verifyCodebaseIntegrity,
                      MarshalledInstance serviceProxy,
                      CodebaseAccessor bootstrapProxy,
                      MethodConstraints methodConstraints,
                      Principal[] serverPrincipals) {
            this.assertedPoolingKey = assertedPoolingKey;
            this.path = path;
            this.verifyCodebaseIntegrity = verifyCodebaseIntegrity;
            this.serviceProxy = serviceProxy;
            this.bootstrapProxy = bootstrapProxy;
            this.methodConstraints = methodConstraints;
            this.serverPrincipals = serverPrincipals;
        }
    }

    static DecodedRequest decodeRequest(byte[] payload, ClassLoader trustedLoader)
            throws IOException, ClassNotFoundException {
        Object[] f = decodeFields(payload, trustedLoader, 7);
        String path = (String) f[1];
        return new DecodedRequest((String) f[0],
                (path == null || path.isEmpty()) ? null : path,
                Boolean.TRUE.equals(f[2]),
                (MarshalledInstance) f[3],
                (CodebaseAccessor) f[4],
                (MethodConstraints) f[5],
                (Principal[]) f[6]);
    }

    // ----------------------------------------------------------- REPLY_OK

    static byte[] encodeReplyOk(String[] interfaceNames, String hostedId)
            throws IOException {
        return encodeFields((Object) interfaceNames, hostedId);
    }

    static final class DecodedReplyOk {
        final String[] interfaceNames;
        final String hostedId;

        DecodedReplyOk(String[] interfaceNames, String hostedId) {
            this.interfaceNames = interfaceNames;
            this.hostedId = hostedId;
        }
    }

    static DecodedReplyOk decodeReplyOk(byte[] payload, ClassLoader trustedLoader)
            throws IOException, ClassNotFoundException {
        Object[] f = decodeFields(payload, trustedLoader, 2);
        return new DecodedReplyOk((String[]) f[0], (String) f[1]);
    }

    // -------------------------------------------------------- REPLY_ERROR

    static byte[] encodeReplyError(String category, String message)
            throws IOException {
        return encodeFields(category, message == null ? "" : message);
    }

    static final class DecodedReplyError {
        final String category;
        final String message;

        DecodedReplyError(String category, String message) {
            this.category = category;
            this.message = message;
        }
    }

    static DecodedReplyError decodeReplyError(byte[] payload,
                                              ClassLoader trustedLoader)
            throws IOException, ClassNotFoundException {
        Object[] f = decodeFields(payload, trustedLoader, 2);
        return new DecodedReplyError((String) f[0], (String) f[1]);
    }

    // ---------------------------------------------------------- INVOKE_*

    static byte[] encodeInvokeRequest(String methodName,
                                      String[] parameterTypeNames,
                                      Object[] args) throws IOException {
        return encodeFields(methodName, parameterTypeNames, args);
    }

    static final class DecodedInvokeRequest {
        final String methodName;
        final String[] parameterTypeNames;
        final Object[] args;

        DecodedInvokeRequest(String methodName, String[] parameterTypeNames,
                             Object[] args) {
            this.methodName = methodName;
            this.parameterTypeNames = parameterTypeNames;
            this.args = args;
        }
    }

    static DecodedInvokeRequest decodeInvokeRequest(byte[] payload,
                                                    ClassLoader loader)
            throws IOException, ClassNotFoundException {
        Object[] f = decodeFields(payload, loader, 3);
        return new DecodedInvokeRequest((String) f[0], (String[]) f[1], (Object[]) f[2]);
    }

    static byte[] encodeInvokeReplyResult(Object result) throws IOException {
        return encodeFields(result);
    }

    static Object decodeInvokeReplyResult(byte[] payload, ClassLoader loader)
            throws IOException, ClassNotFoundException {
        Object[] f = decodeFields(payload, loader, 1);
        return f[0];
    }

    static byte[] encodeInvokeReplyException(String exceptionClassName,
                                             String message)
            throws IOException {
        return encodeFields(
                exceptionClassName == null ? "" : exceptionClassName,
                message == null ? "" : message);
    }

    static DecodedReplyError decodeInvokeReplyException(byte[] payload,
                                                         ClassLoader trustedLoader)
            throws IOException, ClassNotFoundException {
        // Same shape as a REPLY_ERROR (category/message pair); reused rather
        // than duplicated.
        Object[] f = decodeFields(payload, trustedLoader, 2);
        return new DecodedReplyError((String) f[0], (String) f[1]);
    }
}
