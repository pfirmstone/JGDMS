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

import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import javax.security.auth.Subject;

/**
 * The PermissionGrantBuilder creates Dynamic PermissionGrant's based on
 * information provided by the user.  The user must have access to the
 * system policy and have permission to grant permissions.
 * 
 * A PermissionGrantBuilder implementation should also be used as the serialized form
 * for PermissionGrant's, the implementation of PermissionGrant's should
 * remain package private.
 * 
 * This prevents the serialized form becoming part of the public api.
 * 
 * Single Thread use only.
 * @author Peter Firmstone.
 * @since 3.0.0
 * @see PermissionGrant
 */
public abstract class PermissionGrantBuilder {
   
    /**
     * The PermissionGrant generated will apply to all classes loaded by
     * the ClassLoader
     */ 
    public static final int CLASSLOADER = 0;
  
    /**
     * The PermissionGrant generated will apply to all classes belonging to
     * the ProtectionDomain.  This is actually a simplification for the 
     * programmer the PermissionGrant will apply to the CodeSource and the
     * ClassLoader combination, the reason for this is the DomainCombiner may
     * create new instances of ProtectionDomain's from those that exist on
     * the stack.
     * <p>
     * DNS is not consulted, the RFC3986 normalized URI and all Certificates contained
     * by the CodeSources must be equal.
     * 
     * @see java.security.AccessControlContext
     * @see java.security.DomainCombiner
     * @see javax.security.auth.SubjectDomainCombiner
     */
    public static final int PROTECTIONDOMAIN = 2;
    /**
     * The PermissionGrant generated will apply to the Subject that has 
     * all the principals provided.
     * 
     * @see Subject
     */
    public static final int PRINCIPAL = 3;
    /**
     * The PermissionGrant generated will apply to all classes loaded from
     * CodeSource's that have at a minimum the defined array Certificate[]
     * provided the logged in Subject also has all Principals when defined.
     */
    public static final int CODESOURCE_CERTS = 4;
    
    /**
     * <p>
     * The PermissionGrant generated will imply the ProtectionDomain run as
     * a Subject with all Principals (when applicable) and
     * CodeSource that has the Certificates and URI RFC3986 location as specified.
     * </p>
     * <p>
     * The outcome of URI comparison is similar to 
     * {@link CodeSource#implies(CodeSource)}.</p>
     * <p>
     * DNS lookup is avoided for security and performance reasons,
     * DNS is not authenticated and therefore cannot be trusted.  Doing so,
     * could allow an attacker to use DNS Cache poisoning to escalate
     * Permission, by imitating a URL with privilege, such as AllPermission.</p>
     * <p>
     * CodeSource URL are converted to URI and normalized according
     * to <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC3986</a> before being 
     * compared as Strings.</p>
     * <p>
     * A URI based PermissionGrant "implies" a specified {@link ProtectionDomain} if:
     * </P>
     * <ol>
     * <li> The {@link ProtectionDomain}'s <i>{@link CodeSource}</i> is not null.
     * <li> All {@link Principal}'s, if defined in the {@link PermissionGrant} are present in the 
     *      {@link ProtectionDomain}, when run as a {@link Subject}, in any order.
     * <li> A URI based PermissionGrant "implies" a specified {@link ProtectionDomain} 
     *      or non null {@link CodeSource} if:
     * <ol>
     * <li> All {@link Certificate}'s included in a URI based 
     * PermissionGrant are present in that {@link ProtectionDomain}'s 
     * <i>{@link CodeSource}</i>'s certificates, in any order, or no Certificates
     *    are defined by the PermissionGrant.
     * <li> For any {@link URI} in a PermissionGrant, checks are made in the 
     * following order:
     *   <ol>
     *     <li>  Any null {@link URI} implies any 
     *           {@link ProtectionDomain} that contains a non null
     *           {@link CodeSource}, including a null {@link URL} returned by
     *           {@link CodeSource#getLocation()}.  
     *
     *     <li>  If any RFC3986 normalized URI equals the {@link ProtectionDomain}'s 
     *           <i>{@link CodeSource#getLocation()}</i>'s {@link URL}
     *           after it is converted to a RFC3986 normalized {@link URI}, 
     *           the {@link PermissionGrant#implies(java.security.ProtectionDomain)}
     *           method will return true.
     * 
     *     <li>  The {@link CodeSource#getLocation()}'s {@link URL} is checked
     *           against each {@link URI} contained in a PermissionGrant and
     *           returns true if all the following conditions are met for at least
     *           one {@link URI}:
     *     <ol>
     *     <li>  The {@link URI#getScheme()} scheme must be
     *           equal to a {@link CodeSource}'s {@link URL#getProtocol()}
     *           protocol, after normalization to RFC3986 rules.
     *
     *     <li>  If {@link URI#getHost()} is non null,  
     *           and {@link URL#getHost()} is equal after RFC3986 compliant normalization
     *           performed.
     *
     *     <li>  If the {@link URI#getPort()} port is not 
     *           equal to -1 (that is, if a port is specified), it must equal the 
     *           CodeSource URL's port.
     *
     *     <li>  URI and URL path's are normalized to RFC3986, in addition file: scheme
     *           paths are normalized to upper case, on platforms with a backslash
     *           path separator.
     *     <li>  After normalization, if this {@link URI#getPath()} path doesn't equal
     *           <i>codesource</i>'s {@link URL#getPath()} path, then the following checks are made:
     *           If this URI's path ends with "/-",
     *           then <i>codesource</i>'s URL path must start with this URI's
     *           path (exclusive the trailing "-").
     *           If this URI's path ends with a "/*",
     *           then <i>codesource</i>'s URL path must start with this URI's
     *           path and must not have any further "/" separators.
     *           If this URI's path doesn't end with a "/", 
     *           then <i>codesource</i>'s URL path must match this URI's 
     *           path with a '/' appended.
     *
     *     <li>  If this {@link URI#getFragment()} fragment is 
     *           not null, it must equal <i>codesource</i>'s 
     *           {@link URL#getRef()} reference.
     * 
     *     <li>  Unlike {@link CodeSource#implies(java.security.CodeSource) }
     *           {@link URI#getQuery()} query is not appended to the path because
     *           normalization to specific platforms is undefined.  It appears
     *           that {@link URL} was developed prior to RFC2396 and as such
     *           the {@link URL#getFile()} included the query component, later
     *           in Java 1.3 the {@link URL#getPath() } method was added, however
     *           earlier developed classes like {@link URLClassLoader} continued
     *           to use {@link URL#getFile() } and append this with path
     *           separators and wild cards after the query, if it existed.  In 
     *           any case, Certificate and Principal are more prudent identifiers for 
     *           privileges.
     *      </ol>
     *   </ol>
     * </ol>
     * </ol>
     */
    public static final int URI = 5;
    
    public static PermissionGrantBuilder newBuilder(){
        return new PermissionGrantBuilderImp();
    }
    
    /**
     * resets the state for reuse, identical to a newly created 
     * PermissionGrantBuilder, this step must be performed to avoid unintentional
     * grants to previously added URI.
     * @return PermissionGrantBuilder
     */
    public abstract PermissionGrantBuilder reset();
   
    /**
     * Sets the context of the PermissionGrant to on of the static final 
     * fields in this class.
     * 
     * @param context value of a static field defined in this builder.
     * @return PermissionGrantBuilder
     * @throws IllegalStateException if context out of range of static fields 
     * defined in this builder.
     */
    public abstract PermissionGrantBuilder context(int context) throws IllegalStateException;
    
    /**
     * The URI will be added to the PermissionGrant, multiple may be specified by
     * calling multiple times.
     * 
     * @param path - RFC3986 compliant URI or null.
     * @return PermissinoGrantBuilder
     */
    public abstract PermissionGrantBuilder uri(String path);
    /**
     * Extracts ProtectionDomain
     * from the Class for use in the PermissionGrantBuilder.  The ClassLoader
     * and ProtectionDomain are weakly referenced, when collected any 
     * created PermissionGrant affected will be voided.
     * @param cl Class used to determine the ProtectionDomain to be
     * used for PermissionGrant.
     * @return PermissionGrantBuilder.
     */
    public abstract PermissionGrantBuilder clazz(Class cl);
    /**
     * Sets the Certificate[] a CodeSource must have to receive the PermissionGrant.
     * @param certs Certificates
     * @return a PermissionGrantBuilder
     */
    public abstract PermissionGrantBuilder certificates(Certificate[] certs);
    
    /**
     * Sets the Certificate[] a CodeSource must have to receive the PermissionGrant.
     * 
     * @param certs Certificates
     * @param aliases of certificates.
     * @return a PermissionGrantBuilder
     */
    public abstract PermissionGrantBuilder certificates(Certificate[] certs, String[] aliases);
    /**
     * Sets the Principal[] that a Subject must have to be entitled to receive
     * the PermissionGrant.
     * 
     * @param pals Principals.
     * @return a PermissionGrantBuilder
     */
    public abstract PermissionGrantBuilder principals(Principal[] pals);
    /**
     * Specifies Permission's to be granted.
     * @param perm Permissions.
     * @return a PermissionGrantBuilder
     */
    public abstract PermissionGrantBuilder permissions(Permission[] perm);
    /**
     * An Exclusion specifically excludes some code from receiving a 
     * PermissionGrant.  This may be to avoid a known security vulnerability,
     * where code that we don't have control over allows a reference to
     * escape without performing adequate security checks.
     * 
     * EG: I trust code signed by XXX, but they have a security vulnerability
     * in xxx.jar
     * 
     * A better implementation would be to use a deny policy, where exclusions
     * are checked before grants are checked.
     * 
     * In the default implementation, this doesn't apply to Principal only 
     * grants, only Certificate and ClassLoader based grants.
     * @param e
     * @return 
     */
    //public abstract PermissionGrantBuilder exclude(Exclusion e);
    /**
     * Build the PermissionGrant using information supplied.
     * @return an appropriate PermissionGrant.
     */
    public abstract PermissionGrant build();

    /**
     * 
     * 
     * @param domain WeakReference containing ProtectionDomain
     * @return a PermissionGrantBuilder
     */
    public abstract PermissionGrantBuilder setDomain(WeakReference<ProtectionDomain> domain);
}
