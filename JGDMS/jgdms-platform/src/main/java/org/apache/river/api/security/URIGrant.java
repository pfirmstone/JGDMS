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

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedExceptionAction;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.net.Uri;

/**
 * RFC3986 URI PermissionGrant
 * @author Peter Firmstone
 * @since 3.0.0
 */
class URIGrant extends CertificateGrant {
    private static final long serialVersionUID = 1L;
    private final Collection<Uri> location;
    private final int hashCode;
    
    @SuppressWarnings("unchecked")
    URIGrant(String[] uri,
	    Certificate[] certs,
	    String[] aliases,
	    Principal[] pals,
	    Permission[] perm)
    {
        super(certs, aliases, pals, perm);
        int l = uri.length;
        Set<Uri> uris = new HashSet<Uri>(l);
        for ( int i = 0; i < l ; i++ ){
            try {
                // Do we need to move all normalisation into the URIGrant and
                // store the normalised and original forms separately? File uri are platform
                // dependant and someone may want to make a grant applicable many different platforms.
                // Uri resolves this issue - fixed 31st Mar 2013
                uris.add(uri[i] != null ? Uri.parseAndCreate(uri[i]) : null);
            } catch (URISyntaxException ex) {
                ex.printStackTrace(System.err);
            }
        }
        location = Collections.unmodifiableSet(uris);
        int hash = 3;
        hash = 67 * hash + location.hashCode();
        hash = 67 * hash + (super.hashCode());
        hashCode = hash;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
    
    @Override
    public boolean equals(Object o){
        if (o == null) return false;
        if (o == this) return true;
	if (o.hashCode() != this.hashCode()) return false;
        if (o instanceof URIGrant){
            URIGrant c = (URIGrant) o;
            if ( !super.equals(o)) return false;
	    if ( location == c.location) return true;
	    if ( location.equals(c.location)) return true;
        }
        return false;
    }
    
    @Override
    public String toString(){
	if (location != null){
	    StringBuilder sb = new StringBuilder(500);
	    Iterator<Uri> it = location.iterator();
	    while (it.hasNext()){
		sb.append("codebase \"");
		sb.append(it.next());
		sb.append("\"");
		sb.append(",\n");
	    }
	    sb.append(super.toString());
	    return sb.toString();
	}
	return super.toString();
    }
    
    @Override
    public boolean implies(ClassLoader cl, Principal[] p){
	if ( !implies(p)) return false;
	if ( location.isEmpty() ) return true;
        Iterator<Uri> it = location.iterator();
        while (it.hasNext()){
            if (it.next() == null) return true;
        }
	if ( cl == null ) return false;
	return false;  //Indeterminate.
    }
    
    
    /**
     * Checks if passed CodeSource matches this PermissionGrant. Null URI of
     * PermissionGrant implies any CodeSource, only if that CodeSource is not 
     * null.
     */
    @Override
    public boolean implies(CodeSource codeSource, Principal[] p) {
        if ( !implies(p)) return false;
        // sun.security.provider.PolicyFile compatibility for null CodeSource is false.
        // see org.apache.river.test.spec.policyprovider.dynamicPolicyProvider.GrantPrincipal test.
        if (codeSource == null)  return false; // Null CodeSource is not implied.
        if (location.isEmpty()) return true; // But CodeSource with null URL is implied, if this location is empty.
        int l = location.size();
        Uri[] uris = location.toArray(new Uri[l]);
        for (int i = 0; i<l ; i++ ){
            if (uris[i] == null) return true;
        }
        URL url = codeSource.getLocation();
        if (url == null ) return false;
        Uri implied = null;
        try {
            implied = Uri.urlToUri(url);
        } catch (URISyntaxException ex) {
            Logger.getLogger(URIGrant.class.getName()).log(Level.SEVERE, null, ex);
        }
        for (int i = 0; i<l ; i++){
            if (uris[i].implies(implied)) return true;
        }
        return false;
    }
    
    @Override
    public boolean impliesEquivalent(PermissionGrant grant) {
	if (!(grant instanceof URIGrant)) return false;
	if (!super.impliesEquivalent(grant)) return false;
	return location.equals(((URIGrant)grant).location);
    }
    
    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        Iterator<Uri> it = location.iterator();
        while (it.hasNext()){
            pgb.uri(it.next().toString());
        }
        pgb.context(PermissionGrantBuilder.URI);
        return pgb;
    }
    
    //writeReplace method for serialization proxy pattern
    private Object writeReplace() {
        return getBuilderTemplate();
    }
    
    //readObject method for the serialization proxy pattern
    private void readObject(ObjectInputStream stream) 
            throws InvalidObjectException{
        throw new InvalidObjectException("PermissionGrantBuilder required");
    }
    
    private static class NormaliseURLAction implements PrivilegedExceptionAction<Uri> {
        private final URL codesource;
        
        NormaliseURLAction(URL codebase){
            codesource = codebase;
        }

        @Override
        public Uri run() throws Exception {
            return Uri.urlToUri(codesource);
        }
    
    }
}
