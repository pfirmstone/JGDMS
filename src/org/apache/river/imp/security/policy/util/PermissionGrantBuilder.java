/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.imp.security.policy.util;

import java.lang.ref.WeakReference;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;

/**
 * Single Thread use only.
 * @author Peter Firmstone.
 */
public class PermissionGrantBuilder {
    // Store CodeSource
    private CodeSource cs;
    private Certificate[] certs;
    private WeakReference<ProtectionDomain> domain;    
    // Array of principals 
    private Principal[] principals;
    // Permissions collection
    private Permission[] permissions;   
    private int context;
    
    public PermissionGrantBuilder(){
        reset();
    }
    
    public void reset(){
        cs = null;
        certs = null;
        domain = null;
        principals = null;
        permissions = null;
        context = PermissionGrant.CODESOURCE;
    }
    
    public PermissionGrantBuilder context(int context){
        if ( context < 0 ){
            throw new IllegalStateException("context must be >= 0");
        }
        if ( context > 3 ){
            throw new IllegalStateException("context must be <= 3");
        }
        this.context = context;
        return this;
    }
    
    public PermissionGrantBuilder codeSource(CodeSource cs){
        this.cs = cs;
        return this;
    }
    
    public PermissionGrantBuilder clazz(Class cl){
        domain = new WeakReference<ProtectionDomain>(cl.getProtectionDomain());
        return this;
    }
    
    public PermissionGrantBuilder certificates(Certificate[] certs){
        this.certs = certs;
        return this;
    }
    
    public PermissionGrantBuilder principals(Principal[] pals){
        // don't worry about being protective here.
        this.principals = pals;
        return this;
    }
    
    public PermissionGrantBuilder permissions(Permission[] permissions){
        this.permissions = permissions;
        return this;
    }
    
    public PermissionGrant build(){
        switch (context){
            case PermissionGrant.CLASSLOADER:
                return new ClassLoaderGrant(domain, principals, permissions);
            case PermissionGrant.CODESOURCE:
                return new CodeSourceGrant(cs, principals, permissions);
            case PermissionGrant.CODESOURCE_CERTS:
                return new CertificateGrant(certs, principals, permissions);
            case PermissionGrant.PROTECTIONDOMAIN:
                return new ProtectionDomainGrant(domain, principals, permissions);
            default:
                return null;
        }
    }
}
