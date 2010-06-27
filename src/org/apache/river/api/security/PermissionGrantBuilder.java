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

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import net.jini.security.GrantPermission;

/**
 * The PermissionGrantBuilder creates Dynamic PermissionGrant's based on
 * information provided by the user.  The user must have access to the
 * system policy and have permission to grant permissions.
 * 
 * Don't Serialize the PermissionGrant, instead get a builder template
 * and send that.
 * 
 * Single Thread use only.
 * @author Peter Firmstone.
 */
public abstract class PermissionGrantBuilder implements Serializable{
    private static final long serialVersionUID = 1L;
    
    public PermissionGrantBuilder(){
        AccessController.checkPermission(new RuntimePermission("getProtectionDomain"));
    }
    
    /**
     * Build the PermissionGrant using information supplied.
     * @return an appropriate PermissionGrant.
     */
    public abstract void reset();

    public abstract PermissionGrantBuilder context(int context);

    public abstract PermissionGrantBuilder codeSource(CodeSource cs);

    public abstract PermissionGrantBuilder clazz(Class cl);

    public abstract PermissionGrantBuilder domain(WeakReference<ProtectionDomain> pd);

    public abstract PermissionGrantBuilder certificates(Certificate[] certs);

    public abstract PermissionGrantBuilder principals(Principal[] pals);
    
    public final PermissionGrantBuilder denials(Deny denied) {
        AccessController.checkPermission(new DenyPermission());
        return deny(denied);
    }
    
    public final PermissionGrantBuilder grant(Permission[] permissions){
        AccessController.checkPermission(new GrantPermission(permissions));
        return permissions(permissions);
    }
    
    public final PermissionGrant create(){
        return AccessController.doPrivileged(new PrivilegedAction<PermissionGrant>(){
            public PermissionGrant run(){
                return build();
            }
        });
    }

    protected abstract PermissionGrantBuilder permissions(Permission[] permissions);

    protected abstract PermissionGrant build();
    
    protected abstract PermissionGrantBuilder deny(Deny denied);
}
