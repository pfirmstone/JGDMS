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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.io.context.IntegrityEnforcement;
import net.jini.loader.ClassLoading;
import net.jini.loader.ProxyCodebaseSpi;
import net.jini.security.Security;
import org.apache.river.api.net.Uri;
import org.apache.river.concurrent.RC;
import org.apache.river.concurrent.Ref;
import org.apache.river.concurrent.Referrer;

/**
 *
 * @author peter
 */
public class PreferredProxyCodebaseProvider implements ProxyCodebaseSpi {

    private static final ConcurrentMap<Key,ClassLoader> cache;
    
    static {
	ConcurrentMap<Referrer<Key>,Referrer<ClassLoader>> intern1 =
                new ConcurrentHashMap<Referrer<Key>,Referrer<ClassLoader>>();
	cache = RC.concurrentMap(intern1, Ref.STRONG, Ref.WEAK, 60000L, 60000L);
    }
    
    public PreferredProxyCodebaseProvider(){}
    
    /**
     * Determines if the URL is pointing to a directory.
     */
    private static boolean isDirectory(URL url) {
        String file = url.getFile();
        return (file.length() > 0 && file.charAt(file.length() - 1) == File.separatorChar);
    }

    @Override
    public Object resolve(CodebaseAccessor bootstrapProxy,
			MarshalledInstance smartProxy,
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
	String loaderPath = PreferredClassProvider.getLoaderAnnotation(parent, false, null);
	if (path != null && path.equals(loaderPath)){
	    /**
	     * This check prevents us accidentally creating a new ClassLoader for
	     * a returned proxy that can be resolved by the parent ClassLoader
	     * but where that loader hasn't been cached.
	     */
	    loader = parent;
	} else {
	    Uri [] codebases = PreferredClassProvider.pathToURIs(path);
	    Key loaderKey = new Key(
				Proxy.getInvocationHandler(bootstrapProxy),
				Arrays.asList(codebases)
			    );
	    loader = cache.get(loaderKey);
	    if (loader == null){
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
			URL [] codebase = PreferredClassProvider.asURL(codebases);
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
		    } catch (CertificateException ex) {
			throw new IOException("Problem creating signer certificates", ex);
		    } 
		}

		try {
		    loader = AccessController.doPrivileged(
			    new PrivilegedExceptionAction<ClassLoader>(){

				public ClassLoader run() throws IOException {
				    Thread currentThread = Thread.currentThread();
				    ClassLoader context = currentThread.getContextClassLoader();
				    try {
					currentThread.setContextClassLoader(parent);
					return ClassLoading.getClassLoader(path);
				    } finally {
					if (context != null) currentThread.setContextClassLoader(context);
				    }

				}
			    }
		    );
		} catch (PrivilegedActionException ex) {
		    Exception e = ex.getException();
		    if (e instanceof IOException) throw (IOException ) e;
		    if (e instanceof SecurityException) throw (SecurityException) e;
		    if (e instanceof RuntimeException) throw (RuntimeException) e;
		}    
		ClassLoader existed = cache.putIfAbsent(loaderKey, loader);
		if (existed != null) loader = existed;
	    }
	}
	Object sp = smartProxy.get(loader, true, verifier, context);
	// Don't set constraints on smart proxy.
//	if (sp instanceof RemoteMethodControl && mc != null)
//	    sp = ((RemoteMethodControl)sp).setConstraints(mc);
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
    
    private static class Key {
	private final InvocationHandler handler;
	private final List<Uri> codebase;
	private final int hashcode;
	
	Key(InvocationHandler h, List<Uri> codebase){
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
