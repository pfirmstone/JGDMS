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

import java.security.Permission;
import java.security.ProtectionDomain;

/**
 * The DelegateSecurityManager is designed to enable the use of 
 * Delegate decorators to encapsulate security sensitive objects using
 * Li Gong's method guard pattern.
 * <p>
 * In this manner we can prevent references to security sensitive object's from 
 * escaping.
 * <p>
 * See "Inside Java 2 Platform Security" 2nd Edition, ISBN:0-201-78791-1, page 176.
 * <p>
 * Delegate implementations are available separately from the Apache River
 * release.
 * <p>
 * Delegates can be enabled at runtime by using the DelegateSecurityManager,
 * but only for code that utilises delegates.
 * 
 * @see DelegatePermission
 * @author Peter Firmstone
 * @since 3.0.0
 */
public class DelegateSecurityManager extends CombinerSecurityManager {

    public DelegateSecurityManager(){
        super();
    }
    
    protected boolean checkPermission(ProtectionDomain pd, Permission p){
        boolean result = pd.implies(p);
        if (!result && p instanceof DelegatePermission ){
            Permission candidate = ((DelegatePermission)p).getPermission();
            result = pd.implies(candidate);
        }
        return result;
    }
}
