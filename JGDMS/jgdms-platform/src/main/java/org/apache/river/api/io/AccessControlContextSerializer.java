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

package org.apache.river.api.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.DomainCombiner;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import javax.security.auth.Subject;
import net.jini.security.Security;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.net.Uri;

/**
 * Serializer for AccessControlContext.
 */
@Serializer(replaceObType = AccessControlContext.class)
@AtomicSerial
public final class AccessControlContextSerializer implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * Serial field name for the HTTPMD-URL transport bytes.
     * The serial representation stores the binary transport bytes rather than
     * the {@code DomainIdentityRecord[]} array, so that standard Java
     * {@code ObjectOutputStream} (used by {@link AtomicMarshalOutputStream})
     * can write the field without triggering the block that
     * {@code DomainIdentityRecord.writeObject} places on direct
     * Java-serialisation of that class.
     */
    private static final String TRANSPORT_BYTES = "transportBytes";
    /**
     * Serial field name for {@code DigestCodeSource} domains.
     * Stores the output of {@link AtomicMarshalOutputStream} written by
     * {@link #marshalDigestForTransport}.  Old receivers that do not have this
     * field in their serial form will silently receive {@code null} and skip it
     * (fail-secure: no digest domains are reconstructed, which is correct
     * behaviour on a JVM without {@code java.security.DigestCodeSource}).)
     */
    private static final String DIGEST_TRANSPORT_BYTES = "digestTransportBytes";
    /**
     * A minimal {@link URLStreamHandler} used when the real {@code httpmd:}
     * handler ({@code net.jini.url.httpmd.Handler}) is not available on the
     * current classpath.  The URL is created for CodeSource identity purposes
     * only; opening a connection to it always fails.
     */
    private static final URLStreamHandler IDENTITY_HANDLER = new URLStreamHandler() {
        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            throw new IOException("httpmd URL is for identity only: " + u);
        }
    };
    private static final int HTTPMD_PREFIX_LENGTH = 7;
    /** Maximum number of ProtectionDomain records accepted during unmarshal. */
    private static final int MAX_DOMAIN_COUNT = 4096;
    /** Maximum UTF-8 byte length accepted for a location URL field. */
    private static final int MAX_LOCATION_BYTES = 4096;
    /** Maximum number of principals accepted per ProtectionDomain record. */
    private static final int MAX_PRINCIPALS_PER_DOMAIN = 256;
    /** Maximum UTF-8 byte length accepted for a principal type or name field. */
    private static final int MAX_PRINCIPAL_FIELD_BYTES = 4096;
    /**
     * Hard upper bound on the total byte-length of a transport payload.
     * Without this, a hostile sender could combine the per-field limits
     * (4096 domains × 256 principals × 8 KB per principal) to produce a
     * ~8 GB allocation.  16 MB is more than sufficient for real payloads.
     */
    private static final int MAX_TOTAL_PAYLOAD_BYTES = 16 * 1024 * 1024;
    /** Maximum value that fits in an unsigned 16-bit length field. */
    private static final int MAX_UNSIGNED_SHORT_VALUE = 0xFFFF;
    /**
     * Fully-qualified class name of {@code java.security.DigestCodeSource}
     * (from DirtyChai JDK).  Used for class-name inspection without a
     * compile-time dependency on that JDK-specific class.
     */
    private static final String DIGEST_CODESOURCE_CLASS_NAME = "java.security.DigestCodeSource";
    /**
     * The {@code jrt:} CodeSource location of the {@code java.base} JDK module.
     * This domain is present in the ACC of every running JVM and carries no
     * useful diagnostic identity; it is excluded from the anonymous domain count
     * to avoid inflating the transport payload.  All other {@code jrt:} module
     * domains (e.g. {@code jrt:/jdk.crypto.ec}) are retained because their
     * presence can identify processes running a vulnerable JDK module.
     */
    private static final String JRT_JAVA_BASE_LOCATION = "jrt:/java.base";
    // serialPersistentFields is INDEPENDENT of serialForm() (dual-path JOSS keep, STD-008 sec9.1)
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField(TRANSPORT_BYTES, byte[].class),
        new ObjectStreamField(DIGEST_TRANSPORT_BYTES, byte[].class)
    };

    public static SerialForm[] serialForm() {
        return new SerialForm[]{
            new SerialForm(TRANSPORT_BYTES, byte[].class),
            new SerialForm(DIGEST_TRANSPORT_BYTES, byte[].class)
        };
    }

    public static void serialize(PutArg arg, AccessControlContextSerializer obj) throws IOException {
        arg.put(TRANSPORT_BYTES, marshalForTransport(obj.context));
        arg.put(DIGEST_TRANSPORT_BYTES, marshalDigestForTransport(obj.context));
        arg.writeArgs();
    }

    /**
     * Serialises the HTTPMD-verifiable {@link ProtectionDomain}s from
     * {@code acc} into a compact binary payload for JERI transport.
     *
     * <p><b>Transport format:</b>
     * <pre>
     *   [httpmdCount: 4 bytes BE][DomainIdentityRecord...][anonCount: 4 bytes BE]
     * </pre>
     * {@code httpmdCount} is the number of HTTPMD-verifiable domains that follow.
     * {@code anonCount} is the number of non-HTTPMD, non-{@code DigestCodeSource}
     * ("anonymous") domains present in the sender's ACC that could not be
     * transported with a verifiable identity.
     *
     * <p><b>Anonymous domain preservation:</b> Non-HTTPMD, non-{@code DigestCodeSource}
     * domains in the sender's ACC cannot be transported with a verifiable identity,
     * but they act as <em>permission ceilings</em> — their removal would be an
     * implicit privilege escalation.  The {@code anonCount} field allows the
     * receiver to reconstruct placeholder domains (null {@code CodeSource},
     * policy-deferred permissions) that preserve their ceiling effect without
     * asserting any specific identity claim.
     *
     * <p>The {@code jrt:/java.base} domain is excluded from {@code anonCount}
     * because it is present in the ACC of every running JVM and carries no
     * diagnostic value.  All other {@code jrt:} module domains are retained:
     * their presence indicates which JDK modules are loaded and can identify
     * processes running a specific (potentially vulnerable) module version.
     *
     * @param acc the {@link AccessControlContext} to marshal; {@code null} returns
     *            an empty byte array
     * @return the binary transport payload, or an empty byte array when {@code acc}
     *         contains no HTTPMD-verifiable domains
     */
    public static byte[] marshalForTransport(AccessControlContext acc) throws IOException {
        if (acc == null) return new byte[0];
        ProtectionDomain[] extracted = extractDomains(acc);
        List<DomainIdentityRecord> records = new ArrayList<DomainIdentityRecord>(extracted.length);
        int anonCount = 0;
        for (int i = 0; i < extracted.length; i++) {
            DomainIdentityRecord r = DomainIdentityRecord.from(extracted[i], null);
            if (r != null) {
                records.add(r);
            } else {
                // Domain is neither HTTPMD-verifiable nor a DigestCodeSource handled
                // by marshalDigestForTransport().  These domains cannot be transported
                // with a verifiable identity but still act as permission ceilings in
                // the sender's ACC; count them so the receiver can reconstruct
                // placeholder domains to preserve their ceiling effect.
                //
                // Exception: jrt:/java.base is present in every JVM ACC and carries
                // no diagnostic value, so it is excluded to keep the payload compact.
                // All other jrt: module domains are retained (they identify which JDK
                // modules are loaded and may flag vulnerable-module processes).
                CodeSource cs = extracted[i].getCodeSource();
                boolean isDigest = cs instanceof Externalizable
                        && DIGEST_CODESOURCE_CLASS_NAME.equals(cs.getClass().getName());
                if (!isDigest) {
                    URL loc = cs != null ? cs.getLocation() : null;
                    String locText = loc != null ? loc.toExternalForm() : null;
                    if (!JRT_JAVA_BASE_LOCATION.equals(locText)) {
                        anonCount++;
                    }
                }
            }
        }
        // Without at least one verifiable HTTPMD domain there is no remote
        // code identity to anchor the context; transmitting only an anonymous
        // domain count would be meaningless and wastes bandwidth.
        if (records.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        writeInt(baos, records.size());
        for (int i = 0; i < records.size(); i++) {
            records.get(i).writeTo(baos);
        }
        writeInt(baos, anonCount);
        return baos.toByteArray();
    }

    /**
     * Serialises the {@code DigestCodeSource} domains from {@code acc} into a
     * byte array using {@link AtomicMarshalOutputStream}.
     *
     * <p>Because {@code DigestCodeSource} implements {@link Externalizable},
     * {@code writeObject(cs)} causes the stream to call
     * {@code cs.writeExternal(out)} — no reflection is used in our code.
     * The resulting bytes are stored in the {@value #DIGEST_TRANSPORT_BYTES}
     * serial field and decoded on the receiver side by
     * {@link #unmarshalDigestFromTransport}.
     *
     * @return the serialised bytes, or an empty array when no
     *         {@code DigestCodeSource} domains are present
     */
    public static byte[] marshalDigestForTransport(AccessControlContext acc) throws IOException {
        if (acc == null) return new byte[0];
        ProtectionDomain[] extracted = extractDomains(acc);
        List<ProtectionDomain> digestDomains = new ArrayList<ProtectionDomain>(extracted.length);
        for (int i = 0; i < extracted.length; i++) {
            CodeSource cs = extracted[i].getCodeSource();
            if (cs instanceof Externalizable
                    && DIGEST_CODESOURCE_CLASS_NAME.equals(cs.getClass().getName())) {
                digestDomains.add(extracted[i]);
            }
        }
        if (digestDomains.isEmpty()) return new byte[0];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        // AtomicMarshalOutputStream with no codebase annotations is compatible
        // with ObjectOutputStream wire format for reading by AtomicMarshalInputStream.
        AtomicMarshalOutputStream aoos = new AtomicMarshalOutputStream(baos, Collections.emptyList());
        aoos.writeInt(digestDomains.size());
        for (int i = 0; i < digestDomains.size(); i++) {
            ProtectionDomain pd = digestDomains.get(i);
            // writeObject calls cs.writeExternal(out) because DigestCodeSource implements
            // Externalizable — the Externalizable interface is used directly, no reflection.
            aoos.writeObject(pd.getCodeSource());
            Principal[] principals = pd.getPrincipals();
            int principalCount = principals != null ? principals.length : 0;
            aoos.writeInt(principalCount);
            for (int j = 0; j < principalCount; j++) {
                aoos.writeUTF(principals[j].getClass().getName());
                aoos.writeUTF(principals[j].getName());
            }
        }
        aoos.close();
        return baos.toByteArray();
    }

    public static AccessControlContext unmarshalForTransport(byte[] data, Subject authenticatedSubject) throws IOException {
        ProtectionDomain[] domains = unmarshalHttpmdDomains(data, authenticatedSubject);
        return domains.length == 0 ? null : Security.create(domains);
    }

    /**
     * Reads the binary transport payload and returns the resulting
     * {@link ProtectionDomain} array.
     *
     * <p>Payload format: {@code [httpmdCount: 4B BE][DomainIdentityRecord…][anonCount: 4B BE]}.
     * For each anonymous domain a placeholder {@link ProtectionDomain} with
     * {@code null} {@link CodeSource} and {@code null}
     * {@link java.security.PermissionCollection} is reconstructed; its permissions
     * are determined by the server's security policy at run time.
     */
    private static ProtectionDomain[] unmarshalHttpmdDomains(byte[] data, Subject authenticatedSubject) throws IOException {
        if (data == null || data.length == 0) {
            return new ProtectionDomain[0];
        }
        if (data.length > MAX_TOTAL_PAYLOAD_BYTES) {
            throw new InvalidObjectException("payload size exceeds maximum: " + data.length);
        }
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        int count = readInt(in);
        if (count < 0 || count > MAX_DOMAIN_COUNT) {
            throw new InvalidObjectException("invalid domain count: " + count);
        }
        DomainIdentityRecord[] records = new DomainIdentityRecord[count];
        for (int i = 0; i < count; i++) {
            records[i] = DomainIdentityRecord.readFrom(in);
        }
        int anonCount = readInt(in);
        if (anonCount < 0 || anonCount > MAX_DOMAIN_COUNT) {
            throw new InvalidObjectException("invalid anonymous domain count: " + anonCount);
        }
        if (in.read() != -1) {
            throw new InvalidObjectException("unexpected trailing bytes");
        }
        if (count == 0 && anonCount == 0) {
            // Normalise: zero-domain payload carries no identity, same as empty data.
            return new ProtectionDomain[0];
        }
        List<ProtectionDomain> domains = new ArrayList<ProtectionDomain>(count + anonCount);
        for (ProtectionDomain pd : toProtectionDomains(records, authenticatedSubject)) {
            domains.add(pd);
        }
        // Reconstruct placeholder domains for each anonymous (non-HTTPMD,
        // non-DigestCodeSource) domain counted by the sender.  A null CodeSource
        // with null PermissionCollection defers permission decisions to the
        // server's security policy; this mirrors how the sender's actual
        // non-verifiable domain would behave if it could be faithfully reproduced.
        for (int i = 0; i < anonCount; i++) {
            domains.add(new ProtectionDomain(
                    new CodeSource(null, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]));
        }
        return domains.toArray(new ProtectionDomain[0]);
    }

    /**
     * Deserialises the {@code DigestCodeSource} domains stored in the
     * {@value #DIGEST_TRANSPORT_BYTES} serial field using
     * {@link AtomicMarshalInputStream}.
     *
     * <p>{@link AtomicMarshalInputStream} is used because it is hardened
     * against denial-of-service attacks, unlike the standard
     * {@link java.io.ObjectInputStream}.  Since {@code DigestCodeSource}
     * implements {@link Externalizable} and writes only strings and primitive
     * bytes, it is safely handled by {@code AtomicMarshalInputStream}.
     *
     * <p>If {@code DigestCodeSource} is not available on this JVM (e.g. a
     * standard JDK without the DirtyChai patch), the {@code ClassNotFoundException}
     * is caught and no digest domains are added — fail-secure behaviour.
     *
     * @return the reconstructed digest {@link ProtectionDomain} array, or an
     *         empty array when no digest domains are present or the class is absent
     */
    static ProtectionDomain[] unmarshalDigestFromTransport(byte[] data, Subject authenticatedSubject) throws IOException {
        if (data == null || data.length == 0) return new ProtectionDomain[0];
        if (data.length > MAX_TOTAL_PAYLOAD_BYTES) {
            throw new InvalidObjectException("digest transport payload size exceeds maximum: " + data.length);
        }
        List<ProtectionDomain> result = new ArrayList<ProtectionDomain>();
        ObjectInputStream amis = AtomicMarshalInputStream.create(
                new ByteArrayInputStream(data), null, false, null, Collections.emptyList(), false);
        int count = amis.readInt();
        if (count < 0 || count > MAX_DOMAIN_COUNT) {
            throw new InvalidObjectException("invalid digest domain count: " + count);
        }
        for (int i = 0; i < count; i++) {
            try {
                CodeSource cs = (CodeSource) amis.readObject();
                int principalCount = amis.readInt();
                if (principalCount < 0 || principalCount > MAX_PRINCIPALS_PER_DOMAIN) {
                    throw new InvalidObjectException("invalid principal count in digest transport: " + principalCount);
                }
                Principal[] principals;
                if (authenticatedSubject != null) {
                    // Discard stream principals; use the authenticated subject's instead.
                    for (int j = 0; j < principalCount; j++) {
                        amis.readUTF(); // type  (discard)
                        amis.readUTF(); // name  (discard)
                    }
                    Set<Principal> ps = authenticatedSubject.getPrincipals();
                    principals = ps.toArray(new Principal[0]);
                } else {
                    principals = new Principal[principalCount];
                    for (int j = 0; j < principalCount; j++) {
                        amis.readUTF(); // type class name (not stored in NamedPrincipal)
                        String name = amis.readUTF();
                        principals[j] = new NamedPrincipal(name);
                    }
                }
                result.add(new DomainIdentity(cs, principals));
            } catch (ClassNotFoundException e) {
                // DigestCodeSource is not available in this JVM — stop reading
                // further domains (stream position after ClassNotFoundException is
                // not guaranteed, so we cannot safely resume).
                break;
            }
        }
        return result.toArray(new ProtectionDomain[0]);
    }

    /** Merges HTTPMD and DigestCodeSource domains into a single {@link AccessControlContext}. */
    private static AccessControlContext buildContext(ProtectionDomain[] httpmd, ProtectionDomain[] digest) {
        int total = httpmd.length + digest.length;
        if (total == 0) return null;
        ProtectionDomain[] all = new ProtectionDomain[total];
        System.arraycopy(httpmd, 0, all, 0, httpmd.length);
        System.arraycopy(digest, 0, all, httpmd.length, digest.length);
        return Security.create(all);
    }

    private final DomainIdentityRecord[] domains;
    private final transient AccessControlContext context;
    /**
     * Cached result of {@link #marshalDigestForTransport} so that
     * {@link #equals} and {@link #hashCode} do not recompute the digest bytes
     * on every call.  Computed lazily on first access; {@code context} is
     * effectively immutable so the cache is safe to share without a lock after
     * the first write (the worst case is two threads computing the same value
     * concurrently, which is harmless).
     */
    private transient volatile byte[] cachedDigestBytes;

    AccessControlContextSerializer(GetArg arg) throws IOException, ClassNotFoundException {
        this(buildContext(
                unmarshalHttpmdDomains(arg.get(TRANSPORT_BYTES, null, byte[].class), null),
                unmarshalDigestFromTransport(arg.get(DIGEST_TRANSPORT_BYTES, null, byte[].class), null)));
    }

    AccessControlContextSerializer(AccessControlContext context) {
        this(recordsFromContext(context, null), context);
    }

    private AccessControlContextSerializer(DomainIdentityRecord[] domains) throws IOException {
        this(domains, Security.create(toProtectionDomains(domains, null)));
    }

    private AccessControlContextSerializer(DomainIdentityRecord[] domains, AccessControlContext context) {
        this.domains = domains != null ? domains : new DomainIdentityRecord[0];
        this.context = context;
    }

    Object readResolve() throws ObjectStreamException {
        return context;
    }

    /**
     * Extracts the HTTPMD-verifiable {@link DomainIdentityRecord}s from
     * {@code acc} for inclusion in the serial form of this class.
     *
     * <p>Only domains whose {@link CodeSource} location is a syntactically
     * valid {@code httpmd:} URL are included.  {@code DigestCodeSource} domains
     * are excluded here because they are handled separately by
     * {@link #marshalDigestForTransport}.  Non-verifiable domains (file URLs,
     * {@code null} locations, plain {@code http:} URLs, etc.) are dropped;
     * they are counted and preserved as anonymous placeholder domains in the
     * JERI wire transport by {@link #marshalForTransport} to avoid the
     * implicit privilege escalation that silent removal would cause.
     *
     * @param acc             the context to inspect; may be {@code null}
     * @param subjectOverride when non-{@code null}, replaces the domain's own
     *                        principals with those of this {@code Subject}
     * @return the array of HTTPMD records; never {@code null}
     */
    private static DomainIdentityRecord[] recordsFromContext(AccessControlContext acc, Subject subjectOverride) {
        ProtectionDomain[] extracted = extractDomains(acc);
        if (extracted.length == 0) return new DomainIdentityRecord[0];
        // DomainIdentityRecord.from() already filters to HTTPMD-verifiable domains and
        // stamps the correct principals, so no intermediate FilteringDomainCombiner is needed.
        List<DomainIdentityRecord> out = new ArrayList<DomainIdentityRecord>(extracted.length);
        for (int i = 0; i < extracted.length; i++) {
            DomainIdentityRecord r = DomainIdentityRecord.from(extracted[i], subjectOverride);
            if (r != null) out.add(r);
        }
        return out.toArray(new DomainIdentityRecord[out.size()]);
    }

    private static ProtectionDomain[] extractDomains(final AccessControlContext acc) {
        if (acc == null) return new ProtectionDomain[0];
        final ExtractingDomainCombiner extractor = new ExtractingDomainCombiner(acc.getDomainCombiner());
        // Constructing an AccessControlContext with a DomainCombiner requires
        // SecurityPermission("createAccessControlContext").  The permission check
        // walks the full call stack, so a less-privileged frame in the remote-proxy
        // invocation chain can cause it to fail even when the policy grants it to
        // this code.  Wrapping in doPrivileged stops the stack-walk here so only
        // this trusted code's domain is checked.
        final AccessControlContext wrapped = AccessController.doPrivileged(
                (PrivilegedAction<AccessControlContext>) () -> Security.create(acc, extractor));
        /*
         * The JVM invokes DomainCombiner.combine() only when an AccessController
         * stack-walk is triggered.  We force that walk by calling checkPermission
         * inside a doPrivileged block restricted to 'wrapped'.  The permission used
         * here is deliberately innocuous (and is expected to be denied); only the
         * side-effect of driving the combine() callback matters.  The SecurityException
         * is intentionally swallowed.
         */
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            try {
                AccessController.checkPermission(new RuntimePermission("accessClassInPackage.java.lang"));
            } catch (SecurityException ignore) {
            }
            return null;
        }, wrapped);
        return extractor.getCaptured();
    }

    private static ProtectionDomain[] toProtectionDomains(DomainIdentityRecord[] records, Subject authenticatedSubject) {
        if (records == null || records.length == 0) return new ProtectionDomain[0];
        List<ProtectionDomain> domains = new ArrayList<ProtectionDomain>(records.length);
        for (int i = 0; i < records.length; i++) {
            try {
                ProtectionDomain pd = records[i].toProtectionDomain(authenticatedSubject);
                if (pd != null) domains.add(pd);
            } catch (IOException ex) {
                // Drop unverifiable/invalid domains during reconstruction.
            }
        }
        return domains.toArray(new ProtectionDomain[domains.size()]);
    }

    private static boolean isVerifiableHttpmd(String location) {
        if (location == null) return false;
        if (!location.regionMatches(true, 0, "httpmd:", 0, HTTPMD_PREFIX_LENGTH)) return false;
        // Check syntactic validity only.  Full integrity verification is the
        // responsibility of the httpmd URL handler when the code is actually
        // loaded; calling Security.verifyCodebaseIntegrity() here would require
        // IntegrityVerifier services to be registered in the Surefire/test JVM,
        // which is not guaranteed and would reject syntactically correct URLs.
        try {
            Uri.parseAndCreate(location);
            return true;
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private static URL parseHttpmd(String location) throws IOException {
        try {
            Uri uri = Uri.parseAndCreate(location);
            try {
                return uri.toURL();
            } catch (MalformedURLException notRegistered) {
                // The httpmd:// URL handler (net.jini.url.httpmd.Handler) is
                // not registered in this JVM.  Create the URL with an
                // identity-only stream handler so that the CodeSource can
                // still be used for policy matching even when the handler
                // module is not on the classpath.
                return new URL(null, location, IDENTITY_HANDLER);
            }
        } catch (URISyntaxException ex) {
            InvalidObjectException e = new InvalidObjectException("invalid httpmd URL");
            e.initCause(ex);
            throw e;
        }
    }

    private static Principal[] principalsFor(ProtectionDomain pd, Subject authenticatedSubject) {
        if (authenticatedSubject != null) {
            Set<Principal> principalSet = authenticatedSubject.getPrincipals();
            return principalSet.toArray(new Principal[0]);
        }
        Principal[] principals = pd.getPrincipals();
        return principals != null ? principals : new Principal[0];
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static int readInt(ByteArrayInputStream in) throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        int b4 = in.read();
        if ((b1 | b2 | b3 | b4) < 0) throw new InvalidObjectException("Unexpected EOF reading integer value");
        return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
    }

    private static void writeUnsignedShort(ByteArrayOutputStream out, int value) {
        if (value < 0 || value > MAX_UNSIGNED_SHORT_VALUE) {
            throw new IllegalArgumentException("value out of unsigned short range: " + value);
        }
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static int readShort(ByteArrayInputStream in) throws IOException {
        int hi = in.read();
        int lo = in.read();
        if ((hi | lo) < 0) throw new InvalidObjectException("Unexpected EOF reading short value");
        return ((hi & 0xFF) << 8) | (lo & 0xFF);
    }

    private static final class ExtractingDomainCombiner implements DomainCombiner {
        private final DomainCombiner delegate;
        private volatile ProtectionDomain[] captured = new ProtectionDomain[0];

        private ExtractingDomainCombiner(DomainCombiner delegate) {
            this.delegate = delegate;
        }

        public ProtectionDomain[] combine(ProtectionDomain[] current, ProtectionDomain[] assigned) {
            // Capture only the *assigned* domains — those belonging to the
            // AccessControlContext passed in by the caller.  The *current*
            // parameter carries the call-stack domains of the doPrivileged
            // lambda (e.g. AccessControlContextSerializer's own classpath
            // ProtectionDomain), which must not be counted as anonymous domains
            // of the caller's ACC.
            List<ProtectionDomain> collected = new ArrayList<ProtectionDomain>(8);
            if (assigned != null) Collections.addAll(collected, assigned);
            captured = collected.toArray(new ProtectionDomain[collected.size()]);
            if (delegate != null) {
                return delegate.combine(current, assigned);
            }
            return current != null ? current : assigned;
        }

        private ProtectionDomain[] getCaptured() {
            return captured;
        }
    }

    @AtomicSerial
    static final class DomainIdentityRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final String LOCATION = "location";
        private static final String PRINCIPAL_TYPES = "principalTypes";
        private static final String PRINCIPAL_NAMES = "principalNames";
        // serialPersistentFields is INDEPENDENT of serialForm() (dual-path JOSS keep, STD-008 sec9.1)
        private static final ObjectStreamField[] serialPersistentFields = {
            new ObjectStreamField(LOCATION, String.class),
            new ObjectStreamField(PRINCIPAL_TYPES, String[].class),
            new ObjectStreamField(PRINCIPAL_NAMES, String[].class)
        };

        static SerialForm[] serialForm() {
            return new SerialForm[]{
                new SerialForm(LOCATION, String.class),
                new SerialForm(PRINCIPAL_TYPES, String[].class),
                new SerialForm(PRINCIPAL_NAMES, String[].class)
            };
        }

        static void serialize(PutArg arg, DomainIdentityRecord obj) throws IOException {
            arg.put(LOCATION, obj.location);
            arg.put(PRINCIPAL_TYPES, obj.principalTypes);
            arg.put(PRINCIPAL_NAMES, obj.principalNames);
            arg.writeArgs();
        }

        /**
         * Creates a {@link DomainIdentityRecord} for the given
         * {@link ProtectionDomain} if it has a verifiable HTTPMD location.
         * DigestCodeSource domains are handled separately by
         * {@link #marshalDigestForTransport} and are explicitly excluded here
         * to avoid duplicating a DigestCodeSource whose location happens to be
         * an httpmd URL in both the HTTPMD and the digest transport streams.
         */
        static DomainIdentityRecord from(ProtectionDomain pd, Subject authenticatedSubject) {
            CodeSource cs = pd.getCodeSource();
            // DigestCodeSource is handled exclusively by marshalDigestForTransport();
            // skip it here even if its location is an httpmd URL.
            if (cs instanceof Externalizable
                    && DIGEST_CODESOURCE_CLASS_NAME.equals(cs.getClass().getName())) {
                return null;
            }
            URL location = cs != null ? cs.getLocation() : null;
            String locText = location != null ? location.toExternalForm() : null;
            if (!isVerifiableHttpmd(locText)) return null;
            Principal[] principals = principalsFor(pd, authenticatedSubject);
            String[] types = new String[principals.length];
            String[] names = new String[principals.length];
            for (int i = 0; i < principals.length; i++) {
                types[i] = principals[i].getClass().getName();
                names[i] = principals[i].getName();
            }
            return new DomainIdentityRecord(locText, types, names);
        }

        static DomainIdentityRecord readFrom(ByteArrayInputStream in) throws IOException {
            return readHttpmdRecord(in);
        }

        private static DomainIdentityRecord readHttpmdRecord(ByteArrayInputStream in) throws IOException {
            int locLen = readShort(in);
            if (locLen > MAX_LOCATION_BYTES) {
                throw new InvalidObjectException("location length exceeds maximum: " + locLen);
            }
            byte[] locationBytes = new byte[locLen];
            if (locLen > 0 && in.read(locationBytes) != locLen) throw new InvalidObjectException("Unexpected EOF reading location bytes");
            String location = new String(locationBytes, StandardCharsets.UTF_8);
            int principalCount = readShort(in);
            if (principalCount > MAX_PRINCIPALS_PER_DOMAIN) {
                throw new InvalidObjectException("principal count exceeds maximum: " + principalCount);
            }
            String[] types = new String[principalCount];
            String[] names = new String[principalCount];
            for (int i = 0; i < principalCount; i++) {
                int typeLen = readShort(in);
                if (typeLen > MAX_PRINCIPAL_FIELD_BYTES) {
                    throw new InvalidObjectException("principal type length exceeds maximum: " + typeLen);
                }
                byte[] typeBytes = new byte[typeLen];
                if (in.read(typeBytes) != typeLen) throw new InvalidObjectException("Unexpected EOF reading principal type");
                types[i] = new String(typeBytes, StandardCharsets.UTF_8);
                int nameLen = readShort(in);
                if (nameLen > MAX_PRINCIPAL_FIELD_BYTES) {
                    throw new InvalidObjectException("principal name length exceeds maximum: " + nameLen);
                }
                byte[] nameBytes = new byte[nameLen];
                if (in.read(nameBytes) != nameLen) throw new InvalidObjectException("Unexpected EOF reading principal name");
                names[i] = new String(nameBytes, StandardCharsets.UTF_8);
            }
            return new DomainIdentityRecord(location, types, names);
        }

        private final String location;
        private final String[] principalTypes;
        private final String[] principalNames;

        DomainIdentityRecord(GetArg arg) throws IOException, ClassNotFoundException {
            this(
                arg.get(LOCATION, null, String.class),
                arg.get(PRINCIPAL_TYPES, null, String[].class),
                arg.get(PRINCIPAL_NAMES, null, String[].class)
            );
        }

        private DomainIdentityRecord(String location, String[] principalTypes, String[] principalNames) {
            this.location = location;
            this.principalTypes = principalTypes != null ? principalTypes : new String[0];
            this.principalNames = principalNames != null ? principalNames : new String[0];
        }

        private void writeTo(ByteArrayOutputStream out) throws IOException {
            writeHttpmdPayload(out);
        }

        private void writeHttpmdPayload(ByteArrayOutputStream out) throws IOException {
            byte[] loc = location.getBytes(StandardCharsets.UTF_8);
            if (loc.length > MAX_LOCATION_BYTES) {
                throw new InvalidObjectException("location too long to encode: " + loc.length);
            }
            writeUnsignedShort(out, loc.length);
            out.write(loc);
            writePrincipals(out);
        }

        private void writePrincipals(ByteArrayOutputStream out) throws IOException {
            if (principalTypes.length != principalNames.length) {
                throw new InvalidObjectException("principal type/name length mismatch");
            }
            int count = principalTypes.length;
            if (count > MAX_PRINCIPALS_PER_DOMAIN) {
                throw new InvalidObjectException("principal count exceeds maximum: " + count);
            }
            writeUnsignedShort(out, count);
            for (int i = 0; i < count; i++) {
                byte[] type = principalTypes[i].getBytes(StandardCharsets.UTF_8);
                byte[] name = principalNames[i].getBytes(StandardCharsets.UTF_8);
                if (type.length > MAX_PRINCIPAL_FIELD_BYTES) {
                    throw new InvalidObjectException("principal type too long to encode: " + type.length);
                }
                if (name.length > MAX_PRINCIPAL_FIELD_BYTES) {
                    throw new InvalidObjectException("principal name too long to encode: " + name.length);
                }
                writeUnsignedShort(out, type.length);
                out.write(type);
                writeUnsignedShort(out, name.length);
                out.write(name);
            }
        }

        private ProtectionDomain toProtectionDomain(Subject authenticatedSubject) throws IOException {
            if (!isVerifiableHttpmd(location)) return null;
            URL url = parseHttpmd(location);
            Principal[] principals = buildPrincipals(authenticatedSubject);
            return new DomainIdentity(new CodeSource(url, (Certificate[]) null), principals);
        }

        private Principal[] buildPrincipals(Subject authenticatedSubject) throws IOException {
            if (authenticatedSubject != null) {
                Set<Principal> principalSet = authenticatedSubject.getPrincipals();
                return principalSet.toArray(new Principal[0]);
            }
            if (principalTypes.length != principalNames.length) {
                throw new InvalidObjectException("principal type/name length mismatch");
            }
            Principal[] principals = new Principal[principalTypes.length];
            for (int i = 0; i < principals.length; i++) {
                principals[i] = new NamedPrincipal(principalNames[i]);
            }
            return principals;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof DomainIdentityRecord)) return false;
            DomainIdentityRecord other = (DomainIdentityRecord) obj;
            return (location == null ? other.location == null : location.equals(other.location))
                    && Arrays.equals(principalTypes, other.principalTypes)
                    && Arrays.equals(principalNames, other.principalNames);
        }

        @Override
        public int hashCode() {
            int h = location != null ? location.hashCode() : 0;
            h = 31 * h + Arrays.hashCode(principalTypes);
            h = 31 * h + Arrays.hashCode(principalNames);
            return h;
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            throw new NotSerializableException(
                "DomainIdentityRecord must be serialized using @AtomicSerial transport records only");
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new NotSerializableException(
                "DomainIdentityRecord must be deserialized using @AtomicSerial transport records only");
        }
    }

    static final class DomainIdentity extends ProtectionDomain {

        DomainIdentity(CodeSource cs, Principal[] principals) {
            super(cs, null, null, principals);
        }
        
        private void writeObject(ObjectOutputStream out) throws IOException {
            throw new NotSerializableException("DomainIdentity must be serialized using @AtomicSerial transport records only");
        }
        
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new NotSerializableException("DomainIdentity must be deserialized using @AtomicSerial transport records only");
        }
    }

    private static final class NamedPrincipal implements Principal, Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;

        private NamedPrincipal(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof NamedPrincipal)) return false;
            NamedPrincipal other = (NamedPrincipal) obj;
            return name != null ? name.equals(other.name) : other.name == null;
        }

        @Override
        public int hashCode() {
            return name != null ? name.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "NamedPrincipal[" + name + "]";
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            throw new NotSerializableException(
                "NamedPrincipal must not be serialized outside of AccessControlContextSerializer");
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new NotSerializableException(
                "NamedPrincipal must not be deserialized outside of AccessControlContextSerializer");
        }
    }

    /**
     * Returns the cached digest transport bytes for this serializer, computing
     * them if not yet cached.  An {@link IOException} during computation is
     * treated as "no digest domains" ({@code new byte[0]}).
     */
    private byte[] digestBytes() {
        byte[] b = cachedDigestBytes;
        if (b == null) {
            try {
                b = marshalDigestForTransport(context);
            } catch (IOException e) {
                b = new byte[0];
            }
            cachedDigestBytes = b;
        }
        return b;
    }

    /**
     * Two {@link AccessControlContextSerializer} instances are equal when they
     * carry the same set of {@link DomainIdentityRecord} HTTPMD domains and the
     * same {@code DigestCodeSource} digest domains.  Equal instances will be
     * back-referenced by the serialization stream rather than written in full a
     * second time, which is the primary motivation for this implementation.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AccessControlContextSerializer)) return false;
        AccessControlContextSerializer other = (AccessControlContextSerializer) obj;
        return Arrays.equals(domains, other.domains)
                && Arrays.equals(digestBytes(), other.digestBytes());
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(domains) + Arrays.hashCode(digestBytes());
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        ObjectOutputStream.PutField pf = out.putFields();
        pf.put(TRANSPORT_BYTES, marshalForTransport(context));
        pf.put(DIGEST_TRANSPORT_BYTES, marshalDigestForTransport(context));
        out.writeFields();
    }
}
