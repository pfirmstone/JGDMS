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

import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.cert.Certificate;

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
public interface PermissionGrantBuilder {
   
    /**
     * Implied Context of Grant
     */ 
    public static final int CLASSLOADER = 0;
    public static final int CODESOURCE = 1;
    public static final int PROTECTIONDOMAIN = 2;
    public static final int CODESOURCE_CERTS = 3;
    
    /**
     * Build the PermissionGrant using information supplied.
     * @return an appropriate PermissionGrant.
     */
    public abstract void reset();

    public abstract PermissionGrantBuilder context(int context) throws IllegalStateException;

    public abstract PermissionGrantBuilder codeSource(CodeSource cs);

    public abstract PermissionGrantBuilder clazz(Class cl);

    public abstract PermissionGrantBuilder certificates(Certificate[] certs);

    public abstract PermissionGrantBuilder principals(Principal[] pals);
    
    public abstract PermissionGrantBuilder permissions(Permission[] perm);

    public abstract PermissionGrantBuilder deny(Denied denial);
    
    public abstract PermissionGrant build();
}
