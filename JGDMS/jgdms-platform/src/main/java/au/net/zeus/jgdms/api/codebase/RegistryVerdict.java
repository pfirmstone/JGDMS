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
package au.net.zeus.jgdms.api.codebase;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Valid;
import org.apache.river.api.net.Uri;

/**
 * An immutable, serializable authoritative verdict produced by a
 * {@link VerdictRegistry} for a set of codebase URLs.
 *
 * <p>A {@code RegistryVerdict} is the <em>only</em> artefact that clients
 * trust.  Clients obtain it from a {@link VerdictRegistry} whose identity
 * they have verified via Jini security (Integrity + ServerAuthentication
 * constraints against the registry's certificate).  Clients do <em>not</em>
 * need to know about any individual {@link BytecodeAnalysisEngine}.
 *
 * <p>A {@code RegistryVerdict} carries:
 * <ul>
 *   <li>the ordered set of codebase URLs that were evaluated,</li>
 *   <li>the {@link VerdictType} determined by the registry's quorum
 *       policy,</li>
 *   <li>the UTC timestamp (milliseconds since the epoch) at which the
 *       registry issued this verdict,</li>
 *   <li>the DER-encoded digital signature produced with the registry's
 *       private identity key over the canonical serialized form of the
 *       above fields.</li>
 * </ul>
 *
 * <p><strong>Serialization safety.</strong> All fields are validated atomically
 * before the object is constructed, using the {@link AtomicSerial} protocol.
 * The class is {@code final}.
 *
 * @see VerdictRegistry
 * @see BytecodeAnalysisEngine
 * @see JarAnalysisReport
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
@AtomicSerial
public final class RegistryVerdict implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String CODEBASE_URLS = "codebaseUrls";
    private static final String VERDICT        = "verdict";
    private static final String TIMESTAMP      = "timestamp";
    private static final String SIGNATURE      = "signature";

    // serialPersistentFields is INDEPENDENT of serialForm() (dual-path JOSS keep, STD-008 sec9.1)
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField(CODEBASE_URLS, String[].class),
        new ObjectStreamField(VERDICT,       VerdictType.class),
        new ObjectStreamField(TIMESTAMP,     Long.TYPE),
        new ObjectStreamField(SIGNATURE,     byte[].class)
    };

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm(CODEBASE_URLS, String[].class),
            new SerialForm(VERDICT,       VerdictType.class),
            new SerialForm(TIMESTAMP,     Long.TYPE),
            new SerialForm(SIGNATURE,     byte[].class)
        };
    }

    public static void serialize(PutArg arg, RegistryVerdict rv) throws IOException {
        arg.put(CODEBASE_URLS, rv.codebaseUrls.clone());
        arg.put(VERDICT,       rv.verdict);
        arg.put(TIMESTAMP,     rv.timestamp);
        arg.put(SIGNATURE,     rv.signature.clone());
        arg.writeArgs();
    }

    // -------------------------------------------------------------------------
    // Invariant check called before construction
    // -------------------------------------------------------------------------

    private static boolean check(GetArg arg) throws IOException, ClassNotFoundException {
        String[] urls = (String[]) arg.get(CODEBASE_URLS, null);
        if (urls == null || urls.length == 0)
            throw new InvalidObjectException("codebaseUrls must not be null or empty");
        Valid.nullElement(urls, "codebaseUrls must not contain null elements");
        for (int i = 0; i < urls.length; i++) {
            try {
                new Uri(urls[i]);
            } catch (URISyntaxException e) {
                throw new InvalidObjectException(
                        "codebaseUrls[" + i + "] is not a valid RFC3986 URI: " + e.getMessage());
            }
        }
        if (arg.get(VERDICT, null, VerdictType.class) == null)
            throw new InvalidObjectException("verdict must not be null");
        long ts = arg.get(TIMESTAMP, 0L);
        if (ts <= 0)
            throw new InvalidObjectException("timestamp must be positive");
        byte[] sig = (byte[]) arg.get(SIGNATURE, null);
        if (sig == null || sig.length == 0)
            throw new InvalidObjectException("signature must not be null or empty");
        return true;
    }

    /**
     * The ordered set of codebase URLs that were evaluated, stored as RFC3986
     * URI strings for safe serialization.
     *
     * @serial
     */
    private final String[] codebaseUrls;

    /**
     * Runtime {@link Uri} representation of {@link #codebaseUrls}, populated
     * by the constructors.  Not serialized.  Callers that need a
     * {@link java.net.URL} for making a network connection should call
     * {@link Uri#toURL()} on the individual elements.
     */
    private final transient Uri[] codebaseUrlCache;

    /**
     * The verdict determined by the registry's quorum policy.
     *
     * @serial
     */
    private final VerdictType verdict;

    /**
     * UTC time (milliseconds since 1970-01-01T00:00:00Z) at which the
     * registry issued this verdict.
     *
     * @serial
     */
    private final long timestamp;

    /**
     * DER-encoded signature produced by the registry's identity key over
     * the canonical serialized form of {@link #codebaseUrls},
     * {@link #verdict}, and {@link #timestamp}.
     *
     * @serial
     */
    private final byte[] signature;

    /**
     * {@link AtomicSerial} deserialization constructor.  Invariants are
     * checked by {@link #check(GetArg)} before any field is assigned.
     */
    public RegistryVerdict(GetArg arg) throws IOException, ClassNotFoundException {
        this(arg, check(arg));
    }

    private RegistryVerdict(GetArg arg, boolean checked) throws IOException, ClassNotFoundException {
        codebaseUrls     = ((String[]) arg.get(CODEBASE_URLS, null)).clone();
        codebaseUrlCache = stringsToUris(codebaseUrls);
        verdict          = arg.get(VERDICT, null, VerdictType.class);
        timestamp        = arg.get(TIMESTAMP, 0L);
        signature        = ((byte[]) arg.get(SIGNATURE, null)).clone();
    }

    /**
     * Constructs a new {@code RegistryVerdict}.
     *
     * @param codebaseUrls the ordered set of RFC3986-normalised codebase URIs
     *        that were evaluated; must be non-null and non-empty
     * @param verdict      the verdict determined by the quorum policy;
     *        must be non-null
     * @param timestamp    UTC milliseconds since the epoch; must be positive
     * @param signature    DER-encoded registry identity signature; must be
     *        non-null and non-empty
     * @throws IllegalArgumentException if any argument fails a precondition
     * @throws NullPointerException     if any argument is {@code null}
     */
    public RegistryVerdict(Uri[] codebaseUrls,
                            VerdictType verdict,
                            long timestamp,
                            byte[] signature) {
        if (codebaseUrls == null) throw new NullPointerException("codebaseUrls");
        if (codebaseUrls.length == 0) throw new IllegalArgumentException("codebaseUrls must not be empty");
        for (int i = 0; i < codebaseUrls.length; i++) {
            if (codebaseUrls[i] == null)
                throw new NullPointerException("codebaseUrls[" + i + "]");
        }
        if (verdict == null) throw new NullPointerException("verdict");
        if (timestamp <= 0) throw new IllegalArgumentException("timestamp must be positive");
        if (signature == null) throw new NullPointerException("signature");
        if (signature.length == 0) throw new IllegalArgumentException("signature must not be empty");

        this.codebaseUrls     = urisToStrings(codebaseUrls);
        this.codebaseUrlCache = codebaseUrls.clone();
        this.verdict          = verdict;
        this.timestamp        = timestamp;
        this.signature        = signature.clone();
    }

    /**
     * Returns an unmodifiable view of the RFC3986-normalised codebase URIs
     * that were evaluated.  Callers that need a {@link java.net.URL} for
     * making a network connection should call {@link Uri#toURL()} on the
     * individual elements.
     *
     * @return an ordered, unmodifiable set of codebase URIs
     */
    public Set<Uri> getCodebaseUrls() {
        Set<Uri> result = new LinkedHashSet<Uri>(codebaseUrlCache.length * 2);
        for (Uri uri : codebaseUrlCache) {
            result.add(uri);
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns the verdict determined by the registry's quorum policy.
     *
     * @return the verdict; never {@code null}
     */
    public VerdictType getVerdict() {
        return verdict;
    }

    /**
     * Returns the UTC timestamp (milliseconds since the epoch) at which the
     * registry issued this verdict.
     *
     * @return a positive long
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns a copy of the DER-encoded registry identity signature.
     *
     * @return a non-empty byte array; never {@code null}
     */
    public byte[] getSignature() {
        return signature.clone();
    }

    @Override
    public String toString() {
        return "RegistryVerdict{verdict=" + verdict
                + ", timestamp=" + timestamp
                + ", urls=" + Arrays.toString(codebaseUrls) + '}';
    }

    /**
     * Verifies the inline {@link #signature} of this verdict against a
     * known-good public identity key.
     *
     * <p>This is the <em>forge-proof</em> trust anchor for a
     * {@code RegistryVerdict}: unlike relying on the authenticity of the
     * transport channel that delivered the verdict, a caller that holds the
     * registry's genuine public key can prove, purely from the object's own
     * bytes, that the verdict was issued by the registry that owns the
     * corresponding private key and has not been altered.
     *
     * <p>The canonical bytes reconstructed here are <strong>identical</strong>
     * to those signed by the registry when it issues a verdict: for each
     * codebase URI in lexicographic order, a 4-byte big-endian UTF-8 byte
     * length followed by those bytes, then the 4-byte big-endian
     * {@link VerdictType#ordinal()}, then the 8-byte big-endian
     * {@link #getTimestamp() timestamp}.  The URIs are sorted here so that the
     * verification is independent of the iteration order in which they happen
     * to be stored.
     *
     * <p>This method is side-effect free and never throws on a cryptographic
     * mismatch — a bad signature, an unknown algorithm, or an incompatible key
     * all yield {@code false} (fail-closed).  Callers must treat {@code false}
     * as "do not trust this verdict".
     *
     * @param key          the registry's known-good public identity key;
     *                     must be non-null
     * @param sigAlgorithm the JCA standard signature-algorithm name the
     *                     registry uses to sign verdicts (e.g.
     *                     {@code "SHA256withRSA"}); must be non-null and
     *                     non-empty
     * @return {@code true} if and only if the inline signature verifies against
     *         {@code key} using {@code sigAlgorithm}
     * @throws NullPointerException     if {@code key} or {@code sigAlgorithm}
     *                                  is {@code null}
     * @throws IllegalArgumentException if {@code sigAlgorithm} is empty
     */
    public boolean verifySignature(PublicKey key, String sigAlgorithm) {
        if (key == null) throw new NullPointerException("key");
        if (sigAlgorithm == null) throw new NullPointerException("sigAlgorithm");
        if (sigAlgorithm.isEmpty())
            throw new IllegalArgumentException("sigAlgorithm must not be empty");
        try {
            Uri[] sorted = codebaseUrlCache.clone();
            Arrays.sort(sorted, new Comparator<Uri>() {
                @Override
                public int compare(Uri a, Uri b) {
                    return a.toString().compareTo(b.toString());
                }
            });
            byte[] canonical = canonicalBytes(sorted, verdict, timestamp);
            Signature sig = Signature.getInstance(sigAlgorithm);
            sig.initVerify(key);
            sig.update(canonical);
            return sig.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException
                | SignatureException | IOException e) {
            // Fail-closed: any cryptographic failure means "not verified".
            return false;
        }
    }

    /**
     * Produces the canonical bytes that the registry signs for a
     * {@code RegistryVerdict}.  This format is authoritative and MUST stay
     * byte-for-byte identical to the signing code in the verdict-registry
     * service ({@code VerdictRegistryImpl.canonicalBytesForRegistryVerdict})
     * and to the replica verifier
     * ({@code ReadReplicaVerdictRegistry.canonicalBytesForRegistryVerdict}).
     *
     * <p>Format: for each URI in the supplied (already-sorted) order, a 4-byte
     * big-endian UTF-8 byte length followed by those bytes; then the 4-byte
     * big-endian verdict ordinal; then the 8-byte big-endian timestamp.
     */
    private static byte[] canonicalBytes(Uri[] sortedUrls,
                                         VerdictType type,
                                         long timestamp) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream      dos  = new DataOutputStream(baos);
        for (Uri uri : sortedUrls) {
            byte[] b = uri.toString().getBytes(StandardCharsets.UTF_8);
            dos.writeInt(b.length);
            dos.write(b);
        }
        dos.writeInt(type.ordinal());
        dos.writeLong(timestamp);
        dos.flush();
        return baos.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a {@link Uri} array to a String array by calling
     * {@link Uri#toString()} on each element.
     */
    private static String[] urisToStrings(Uri[] uris) {
        String[] result = new String[uris.length];
        for (int i = 0; i < uris.length; i++) {
            result[i] = uris[i].toString();
        }
        return result;
    }

    /**
     * Converts a String array of RFC3986 URIs to a {@link Uri} array.
     * Called after {@link #check(GetArg)} has already validated the strings.
     */
    private static Uri[] stringsToUris(String[] urls) throws IOException {
        Uri[] result = new Uri[urls.length];
        for (int i = 0; i < urls.length; i++) {
            try {
                result[i] = new Uri(urls[i]);
            } catch (URISyntaxException e) {
                // Should not happen: check() already validated these strings.
                throw new InvalidObjectException(
                        "codebaseUrls[" + i + "] could not be parsed as URI: "
                        + e.getMessage());
            }
        }
        return result;
    }
}
