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

package org.apache.river.api.security;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.lang.ref.WeakReference;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.net.Uri;

/**
 * PermissionGrantBuilderImp represents the serialized form of all
 * PermissionGrant implementations in this package.
 *
 * All implementations of PermissionGrant are immutable with final fields.
 * 
 * PermissinGrantBuilderImp ensures the correct PermissionGrant implementation
 * is returned, this reduces the 
 * 
 * @author Peter Firmstone
 * @since 3.0.0
 */
class PermissionGrantBuilderImp extends PermissionGrantBuilder implements
        Serializable{
    // Static fields
    private static final long serialVersionUID = 1L;
    private static final PermissionGrant nullGrant = new NullPermissionGrant();
    private static final Logger LOGGER = Logger.getLogger(PermissionGrantBuilderImp.class.getName());
    
   
    /*@serial */
    private String[] uri;
    /*@serial */
    private Certificate[] certs;
    /*@serial */
    private Principal[] principals;
    /*@serial */
    private Permission[] permissions;
    /*@serial */
    private int context;
    /*@serial */
    private boolean hasDomain;
    /*@serial */
    private String[] aliases;
    // New serial state fields:
    /* @serial */ private String digestAlgorithm;
    /* @serial */ private byte[] digest;
    // Transient Fields
    private transient Collection<String> uris;
    private transient WeakReference<ProtectionDomain> domain;
    // The ClassLoader of the class passed to clazz(), which is not necessarily
    // the loader of its ProtectionDomain: a java.lang.reflect.Proxy class has
    // the shared all permission domain, whose loader is null.
    private transient WeakReference<ClassLoader> loader;
   
    PermissionGrantBuilderImp() {
        super();
        reset();
    }

    /**
     * Resets builder back to initial state, ready to receive new information
     * for building a new PermissionGrant.
     */
    public final PermissionGrantBuilder reset() {
        uri = null;
        if (uris != null) uris.clear();
        certs = null;
	aliases = null;
        domain = null;
        loader = null;
        hasDomain = false;
        principals = null;
        permissions = null;
        digestAlgorithm = null;
        digest = null;
        context = -1;
        return this;
    }
    
    public PermissionGrantBuilder context(int context) {
        if (context < 0) {
            throw new IllegalStateException("context must be >= 0");
        }
        if (context > 7) {
            throw new IllegalStateException("context must be <= 7");
        }
        this.context = context;
        return this;
    }
    
        @Override
    public PermissionGrantBuilder uri(String path) {
        if (this.uris == null) this.uris = new ArrayList<String>(6);
        this.uris.add(path);
        return this;
    }
    

    public PermissionGrantBuilder clazz(Class cl) {
        if (cl != null) {
	    ProtectionDomain pd = cl.getProtectionDomain();
	    if ( pd != null ){
		domain = new WeakReference<ProtectionDomain>(pd);
                hasDomain = true;
                ClassLoader cll = cl.getClassLoader();
                loader = cll != null ? new WeakReference<ClassLoader>(cll) : null;
            }
        }
        return this;
    }

    /** Package private, see {@link CodebaseDomainGrant#getBuilderTemplate()}. */
    PermissionGrantBuilderImp setLoader(WeakReference<ClassLoader> cl) {
        loader = cl;
        return this;
    }

    public PermissionGrantBuilder setDomain(WeakReference<ProtectionDomain> pd) {
        domain = pd;
        if ( domain != null) hasDomain = true;
        return this;
    }

    public PermissionGrantBuilder certificates(Certificate[] certs) {
        return certificates(certs, new String[0]);
    }
    
    
    @Override
    public PermissionGrantBuilder certificates(Certificate[] certs, String[] aliases) {
	this.certs = certs;
	this.aliases = aliases;
        return this;
    }

    public PermissionGrantBuilder principals(Principal[] pals) {
        // don't worry about being protective here.
        this.principals = pals;
        return this;
    }

    public PermissionGrantBuilder permissions(Permission[] permissions) {
        this.permissions = permissions;
        return this;
    }

    @Override
    public PermissionGrantBuilder digest(String algorithm, byte[] value) {
        this.digestAlgorithm = algorithm;
        this.digest = value != null ? value.clone() : null;
        return this;
    }

    public PermissionGrant build() {
        switch (context) {
            case CLASSLOADER: //Dynamic grant
                // Don't return principal grant if domain null, dynamic grant's
                // are treated special.
                return new ClassLoaderGrant(domain, principals, permissions );
            case CODEBASE_DOMAIN: //Dynamic grant
                return buildCodebaseDomainGrant();
            case URI:
                if (uris != null && !uris.isEmpty() ) uri = uris.toArray(new String[uris.size()]);
                if (uri == null ) uri = new String[0];
                return new URIGrant(uri, certs, aliases, principals, permissions);              
            case CODESOURCE_CERTS:
                return new CertificateGrant(certs, aliases, principals, permissions);
            case PROTECTIONDOMAIN: //Dynamic grant
                return new ProtectionDomainGrant(domain, principals, permissions );
            case PRINCIPAL:
                return new PrincipalGrant(principals, permissions);
            case DIGEST:
                if (uris != null && !uris.isEmpty()) uri = uris.toArray(new String[uris.size()]);
                if (uri == null) uri = new String[0];
                return new DigestGrant(uri, digestAlgorithm, digest,
                                       certs, aliases, principals, permissions);
            default:
                return nullGrant;
        }
    }
    
    /**
     * The codebase is the locations of the ClassLoader of the anchor domain,
     * the ClassLoader is the record of what was verified.  Any other kind of
     * ClassLoader, or one without a ClassLoader, results in the strictest 
     * grant, to the anchor ProtectionDomain only.
     */
    private PermissionGrant buildCodebaseDomainGrant() {
        ProtectionDomain pd = domain != null ? domain.get() : null;
        // Without a domain the grant applies to all domains, dynamic grants 
        // are treated special.
        if (domain == null) return new ProtectionDomainGrant(null, principals, permissions);
        Set<Uri> codebase = new HashSet<Uri>();
        // The loader of the class, falling back to that of its domain.
        WeakReference<ClassLoader> loaderRef = loader;
        ClassLoader cl = loaderRef != null ? loaderRef.get() 
                : (pd != null ? pd.getClassLoader() : null);
        if (loaderRef == null && cl != null) {
            loaderRef = new WeakReference<ClassLoader>(cl);
        }
        if (cl instanceof URLClassLoader) {
            for (URL url : ((URLClassLoader) cl).getURLs()) {
                try {
                    // Not URL.equals, it can cause DNS lookups.
                    codebase.add(Uri.urlToUri(url));
                } catch (URISyntaxException ex) {
                    // Fail closed, domains at this location are not implied.
                    LOGGER.log(Level.FINE, "Unable to convert codebase URL", ex);
                }
            }
        } else if (pd != null) {
            LOGGER.log(Level.CONFIG, 
                "ClassLoader {0} is not a URLClassLoader, dynamic grant is "
                + "limited to the ProtectionDomain of the granted class",
                cl);
        }
        return new CodebaseDomainGrant(domain, loaderRef, codebase, principals, permissions);
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
        if (hasDomain){
            // In the event that this is a PROTECTIONDOMAIN or CLASSLOADER grant
            // the PermissionGrant returned by the build method will be void.
            domain = new WeakReference<ProtectionDomain>((ProtectionDomain) null);
        } else {
            domain = null;
        }
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException{
        if (uris != null && !uris.isEmpty()) uri = uris.toArray(new String[uris.size()]);
        out.defaultWriteObject();
    }
    
    // readResolve method returns a PermissionGrant instance.
    private Object readResolve(){
        // Don't deserialize specific grant's, they will grant to any domain.
        if (context == CLASSLOADER || context == PROTECTIONDOMAIN 
                || context == CODEBASE_DOMAIN){
            if (hasDomain) return nullGrant;
        }
        // It's ok to return domainless dynamic grants.
        return build();
    }



    // This is a singleton so we don't need to implement equals or hashCode.
    static class NullPermissionGrant extends PermissionGrant implements Serializable {
        private static final long serialVersionUID = 1L;

        public boolean implies(ProtectionDomain pd) {
            return false;
        }

        public boolean implies(ClassLoader cl, Principal[] pal) {
            return false;
        }

        public boolean implies(CodeSource codeSource, Principal[] pal) {
            return false;
        }

        public boolean isVoid() {
            return true;
        }

        public PermissionGrantBuilder getBuilderTemplate() {
            return new PermissionGrantBuilderImp();
        }
        
        public String toString(){
            return "Null PermissionGrant";
        }
        
        private Object readResolve(){
            return nullGrant;
        }

	@Override
	public boolean impliesEquivalent(PermissionGrant grant) {
	    return false;
	}

	@Override
	public boolean isDyanamic() {
	    return true;
	}
        
    }
}
