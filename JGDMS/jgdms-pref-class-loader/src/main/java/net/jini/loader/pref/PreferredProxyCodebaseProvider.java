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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLPermission;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.security.AccessController;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.constraint.StringMethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.io.context.IntegrityEnforcement;
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
    private static volatile long verdictRetryBaseDelayMs = DEFAULT_VERDICT_RETRY_BASE_DELAY_MS;
    private static final Semaphore JAR_LOAD_SEMAPHORE =
            new Semaphore(loadMaxConcurrentJarLoads(), true);
    
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
     * Computes the SHA-256 hex digest of the JAR accessible at the given URL.
     *
     * <p>The URL must point directly to a JAR file (not a directory).
     *
     * @param jarUrl the URL of the JAR to hash
     * @return lowercase hexadecimal SHA-256 digest (64 characters)
     * @throws IOException if the JAR cannot be read or SHA-256 is unavailable
     */
    static String computeJarHash(URL jarUrl) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 MessageDigest not available", e);
        }
        try (InputStream in = jarUrl.openStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        }
        return bytesToHex(digest.digest());
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
     * Computes a combined digest over all non-directory JAR URLs in the given
     * codebase, in order.
     *
     * <p>The algorithm is: for each JAR URL (in order, excluding directory
     * URLs), compute an inner digest of the entire JAR content bytes; then
     * feed all inner digest bytes sequentially into an outer
     * {@link MessageDigest} and return the final outer digest.  This
     * "hash-of-hashes" approach makes the result independent of the JAR
     * content layout across URL boundaries.
     *
     * <p>For a codebase containing a single JAR this is equivalent to
     * {@code digest(digest(jarContent))}.
     *
     * @param codebase  the JAR URLs; directory URLs are skipped
     * @param algorithm the digest algorithm name (e.g. {@code "SHA-256"})
     * @return the combined codebase digest bytes
     * @throws IOException if a JAR cannot be read or the algorithm is unknown
     */
    static byte[] computeCodebaseDigestBytes(URL[] codebase, String algorithm)
            throws IOException {
        MessageDigest outer;
        try {
            outer = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(algorithm + " MessageDigest not available", ex);
        }
        for (URL url : codebase) {
            if (!isDirectory(url)) {
                MessageDigest inner;
                try {
                    inner = MessageDigest.getInstance(algorithm);
                } catch (NoSuchAlgorithmException ex) {
                    throw new IOException(algorithm + " MessageDigest not available", ex);
                }
                try (InputStream in = url.openStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        inner.update(buf, 0, n);
                    }
                }
                outer.update(inner.digest());
            }
        }
        return outer.digest();
    }

    /**
     * Extracts the server-side {@link Principal}s declared via
     * {@link ServerMinPrincipal} constraints in the given
     * {@link MethodConstraints}, using the {@code getClassAnnotation} method
     * as a representative key.
     *
     * @param mc the method constraints set on the bootstrap proxy, or
     *           {@code null}
     * @return an array of server principals, or {@code null} if none are
     *         found
     */
    static Principal[] extractServerPrincipals(MethodConstraints mc) {
        if (mc == null) return null;
        Method m;
        try {
            m = CodebaseAccessor.class.getMethod("getClassAnnotation");
        } catch (NoSuchMethodException ex) {
            logger.log(Level.FINE,
                    "Could not reflect CodebaseAccessor.getClassAnnotation", ex);
            return null;
        }
        InvocationConstraints ic = mc.getConstraints(m);
        Set<Principal> principals = new HashSet<Principal>();
        for (InvocationConstraint c : ic.requirements()) {
            if (c instanceof ServerMinPrincipal) {
                @SuppressWarnings("unchecked")
                Set<Principal> elements = ((ServerMinPrincipal) c).elements();
                principals.addAll(elements);
            }
        }
        return principals.isEmpty() ? null : principals.toArray(new Principal[0]);
    }

    /**
     * Checks whether the given server principals are granted
     * {@link BootstrapPermission}{@code ("loadCodebase")} by the current
     * security policy.
     *
     * @param serverPrincipals the authenticated server principals
     * @param path             the codebase annotation string (for messages)
     * @throws SecurityException if the policy does not grant
     *                           {@code BootstrapPermission} to the principals
     */
    private static void checkBootstrapPermission(Principal[] serverPrincipals,
                                                  String path) {
        final Policy policy = AccessController.doPrivileged(
                new PrivilegedAction<Policy>() {
                    @Override
                    public Policy run() {
                        return Policy.getPolicy();
                    }
                });
        ProtectionDomain serverDomain =
                new ProtectionDomain(null, null, null, serverPrincipals);
        if (!policy.implies(serverDomain, new BootstrapPermission(BootstrapPermission.TARGET_NAME))) {
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
     * Checks the verdict for a single JAR against the given
     * {@link VerdictRegistry}.
     *
     * <p>Verdict semantics:
     * <ul>
     *   <li>{@link VerdictType#SAFE} — proceed; logged at {@code FINEST}.</li>
     *   <li>{@link VerdictType#INCONCLUSIVE} — proceed with caution; logged
     *       at {@code WARNING}.</li>
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
     *                     registry is unreachable
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
        long retryDelayMs = verdictRetryBaseDelayMs;
        for (int attempt = 0; attempt <= VERDICT_RETRY_ATTEMPTS; attempt++) {
            try {
                return vr.getVerdictByHash(contentHash);
            } catch (RemoteException e) {
                if (attempt == VERDICT_RETRY_ATTEMPTS) {
                    logger.log(Level.SEVERE,
                            "VerdictRegistry unreachable for codebase: {0}", path);
                    throw new IOException(
                            "VerdictRegistry unavailable; refusing to load codebase: "
                            + path, e);
                }
                logger.log(Level.WARNING,
                        "VerdictRegistry lookup failed for codebase: {0}; retrying in {1} ms",
                        new Object[]{path, retryDelayMs});
                sleepBeforeVerdictRetryOrThrow(retryDelayMs, path);
                retryDelayMs *= 2L;
            }
        }
        throw new AssertionError("unreachable");
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
        return (file.length() > 0 && file.charAt(file.length() - 1) == File.separatorChar);
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
	while(it.hasNext()){
	    Object o = it.next();
	    if (o instanceof MethodConstraints){
		mc = (MethodConstraints) o;
	    } else if (o instanceof IntegrityEnforcement){
		integrityEnforcement = (IntegrityEnforcement) o;
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

                    // Gate 1: BootstrapPermission check.
                    // Only applied when the client has configured SPIFFE-based
                    // ServerMinPrincipal constraints — this ensures backward
                    // compatibility for deployments without SPIFFE auth.
                    Principal[] serverPrincipals = extractServerPrincipals(mc);
                    if (serverPrincipals != null && serverPrincipals.length > 0) {
                        // Throws SecurityException if the server's principal
                        // is not granted BootstrapPermission in the local policy.
                        checkBootstrapPermission(serverPrincipals, path);

                        // Gate 2: Codebase digest comparison.
                        // Obtain the server's pre-computed codebase digest over
                        // the authenticated (SPIFFE/TLS) channel.
                        String algo = null;
                        byte[] serverDigest = null;
                        try {
                            algo = bootstrapProxy.getCodebaseDigestAlgorithm();
                            serverDigest = bootstrapProxy.getCodebaseDigest();
                        } catch (IOException ex) {
                            logger.log(Level.WARNING,
                                    "Boot window: failed to fetch codebase digest from server"
                                    + " (proceeding on BootstrapPermission alone);"
                                    + " codebase: {0}; SHA-256: {1}",
                                    new Object[]{path, bootWindowHashes.toString()});
                        }

                        if (algo != null && serverDigest != null && serverDigest.length > 0) {
                            // Compute the same combined digest locally.
                            byte[] localDigest = computeCodebaseDigestBytes(codebase, algo);
                            if (!Arrays.equals(localDigest, serverDigest)) {
                                logger.log(Level.SEVERE,
                                        "Codebase digest mismatch (possible MITM attack);"
                                        + " refusing codebase: {0}; SHA-256: {1}",
                                        new Object[]{path, bootWindowHashes.toString()});
                                throw new SecurityException(
                                        "Codebase digest mismatch (possible MITM attack);"
                                        + " refusing: " + path);
                            }
                            // Digests match: dynamically grant DigestGrant so that
                            // the downloaded code can only be defined if the digest
                            // still matches at class-definition time (DirtyChai JVM).
                            tryGrantDigestGrant(algo, localDigest, codebase);
                            logger.log(Level.INFO,
                                    "Boot window: codebase digest verified and DigestGrant applied;"
                                    + " codebase: {0}",
                                    path);
                        } else {
                            // BootstrapPermission granted, but server did not
                            // provide a digest.  Log the hashes for auditing.
                            logger.log(Level.WARNING,
                                    "Boot window: BootstrapPermission granted but server"
                                    + " provided no codebase digest;"
                                    + " codebase: {0}; SHA-256: {1}",
                                    new Object[]{path, bootWindowHashes.toString()});
                        }
                    } else {
                        // No SPIFFE principals configured — fall back to the
                        // existing permissive boot-window behaviour.
                        logger.log(Level.WARNING,
                                "VerdictRegistry not yet set; skipping verdict check"
                                + " (boot-time permissive policy) - codebase: {0}; SHA-256: {1}",
                                new Object[]{path, bootWindowHashes.toString()});
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
                                    PreferredClassLoader.getLoaderAccessControlContext(codebase)
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
