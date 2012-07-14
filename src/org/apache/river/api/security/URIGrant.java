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
import java.net.URI;
import java.net.URL;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 *
 * @author Peter Firmstone
 */
class URIGrant extends CertificateGrant {
    private static final long serialVersionUID = 1L;
    private final Collection<URI> location;
    private final int hashCode;
    
    @SuppressWarnings("unchecked")
    URIGrant(URI[] uri, Certificate[] certs, Principal[] pals, Permission[] perm){
        super( certs, pals, perm);
        int l = uri.length;
        Collection<URI> uris = new ArrayList<URI>(l);
        for ( int i = 0; i < l ; i++ ){
            uris.add(uri[i] != null ? uri[i].normalize() : null);
        }
        location = Collections.unmodifiableCollection(uris);
        int hash = 3;
        hash = 67 * hash + (this.location != null ? location.hashCode() : 0);
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
        StringBuilder sb = new StringBuilder(500);
        return sb.append("\n")
                 .append("URI: ")
                 .append(location.toString())
                 .append(super.toString())
                 .append("\n")
                 .toString();
    }
    
    @Override
    public boolean implies(ClassLoader cl, Principal[] p){
	if ( !implies(p)) return false;
	if ( location.isEmpty() ) return true;
        Iterator<URI> it = location.iterator();
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
        // see com.sun.jini.test.spec.policyprovider.dynamicPolicyProvider.GrantPrincipal test.
        if (codeSource == null)  return false; // Null CodeSource is not implied.
        if (location.isEmpty()) return true; // But CodeSource with null URL is implied, if this location is empty.
        int l = location.size();
        URI[] uris = location.toArray(new URI[l]);
        for (int i = 0; i<l ; i++ ){
            if (uris[i] == null) return true;
        }
        URL url = codeSource.getLocation();
        if (url == null ) return false;
        URI implied = null;
        try {
            implied = AccessController.doPrivileged(new NormaliseURLAction(url));
        } catch (PrivilegedActionException ex) {
            Exception cause = ex.getException();
            cause.printStackTrace(System.err);
            return false;
        }
        for (int i = 0; i<l ; i++){
            if (implies(uris[i], implied)) return true;
        }
        return false;
    }
    
    /* This section of code was copied from Apache Harmony's CodeSource
     * SVN Revision 929252
     * 
     * 
     * Indicates whether the specified code source is implied by this {@code
     * URI}. Returns {@code true} if all of the following conditions are
     * {@code true}, otherwise {@code false}:
     * <p>
     * <ul>
     * <li>{@code cs} is not {@code null}
     * <li>if this {@code CodeSource}'s location is not {@code null}, the
     * following conditions are checked
     * <ul>
     * <li>this {@code CodeSource}'s location is not {@code null}
     * <li>this {@code CodeSource}'s location protocol is equal to {@code cs}'s
     * location protocol
     * <li>if this {@code CodeSource}'s location host is not {@code null}, the
     * following conditions are checked
     * <ul>
     * <li>{@code cs}'s host is not {@code null}
     * <li>the wildcard or partial wildcard of this {@code URI}'s
     * host matches {@code cs}'s location host.
     * </ul>
     * <li>if this {@code CodeSource}'s location port != -1 the port of {@code
     * cs}'s location is equal to this {@code CodeSource}'s location port
     * <li>this {@code CodeSource}'s location file matches {@code cs}'s file
     * whereas special wildcard matching applies as described below
     * <li>this {@code CodeSource}'s location reference is equal to to {@code
     * cs}'s location reference
     * </ul>
     * </ul>
     * <p>
     * Note: If this {@code CodeSource} has a {@code null} location,
     * this method returns {@code true}.
     * <p>
     * Matching rules for the {@code CodeSource}'s location file:
     * <ul>
     * <li>if this {@code CodeSource}'s location file ends with {@code "/-"},
     * then {@code cs}'s file must start with {@code CodeSource}'s location file
     * (exclusive the trailing '-')
     * <li>if this {@code CodeSource}'s location file ends with {@code "/*"},
     * then {@code cs}'s file must start with {@code CodeSource}'s location file
     * (exclusive the trailing '*') and must not have any further '/'
     * <li>if this {@code CodeSource}'s location file ends with {@code "/"},
     * then {@code cs}'s file must start with {@code CodeSource}'s location file
     * <li>if this {@code CodeSource}'s location file does not end with {@code
     * "/"}, then {@code cs}'s file must start with {@code CodeSource}'s
     * location file with the '/' appended to it.
     * </ul>
     * Examples for locations that imply the location
     * "http://harmony.apache.org/milestones/M9/apache-harmony.jar":
     *
     * <pre>
     * http:
     * http://&#42;/milestones/M9/*
     * http://*.apache.org/milestones/M9/*
     * http://harmony.apache.org/milestones/-
     * http://harmony.apache.org/milestones/M9/apache-harmony.jar
     * </pre>
     *
     * @param cs
     *            the code source to check.
     * @return {@code true} if the argument code source is implied by this
     *         {@code CodeSource}, otherwise {@code false}.
     */
    private final boolean implies(URI grant, URI implied) {
        //
        // Here, javadoc:N refers to the appropriate item in the API spec for 
        // the CodeSource.implies()
        // The info was taken from the 1.5 final API spec

        // javadoc:1
//        if (cs == null) {
//            return false;
//        }

        /* Certificates can safely be ignored, they're checked by CertificateGrant */
        
        // javadoc:2
        // with a comment: the javadoc says only about certificates and does 
        // not explicitly mention CodeSigners' certs.
        // It seems more convenient to use getCerts() to get the real 
        // certificates - with a certificates got form the signers
//        Certificate[] thizCerts = getCertificatesNoClone();
//        if (thizCerts != null) {
//            Certificate[] thatCerts = cs.getCertificatesNoClone();
//            if (thatCerts == null
//                    || !PolicyUtils.matchSubset(thizCerts, thatCerts)) {
//                return false;
//            }
//        }

        // javadoc:3
        if (grant != null) {
            
            //javadoc:3.1
//            URL otherURL = cs.getLocation();
//            if ( otherURL == null) {
//                return false;
//            }
//            URI otherURI;
//            try {
//                otherURI = otherURL.toURI();
//            } catch (URISyntaxException ex) {
//                return false;
//            }
            //javadoc:3.2
            if (grant.equals(implied)) {
                return true;
            }
            //javadoc:3.3
            if (!grant.getScheme().equals(implied.getScheme())) {
                return false;
            }
            //javadoc:3.4
            String thisHost = grant.getHost();
            if (thisHost != null) {
                String thatHost = implied.getHost();
                if (thatHost == null) {
                    return false;
                }

                // 1. According to the spec, an empty string will be considered 
                // as "localhost" in the SocketPermission
                // 2. 'file://' URLs will have an empty getHost()
                // so, let's make a special processing of localhost-s, I do 
                // believe this'll improve performance of file:// code sources 

                //
                // Don't have to evaluate both the boolean-s each time.
                // It's better to evaluate them directly under if() statement.
                // 
                // boolean thisIsLocalHost = thisHost.length() == 0 || "localhost".equals(thisHost);
                // boolean thatIsLocalHost = thatHost.length() == 0 || "localhost".equals(thatHost);
                // 
                // if( !(thisIsLocalHost && thatIsLocalHost) &&
                // !thisHost.equals(thatHost)) {

                if (!((thisHost.length() == 0 || "localhost".equals(thisHost)) && (thatHost //$NON-NLS-1$
                        .length() == 0 || "localhost".equals(thatHost))) //$NON-NLS-1$
                        && !thisHost.equals(thatHost)) {
                    
                    // Do wildcard matching here to replace SocketPermission functionality.
                    // This section was copied from Apache Harmony SocketPermission
                    boolean hostNameMatches = false;
                    boolean isPartialWild = (thisHost.charAt(0) == '*');
                    if (isPartialWild) {
                        boolean isWild = (thisHost.length() == 1);
                        if (isWild) {
                            hostNameMatches = true;
                        } else {
                            // Check if thisHost matches the end of thatHost after the wildcard
                            int length = thisHost.length() - 1;
                            hostNameMatches = thatHost.regionMatches(thatHost.length() - length,
                                    thisHost, 1, length);
                        }
                    }
                    if (!hostNameMatches) return false; // else continue.
                    
                    /* Don't want to try resolving URIGrant, it either has a
                     * matching host or it doesn't.
                     * 
                     * The following section is for resolving hosts, it is
                     * not relevant here, but has been preserved for information
                     * purposes only.
                     * 
                     * Not only is it expensive to perform DNS resolution, hence
                     * the creation of URIGrant, but a CodeSource.implies
                     * may also require another SocketPermission which may 
                     * cause the policy to get stuck in an endless loop, since it
                     * doesn't perform the implies in priviledged mode, it might
                     * also allow an attacker to substitute one codebase for
                     * another using a dns cache poisioning attack.  In any case
                     * the DNS cannot be assumed trustworthy enough to supply
                     * the policy with information at this level. The implications
                     * are greater than the threat posed by SocketPermission
                     * which simply allows a network connection, as this may
                     * apply to any Permission, even AllPermission.
                     * 
                     * Typically the URI of the codebase will be a match for
                     * the codebase annotation string that is stored as a URL
                     * in CodeSource, then converted to a URI for comparison.
                     */

                    // Obvious, but very slow way....
                    // 
                    // SocketPermission thisPerm = new SocketPermission(
                    //          this.location.getHost(), "resolve");
                    // SocketPermission thatPerm = new SocketPermission(
                    //          cs.location.getHost(), "resolve");
                    // if (!thisPerm.implies(thatPerm)) { 
                    //      return false;
                    // }
                    //
                    // let's cache it: 

//                    if (this.sp == null) {
//                        this.sp = new SocketPermission(thisHost, "resolve"); //$NON-NLS-1$
//                    }
//
//                    if (cs.sp == null) {
//                        cs.sp = new SocketPermission(thatHost, "resolve"); //$NON-NLS-1$
//                    } 
//
//                    if (!this.sp.implies(cs.sp)) {
//                        return false;
//                    }
                    
                } // if( ! this.location.getHost().equals(cs.location.getHost())
            } // if (this.location.getHost() != null)

            //javadoc:3.5
            if (grant.getPort() != -1) {
                if (grant.getPort() != implied.getPort()) {
                    return false;
                }
            }

            //javadoc:3.6
            // compatbility with URL.getFile
            String thisFile = grant.getPath();
            String thatFile = implied.getPath();
            if (thatFile == null || thisFile == null) return false;
            if (thisFile.endsWith("/-")) { //javadoc:3.6."/-" //$NON-NLS-1$
                if (!thatFile.startsWith(thisFile.substring(0, thisFile
                        .length() - 2))) {
                    return false;
                }
            } else if (thisFile.endsWith("/*")) { //javadoc:3.6."/*" //$NON-NLS-1$
                if (!thatFile.startsWith(thisFile.substring(0, thisFile
                        .length() - 2))) {
                    return false;
                }
                // no further separators(s) allowed
                if (thatFile.indexOf("/", thisFile.length() - 1) != -1) { //$NON-NLS-1$
                    return false;
                }
            } else {
                // javadoc:3.6."/"
                if (!thisFile.equals(thatFile)) {
                    if (!thisFile.endsWith("/")) { //$NON-NLS-1$
                        if (!thatFile.equals(thisFile + "/")) { //$NON-NLS-1$
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
            
            //javadoc:3.7
            // A URL Anchor is a URI Fragment.
            if (grant.getFragment() != null) {
                if (!grant.getFragment().equals(implied.getFragment())) {
                    return false;
                }
            }
            // ok, every check was made, and they all were successful. 
            // it's ok to return true.
        } // if this.location != null

        // javadoc: a note about CodeSource with null location and null Certs 
        // is applicable here 
        return true;
    }

    @Override
    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = super.getBuilderTemplate();
        Iterator<URI> it = location.iterator();
        while (it.hasNext()){
            pgb.uri(it.next());
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
    
    private static class NormaliseURLAction implements PrivilegedExceptionAction<URI> {
        private final URL codesource;
        
        NormaliseURLAction(URL codebase){
            codesource = codebase;
        }

        @Override
        public URI run() throws Exception {
            return PolicyUtils.normalizeURL(codesource);
        }
    
    }
}
