
package org.apache.river.api.security;

import java.security.Permission;
import org.apache.river.api.common.Beta;

/**
 * Jar files that specify a META-INF/permissions.perm file as per the OSGi
 * syntax, allow a ProxyVerifier to grant these permissions dynamically.
 * 
 * @author peter
 */
@Beta
public interface CodeSourceRequiredPermissions {
    /* TODO: Override and create our own CodeSource
     * implementation that contains permissions.perm
     * After we retrieve the manifest, class bytes and
     * certificates, create the CodeSource we call
     * defineClass(String name, byte[]b, int off, int len, CodeSource cs)
     * 
     * This will be utilised by a class that overrides 
     * BasicProxyPreparer.getPermissions()
     * to retrieve the advisory permissions.
     */
    public Permission [] getPermissions();
}
