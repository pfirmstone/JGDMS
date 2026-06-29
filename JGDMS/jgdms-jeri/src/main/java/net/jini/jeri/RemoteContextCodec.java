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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
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
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.security.auth.Subject;
import net.jini.security.Security;

/**
 * Transmits a remote caller's <em>reducing</em> {@link ProtectionDomain} set --
 * codebases only, no principals -- over JERI, and reconstructs it at the receiver
 * with the authenticated worker {@link Subject}'s principals stamped on.
 *
 * <p>This is the subtractive half of the two-gate workload-authorisation model.
 * The reconstructed domains form the remote caller's {@link AccessControlContext}
 * at the server (the permission ceiling that reduces the dispatched call), while
 * the additive workload identity is the authenticated {@code RemoteSubject} whose
 * principals are stamped here -- the principals come from the verified mTLS
 * connection, <em>never</em> from the wire.
 *
 * <p><b>Wire format.</b>  Written and read through the proxy's injected
 * {@link ObjectOutput}/{@link ObjectInput}, so the payload is DER under a
 * DER-exported proxy and JOSS otherwise.  On the wire the whole payload is a
 * single length-delimited block (see {@code BasicInvocationDispatcher}) decoded
 * in its own isolated codec stream, so this untrusted remote input cannot share
 * decode state, a handle table, or a DoS budget with the application arguments:
 * <pre>
 *   int   domainCount
 *   repeat domainCount:
 *     byte kind                          // 0 = NULL_CS, 1 = DIGEST, 2 = URL
 *       DIGEST : utf uri; utf algorithm; object digest (byte[]); certs
 *       URL    : utf location;                                   certs
 *       NULL_CS: --                      // only a domain genuinely null-CS at the sender
 *   certs := int n; { object der (byte[]) } * n
 * </pre>
 * Every domain keeps its codebase identity: a {@code DigestCodeSource} carries its
 * URI + algorithm + digest (self-verifying); other domains carry their URL and any
 * signing certificates.  No anonymous placeholder domains and no principals are
 * written.
 */
final class RemoteContextCodec {

    private RemoteContextCodec() { }

    private static final byte KIND_NULL_CS = 0;
    private static final byte KIND_DIGEST  = 1;
    private static final byte KIND_URL     = 2;

    /** DoS ceilings applied while reading an untrusted remote ACC. */
    private static final int MAX_DOMAINS    = 4096;
    private static final int MAX_CERTS      = 100;
    private static final int MAX_DIGEST_LEN = 512;
    private static final int MAX_CERT_LEN   = 64 * 1024;

    private static final String DIGEST_CODESOURCE = "java.security.DigestCodeSource";

    /**
     * Reflective handles to DirtyChai's {@code java.security.DigestCodeSource}.
     * jgdms-jeri compiles on stock OpenJDK (where the class is absent) but runs
     * only on DirtyChai (where it is always present), so the type is bound
     * reflectively.  When absent the {@code DIGEST} kind degrades: the sender
     * never produces a {@code DigestCodeSource} (its CodeSources are not of that
     * type) and the receiver represents a received {@code DIGEST} domain by its
     * URL alone.  This does not occur at runtime.
     */
    private static final Method DCS_ALGORITHM;
    private static final Method DCS_DIGEST;
    private static final Constructor<?> DCS_CTOR; // (String uri, Certificate[] certs, String alg, byte[] digest)
    static {
        Method alg = null, dig = null;
        Constructor<?> ctor = null;
        try {
            Class<?> c = Class.forName(DIGEST_CODESOURCE);
            alg  = c.getMethod("getDigestAlgorithm");
            dig  = c.getMethod("getDigest");
            ctor = c.getConstructor(String.class, Certificate[].class, String.class, byte[].class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // Non-DirtyChai JVM -- see field javadoc.
        }
        DCS_ALGORITHM = alg;
        DCS_DIGEST = dig;
        DCS_CTOR = ctor;
    }

    /**
     * Identity-only {@link URLStreamHandler} used to reconstruct a codebase URL
     * for {@code CodeSource} (policy-matching) purposes when the real protocol
     * handler is not registered on the receiver; opening a connection always
     * fails.
     */
    private static final URLStreamHandler IDENTITY_HANDLER = new URLStreamHandler() {
        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            throw new IOException("codebase URL is for identity only: " + u);
        }
    };

    // ------------------------------------------------------------------- send

    /**
     * Writes the reducing domains of {@code acc} -- their codebases only, no
     * principals -- to {@code out}.
     *
     * <p>Every reducing domain is transmitted, including a domain whose
     * {@link CodeSource} is {@code null} (a dynamic proxy, lambda, or bootstrap
     * domain): it is a genuine reducer and dropping it would <em>elevate</em>
     * authority.  Such a domain is reconstructed on the receiver as a
     * codebase-less, principal-bearing domain, so it reduces to whatever the
     * authenticated worker principals are granted (a principal-only grant) and
     * never grants codebase-scoped authority -- "the caller may use only the
     * Principal."  No principals and no synthetic placeholders are written.
     */
    static void marshal(ObjectOutput out, AccessControlContext acc) throws IOException {
        ProtectionDomain[] domains =
                (acc == null) ? new ProtectionDomain[0] : extractDomains(acc);
        out.writeInt(domains.length);
        for (int i = 0; i < domains.length; i++) {
            CodeSource cs = domains[i].getCodeSource();
            if (isDigestCodeSource(cs)) {
                out.writeByte(KIND_DIGEST);
                URL loc = cs.getLocation();
                out.writeUTF(loc != null ? loc.toExternalForm() : "");
                try {
                    String alg = (String) DCS_ALGORITHM.invoke(cs);
                    out.writeUTF(alg != null ? alg : "");
                    out.writeObject(DCS_DIGEST.invoke(cs)); // byte[]
                } catch (ReflectiveOperationException e) {
                    throw new IOException("DigestCodeSource field extraction failed", e);
                }
                writeCerts(out, cs.getCertificates());
            } else if (cs != null && cs.getLocation() != null) {
                out.writeByte(KIND_URL);
                out.writeUTF(cs.getLocation().toExternalForm());
                writeCerts(out, cs.getCertificates());
            } else {
                // Genuinely codebase-less domain at the sender: a reducer with no
                // codebase identity.  Reconstructed with a null CodeSource + the
                // authenticated worker principals; matches principal-only grants.
                out.writeByte(KIND_NULL_CS);
            }
        }
    }

    private static boolean isDigestCodeSource(CodeSource cs) {
        return cs != null && DCS_CTOR != null
                && DIGEST_CODESOURCE.equals(cs.getClass().getName());
    }

    private static void writeCerts(ObjectOutput out, Certificate[] certs) throws IOException {
        int n = certs == null ? 0 : certs.length;
        out.writeInt(n);
        for (int i = 0; i < n; i++) {
            try {
                out.writeObject(certs[i].getEncoded()); // byte[] DER
            } catch (CertificateException e) {
                throw new IOException("certificate encoding failed", e);
            }
        }
    }

    // ---------------------------------------------------------------- receive

    /**
     * Reads the reducing domains from {@code in} and returns them stamped with the
     * authenticated {@code workerSubject}'s principals (the additive, authenticated
     * workload identity).  Returns an empty array when no domains were transmitted.
     *
     * @param in            the proxy's codec stream over the (already length-bounded)
     *                      ACC block
     * @param workerSubject the authenticated remote-peer worker subject; its
     *                      principals -- and only its principals -- are stamped onto
     *                      every reconstructed domain
     */
    static ProtectionDomain[] unmarshal(ObjectInput in, Subject workerSubject)
            throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_DOMAINS) {
            throw new IOException("invalid remote domain count: " + count);
        }
        Principal[] principals = workerPrincipals(workerSubject);
        List<ProtectionDomain> domains = new ArrayList<ProtectionDomain>(count);
        for (int i = 0; i < count; i++) {
            byte kind = in.readByte();
            CodeSource cs;
            switch (kind) {
                case KIND_NULL_CS:
                    cs = null;
                    break;
                case KIND_DIGEST:
                    cs = readDigest(in);
                    break;
                case KIND_URL:
                    cs = new CodeSource(parseUrl(in.readUTF()), readCerts(in));
                    break;
                default:
                    throw new IOException("unknown remote domain kind: " + kind);
            }
            domains.add(new ReconstructedDomain(cs, principals));
        }
        return domains.toArray(new ProtectionDomain[domains.size()]);
    }

    private static CodeSource readDigest(ObjectInput in) throws IOException {
        String uri = in.readUTF();
        String alg = in.readUTF();
        byte[] digest = readByteArray(in, MAX_DIGEST_LEN, "digest");
        Certificate[] certs = readCerts(in);
        if (DCS_CTOR == null) {
            // Non-DirtyChai receiver: represent by URL alone (see DCS_CTOR javadoc).
            return new CodeSource(parseUrl(uri), certs);
        }
        try {
            return (CodeSource) DCS_CTOR.newInstance(uri, certs, alg, digest);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IOException("DigestCodeSource reconstruction failed for " + uri, e);
        }
    }

    private static Certificate[] readCerts(ObjectInput in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > MAX_CERTS) {
            throw new IOException("invalid certificate count: " + n);
        }
        if (n == 0) return null;
        CertificateFactory cf;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new IOException("X.509 CertificateFactory unavailable", e);
        }
        Certificate[] certs = new Certificate[n];
        for (int i = 0; i < n; i++) {
            byte[] der = readByteArray(in, MAX_CERT_LEN, "certificate");
            try {
                certs[i] = cf.generateCertificate(new ByteArrayInputStream(der));
            } catch (CertificateException e) {
                throw new IOException("certificate decoding failed", e);
            }
        }
        return certs;
    }

    private static byte[] readByteArray(ObjectInput in, int max, String what) throws IOException {
        Object o;
        try {
            o = in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("unexpected class reading " + what, e);
        }
        if (!(o instanceof byte[])) {
            throw new IOException(what + " was not a byte array");
        }
        byte[] b = (byte[]) o;
        if (b.length > max) {
            throw new IOException(what + " exceeds maximum length: " + b.length);
        }
        return b;
    }

    private static URL parseUrl(String s) throws IOException {
        if (s == null || s.isEmpty()) return null;
        try {
            return new URL(s);
        } catch (MalformedURLException notRegistered) {
            // The scheme's protocol handler is not registered on this receiver.
            // Build an identity-only URL so the CodeSource can still be used for
            // policy matching.
            try {
                return new URL(null, s, IDENTITY_HANDLER);
            } catch (MalformedURLException e) {
                throw new IOException("invalid codebase URL: " + s, e);
            }
        }
    }

    private static Principal[] workerPrincipals(Subject workerSubject) {
        if (workerSubject == null) return new Principal[0];
        Set<Principal> p = workerSubject.getPrincipals();
        return p.toArray(new Principal[p.size()]);
    }

    // ---------------------------------------------------------- domain extract

    /**
     * Captures the {@link ProtectionDomain}s of {@code acc}.  Constructing an
     * {@link AccessControlContext} with a {@link DomainCombiner} needs
     * {@code SecurityPermission("createAccessControlContext")}; that check walks
     * the whole stack, so a less-privileged frame in the proxy-invocation chain
     * could fail it even when this library is granted it.  The doPrivileged blocks
     * truncate the walk to this trusted frame, and force the combine() callback
     * with an innocuous (expected-denied, swallowed) permission check.
     */
    private static ProtectionDomain[] extractDomains(final AccessControlContext acc) {
        final ExtractingDomainCombiner extractor =
                new ExtractingDomainCombiner(acc.getDomainCombiner());
        final AccessControlContext wrapped = AccessController.doPrivileged(
                (PrivilegedAction<AccessControlContext>) () -> Security.create(acc, extractor));
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            try {
                AccessController.checkPermission(
                        new RuntimePermission("accessClassInPackage.java.lang"));
            } catch (SecurityException ignore) {
                // Driving the combine() callback is the only purpose.
            }
            return null;
        }, wrapped);
        return extractor.getCaptured();
    }

    private static final class ExtractingDomainCombiner implements DomainCombiner {
        private final DomainCombiner delegate;
        private volatile ProtectionDomain[] captured = new ProtectionDomain[0];

        private ExtractingDomainCombiner(DomainCombiner delegate) {
            this.delegate = delegate;
        }

        public ProtectionDomain[] combine(ProtectionDomain[] current,
                                          ProtectionDomain[] assigned) {
            // Capture only the *assigned* domains -- those of the AccessControlContext
            // passed in.  The *current* parameter is the doPrivileged lambda's own
            // call-stack domains, which are not the caller's reducing context.
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

    /**
     * A reconstructed reducing domain: the remote caller's real {@link CodeSource}
     * plus the authenticated worker principals, with no static permissions (so the
     * server's policy alone decides what it grants).
     */
    private static final class ReconstructedDomain extends ProtectionDomain {
        ReconstructedDomain(CodeSource cs, Principal[] principals) {
            super(cs, null, null, principals);
        }
    }
}
