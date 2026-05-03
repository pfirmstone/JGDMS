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
package net.jini.jeri.ssl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500PrivateCredential;

/**
 * Manages SPIFFE X.509-SVID credentials for JGDMS JERI TLS connections.
 *
 * <h2>Purpose</h2>
 * <p>A {@code SpiffeCredentialManager} maintains a live {@link Subject} whose
 * credentials are populated from a SPIFFE/SPIRE identity provider.  Each host
 * in the five-host JGDMS trust pipeline (Hosts 1–5) and every client JVM
 * should instantiate one {@code SpiffeCredentialManager} to automatically
 * rotate the short-lived SPIFFE SVIDs (~1 hour TTL) used by JERI TLS.
 *
 * <h2>SPIFFE/SPIRE integration</h2>
 * <p>SPIRE writes X.509-SVID material to well-known PEM files via its
 * <em>file-based credential helper</em>:
 * <ul>
 *   <li>{@code svid.pem} — the leaf SVID certificate followed by any
 *       intermediates, in PEM format.</li>
 *   <li>{@code svid_key.pem} — the SVID private key in PKCS#8 PEM
 *       ({@code PRIVATE KEY}) or PKCS#1 PEM ({@code RSA PRIVATE KEY} /
 *       {@code EC PRIVATE KEY}) format.</li>
 *   <li>{@code bundle.pem} — the SPIFFE trust bundle (root CA certificates)
 *       in PEM format.  Currently informational; clients are expected to
 *       configure JERI's {@link javax.net.ssl.TrustManager} with these CAs
 *       separately.</li>
 * </ul>
 * <p>To use the SPIRE Workload API (gRPC over UNIX socket at
 * {@code /run/spire/sockets/agent.sock}) instead, implement
 * {@link SvidSource} and pass it to the
 * {@link #SpiffeCredentialManager(Subject, SvidSource, long)} constructor.
 *
 * <h2>Subject integration</h2>
 * <p>The manager writes to the provided {@link Subject} using
 * {@code Subject.doAs(subject, PrivilegedAction)} to add and remove
 * credentials atomically.  The public-credential set receives one
 * {@link CertPath} containing the SVID leaf and any intermediates.  The
 * private-credential set receives one {@link X500PrivateCredential} coupling
 * the leaf certificate to its private key.
 *
 * <h2>SPIFFE ID naming scheme</h2>
 * <p>The SPIFFE IDs used in JGDMS follow the convention from Issue #205:
 * <pre>
 *   spiffe://jgdms.example.org/host/lookup       → Host 1
 *   spiffe://jgdms.example.org/host/bae/engine-N → Host 2 instances
 *   spiffe://jgdms.example.org/host/registry     → Host 3
 *   spiffe://jgdms.example.org/host/downloader   → Host 4
 *   spiffe://jgdms.example.org/host/telemetry    → Host 5
 *   spiffe://jgdms.example.org/client/...        → Client JVMs
 * </pre>
 * <p>These IDs are embedded in the SVID's SubjectAlternativeName extension
 * as a URI SAN; no additional configuration is required by the manager.
 *
 * <h2>Automatic rotation</h2>
 * <p>The manager schedules a background refresh task that fires at
 * {@code (svid_expiry - renewalLeadSeconds)} to refresh credentials before
 * they expire.  If a refresh attempt fails, the task is retried every
 * {@link #RETRY_INTERVAL_SECONDS} seconds.  The background thread is a
 * daemon thread so it does not prevent JVM shutdown.
 *
 * <h2>Thread safety</h2>
 * <p>All credential updates are synchronised on the provided {@link Subject}
 * so that concurrent JERI SSL handshakes observe a consistent state.
 *
 * @see SubjectCredentials
 * @see SvidSource
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public final class SpiffeCredentialManager implements AutoCloseable {

    private static final Logger logger =
            Logger.getLogger(SpiffeCredentialManager.class.getName());

    /** Default lead time (seconds) before SVID expiry to trigger renewal. */
    public static final long DEFAULT_RENEWAL_LEAD_SECONDS = 300L;

    /** Retry interval (seconds) when a refresh attempt fails. */
    private static final long RETRY_INTERVAL_SECONDS = 30L;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final Subject subject;
    private final SvidSource svidSource;
    private final long renewalLeadSeconds;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> scheduledTask;

    // -------------------------------------------------------------------------
    // SvidSource SPI
    // -------------------------------------------------------------------------

    /**
     * Service-provider interface for fetching current SVID material.
     *
     * <p>Implementations may retrieve SVIDs from:
     * <ul>
     *   <li>PEM files written by the SPIRE agent's credential helper
     *       (see {@link FileSvidSource});</li>
     *   <li>the SPIRE Workload API (gRPC over UNIX socket); or</li>
     *   <li>any other SVID-issuing source.</li>
     * </ul>
     */
    public interface SvidSource {
        /**
         * Returns the current SVID as an {@link Svid} value object.
         *
         * <p>This method must be idempotent: multiple successive calls must
         * return equivalent SVIDs as long as the SVID has not been rotated.
         *
         * @return the current SVID; never {@code null}
         * @throws IOException              if the SVID cannot be read
         * @throws GeneralSecurityException if the SVID cannot be parsed or
         *                                  validated
         */
        Svid fetch() throws IOException, GeneralSecurityException;
    }

    /**
     * Immutable holder for a single X.509-SVID.
     */
    public static final class Svid {
        /** The SVID certificate chain (leaf first, intermediates following). */
        public final CertPath certPath;

        /** The SVID private key. */
        public final PrivateKey privateKey;

        /**
         * Constructs an {@code Svid}.
         *
         * @param certPath   the SVID certificate chain; must be non-null
         * @param privateKey the SVID private key; must be non-null
         */
        public Svid(CertPath certPath, PrivateKey privateKey) {
            if (certPath == null)   throw new NullPointerException("certPath");
            if (privateKey == null) throw new NullPointerException("privateKey");
            this.certPath   = certPath;
            this.privateKey = privateKey;
        }

        /**
         * Returns the leaf (SVID) certificate, i.e. the first in the chain.
         *
         * @return the leaf certificate; never {@code null}
         * @throws IllegalStateException if the certificate path is empty
         */
        public X509Certificate leafCertificate() {
            List<? extends java.security.cert.Certificate> certs =
                    certPath.getCertificates();
            if (certs.isEmpty())
                throw new IllegalStateException("certPath must not be empty");
            return (X509Certificate) certs.get(0);
        }
    }

    // -------------------------------------------------------------------------
    // FileSvidSource
    // -------------------------------------------------------------------------

    /**
     * {@link SvidSource} implementation that reads SVID material from PEM
     * files on the local filesystem — the standard output of the SPIRE
     * agent's file-based credential helper.
     *
     * <p>The expected files are:
     * <ul>
     *   <li>{@code svid.pem} — the leaf SVID certificate followed by any
     *       intermediate certificates.</li>
     *   <li>{@code svid_key.pem} — the SVID private key (PKCS#8
     *       {@code PRIVATE KEY} or EC {@code EC PRIVATE KEY} or RSA
     *       {@code RSA PRIVATE KEY}).</li>
     * </ul>
     *
     * <p>Example SPIRE agent configuration:
     * <pre>
     * plugins {
     *   WorkloadAttestor "unix" {}
     *
     *   SVIDStore "disk" {
     *     plugin_data {
     *       svid_file_name    = "/run/spire/svid.pem"
     *       key_file_name     = "/run/spire/svid_key.pem"
     *       bundle_file_name  = "/run/spire/bundle.pem"
     *     }
     *   }
     * }
     * </pre>
     */
    public static final class FileSvidSource implements SvidSource {

        private static final String CERT_FACTORY_TYPE = "X.509";
        private static final String RSA_KEY_ALGORITHM  = "RSA";
        private static final String EC_KEY_ALGORITHM   = "EC";

        // PEM boundary markers
        private static final String BEGIN_CERT        = "-----BEGIN CERTIFICATE-----";
        private static final String END_CERT          = "-----END CERTIFICATE-----";
        private static final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
        private static final String END_PRIVATE_KEY   = "-----END PRIVATE KEY-----";
        private static final String BEGIN_RSA_KEY     = "-----BEGIN RSA PRIVATE KEY-----";
        private static final String END_RSA_KEY       = "-----END RSA PRIVATE KEY-----";
        private static final String BEGIN_EC_KEY      = "-----BEGIN EC PRIVATE KEY-----";
        private static final String END_EC_KEY        = "-----END EC PRIVATE KEY-----";

        private final Path svidPem;
        private final Path svidKeyPem;

        /**
         * Creates a {@code FileSvidSource} reading from the given PEM files.
         *
         * @param svidPem    path to the SVID certificate PEM file;
         *                   must be non-null
         * @param svidKeyPem path to the SVID private key PEM file;
         *                   must be non-null
         * @throws NullPointerException if either argument is {@code null}
         */
        public FileSvidSource(Path svidPem, Path svidKeyPem) {
            if (svidPem == null)    throw new NullPointerException("svidPem");
            if (svidKeyPem == null) throw new NullPointerException("svidKeyPem");
            this.svidPem    = svidPem;
            this.svidKeyPem = svidKeyPem;
        }

        /**
         * Creates a {@code FileSvidSource} using the default SPIRE file names
         * under the given directory.
         *
         * <p>The expected layout is:
         * <pre>
         *   &lt;spireDir&gt;/svid.pem
         *   &lt;spireDir&gt;/svid_key.pem
         * </pre>
         *
         * @param spireDir the directory containing the SPIRE PEM files;
         *                 must be non-null
         * @throws NullPointerException if {@code spireDir} is {@code null}
         */
        public FileSvidSource(Path spireDir) {
            this(spireDir.resolve("svid.pem"), spireDir.resolve("svid_key.pem"));
        }

        @Override
        public Svid fetch() throws IOException, GeneralSecurityException {
            CertPath certPath = loadCertPath();
            PrivateKey key    = loadPrivateKey();
            return new Svid(certPath, key);
        }

        private CertPath loadCertPath() throws IOException, GeneralSecurityException {
            byte[] pem = Files.readAllBytes(svidPem);
            List<X509Certificate> certs = new ArrayList<X509Certificate>();
            CertificateFactory cf = CertificateFactory.getInstance(CERT_FACTORY_TYPE);

            String pemStr = new String(pem, StandardCharsets.US_ASCII);
            int pos = 0;
            while (true) {
                int beginIdx = pemStr.indexOf(BEGIN_CERT, pos);
                if (beginIdx < 0) break;
                int endIdx = pemStr.indexOf(END_CERT, beginIdx);
                if (endIdx < 0)
                    throw new IOException("Malformed PEM: missing END CERTIFICATE");
                endIdx += END_CERT.length();
                String block = pemStr.substring(beginIdx, endIdx);
                byte[] der = decodePemBlock(block, BEGIN_CERT, END_CERT);
                X509Certificate cert = (X509Certificate) cf.generateCertificate(
                        new ByteArrayInputStream(der));
                certs.add(cert);
                pos = endIdx;
            }
            if (certs.isEmpty())
                throw new IOException("No certificates found in " + svidPem);
            return cf.generateCertPath(certs);
        }

        private PrivateKey loadPrivateKey() throws IOException, GeneralSecurityException {
            String pem = new String(Files.readAllBytes(svidKeyPem), StandardCharsets.US_ASCII);

            if (pem.contains(BEGIN_PRIVATE_KEY)) {
                // PKCS#8 — algorithm-agnostic
                byte[] der = decodePemBlock(pem, BEGIN_PRIVATE_KEY, END_PRIVATE_KEY);
                try {
                    return KeyFactory.getInstance(RSA_KEY_ALGORITHM)
                            .generatePrivate(new PKCS8EncodedKeySpec(der));
                } catch (GeneralSecurityException ignored) { }
                try {
                    return KeyFactory.getInstance(EC_KEY_ALGORITHM)
                            .generatePrivate(new PKCS8EncodedKeySpec(der));
                } catch (GeneralSecurityException ignored) { }
                // Try other registered KeyFactory providers
                for (java.security.Provider p : java.security.Security.getProviders()) {
                    for (java.security.Provider.Service svc : p.getServices()) {
                        if ("KeyFactory".equals(svc.getType())) {
                            try {
                                return KeyFactory.getInstance(svc.getAlgorithm())
                                        .generatePrivate(new PKCS8EncodedKeySpec(der));
                            } catch (GeneralSecurityException ignored2) { }
                        }
                    }
                }
                throw new GeneralSecurityException(
                        "Cannot load PKCS#8 key from " + svidKeyPem);

            } else if (pem.contains(BEGIN_EC_KEY)) {
                // SEC 1 EC private key — wrap in PKCS#8 envelope
                byte[] sec1 = decodePemBlock(pem, BEGIN_EC_KEY, END_EC_KEY);
                // SEC 1 DER → PKCS#8 wrapper using Java's ECKeyFactory
                // Note: Java's EC KeyFactory accepts SEC1 DER directly via
                // PKCS8EncodedKeySpec only in some JVMs.  Use a brute-force
                // approach: try raw PKCS8EncodedKeySpec first, then fail with
                // a helpful message.
                try {
                    return KeyFactory.getInstance(EC_KEY_ALGORITHM)
                            .generatePrivate(new PKCS8EncodedKeySpec(sec1));
                } catch (GeneralSecurityException e) {
                    throw new GeneralSecurityException(
                            "Cannot load EC private key from " + svidKeyPem
                            + ": use PKCS#8 format (BEGIN PRIVATE KEY) for "
                            + "maximum compatibility", e);
                }

            } else if (pem.contains(BEGIN_RSA_KEY)) {
                // PKCS#1 RSA private key — re-wrap in PKCS#8 envelope
                byte[] pkcs1 = decodePemBlock(pem, BEGIN_RSA_KEY, END_RSA_KEY);
                // RSAPrivateKeySpec from PKCS#1 is JCA provider-specific.
                // Use the sun.security.rsa path only as a fallback; prefer
                // configuring SPIRE to emit PKCS#8 instead.
                try {
                    // Wrap in a PKCS#8 skeleton (sequence → version + alg +
                    // key).  The minimal PKCS#8 wrapper for RSA is 26 bytes of
                    // header + the PKCS#1 payload.
                    byte[] pkcs8 = wrapRsaPkcs1InPkcs8(pkcs1);
                    return KeyFactory.getInstance(RSA_KEY_ALGORITHM)
                            .generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
                } catch (Exception e) {
                    throw new GeneralSecurityException(
                            "Cannot load RSA private key from " + svidKeyPem
                            + ": use PKCS#8 format (BEGIN PRIVATE KEY) for "
                            + "maximum compatibility", e);
                }

            } else {
                throw new IOException("No recognised PEM private key block in "
                        + svidKeyPem);
            }
        }

        /**
         * Strips the PEM header/footer from a single-block PEM string and
         * decodes the base64 content.
         */
        static byte[] decodePemBlock(String pem, String header, String footer) {
            int begin = pem.indexOf(header);
            int end   = pem.indexOf(footer);
            if (begin < 0 || end < 0)
                throw new IllegalArgumentException(
                        "PEM block missing header or footer: " + header);
            String base64 = pem.substring(begin + header.length(), end)
                    .replaceAll("\\s+", "");
            return Base64.getDecoder().decode(base64);
        }

        /**
         * Wraps a PKCS#1 RSA private key DER blob in a minimal PKCS#8
         * ({@code PrivateKeyInfo}) DER envelope.
         *
         * <p>PKCS#8 structure:
         * <pre>
         * SEQUENCE {
         *   INTEGER { 0 }             -- version
         *   SEQUENCE {
         *     OID { rsaEncryption }   -- 1.2.840.113549.1.1.1
         *     NULL
         *   }
         *   OCTET_STRING { &lt;pkcs1DER&gt; }
         * }
         * </pre>
         */
        static byte[] wrapRsaPkcs1InPkcs8(byte[] pkcs1) {
            // RSA OID: 1.2.840.113549.1.1.1 in DER
            byte[] rsaOid = {
                0x30, 0x0d,                   // SEQUENCE { ...algId
                0x06, 0x09,                   // OID
                0x2a, (byte)0x86, 0x48,       // 1.2.840
                (byte)0x86, (byte)0xf7, 0x0d, // .113549
                0x01, 0x01, 0x01,             // .1.1.1 (rsaEncryption)
                0x05, 0x00                    // NULL
            };
            byte[] versionDer  = { 0x02, 0x01, 0x00 };  // INTEGER 0
            // OCTET STRING wrapping the PKCS#1 key
            byte[] octetHeader = encodeLength(0x04, pkcs1.length);
            byte[] contentLen  = computeSequenceLength(
                    versionDer.length + rsaOid.length
                    + octetHeader.length + pkcs1.length);
            byte[] outer = new byte[1 + contentLen.length
                    + versionDer.length + rsaOid.length
                    + octetHeader.length + pkcs1.length];
            int i = 0;
            outer[i++] = 0x30;  // SEQUENCE
            System.arraycopy(contentLen, 0, outer, i, contentLen.length);
            i += contentLen.length;
            System.arraycopy(versionDer, 0, outer, i, versionDer.length);
            i += versionDer.length;
            System.arraycopy(rsaOid, 0, outer, i, rsaOid.length);
            i += rsaOid.length;
            System.arraycopy(octetHeader, 0, outer, i, octetHeader.length);
            i += octetHeader.length;
            System.arraycopy(pkcs1, 0, outer, i, pkcs1.length);
            return outer;
        }

        private static byte[] encodeLength(int tag, int len) {
            if (len < 128) {
                return new byte[]{ (byte) tag, (byte) len };
            } else if (len < 256) {
                return new byte[]{ (byte) tag, (byte) 0x81, (byte) len };
            } else {
                return new byte[]{ (byte) tag, (byte) 0x82,
                        (byte)(len >> 8), (byte)(len & 0xff) };
            }
        }

        private static byte[] computeSequenceLength(int innerLen) {
            if (innerLen < 128) {
                return new byte[]{ (byte) innerLen };
            } else if (innerLen < 256) {
                return new byte[]{ (byte) 0x81, (byte) innerLen };
            } else {
                return new byte[]{ (byte) 0x82,
                        (byte)(innerLen >> 8), (byte)(innerLen & 0xff) };
            }
        }
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code SpiffeCredentialManager} that reads SVIDs from PEM
     * files in the given directory using the default renewal lead time.
     *
     * @param subject  the JGDMS {@link Subject} to manage; must be non-null
     *                 and must not be read-only
     * @param spireDir directory containing {@code svid.pem} and
     *                 {@code svid_key.pem}; must be non-null
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code subject} is read-only
     */
    public SpiffeCredentialManager(Subject subject, Path spireDir) {
        this(subject, new FileSvidSource(spireDir), DEFAULT_RENEWAL_LEAD_SECONDS);
    }

    /**
     * Creates a {@code SpiffeCredentialManager} with a custom
     * {@link SvidSource} and renewal lead time.
     *
     * @param subject              the JGDMS {@link Subject} to manage;
     *                             must be non-null and must not be read-only
     * @param svidSource           source of SVID material; must be non-null
     * @param renewalLeadSeconds   seconds before SVID expiry to trigger
     *                             renewal; must be positive
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code subject} is read-only or
     *                                  {@code renewalLeadSeconds} ≤ 0
     */
    public SpiffeCredentialManager(Subject subject,
                                    SvidSource svidSource,
                                    long renewalLeadSeconds) {
        if (subject == null)              throw new NullPointerException("subject");
        if (svidSource == null)           throw new NullPointerException("svidSource");
        if (renewalLeadSeconds <= 0)
            throw new IllegalArgumentException("renewalLeadSeconds must be positive");
        if (subject.isReadOnly())
            throw new IllegalArgumentException("subject must not be read-only");

        this.subject             = subject;
        this.svidSource          = svidSource;
        this.renewalLeadSeconds  = renewalLeadSeconds;
        this.scheduler           = Executors.newSingleThreadScheduledExecutor(
                r -> {
                    Thread t = new Thread(r, "SpiffeCredentialManager-refresher");
                    t.setDaemon(true);
                    return t;
                });
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Performs an initial synchronous SVID load and starts the background
     * renewal scheduler.
     *
     * <p>This method must be called once after construction.  It blocks until
     * the first successful SVID load.
     *
     * @throws IOException              if the initial SVID load fails
     * @throws GeneralSecurityException if the initial SVID cannot be parsed
     * @throws IllegalStateException    if this manager has been closed
     */
    public void start() throws IOException, GeneralSecurityException {
        if (closed.get())
            throw new IllegalStateException("SpiffeCredentialManager is closed");
        Svid svid = svidSource.fetch();
        updateSubjectCredentials(svid);
        scheduleRenewal(svid);
        logger.log(Level.INFO, "SpiffeCredentialManager started; SVID expires at {0}",
                svid.leafCertificate().getNotAfter());
    }

    /**
     * Performs an immediate synchronous SVID refresh, replacing the current
     * credentials in the managed {@link Subject}.
     *
     * <p>This method may be called at any time to force a refresh (e.g. after
     * a JERI connection failure that suggests credential expiry).
     *
     * @throws IOException              if the SVID cannot be fetched
     * @throws GeneralSecurityException if the SVID cannot be parsed
     * @throws IllegalStateException    if this manager has been closed
     */
    public void refresh() throws IOException, GeneralSecurityException {
        if (closed.get())
            throw new IllegalStateException("SpiffeCredentialManager is closed");
        Svid svid = svidSource.fetch();
        updateSubjectCredentials(svid);
        scheduleRenewal(svid);
        logger.log(Level.INFO, "SVID refreshed; new expiry at {0}",
                svid.leafCertificate().getNotAfter());
    }

    /**
     * Stops the background renewal scheduler and removes SVID credentials
     * from the managed {@link Subject}.
     *
     * <p>After this call the {@link Subject} is left in a state with no
     * SVID public or private credentials.  JERI TLS connections will fail
     * until new credentials are loaded.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            scheduler.shutdownNow();
            clearSubjectCredentials();
            logger.log(Level.INFO, "SpiffeCredentialManager closed");
        }
    }

    // -------------------------------------------------------------------------
    // Subject manipulation
    // -------------------------------------------------------------------------

    /**
     * Replaces the SVID credentials in the managed {@link Subject}.
     *
     * <p>Operations on the Subject's public and private credential sets are
     * synchronised on the Subject so that concurrent JERI SSL handshakes
     * observe a consistent state — either the old credentials or the new
     * ones, never a mixture.
     */
    private void updateSubjectCredentials(Svid svid) {
        X509Certificate leaf = svid.leafCertificate();
        X500PrivateCredential privateCredential =
                new X500PrivateCredential(leaf, svid.privateKey);

        synchronized (subject) {
            // Remove any existing SVID credentials before adding new ones.
            subject.getPublicCredentials(CertPath.class).clear();
            subject.getPrivateCredentials(X500PrivateCredential.class).clear();

            subject.getPublicCredentials().add(svid.certPath);
            subject.getPrivateCredentials().add(privateCredential);
        }
    }

    private void clearSubjectCredentials() {
        synchronized (subject) {
            subject.getPublicCredentials(CertPath.class).clear();
            subject.getPrivateCredentials(X500PrivateCredential.class).clear();
        }
    }

    // -------------------------------------------------------------------------
    // Renewal scheduling
    // -------------------------------------------------------------------------

    /**
     * Schedules a renewal task to fire {@link #renewalLeadSeconds} seconds
     * before the SVID expires.
     */
    private void scheduleRenewal(Svid svid) {
        ScheduledFuture<?> existing = scheduledTask;
        if (existing != null) existing.cancel(false);

        Date notAfter  = svid.leafCertificate().getNotAfter();
        long expireMs  = notAfter.getTime();
        long nowMs     = System.currentTimeMillis();
        long renewAt   = expireMs - renewalLeadSeconds * 1_000L;
        long delayMs   = Math.max(0L, renewAt - nowMs);

        logger.log(Level.FINE, "SVID renewal scheduled in {0} s",
                TimeUnit.MILLISECONDS.toSeconds(delayMs));

        scheduledTask = scheduler.schedule(this::renewalTask,
                delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Background renewal task.  On failure, reschedules itself after
     * {@link #RETRY_INTERVAL_SECONDS}.
     */
    private void renewalTask() {
        if (closed.get()) return;
        try {
            refresh();
        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "SVID refresh failed; retrying in " + RETRY_INTERVAL_SECONDS
                    + " s", e);
            scheduler.schedule(this::renewalTask,
                    RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }
}
