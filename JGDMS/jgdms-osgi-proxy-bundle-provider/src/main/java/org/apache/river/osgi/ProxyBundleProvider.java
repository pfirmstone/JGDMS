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
package org.apache.river.osgi;

import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Requirement;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 *
 * @author peter
 */
@Requirement(
	namespace="osgi.extender",
	filter="(osgi.extender=osgi.serviceloader.registrar)")
@Capability(
	namespace="osgi.serviceloader",
	name="net.jini.loader.ProxyCodebaseSpi")
public class ProxyBundleProvider implements ProxyCodebaseSpi, BundleActivator {
    private static final ConcurrentMap<String,Uri[]> uriCache;
    private static final ConcurrentMap<Key,ClassLoader> cache;
    
    static {
	cache = new ConcurrentHashMap<Key,ClassLoader>();
	ConcurrentMap<Referrer<String>,Referrer<Uri[]>> intern1 =
                new ConcurrentHashMap<Referrer<String>,Referrer<Uri[]>>();
        uriCache = RC.concurrentMap(intern1, Ref.TIME, Ref.STRONG, 60000L, 60000L);
    }
    
    volatile BundleContext bc;
    
    
    public ProxyBundleProvider(){
	this.bc = null;
    }
    
    /**
     * Determines if the URL is pointing to a directory.
     */
    private static boolean isDirectory(URL url) {
        String file = url.getFile();
        return (file.length() > 0 && file.charAt(file.length() - 1) == File.separatorChar);
    }
    
    /**
     * Convert a string containing a space-separated list of URL Strings into a
     * corresponding array of Uri objects, throwing a MalformedURLException
     * if any of the URLs are invalid.  This method returns null if the
     * specified string is null.
     *
     * @param path the string path to be converted to an array of urls
     * @return the string path converted to an array of URLs, or null
     * @throws MalformedURLException if the string path of urls contains a
     *         mal-formed url which can not be converted into a url object.
     */
    private Uri[] pathToURIs(String path) throws MalformedURLException {
	if (path == null) {
	    return null;
	}
        Uri[] urls = uriCache.get(path); // Cache of previously converted strings.
        if (urls != null) return urls;
	StringTokenizer st = new StringTokenizer(path);	// divide by spaces
	urls = new Uri[st.countTokens()];
	for (int i = 0; st.hasMoreTokens(); i++) {
            try {
                String ur = st.nextToken();
                ur = Uri.fixWindowsURI(ur);
                urls[i] = Uri.parseAndCreate(ur);
            } catch (URISyntaxException ex) {
                throw new MalformedURLException("URL's must be RFC 3986 Compliant: " 
                        + ex.getMessage());
            }
	}
        Uri [] existed = uriCache.putIfAbsent(path, urls);
        if (existed != null) urls = existed;
	return urls;
    }

    @Override
    public Object resolve(CodebaseAccessor bootstrapProxy,
			MarshalledInstance smartProxy,
			ClassLoader parent,
			ClassLoader verifier,
			Collection context) 
	    throws IOException, ClassNotFoundException
    {
	if (bc == null) throw new NullPointerException("Bundle is not active");
	if (context == null) throw new NullPointerException(
		"stream context cannot be null");
	if (!(bootstrapProxy instanceof RemoteMethodControl)) 
	    throw new IllegalArgumentException(
		    "bootstrap proxy must be instance of RemoteMethodControl");
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
	bootstrapProxy = (CodebaseAccessor) 
		((RemoteMethodControl) bootstrapProxy).setConstraints(mc); // MinPrincipal happens here.
	String path = bootstrapProxy.getClassAnnotation();
	String proxyBundlePath = null;
	Uri [] codebases = pathToURIs(path);
	Key loaderKey = new Key(Proxy.getInvocationHandler(bootstrapProxy), codebases[0]);
	ClassLoader loader = cache.get(loaderKey);
	if (loader == null){
	    byte [] encodedCerts = bootstrapProxy.getEncodedCerts();
	    Collection<? extends Certificate> certs = null;
	    if ((encodedCerts == null 
		|| encodedCerts.length == 0 )
		&& integrityEnforcement != null
		&& integrityEnforcement.integrityEnforced())
	    {
		Security.verifyCodebaseIntegrity(path, null);
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
		    certs = certPath.getCertificates();
		    proxyBundlePath = codebases[0].toString();
		    URL searchURL = createSearchURL(new URL(proxyBundlePath));
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
		} catch (CertificateException ex) {
		    throw new IOException("Problem creating signer certificates", ex);
		} 
	    }
	    if (proxyBundlePath == null) proxyBundlePath = codebases[0].toString();
	    try {
		final Bundle proxyBundle = bc.installBundle(proxyBundlePath);
		loader = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>(){

		    public ClassLoader run() {
			return new BundleDelegatingClassLoader(proxyBundle);
		    }
		    
		});
		ClassLoader existed = cache.putIfAbsent(loaderKey, loader);
		if (existed != null){
		    loader = existed;
		    try {
			proxyBundle.uninstall();
		    } catch (BundleException ex){}// Ignore
		}
	    } catch (BundleException ex) {
		throw new IOException("Unable to resolve Bundle", ex);
	    }
	}
	Object sp = smartProxy.get(loader, true, null, context);
	if (mc != null){
	    if (sp instanceof RemoteMethodControl){
		sp = ((RemoteMethodControl)sp).setConstraints(mc);
	    } else {
		throw new InvalidObjectException(
		    "Proxy must be an instance of RemoteMethodControl, when constraints are in force"
		);
	    }
	}
	return sp;
    }

    public void start(BundleContext bc) throws Exception {
	this.bc = bc;
    }

    public void stop(BundleContext bc) throws Exception {
	this.bc = null;
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

    public boolean substitute(Class serviceClass, ClassLoader streamLoader) {
	// Simply checks whether the class is visible at this endpoint,
	// if it is, it should also be at the remote endpoint
	// so shouldn't need substitution as OSGi
	// environments are expected to have identical bundles at
	// each endpoint.
	String name = serviceClass.getName();
	try {
	    Class found = streamLoader.loadClass(name);
	    return !found.equals(serviceClass);
	} catch (ClassNotFoundException e){
	    return true;
	}
    }
    
    private static class Key {
	private final InvocationHandler handler;
	private final Uri codebase;
	private final int hashcode;
	
	Key(InvocationHandler h, Uri codebase){
	    this.handler = h;
	    this.codebase = codebase;
	    int hash = 5;
	    hash = 73 * hash + (this.handler != null ? this.handler.hashCode() : 0);
	    hash = 73 * hash + (this.codebase != null ? this.codebase.hashCode() : 0);
	    this.hashcode = hash;
	}

	@Override
	public int hashCode() {
	    return hashcode;
	}
	
	@Override
	public boolean equals(Object o){
	    if (!(o instanceof Key)) return false;
	    if (!handler.equals(((Key)o).handler)) return false;
	    return codebase.equals(((Key)o).codebase);
	}
    }
    
}
