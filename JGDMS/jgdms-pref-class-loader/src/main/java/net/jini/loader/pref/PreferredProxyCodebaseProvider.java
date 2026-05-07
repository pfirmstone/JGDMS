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
import java.lang.reflect.Proxy;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.constraint.StringMethodConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.io.context.IntegrityEnforcement;
import net.jini.loader.ProxyCodebaseSpi;
import net.jini.security.Security;
import org.apache.river.api.net.Uri;
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
     * @param registry the {@link VerdictRegistry} proxy to use; may be
     *                 {@code null} to disable verdict checking
     */
    public static void setVerdictRegistry(VerdictRegistry registry) {
        VerdictRegistryHolder.set(registry);
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
        InputStream in = jarUrl.openStream();
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        } finally {
            in.close();
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
     *   <li>{@link RemoteException} — registry unreachable; throw
     *       {@link IOException}; logged at {@code SEVERE}.</li>
     * </ul>
     *
     * @param vr          the registry to query; must be non-null
     * @param contentHash lowercase SHA-256 hex digest of the JAR
     * @param path        the codebase annotation string (used in messages)
     * @throws IOException if the verdict is absent, DANGEROUS, or the
     *                     registry is unreachable
     */
    static void checkVerdictForJar(VerdictRegistry vr,
                                    String contentHash,
                                    String path) throws IOException {
        RegistryVerdict verdict;
        try {
            verdict = vr.getVerdictByHash(contentHash);
        } catch (RemoteException e) {
            logger.log(Level.SEVERE,
                    "VerdictRegistry unreachable for codebase: {0}", path);
            throw new IOException(
                    "VerdictRegistry unavailable; refusing to load codebase: "
                    + path, e);
        }
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
        } else {
            // SAFE
            logger.log(Level.FINEST,
                    "JAR verdict is SAFE (SHA-256: {0})", contentHash);
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
                    for (int i = 0, l = codebase.length; i < l; i++){
                        URL searchURL = createSearchURL(codebase[i]);
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
            if (vr != null) {
                for (int vi = 0, vl = codebase.length; vi < vl; vi++) {
                    URL jarUrl = codebase[vi];
                    if (!isDirectory(jarUrl)) {
                        String contentHash = computeJarHash(jarUrl);
                        checkVerdictForJar(vr, contentHash, path);
                    }
                }
            } else {
                logger.log(Level.FINE,
                        "VerdictRegistry not yet set; skipping verdict check"
                        + " (boot-time permissive policy) for codebase: {0}",
                        path);
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
