/*
 * Copyright 2018 The Apache Software Foundation.
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
package net.jini.loader.pref;

import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLPermission;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Permission;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.UnresolvedPermission;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.constraint.StringMethodConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.io.context.IntegrityEnforcement;
import net.jini.io.context.ServerSubject;
import net.jini.loader.DownloadPermission;
import net.jini.loader.ProxyCodebaseSpi;
import net.jini.security.Security;
import org.apache.river.api.net.Uri;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;
import org.apache.river.concurrent.RC;
import org.apache.river.concurrent.Ref;
import org.apache.river.concurrent.Referrer;

/**
 * This provider allows the use of preferred classes for services configured
 * to use AtomicILFactory.
 * 
 * Firstly if a service proxy returns to a node from which it was exported, 
 * it will always be loaded by the ClassLoader from which it was exported,
 * in this case it is the responsibility of the local environment to ensure
 * visibility.
 * 
 * Secondly if a service proxy is unmarshalled by itself, it will not create
 * a new ClassLoader, it must confirm the annotation doesn't match the 
 * marshalling stream's loader.
 * 
 * Thirdly, if a proxy has been unmarshalled previously, has an equal
 * InvocationHandler, annotations and parent loader, it will be loaded by
 * the cached ClassLoader.
 * 
 * Otherwise a new ClassLoader will be created with the stream ClassLoader as 
 * it's parent (the client that's unmarshalled it).
 * 
 * @author peter
 */
public class PreferredProxyCodebaseProvider implements ProxyCodebaseSpi {

    private static final Logger logger =
            Logger.getLogger(PreferredProxyCodebaseProvider.class.getName());

    private static final ConcurrentMap<Key,ClassLoader> CACHE;
    private static final ConcurrentMap<Key,ClassLoader> SERVICES_EXP;

    /**
     * Permission required to call {@link #setVerdictRegistry(VerdictRegistry)}.
     * Callers that do not hold {@code RuntimePermission("setVerdictRegistry")}
     * will receive a {@link SecurityException} when a security manager is
     * installed.
     */
    private static final Permission SET_VERDICT_REGISTRY_PERMISSION =
            new RuntimePermission("setVerdictRegistry");
    static final int VERDICT_RETRY_ATTEMPTS = 3;
    static final long DEFAULT_VERDICT_RETRY_BASE_DELAY_MS = 1000L;
    static final String MAX_CONCURRENT_JAR_LOADS_PROPERTY = "jgdms.proxy.maxConcurrentJarLoads";
    static final int DEFAULT_MAX_CONCURRENT_JAR_LOADS = 4;

    /** Maximum number of JARs accepted in a single codebase (DoS guard). */
    static final String MAX_CODEBASE_JARS_PROPERTY = "jgdms.proxy.maxCodebaseJars";
    static final int DEFAULT_MAX_CODEBASE_JARS = 100;

    /**
     * Maximum bytes read from a single JAR URL when computing its digest
     * (DoS guard — prevents an enormous stream from consuming unbounded CPU
     * time).  Default is 512 MiB.
     */
    static final String MAX_JAR_BYTES_PROPERTY = "jgdms.proxy.maxJarBytes";
    static final long DEFAULT_MAX_JAR_BYTES = 512L * 1024L * 1024L;

    /**
     * Connect and read timeout in milliseconds applied to JAR URL connections
     * opened during digest computation (DoS guard — prevents an unresponsive
     * server from blocking the thread indefinitely).  Default is 30 seconds.
     */
    static final String JAR_READ_TIMEOUT_MS_PROPERTY = "jgdms.proxy.jarReadTimeoutMs";
    static final int DEFAULT_JAR_READ_TIMEOUT_MS = 30_000;

    private static volatile long verdictRetryBaseDelayMs = DEFAULT_VERDICT_RETRY_BASE_DELAY_MS;
    private static final Semaphore JAR_LOAD_SEMAPHORE =
            new Semaphore(loadMaxConcurrentJarLoads(), true);
    private static final int maxCodebaseJars = loadMaxCodebaseJars();
    private static final long maxJarBytes = loadMaxJarBytes();
    private static final int jarReadTimeoutMs = loadJarReadTimeoutMs();

    /**
     * System property that controls the in-memory verdict cache TTL in
     * milliseconds.  When the {@link VerdictRegistry} is unreachable (all
     * retry attempts exhausted), a cached verdict whose age is within this
     * window is re-used instead of throwing an {@link IOException}.
     * Default: 300 000 ms (5 minutes).
     */
    static final String VERDICT_CACHE_TTL_MS_PROPERTY = "jgdms.proxy.verdictCacheTtlMs";
    static final long DEFAULT_VERDICT_CACHE_TTL_MS = 300_000L;

    /** In-memory signed-verdict cache keyed by SHA-256 hex digest string. */
    static final ConcurrentHashMap<String, CachedVerdict> VERDICT_CACHE =
            new ConcurrentHashMap<>();
    private static final long verdictCacheTtlMs = loadVerdictCacheTtlMs();

    /**
     * System property that controls strict mode for
     * {@link VerdictType#INCONCLUSIVE} JAR loads.  Strict mode is
     * <strong>enabled by default</strong>: {@link #checkVerdictForJar} demands
     * an {@link INCONCLUSIVEPermit}{@code (contentHash)} from the caller's
     * {@link java.security.AccessControlContext} before proceeding with an
     * INCONCLUSIVE verdict.  Set this property to {@code "false"} to disable
     * strict mode and allow INCONCLUSIVE JARs to load with a WARNING log entry
     * only (not recommended for production).
     *
     * <p>Strict mode requires that a security policy grants
     * {@code INCONCLUSIVEPermit} to every trusted code path that may load
     * proxies with INCONCLUSIVE JARs.  The wildcard form
     * ({@code INCONCLUSIVEPermit "*"}) can be used during a transition
     * period; per-digest grants are the recommended long-term approach.
     */
    static final String INCONCLUSIVE_STRICT_MODE_PROPERTY = "jgdms.proxy.inconclusiveStrictMode";

    /**
     * Whether INCONCLUSIVE-verdict loads require an explicit
     * {@link INCONCLUSIVEPermit}.  Volatile so that tests can toggle the
     * value without reloading the class (see {@link #setInconclusiveStrictMode}).
     */
    static volatile boolean inconclusiveStrictMode = loadInconclusiveStrictMode();

    /**
     * Constructor for {@code java.security.DigestCodeSource(CodeSource, String)},
     * reflectively resolved at class-load time (DirtyChai only; {@code null} on
     * standard JDK).  Used by {@link #checkBootstrapPermissionByDigest} to avoid
     * the call-site catching {@link ClassNotFoundException} on every invocation.
     */
    private static final Constructor<?> DIGEST_CODE_SOURCE_CTOR = probeDigestCodeSourceCtor();

    /** Holds a cached {@link RegistryVerdict} together with its capture timestamp. */
    static final class CachedVerdict {
        final RegistryVerdict verdict;
        final long capturedAtMs;

        CachedVerdict(RegistryVerdict verdict, long capturedAtMs) {
            this.verdict = verdict;
            this.capturedAtMs = capturedAtMs;
        }

        /** Returns {@code true} if this entry is still within the TTL window. */
        boolean isAlive(long nowMs, long ttlMs) {
            return ttlMs > 0 && (nowMs - capturedAtMs) < ttlMs;
        }
    }

    static {
	ConcurrentMap<Referrer<Key>,Referrer<ClassLoader>> intern1 =
                new ConcurrentHashMap<Referrer<Key>,Referrer<ClassLoader>>();
	CACHE = RC.concurrentMap(intern1, Ref.STRONG, Ref.WEAK, 60000L, 60000L);
        intern1 = new ConcurrentHashMap<Referrer<Key>,Referrer<ClassLoader>>();
	SERVICES_EXP = RC.concurrentMap(intern1, Ref.STRONG, Ref.WEAK, 60000L, 60000L);
    }
    
    public PreferredProxyCodebaseProvider(){}

    /**
     * Injects the {@link VerdictRegistry} proxy to be consulted before any
     * new {@link PreferredClassLoader} is created.
     *
     * <p>This method should be called once at node startup, after the
     * {@code VerdictRegistry} service has been discovered in the Jini lookup
     * service.  Until it is called the verdict check is <em>skipped</em>
     * (boot-time permissive policy) so that the node can come up before the
     * registry is reachable.  Once set, all subsequent cache-miss codebase
     * loads are guarded by the registry.
     *
     * <p>The field is {@code volatile} so this call is thread-safe without
     * additional synchronization.
     *
     * <p>If a security manager is installed, this method demands
     * {@code RuntimePermission("setVerdictRegistry")} from the caller. This
     * prevents untrusted code from replacing or nullifying the registry
     * (which would re-enable the boot-time permissive policy and bypass
     * verdict enforcement).
     *
     * @param registry the {@link VerdictRegistry} proxy to use; may be
     *                 {@code null} to disable verdict checking
     * @throws SecurityException if a security manager is installed and the
     *         caller does not hold
     *         {@code RuntimePermission("setVerdictRegistry")}
     */
    public static void setVerdictRegistry(VerdictRegistry registry) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_VERDICT_REGISTRY_PERMISSION);
        }
        VerdictRegistryHolder.set(registry);
    }

    static void setVerdictRetryBaseDelayMs(long retryBaseDelayMs) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_VERDICT_REGISTRY_PERMISSION);
        }
        verdictRetryBaseDelayMs = retryBaseDelayMs;
    }

    static void resetVerdictRetryBaseDelayMs() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_VERDICT_REGISTRY_PERMISSION);
        }
        verdictRetryBaseDelayMs = DEFAULT_VERDICT_RETRY_BASE_DELAY_MS;
    }

    /**
     * Overrides the runtime value of {@link #inconclusiveStrictMode}.
     * For use in tests only.
     */
    static void setInconclusiveStrictMode(boolean strict) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_VERDICT_REGISTRY_PERMISSION);
        }
        inconclusiveStrictMode = strict;
    }

    /**
     * Resets {@link #inconclusiveStrictMode} to the value loaded from the
     * system property at class-load time.  For use in tests only.
     */
    static void resetInconclusiveStrictMode() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_VERDICT_REGISTRY_PERMISSION);
        }
        inconclusiveStrictMode = loadInconclusiveStrictMode();
    }

    /** Clears the in-memory verdict cache.  For use in tests only. */
    static void clearVerdictCache() {
        VERDICT_CACHE.clear();
    }

    static int parseMaxConcurrentJarLoads(String value) {
        if (value == null) {
            return DEFAULT_MAX_CONCURRENT_JAR_LOADS;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_MAX_CONCURRENT_JAR_LOADS;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ex) {
            // fall back to default below
        }
        logger.log(Level.WARNING,
                "Invalid {0} value: {1}; using default {2}",
                new Object[]{
                    MAX_CONCURRENT_JAR_LOADS_PROPERTY,
                    value,
                    Integer.valueOf(DEFAULT_MAX_CONCURRENT_JAR_LOADS)
                });
        return DEFAULT_MAX_CONCURRENT_JAR_LOADS;
    }

    private static int loadMaxConcurrentJarLoads() {
        String value = null;
        try {
            value = System.getProperty(MAX_CONCURRENT_JAR_LOADS_PROPERTY);
        } catch (SecurityException ex) {
            logger.log(Level.WARNING,
                    "Unable to read {0}; using default {1}",
                    new Object[]{
                        MAX_CONCURRENT_JAR_LOADS_PROPERTY,
                        Integer.valueOf(DEFAULT_MAX_CONCURRENT_JAR_LOADS)
                    });
            return DEFAULT_MAX_CONCURRENT_JAR_LOADS;
        }
        return parseMaxConcurrentJarLoads(value);
    }

    static int parseMaxCodebaseJars(String value) {
        if (value == null) {
            return DEFAULT_MAX_CODEBASE_JARS;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_MAX_CODEBASE_JARS;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ex) {
            // fall back to default below
        }
        logger.log(Level.WARNING,
                "Invalid {0} value: {1}; using default {2}",
                new Object[]{
                    MAX_CODEBASE_JARS_PROPERTY,
                    value,
                    Integer.valueOf(DEFAULT_MAX_CODEBASE_JARS)
                });
        return DEFAULT_MAX_CODEBASE_JARS;
    }

    private static int loadMaxCodebaseJars() {
        String value = null;
        try {
            value = System.getProperty(MAX_CODEBASE_JARS_PROPERTY);
        } catch (SecurityException ex) {
            logger.log(Level.WARNING,
                    "Unable to read {0}; using default {1}",
                    new Object[]{
                        MAX_CODEBASE_JARS_PROPERTY,
                        Integer.valueOf(DEFAULT_MAX_CODEBASE_JARS)
                    });
            return DEFAULT_MAX_CODEBASE_JARS;
        }
        return parseMaxCodebaseJars(value);
    }

    static long parseMaxJarBytes(String value) {
        if (value == null) {
            return DEFAULT_MAX_JAR_BYTES;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_MAX_JAR_BYTES;
        }
        try {
            long parsed = Long.parseLong(trimmed);
            if (parsed > 0L) {
                return parsed;
            }
        } catch (NumberFormatException ex) {
            // fall back to default below
        }
        logger.log(Level.WARNING,
                "Invalid {0} value: {1}; using default {2}",
                new Object[]{
                    MAX_JAR_BYTES_PROPERTY,
                    value,
                    Long.valueOf(DEFAULT_MAX_JAR_BYTES)
                });
        return DEFAULT_MAX_JAR_BYTES;
    }

    private static long loadMaxJarBytes() {
        String value = null;
        try {
            value = System.getProperty(MAX_JAR_BYTES_PROPERTY);
        } catch (SecurityException ex) {
            logger.log(Level.WARNING,
                    "Unable to read {0}; using default {1}",
                    new Object[]{
                        MAX_JAR_BYTES_PROPERTY,
                        Long.valueOf(DEFAULT_MAX_JAR_BYTES)
                    });
            return DEFAULT_MAX_JAR_BYTES;
        }
        return parseMaxJarBytes(value);
    }

    static int parseJarReadTimeoutMs(String value) {
        if (value == null) {
            return DEFAULT_JAR_READ_TIMEOUT_MS;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_JAR_READ_TIMEOUT_MS;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ex) {
            // fall back to default below
        }
        logger.log(Level.WARNING,
                "Invalid {0} value: {1}; using default {2}",
                new Object[]{
                    JAR_READ_TIMEOUT_MS_PROPERTY,
                    value,
                    Integer.valueOf(DEFAULT_JAR_READ_TIMEOUT_MS)
                });
        return DEFAULT_JAR_READ_TIMEOUT_MS;
    }

    private static int loadJarReadTimeoutMs() {
        String value = null;
        try {
            value = System.getProperty(JAR_READ_TIMEOUT_MS_PROPERTY);
        } catch (SecurityException ex) {
            logger.log(Level.WARNING,
                    "Unable to read {0}; using default {1}",
                    new Object[]{
                        JAR_READ_TIMEOUT_MS_PROPERTY,
                        Integer.valueOf(DEFAULT_JAR_READ_TIMEOUT_MS)
                    });
            return DEFAULT_JAR_READ_TIMEOUT_MS;
        }
        return parseJarReadTimeoutMs(value);
    }

    static long parseVerdictCacheTtlMs(String value) {
        if (value == null) {
            return DEFAULT_VERDICT_CACHE_TTL_MS;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_VERDICT_CACHE_TTL_MS;
        }
        try {
            long parsed = Long.parseLong(trimmed);
            if (parsed >= 0) {
                return parsed;
            }
        } catch (NumberFormatException ex) {
            // fall back to default below
        }
        logger.log(Level.WARNING,
                "Invalid {0} value: {1}; using default {2}",
                new Object[]{
                    VERDICT_CACHE_TTL_MS_PROPERTY,
                    value,
                    Long.valueOf(DEFAULT_VERDICT_CACHE_TTL_MS)
                });
        return DEFAULT_VERDICT_CACHE_TTL_MS;
    }

    private static long loadVerdictCacheTtlMs() {
        String value = null;
        try {
            value = System.getProperty(VERDICT_CACHE_TTL_MS_PROPERTY);
        } catch (SecurityException ex) {
            logger.log(Level.WARNING,
                    "Unable to read {0}; using default {1}",
                    new Object[]{
                        VERDICT_CACHE_TTL_MS_PROPERTY,
                        Long.valueOf(DEFAULT_VERDICT_CACHE_TTL_MS)
                    });
            return DEFAULT_VERDICT_CACHE_TTL_MS;
        }
        return parseVerdictCacheTtlMs(value);
    }

    /**
     * Parses the value of the {@value #INCONCLUSIVE_STRICT_MODE_PROPERTY}
     * system property.  Returns {@code false} if and only if {@code value} is
     * (case-insensitively) {@code "false"} after trimming; returns {@code true}
     * for {@code null}, empty, or any other string (strict mode is the
     * default).
     *
     * @param value the raw property string, or {@code null}
     * @return the parsed boolean
     */
    static boolean parseInconclusiveStrictMode(String value) {
        return !"false".equalsIgnoreCase(value == null ? null : value.trim());
    }

    private static boolean loadInconclusiveStrictMode() {
        String value = null;
        try {
            value = System.getProperty(INCONCLUSIVE_STRICT_MODE_PROPERTY);
        } catch (SecurityException ex) {
            logger.log(Level.WARNING,
                    "Unable to read {0}; defaulting to strict mode",
                    INCONCLUSIVE_STRICT_MODE_PROPERTY);
            return true;
        }
        return parseInconclusiveStrictMode(value);
    }

    /**
     * Opens a connection to the given URL with the configured connect and read
     * timeouts applied, then returns its input stream.
     *
     * <p>Using a timeout prevents a slow or unresponsive server from blocking
     * the calling thread indefinitely (denial-of-service guard).
     *
     * @param url the URL to connect to
     * @return the input stream for reading the resource
     * @throws IOException if the connection cannot be established or times out
     */
    private static InputStream openUrlWithTimeout(final URL url) throws IOException {
        // doPrivileged stops the stack-walk at this trusted frame so that
        // unprivileged proxy/deserialisation frames above us do not cause
        // AccessController.doPrivilegedWithCombiner (inside HttpURLConnection)
        // to fail the SecurityPermission("createAccessControlContext") check.
        try {
            return AccessController.doPrivileged((PrivilegedAction<InputStream>) () -> {
                try {
                    URLConnection conn = url.openConnection();
                    conn.setConnectTimeout(jarReadTimeoutMs);
                    conn.setReadTimeout(jarReadTimeoutMs);
                    return conn.getInputStream();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException) throw (IOException) ex.getCause();
            throw ex;
        }
    }

    private static boolean containsJarCodebase(URL[] codebase) {
        for (int index = 0, length = codebase.length; index < length; index++) {
            if (!isDirectory(codebase[index])) {
                return true;
            }
        }
        return false;
    }

    private static boolean acquireJarLoadPermitIfNeeded(URL[] codebase, String path)
            throws IOException {
        if (!containsJarCodebase(codebase)) {
            return false;
        }
        try {
            JAR_LOAD_SEMAPHORE.acquire();
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Interrupted while waiting for codebase download slot: " + path, ex);
        }
    }

    /**
     * Computes the SCAP content hash of the JAR accessible at the given URL.
     *
     * <p>Per JGDMS-STD-002 v1.3 the client computes the content hash as
     * {@code SHA-256(rawDownloadedBytes)} — a plain SHA-256 over the bytes
     * pulled from {@code jarUrl}, with no normalisation step.  Producers run
     * {@code net.pack200.Normalize} at build time via the
     * {@code pack200-normalize-maven-plugin}, so the published artifact IS the
     * canonical form {@code C} and {@code SHA-256(rawBytes) ==
     * SHA-256(C) == contentHash} that Host 4 stored in the
     * {@link VerdictRegistry}.  A third-party JAR that the publisher forgot to
     * pre-normalise will simply miss the verdict lookup; the fail-closed gate
     * then refuses to load it (operationally strict — see
     * {@link #checkVerdictForJar}).
     *
     * <p>The URL must point directly to a JAR file (not a directory).
     *
     * @param jarUrl the URL of the JAR to hash
     * @return lowercase hexadecimal SHA-256 digest of the raw JAR bytes
     *         (64 characters)
     * @throws IOException if the JAR cannot be read or SHA-256 is unavailable
     */
    static String computeJarHash(URL jarUrl) throws IOException {
        byte[] rawJarBytes = readJarBytesBounded(jarUrl);

        // SCAP v1.3: client hashes the RAW downloaded bytes.  Producers
        // pre-normalise codebase JARs at build time (pack200-normalize-maven-plugin),
        // so for any properly-shipped JAR rawBytes == canonical C and this hash
        // equals the contentHash Host 4 recorded.  An unstamped third-party JAR
        // will simply miss the lookup → fail-closed refusal in checkVerdictForJar.
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 MessageDigest not available", e);
        }
        return bytesToHex(digest.digest(rawJarBytes));
    }

    /**
     * Reads the full content of the JAR at {@code jarUrl} into a byte array,
     * applying the configured connect/read timeouts and the
     * {@link #maxJarBytes} size cap (DoS guards).
     *
     * @param jarUrl the URL of the JAR to read
     * @return the raw JAR bytes
     * @throws IOException if the JAR cannot be read or exceeds the size cap
     */
    private static byte[] readJarBytesBounded(URL jarUrl) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = openUrlWithTimeout(jarUrl)) {
            byte[] buf = new byte[8192];
            int n;
            long totalRead = 0L;
            while ((n = in.read(buf)) > 0) {
                totalRead += n;
                if (totalRead > maxJarBytes) {
                    throw new IOException(
                            "JAR at " + jarUrl + " exceeds maximum allowed size of "
                            + maxJarBytes + " bytes during hash computation");
                }
                out.write(buf, 0, n);
            }
        }
        return out.toByteArray();
    }

    /**
     * Converts a byte array to a lowercase hexadecimal string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * Checks whether the given server principals are granted
     * {@link BootstrapPermission}{@code ("loadCodebase")} by the current
     * security policy.
     *
     * <p><b>Implementation note:</b> This check is performed via
     * {@link net.jini.security.Security#checkPermission(java.security.Permission,
     * java.security.ProtectionDomain...)} rather than calling
     * {@link java.security.Policy#implies Policy.implies(ProtectionDomain, Permission)}
     * directly.  Routing through the {@link SecurityManager} ensures that
     * policy-audit security managers (e.g. {@code SecurityPolicyWriter} /
     * {@code polpAudit}) observe the check and can record the required
     * {@code BootstrapPermission} during policy-update runs.  An
     * {@link java.security.AccessControlContext} containing a freshly-constructed
     * {@link ProtectionDomain} carrying the server principals is built via
     * {@code AccessControlContext.create()} on DirtyChai (virtual-thread-safe)
     * or via the standard constructor on OpenJDK, keeping compile-time
     * compatibility with both runtimes.
     *
     * @param serverPrincipals the authenticated server principals (from the
     *                         TLS-layer {@link net.jini.io.context.ServerSubject})
     * @param path             the codebase annotation string (for messages)
     * @throws SecurityException if the policy does not grant
     *                           {@code BootstrapPermission} to the principals
     */
    private static void checkBootstrapPermission(Principal[] serverPrincipals,
                                                   String path) {
        ProtectionDomain serverDomain =
                new ProtectionDomain(null, null, null, serverPrincipals);
        try {
            Security.checkPermission(
                    new BootstrapPermission(BootstrapPermission.TARGET_NAME),
                    serverDomain);
        } catch (SecurityException e) {
            logger.log(Level.SEVERE,
                    "Server principal denied BootstrapPermission;"
                    + " refusing codebase: {0}",
                    path);
            throw new SecurityException(
                    "Server principal denied BootstrapPermission;"
                    + " refusing codebase: " + path);
        }
    }

    /**
     * Reflective probe for {@code java.security.DigestCodeSource(CodeSource, String)}.
     * Returns the {@link Constructor} if running on DirtyChai, {@code null} otherwise.
     */
    private static Constructor<?> probeDigestCodeSourceCtor() {
        try {
            Class<?> cls = Class.forName("java.security.DigestCodeSource");
            return cls.getConstructor(CodeSource.class, String.class);
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            // Standard JDK — DigestCodeSource not available.
            return null;
        }
    }

    /**
     * Performs a {@link BootstrapPermission} check for a codebase that arrived
     * during the boot window <em>without</em> a SPIFFE server principal.
     *
     * <p>When running on DirtyChai the check uses
     * {@code java.security.DigestCodeSource} (resolved reflectively via
     * {@link #DIGEST_CODE_SOURCE_CTOR}) so that policy entries may be written
     * against JAR digests rather than URLs, providing cryptographic identity
     * assurance even without a TLS-level SPIFFE principal.  On a standard JDK
     * (where {@code DigestCodeSource} is absent) a plain {@link CodeSource}
     * with only the JAR URL is used, allowing URL-based policy entries.  If
     * neither flavour can be granted {@code BootstrapPermission} by the
     * installed policy a {@link SecurityException} is thrown — the codebase is
     * refused.
     *
     * <p>If this JVM is a standard JDK (i.e. {@link #DIGEST_CODE_SOURCE_CTOR}
     * is {@code null}) and the DigestCodeSource-construction fallback also
     * fails, the method logs a warning and returns permissively so that
     * deployments that have not yet adopted DirtyChai are not broken.
     *
     * @param codebase        the JAR URLs that form the service proxy codebase
     * @param serverPrincipals the TLS-authenticated server principals, or
     *                        {@code null} / empty if not available
     * @param path            codebase annotation string (for log messages)
     * @param hashLog         pre-computed SHA-256 audit string (for log messages)
     * @throws SecurityException if the policy denies {@code BootstrapPermission}
     *                           on DirtyChai
     */
    private static void checkBootstrapPermissionByDigest(URL[] codebase,
                                                         Principal[] serverPrincipals,
                                                         String path,
                                                         String hashLog) {
        if (DIGEST_CODE_SOURCE_CTOR == null) {
            // Standard JDK: DigestCodeSource unavailable — maintain permissive
            // boot-window behaviour.
            logger.log(Level.WARNING,
                    "Boot window: VerdictRegistry not set and no SPIFFE principal;"
                    + " DigestCodeSource unavailable (standard JDK) — proceeding"
                    + " permissively; codebase: {0}; SHA-256: {1}",
                    new Object[]{path, hashLog});
            return;
        }

        Principal[] principals = (serverPrincipals != null && serverPrincipals.length > 0)
                ? serverPrincipals : new Principal[0];
        BootstrapPermission bootPerm =
                new BootstrapPermission(BootstrapPermission.TARGET_NAME);

        for (URL jarUrl : codebase) {
            if (isDirectory(jarUrl)) {
                continue;
            }
            CodeSource cs;
            try {
                // DigestCodeSource(CodeSource, String) downloads the JAR and
                // computes its SHA-256 digest — this re-download is intentional:
                // the resulting DigestCodeSource lets the policy verify the JAR
                // by cryptographic digest rather than URL alone.
                CodeSource plain = new CodeSource(jarUrl,
                       (java.security.cert.Certificate[]) null);
                cs = (CodeSource) DIGEST_CODE_SOURCE_CTOR.newInstance(plain, "SHA-256");
            } catch (InvocationTargetException ex) {
                // IOException / NoSuchAlgorithmException from the constructor.
                logger.log(Level.WARNING,
                       "Boot window: DigestCodeSource construction failed for {0};"
                       + " falling back to URL-based BootstrapPermission check;"
                       + " cause: {1}",
                       new Object[]{jarUrl, ex.getCause()});
                cs = new CodeSource(jarUrl, (java.security.cert.Certificate[]) null);
            } catch (ReflectiveOperationException ex) {
                logger.log(Level.WARNING,
                       "Boot window: reflection failure creating DigestCodeSource for {0};"
                       + " falling back to URL-based check; cause: {1}",
                       new Object[]{jarUrl, ex});
                cs = new CodeSource(jarUrl, (java.security.cert.Certificate[]) null);
            }

            ProtectionDomain digestPd = new ProtectionDomain(cs, null, null, principals);
            boolean granted;
            try {
                // Preferred (stronger) form: the policy grants BootstrapPermission
                // scoped to this JAR's cryptographic digest (a DigestGrant).
                Security.checkPermission(bootPerm, digestPd);
                granted = true;
            } catch (SecurityException e) {
                granted = false;
            }
            if (!granted) {
                // Honour a non-digest BootstrapPermission grant.  The installed
                // policy may grant BootstrapPermission by URL or unconditionally
                // rather than by JAR digest; a digest-bearing DigestCodeSource
                // domain does not pick up such grants.  Re-check against a plain
                // CodeSource so the policy is honoured rather than silently
                // overridden into a breakage.  Running without digest-scoped
                // grants is weaker and not recommended, hence the WARNING.
                ProtectionDomain plainPd = new ProtectionDomain(
                        new CodeSource(jarUrl, (java.security.cert.Certificate[]) null),
                        null, null, principals);
                try {
                    Security.checkPermission(bootPerm, plainPd);
                    granted = true;
                    logger.log(Level.WARNING,
                           "Boot window: JAR {0} permitted by a non-digest"
                           + " BootstrapPermission grant (digest-scoped grants are"
                           + " recommended); codebase: {1}; SHA-256: {2}",
                           new Object[]{jarUrl, path, hashLog});
                } catch (SecurityException e) {
                    granted = false;
                }
            }
            if (!granted) {
                logger.log(Level.SEVERE,
                       "Boot window: JAR denied BootstrapPermission (digest/URL check);"
                       + " refusing codebase: {0}; jar: {1}; SHA-256: {2}",
                       new Object[]{path, jarUrl, hashLog});
                throw new SecurityException(
                       "Boot window: JAR denied BootstrapPermission;"
                       + " refusing codebase: " + path);
            }
        }

        logger.log(Level.INFO,
                "Boot window: all JARs passed BootstrapPermission (digest/URL check);"
                + " codebase: {0}; SHA-256: {1}",
                new Object[]{path, hashLog});
    }

    /**
     * Attempts to make a best-effort {@link PermissionGrant} scoped to the
     * given codebase digest, granting {@link DownloadPermission},
     * {@link URLPermission} for each JAR URL, and an
     * {@link UnresolvedPermission} for {@code net.jini.loader.LoadClassPermission}
     * (the last permission only takes effect on DirtyChai JVMs that carry a
     * {@code DigestCodeSource}).
     *
     * <p>If the installed policy is not a {@code RevocablePolicy} or the
     * calling context lacks {@link net.jini.security.GrantPermission}, the
     * grant attempt is silently skipped.
     *
     * @param algorithm  the digest algorithm used (e.g. {@code "SHA-256"})
     * @param digest     the codebase digest bytes
     * @param codebase   the JAR/directory URLs
     */
    private static void tryGrantDigestGrant(String algorithm,
                                            byte[] digest,
                                            URL[] codebase) {
        try {
            List<Permission> perms = new ArrayList<Permission>();
            perms.add(new DownloadPermission());
            for (URL url : codebase) {
                if (!isDirectory(url)) {
                    try {
                        perms.add(new URLPermission(url.toString()));
                    } catch (Exception ex) {
                        logger.log(Level.FINE,
                                "Could not create URLPermission for {0}", url);
                    }
                }
            }
            // LoadClassPermission lives in DirtyChai; use UnresolvedPermission
            // so that the grant is recorded even when running on a standard JVM.
            perms.add(new UnresolvedPermission(
                    "net.jini.loader.LoadClassPermission", null, null, null));
            PermissionGrant grant = PermissionGrantBuilder.newBuilder()
                    .context(PermissionGrantBuilder.DIGEST)
                    .digest(algorithm, digest)
                    .permissions(perms.toArray(new Permission[0]))
                    .build();
            Security.grant(grant);
        } catch (UnsupportedOperationException ex) {
            logger.log(Level.FINE,
                    "DigestGrant skipped: policy does not support revocable grants");
        } catch (SecurityException ex) {
            logger.log(Level.FINE,
                    "DigestGrant skipped: calling context lacks GrantPermission: {0}",
                    ex.getMessage());
        }
    }

    /**
     * Computes the content digest of each non-directory JAR URL in the given
     * codebase, in order.
     *
     * <p>Element {@code i} of the returned array is the raw digest bytes of
     * the {@code i}-th non-directory JAR, computed with the given
     * {@code algorithm}.  Directory URLs are skipped.
     *
     * @param codebase  the JAR/directory URLs
     * @param algorithm the digest algorithm (e.g. {@code "SHA-256"})
     * @return array of per-JAR digest byte arrays (one per non-directory JAR,
     *         in codebase order)
     * @throws IOException if a JAR cannot be read or the algorithm is unknown
     * @see #getDigestOffsets()
     */
    static byte[][] computeIndividualJarDigests(URL[] codebase, String algorithm)
            throws IOException {
        if (codebase.length > maxCodebaseJars) {
            throw new IOException(
                    "Codebase exceeds maximum JAR count ("
                    + maxCodebaseJars + "): " + codebase.length + " URLs");
        }
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(algorithm + " MessageDigest not available", ex);
        }
        List<byte[]> digests = new ArrayList<byte[]>();
        for (URL url : codebase) {
            if (!isDirectory(url)) {
                md.reset();
                try (InputStream in = openUrlWithTimeout(url)) {
                    byte[] buf = new byte[8192];
                    int n;
                    long totalRead = 0L;
                    while ((n = in.read(buf)) > 0) {
                        totalRead += n;
                        if (totalRead > maxJarBytes) {
                            throw new IOException(
                                    "JAR at " + url + " exceeds maximum allowed size of "
                                    + maxJarBytes + " bytes during digest computation");
                        }
                        md.update(buf, 0, n);
                    }
                }
                digests.add(md.digest());
            }
        }
        return digests.toArray(new byte[0][]);
    }

    /**
     * Extracts the {@code i}-th per-JAR digest from a flat digest array using
     * the corresponding offsets array.
     *
     * <p>The last digest (when {@code i == offsets.length - 1}) runs to the
     * end of {@code flat}.
     *
     * @param flat    the flat concatenation of per-JAR digests
     * @param offsets the start byte offsets of each digest in {@code flat}
     * @param i       zero-based index of the digest to extract
     * @return a copy of the bytes for digest {@code i}
     */
    private static byte[] extractJarDigest(byte[] flat, int[] offsets, int i) {
        int start = offsets[i];
        int end = (i + 1 < offsets.length) ? offsets[i + 1] : flat.length;
        return Arrays.copyOfRange(flat, start, end);
    }

    /**
     * Validates that server-supplied flat digest data and byte offsets are
     * consistent and within permitted bounds before they are used.
     *
     * <p>Checks performed:
     * <ul>
     *   <li>The number of offsets does not exceed the configured maximum
     *       ({@link #maxCodebaseJars}).</li>
     *   <li>The flat array length does not exceed
     *       {@code maxCodebaseJars * 512} bytes (generous upper bound for
     *       any standard digest algorithm).</li>
     *   <li>Each offset is non-negative and within the flat array bounds.</li>
     *   <li>Offsets are monotonically non-decreasing (required for correct
     *       slice extraction by {@link #extractJarDigest}).</li>
     * </ul>
     *
     * @param flat    the flat digest array received from the server
     * @param offsets the byte offsets received from the server
     * @throws IOException if any validation check fails, indicating a
     *                     malformed or malicious server response
     */
    static void validateDigestOffsets(byte[] flat, int[] offsets)
            throws IOException {
        if (offsets.length > maxCodebaseJars) {
            throw new IOException(
                    "Server returned too many JAR digest offsets ("
                    + offsets.length + " > max " + maxCodebaseJars + ")");
        }
        // 512 bytes per entry is generous for any standard digest algorithm
        // (SHA-512 produces 64 bytes; SHA-256 produces 32 bytes).
        int maxFlatLen = maxCodebaseJars * 512;
        if (flat.length > maxFlatLen) {
            throw new IOException(
                    "Server flat digest array is too large: "
                    + flat.length + " bytes (max " + maxFlatLen + ")");
        }
        for (int i = 0; i < offsets.length; i++) {
            if (offsets[i] < 0) {
                throw new IOException(
                        "Server digest offset[" + i + "] is negative: "
                        + offsets[i]);
            }
            if (offsets[i] > flat.length) {
                throw new IOException(
                        "Server digest offset[" + i + "] is out of bounds: "
                        + offsets[i] + " > flat.length=" + flat.length);
            }
            if (i > 0 && offsets[i] < offsets[i - 1]) {
                throw new IOException(
                        "Server digest offsets are not monotonically non-decreasing"
                        + " at index " + i + ": offsets[" + (i - 1) + "]="
                        + offsets[i - 1] + " > offsets[" + i + "]="
                        + offsets[i]);
            }
        }
    }

    /**
     * Merges two principal arrays into a single de-duplicated array.
     *
     * <p>Preserves insertion order: {@code first} entries appear before
     * {@code second} entries.  Duplicates (determined by
     * {@link Object#equals}) are silently dropped.
     *
     * @param first  the primary principals (e.g. local SPIFFE identity); may
     *               be {@code null} or empty
     * @param second the secondary principals (e.g. server SPIFFE identity);
     *               may be {@code null} or empty
     * @return a merged array, or {@code null} if both inputs are null/empty
     */
    static Principal[] mergePrincipals(Principal[] first, Principal[] second) {
        boolean firstEmpty  = (first  == null || first.length  == 0);
        boolean secondEmpty = (second == null || second.length == 0);
        if (firstEmpty && secondEmpty) return null;
        if (firstEmpty)  return second.clone();
        if (secondEmpty) return first.clone();
        LinkedHashSet<Principal> merged = new LinkedHashSet<Principal>(
                first.length + second.length);
        for (Principal p : first)  merged.add(p);
        for (Principal p : second) merged.add(p);
        return merged.toArray(new Principal[0]);
    }

    /**
     * Issues one {@link PermissionGrant} per non-directory JAR, scoped to the
     * individual JAR's content digest and optionally restricted to the given
     * principals.
     *
     * <p>Because {@code DigestCodeSource} (DirtyChai) is per-JAR, a single
     * combined digest cannot be matched against any real
     * {@code ProtectionDomain}.  This method issues granular grants — one per
     * JAR — so that the downloaded code can only be defined and loaded on a
     * DirtyChai JVM when the JAR content still matches the server-attested
     * digest.
     *
     * <p>No URL is included in the grant.  The grant matches any code source
     * whose content digest equals the per-JAR digest, regardless of location.
     *
     * <p>If {@code localPrincipals} is non-null and non-empty, each grant is
     * further scoped to those principals (typically the local SPIFFE workload
     * identity).  This means the grant only applies when both the JAR digest
     * matches <em>and</em> the calling context carries the local principal,
     * providing defense-in-depth.
     *
     * <p>If {@code serverPrincipals} is additionally non-null and non-empty,
     * the server's authenticated SPIFFE identity is also required in the grant.
     * This binds each per-JAR {@code DigestGrant} to the specific
     * client↔server pair that attested to those JAR bytes, preventing another
     * service that ships code with the same content digest from reusing the
     * grant (digest-codesource hijacking defence — Option 1).
     *
     * <p>The combined principal set passed to
     * {@link PermissionGrantBuilder#principals} is the union of
     * {@code localPrincipals} and {@code serverPrincipals}, de-duplicated while
     * preserving insertion order.  The grant fires only when the policy
     * evaluation context contains <em>all</em> of those principals.
     *
     * <p>If the installed policy is not a {@code RevocablePolicy} or the
     * calling context lacks {@link net.jini.security.GrantPermission}, the
     * grant attempt is silently skipped.
     *
     * @param algorithm       the digest algorithm (e.g. {@code "SHA-256"})
     * @param perJarDigests   individual per-JAR digest bytes (in codebase order,
     *                        one per non-directory JAR)
     * @param localPrincipals the local client principals to scope the grant to,
     *                        or {@code null} to grant to any principal
     * @param serverPrincipals the authenticated server principals from the TLS
     *                        layer (via {@link net.jini.io.context.ServerSubject});
     *                        when non-null and non-empty these are merged with
     *                        {@code localPrincipals} so that the grant is bound
     *                        to the specific service that attested to the codebase
     */
    private static void tryGrantPerUriDigestGrants(String algorithm,
                                                   byte[][] perJarDigests,
                                                   Principal[] localPrincipals,
                                                   Principal[] serverPrincipals) {
        Principal[] grantPrincipals = mergePrincipals(localPrincipals, serverPrincipals);
        for (int i = 0; i < perJarDigests.length; i++) {
            try {
                List<Permission> perms = new ArrayList<Permission>();
                perms.add(new DownloadPermission());
                // LoadClassPermission lives in DirtyChai; use UnresolvedPermission
                // so that the grant is recorded even on a standard JVM.
                perms.add(new UnresolvedPermission(
                        "net.jini.loader.LoadClassPermission", null, null, null));
                PermissionGrantBuilder builder = PermissionGrantBuilder.newBuilder()
                        .context(PermissionGrantBuilder.DIGEST)
                        .digest(algorithm, perJarDigests[i])
                        .permissions(perms.toArray(new Permission[0]));
                if (grantPrincipals != null && grantPrincipals.length > 0) {
                    builder = builder.principals(grantPrincipals);
                }
                Security.grant(builder.build());
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                            "Per-JAR DigestGrant {0} applied"
                            + " (local+server principals: {1})",
                            new Object[]{i,
                                grantPrincipals != null
                                    ? Arrays.toString(grantPrincipals)
                                    : "none"});
                }
            } catch (UnsupportedOperationException ex) {
                logger.log(Level.FINE,
                        "DigestGrant skipped: policy does not support revocable grants");
            } catch (SecurityException ex) {
                logger.log(Level.FINE,
                        "DigestGrant skipped: calling context lacks GrantPermission: {0}",
                        ex.getMessage());
            }
        }
    }

    /**
     * Checks the verdict for a single JAR against the given
     * {@link VerdictRegistry}.
     *
     * <p>Verdict semantics:
     * <ul>
     *   <li>{@link VerdictType#SAFE} — proceed; logged at {@code FINEST}.</li>
     *   <li>{@link VerdictType#INCONCLUSIVE} — an {@link INCONCLUSIVEPermit}
     *       {@code (contentHash)} is demanded from the caller's
     *       {@link java.security.AccessControlContext} by default (strict mode).
     *       If not granted, {@link IOException} is thrown.  Set
     *       {@link PreferredProxyCodebaseProvider#INCONCLUSIVE_STRICT_MODE_PROPERTY}
     *       to {@code "false"} to disable strict mode and allow the load to
     *       proceed with a {@code WARNING} log entry only.</li>
     *   <li>{@link VerdictType#DANGEROUS} — throw {@link IOException}; logged
     *       at {@code SEVERE}.</li>
     *   <li>{@code null} return (no verdict yet) — throw {@link IOException};
     *       logged at {@code WARNING}.</li>
     *   <li>{@link RemoteException} — registry unreachable; retry with
     *       exponential backoff (1 s → 2 s → 4 s) before throwing
     *       {@link IOException}; transient failures are logged at
     *       {@code WARNING}, final failure at {@code SEVERE}.</li>
     * </ul>
     *
     * @param vr          the registry to query; must be non-null
     * @param contentHash lowercase SHA-256 hex digest of the JAR
     * @param path        the codebase annotation string (used in messages)
     * @return {@code true} if the verdict is {@link VerdictType#INCONCLUSIVE},
     *         otherwise {@code false}
     * @throws IOException if the verdict is absent, DANGEROUS, or the
     *                     registry is unreachable; or if the verdict is
     *                     INCONCLUSIVE and strict mode is enabled but the
     *                     caller does not hold the required
     *                     {@link INCONCLUSIVEPermit}
     */
    static boolean checkVerdictForJar(VerdictRegistry vr,
                                       String contentHash,
                                       String path) throws IOException {
        RegistryVerdict verdict = getVerdictByHashWithRetry(vr, contentHash, path);
        if (verdict == null) {
            logger.log(Level.WARNING,
                    "No verdict for JAR (SHA-256: {0}); codebase refused: {1}",
                    new Object[]{contentHash, path});
            throw new IOException(
                    "No verdict available for JAR (SHA-256: " + contentHash
                    + "); codebase refused: " + path);
        }
        VerdictType type = verdict.getVerdict();
        if (type == VerdictType.DANGEROUS) {
            logger.log(Level.SEVERE,
                    "JAR verdict is DANGEROUS (SHA-256: {0}); codebase refused: {1}",
                    new Object[]{contentHash, path});
            throw new IOException(
                    "JAR verdict is DANGEROUS (SHA-256: " + contentHash
                    + "); codebase refused: " + path);
        } else if (type == VerdictType.INCONCLUSIVE) {
            logger.log(Level.WARNING,
                    "JAR verdict is INCONCLUSIVE (SHA-256: {0}); proceeding with caution",
                    contentHash);
            if (inconclusiveStrictMode) {
                try {
                    AccessController.checkPermission(new INCONCLUSIVEPermit(contentHash));
                } catch (SecurityException ex) {
                    logger.log(Level.SEVERE,
                            "Strict mode: INCONCLUSIVE verdict for JAR (SHA-256: {0})"
                            + " refused — no INCONCLUSIVEPermit granted; codebase: {1}",
                            new Object[]{contentHash, path});
                    throw new IOException(
                            "Strict mode: INCONCLUSIVE verdict for JAR (SHA-256: "
                            + contentHash
                            + ") refused — no INCONCLUSIVEPermit granted; codebase: "
                            + path,
                            ex);
                }
            }
            return true;
        } else {
            // SAFE
            logger.log(Level.FINEST,
                    "JAR verdict is SAFE (SHA-256: {0})", contentHash);
        }
        return false;
    }

    private static RegistryVerdict getVerdictByHashWithRetry(VerdictRegistry vr,
                                                             String contentHash,
                                                             String path)
            throws IOException {
        // Try the primary registry first, then any configured replicas.
        // When getAll() is empty (e.g. unit tests that pass vr directly),
        // fall back to the supplied vr parameter.
        VerdictRegistry[] allRegistries = VerdictRegistryHolder.getAll();
        if (allRegistries.length == 0 && vr != null) {
            allRegistries = new VerdictRegistry[]{vr};
        }
        RemoteException lastRemoteException = null;
        for (VerdictRegistry candidate : allRegistries) {
            if (candidate == null) continue;
            long retryDelayMs = verdictRetryBaseDelayMs;
            for (int attempt = 0; attempt <= VERDICT_RETRY_ATTEMPTS; attempt++) {
                try {
                    RegistryVerdict result = candidate.getVerdictByHash(contentHash);
                    if (result != null) {
                        VERDICT_CACHE.put(contentHash,
                                new CachedVerdict(result, System.currentTimeMillis()));
                    }
                    return result;
                } catch (RemoteException e) {
                    lastRemoteException = e;
                    if (attempt == VERDICT_RETRY_ATTEMPTS) {
                        // All retries on this candidate exhausted — try next replica.
                        logger.log(Level.WARNING,
                                "VerdictRegistry candidate unreachable for codebase: {0};"
                                + " trying next replica if available", path);
                        break;
                    }
                    logger.log(Level.WARNING,
                            "VerdictRegistry lookup failed for codebase: {0};"
                            + " retrying in {1} ms",
                            new Object[]{path, retryDelayMs});
                    sleepBeforeVerdictRetryOrThrow(retryDelayMs, path);
                    retryDelayMs *= 2L;
                }
            }
        }
        // All candidates failed — fall back to in-memory cache.
        logger.log(Level.SEVERE,
                "All VerdictRegistry candidates unreachable for codebase: {0}", path);
        CachedVerdict cached = VERDICT_CACHE.get(contentHash);
        if (cached != null
                && cached.isAlive(System.currentTimeMillis(), verdictCacheTtlMs)) {
            logger.log(Level.WARNING,
                    "Using cached verdict for JAR (SHA-256: {0})"
                    + " because all VerdictRegistry candidates are unreachable",
                    contentHash);
            return cached.verdict;
        }
        throw new IOException(
                "VerdictRegistry unavailable; refusing to load codebase: "
                + path, lastRemoteException);
    }

    private static void sleepBeforeVerdictRetryOrThrow(long retryDelayMs,
                                                       String path)
            throws IOException {
        if (retryDelayMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Interrupted while retrying VerdictRegistry lookup for codebase: "
                    + path, ex);
        }
    }

    /**
     * Determines if the URL is pointing to a directory.
     */
    private static boolean isDirectory(URL url) {
        String file = url.getFile();
        return (file.length() > 0 && file.charAt(file.length() - 1) == '/');
    }

    @Override
    public Object resolve(CodebaseAccessor bootstrapProxy,
			MarshalledInstance serviceProxy,
			final ClassLoader parent,
			ClassLoader verifier,
			Collection context) 
	    throws IOException, ClassNotFoundException
    {
	if (context == null) throw new NullPointerException(
		"stream context cannot be null");
	Iterator it = context.iterator();
	MethodConstraints mc = null;
	IntegrityEnforcement integrityEnforcement = null;
	Subject serverSubjectFromContext = null;
	while(it.hasNext()){
	    Object o = it.next();
	    if (o instanceof MethodConstraints){
		mc = (MethodConstraints) o;
	    } else if (o instanceof IntegrityEnforcement){
		integrityEnforcement = (IntegrityEnforcement) o;
	    } else if (o instanceof ServerSubject) {
		serverSubjectFromContext = ((ServerSubject) o).getServerSubject();
	    }
	}
	if (mc != null || integrityEnforcement != null){
	    if (!(bootstrapProxy instanceof RemoteMethodControl)) 
		throw new IOException(
		    "bootstrap proxy must be instance of RemoteMethodControl for client to apply method constraints");
	    bootstrapProxy = (CodebaseAccessor) 
		((RemoteMethodControl) bootstrapProxy).setConstraints(mc); // MinPrincipal happens here.
	}   
	ClassLoader loader;
	final String path = bootstrapProxy.getClassAnnotation();
        Uri [] codebases = PreferredClassProvider.pathToURIs(path);
        final URL [] codebase = PreferredClassProvider.asURL(codebases);
        // Extract the authenticated server principals from the TLS-layer
        // ServerSubject injected by SslConnection.populateContext().  Using
        // the actual authenticated identity (not client-declared
        // ServerMinPrincipal constraints) ensures that DigestGrants are
        // scoped to what the server *proved* during the TLS handshake.
        final Principal[] serverPrincipals;
        if (serverSubjectFromContext != null) {
            Set<Principal> principals = serverSubjectFromContext.getPrincipals();
            serverPrincipals = principals.isEmpty()
                    ? null : principals.toArray(new Principal[0]);
        } else {
            serverPrincipals = null;
        }
        Key loaderKey = new Key(
                            Proxy.getInvocationHandler(bootstrapProxy),
                            Arrays.asList(codebases), null
                        );
        loader = SERVICES_EXP.get(loaderKey); // Was it exported from here?
        String loaderPath = PreferredClassProvider.getLoaderAnnotation(parent, false, null);
        if (loader == null && path != null && path.equals(loaderPath)){ // Has it unmarshalled itself?
            /**
             * This check prevents us accidentally creating a new ClassLoader for
             * a returned proxy that can be resolved by the parent ClassLoader
             * but where that loader hasn't been cached.  This would only occur
             * where a proxy unmarshalls another instance of itself.  This
             * is unlikely occur at the server endpoint node.
             */
            loader = parent;
        }
        if (loader == null){
            loaderKey = new Key(
                                Proxy.getInvocationHandler(bootstrapProxy),
                                Arrays.asList(codebases), parent
                            );
            loader = CACHE.get(loaderKey); // Has it been unmarshalled previously?
        }
        if (loader == null){ // Create a new loader.
            boolean jarPermitAcquired = acquireJarLoadPermitIfNeeded(codebase, path);
            try {
                byte [] encodedCerts = bootstrapProxy.getEncodedCerts();
                if ((encodedCerts == null 
                    || encodedCerts.length == 0 )
                    && integrityEnforcement != null
                    && integrityEnforcement.integrityEnforced())
                {
                    Security.verifyCodebaseIntegrity(path, verifier);
                } else if (encodedCerts != null && encodedCerts.length > 0) {
                    // Although we trust the bootstrapProxy now, if we require validation,
                    // we must check the jar file has been signed.
                    try {
                        String certFactoryType = bootstrapProxy.getCertFactoryType();
                        String certPathEncoding = bootstrapProxy.getCertPathEncoding();
                        CertificateFactory factory =
                                CertificateFactory.getInstance(certFactoryType);
                        CertPath certPath = factory.generateCertPath(
                                new ByteArrayInputStream(encodedCerts), certPathEncoding);
                        Collection<? extends Certificate> certs = certPath.getCertificates();
                        for (int index = 0, length = codebase.length; index < length; index++){
                            URL searchURL = createSearchURL(codebase[index]);
                            URL jarURL = ((JarURLConnection) searchURL
                                .openConnection()).getJarFileURL();
                            JarURLConnection juc = (JarURLConnection) new URL(
                                    "jar", "", //$NON-NLS-1$ //$NON-NLS-2$
                                    jarURL.toExternalForm() + "!/").openConnection(); //$NON-NLS-1$
                            juc.connect();
                            InputStream in = juc.getInputStream();
                            byte [] bytes = new byte[1024];
                            int bytesRead = 0;
                            // reading in the entire jar file will check it's validity.
                            // it will also be cached.
                            do { // keep reading until we reach end of stream.
                                bytesRead = in.read(bytes);
                            } while (bytesRead == 1024);
                            // We should be able to read certs now, confirming the jar 
                            // has been verified.
                            Certificate [] certificates = juc.getCertificates();
                            if (certs == null){
                                throw new SecurityException("jar file invalid");
                            }
                            // Check our certs match.
                            HashSet<Certificate> actualCerts 
                                    = new HashSet<Certificate>(Arrays.asList(certificates));
                            HashSet<Certificate> requiredCerts = new HashSet<Certificate>(certs);
                            if (!actualCerts.containsAll(requiredCerts)){
                                throw new SecurityException("certificates don't match");
                            }
                        }
                        // TODO: Consider whether we need DownloadPermission
                        // to be granted dynamically here or not?
                        // DownloadPermission doesn't prevent download, only
                        // defining or loading classes.
                        // However it appears that integrity constraints should
                        // be sufficient, given we have already authenticated
                        // the service prior to any codebase download.
                    } catch (CertificateException ex) {
                        throw new IOException("Problem creating signer certificates", ex);
                    } 
                }

                // ----------------------------------------------------------------
                // Verdict check — query VerdictRegistry before creating a new
                // ClassLoader.  When verdictRegistry is null (boot-time permissive
                // policy) the check is skipped so the node can start up before
                // the registry is reachable.
                // ----------------------------------------------------------------
                VerdictRegistry vr = VerdictRegistryHolder.get();
                boolean inconclusiveVerdictSeen = false;
                if (vr != null) {
                    for (int verdictIndex = 0, verdictLength = codebase.length;
                            verdictIndex < verdictLength;
                            verdictIndex++) {
                        URL jarUrl = codebase[verdictIndex];
                        if (!isDirectory(jarUrl)) {
                            String contentHash = computeJarHash(jarUrl);
                            inconclusiveVerdictSeen |= checkVerdictForJar(vr, contentHash, path);
                        }
                    }
                } else {
                    // VerdictRegistry not yet set: boot-time window.
                    // Build per-JAR hash log for audit purposes.
                    StringBuilder bootWindowHashes = new StringBuilder();
                    bootWindowHashes.append('[');
                    boolean first = true;
                    for (int verdictIndex = 0, verdictLength = codebase.length;
                            verdictIndex < verdictLength;
                            verdictIndex++) {
                        URL jarUrl = codebase[verdictIndex];
                        if (!isDirectory(jarUrl)) {
                            if (!first) {
                                bootWindowHashes.append(", ");
                            }
                            first = false;
                            String contentHash;
                            try {
                                contentHash = computeJarHash(jarUrl);
                            } catch (IOException ex) {
                                contentHash = "<unreadable>";
                            }
                            bootWindowHashes.append(jarUrl).append('=').append(contentHash);
                        }
                    }
                    bootWindowHashes.append(']');

                    // Gate 1: BootstrapPermission check against the TLS-authenticated
                    // server identity supplied by ServerSubject.  Absent a ServerSubject
                    // (non-TLS or anonymous-server deployment) serverPrincipals is null
                    // and the gate is skipped for backward compatibility.
                    if (serverPrincipals != null && serverPrincipals.length > 0) {
                        // Throws SecurityException if the server's principal
                        // is not granted BootstrapPermission in the local policy.
                        checkBootstrapPermission(serverPrincipals, path);

                        // Gate 2: Per-JAR codebase digest verification and DigestGrant issuance.
                        // The server provides a flat byte[] containing all per-JAR digests
                        // concatenated, and an int[] of start byte offsets into that array.
                        // This is the only grant mechanism that works with DirtyChai's
                        // DigestCodeSource, which is per-JAR.
                        String algo = null;
                        byte[] serverFlatDigest = null;
                        int[] serverDigestOffsets = null;
                        try {
                            algo = bootstrapProxy.getCodebaseDigestAlgorithm();
                            serverFlatDigest = bootstrapProxy.getCodebaseDigest();
                            serverDigestOffsets = bootstrapProxy.getDigestOffsets();
                        } catch (IOException ex) {
                            logger.log(Level.WARNING,
                                    "Boot window: failed to fetch codebase digest from server"
                                    + " (proceeding on BootstrapPermission alone);"
                                    + " codebase: {0}; SHA-256: {1}",
                                    new Object[]{path, bootWindowHashes.toString()});
                        }

                        if (algo != null && serverFlatDigest != null
                                && serverFlatDigest.length > 0
                                && serverDigestOffsets != null
                                && serverDigestOffsets.length > 0) {
                            // Validate server-supplied offsets before use (DoS guard:
                            // prevents negative/out-of-bounds/non-monotonic offsets from
                            // causing unchecked exceptions or processing an enormous array).
                            try {
                                validateDigestOffsets(serverFlatDigest, serverDigestOffsets);
                            } catch (IOException ex) {
                                logger.log(Level.SEVERE,
                                        "Boot window: server provided invalid digest offsets"
                                        + " (possible tampered server response);"
                                        + " refusing codebase: {0}; reason: {1}",
                                        new Object[]{path, ex.getMessage()});
                                throw new SecurityException(
                                        "Server provided invalid digest offsets; refusing: "
                                        + path, ex);
                            }
                            // Compute individual per-JAR digests locally.
                            byte[][] localDigests = computeIndividualJarDigests(codebase, algo);
                            if (localDigests.length != serverDigestOffsets.length) {
                                logger.log(Level.SEVERE,
                                        "JAR count mismatch: server provided {0} digests,"
                                        + " local codebase has {1} JARs;"
                                        + " refusing codebase: {2}",
                                        new Object[]{serverDigestOffsets.length,
                                            localDigests.length, path});
                                throw new SecurityException(
                                        "JAR count mismatch in codebase digest; refusing: "
                                        + path);
                            }
                            boolean allMatch = true;
                            for (int di = 0; di < localDigests.length; di++) {
                                byte[] serverDigest = extractJarDigest(
                                        serverFlatDigest, serverDigestOffsets, di);
                                if (!Arrays.equals(localDigests[di], serverDigest)) {
                                    allMatch = false;
                                    logger.log(Level.SEVERE,
                                            "Per-JAR digest mismatch at index {0}"
                                            + " (possible MITM attack);"
                                            + " refusing codebase: {1}; SHA-256: {2}",
                                            new Object[]{di, path,
                                                bootWindowHashes.toString()});
                                    break;
                                }
                            }
                            if (!allMatch) {
                                throw new SecurityException(
                                        "Per-JAR digest mismatch (possible MITM attack);"
                                        + " refusing: " + path);
                            }
                            // All per-JAR digests verified: grant one DigestGrant per JAR,
                            // scoped to the local client's SPIFFE principal AND the
                            // server's authenticated SPIFFE principal.  Binding the grant
                            // to both identities prevents another service that ships the
                            // same JAR bytes from reusing this grant (Option 1 —
                            // digest-codesource hijacking defence).
                            Principal[] localPrincipals = Security.currentPrincipals();
                            tryGrantPerUriDigestGrants(algo, localDigests,
                                    localPrincipals, serverPrincipals);
                            logger.log(Level.INFO,
                                    "Boot window: {0} per-JAR codebase digest(s) verified"
                                    + " and DigestGrant(s) applied; codebase: {1}",
                                    new Object[]{localDigests.length, path});
                        } else {
                            // BootstrapPermission granted, but server did not provide
                            // per-JAR digest information.  Log the hashes for auditing.
                            logger.log(Level.WARNING,
                                    "Boot window: BootstrapPermission granted but server"
                                    + " provided no per-JAR digest information;"
                                    + " DigestGrants not issued;"
                                    + " codebase: {0}; SHA-256: {1}",
                                    new Object[]{path, bootWindowHashes.toString()});
                        }
                    } else {
                        // No SPIFFE principals configured: check BootstrapPermission
                        // using DigestCodeSource (DirtyChai) or plain CodeSource (standard
                        // JDK) so the policy can gate boot-window loading by JAR digest
                        // or URL even when no TLS-level principal is present.
                        checkBootstrapPermissionByDigest(codebase, serverPrincipals,
                                path, bootWindowHashes.toString());
                    }
                }

                /**
                 * The next section of code previously 
                 * called ClassLoading.getClassLoader(path).
                 * 
                 * Unfortunately, this results in two proxies with identical
                 * paths but different endpoints sharing a ClassLoader, because
                 * the identity is only determined by the codebase annotation string.
                 * 
                 * This is not acceptable if two different services use the
                 * same codebase, for example two different entities might
                 * use maven to provision codebases, and use maven central for
                 * their codebase.
                 */
                loader = AccessController.doPrivileged(
                        new PrivilegedAction<ClassLoader>() {
                            @Override
                            public ClassLoader run() {
                                return new PreferredClassLoader(
                                    codebase, parent, null, false,
                                    PreferredClassLoader.getLoaderAccessControlContext(codebase),
                                    serverPrincipals
                                );
                            }
                        }
                );
                ClassLoader existed = CACHE.putIfAbsent(loaderKey, loader);
                if (existed != null) loader = existed;
                if (inconclusiveVerdictSeen) {
                    Security.markInconclusiveProxyClassLoader(loader);
                }
            } finally {
                if (jarPermitAcquired) {
                    JAR_LOAD_SEMAPHORE.release();
                }
            }
        }
	
	Object sp = serviceProxy.get(loader, true, verifier, context);
	/**
	 * The following exists because a trusted proxy might be using a third
	 * party service and whish to apply it's own constraints to the third
	 * party proxy, however the client is also applying constraints to the
	 * trusted proxy that's de-serializing the third party proxy,
	 * so we must ensure that all constraints are applied
	 * to the third party proxy.
	 */
	if (mc != null){
	    if (sp instanceof RemoteMethodControl){
		RemoteMethodControl rmc = (RemoteMethodControl) sp;
		MethodConstraints existing = rmc.getConstraints();
		if (existing instanceof BasicMethodConstraints )
		{
		    existing = new StringMethodConstraints((BasicMethodConstraints) existing);
		} 
		if (mc instanceof BasicMethodConstraints){
		    mc = new StringMethodConstraints((BasicMethodConstraints) mc);
		}
		if (existing instanceof StringMethodConstraints 
			&& mc instanceof StringMethodConstraints )
		{
		    mc = ((StringMethodConstraints)mc).combine((StringMethodConstraints)existing);
		}
		sp = rmc.setConstraints(mc);
	    } else {
		throw new InvalidObjectException(
		    "Proxy must be an instance of RemoteMethodControl, when constraints are in force " + sp
		);
	    }
	}
	return sp;
    }
    
    /**
     * Returns an URL that will be checked if it contains the class or resource.
     * If the file component of the URL is not a directory, a Jar URL will be
     * created.
     *
     * @return java.net.URL a test URL
     */
    private URL createSearchURL(URL url) throws MalformedURLException {
        if (url == null) return url;
        String protocol = url.getProtocol();
        if (isDirectory(url) || protocol.equals("jar")) { //$NON-NLS-1$
            return url;
        }
	return new URL("jar", "", //$NON-NLS-1$ //$NON-NLS-2$
		-1, url.toString() + "!/"); //$NON-NLS-1$
    }

    @Override
    public boolean substitute(Class serviceClass, ClassLoader streamLoader) {
	return true;
	/*
	 * Because we're not in a modular environment, we can't make any
	 * decisions based on local class visibility.  There are no dependency
	 * rules to satisfy that we can use to determine whether this class
	 * should be resolvable at the client.  In other words the ClassLoader
	 * for our stream, isn't guaranteed to have the same visibility as the remote
	 * end's stream default ClassLoader.
	 */
//	String annotation = ClassLoading.getClassAnnotation(serviceClass);
//	String loaderAnnotation = 
//		PreferredClassProvider.getLoaderAnnotation(
//						    streamLoader, false, null);
//	return annotation != null && !annotation.equals(loaderAnnotation);
    }
    
    @Override
    public void record(CodebaseAccessor service, InvocationHandler handler, ClassLoader loader)
            throws ExportException
    {
        String path = null;
        try {
            path = service.getClassAnnotation();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        if (path != null){
            Uri [] codebases;
            try {
                codebases = PreferredClassProvider.pathToURIs(path);
            } catch (MalformedURLException ex) {
                throw new ExportException("There's a problem with the codebase annotation", ex);
            }
            Key loaderKey = new Key(
                                handler, 
                                Arrays.asList(codebases), null
                            );
            ClassLoader existed = SERVICES_EXP.putIfAbsent(loaderKey, loader);
            if (existed != null) throw new ExportException("Remote Object is already exported");
        }
    }
    
    /**
     * 
     */
    private static class Key {
	private final InvocationHandler handler;
	private final List<Uri> codebase;
	private final int hashcode;
        private final WeakReference<ClassLoader> parent;
	
	Key(InvocationHandler h, List<Uri> codebase, ClassLoader parent){
	    this.handler = h;
	    this.codebase = codebase;
	    int hash = 5;
	    hash = 73 * hash + (this.handler != null ? this.handler.hashCode() : 0);
	    hash = 73 * hash + (this.codebase != null ? this.codebase.hashCode() : 0);
            hash = 73 * hash + (parent != null ? parent.hashCode() : 0);
	    this.hashcode = hash;
            this.parent = new WeakReference<ClassLoader>(parent);
	}

	@Override
	public int hashCode() {
	    return hashcode;
	}
	
	@Override
	public boolean equals(Object o){
	    if (!(o instanceof Key)) return false;
	    if (!handler.equals(((Key)o).handler)) return false;
	    if (!codebase.equals(((Key)o).codebase)) return false;
            return Objects.equals(parent.get(), ((Key)o).parent.get());
	}
    }
    
}
