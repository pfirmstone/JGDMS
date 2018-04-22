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
import java.util.ArrayList;

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
    // Transient Fields
    private transient Collection<String> uris;
    private transient WeakReference<ProtectionDomain> domain;
   
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
        hasDomain = false;
        principals = null;
        permissions = null;
        context = -1;
        return this;
    }
    
    public PermissionGrantBuilder context(int context) {
        if (context < 0) {
            throw new IllegalStateException("context must be >= 0");
        }
        if (context > 5) {
            throw new IllegalStateException("context must be <= 5");
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
            }
        }
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

    public PermissionGrant build() {
        switch (context) {
            case CLASSLOADER: //Dynamic grant
                // Don't return principal grant if domain null, dynamic grant's
                // are treated special.
                return new ClassLoaderGrant(domain, principals, permissions );
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
            default:
                return nullGrant;
        }
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
        if (context == CLASSLOADER || context == PROTECTIONDOMAIN){
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
