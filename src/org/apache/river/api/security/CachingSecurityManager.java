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
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Set;
import javax.security.auth.Subject;

/**
 * The CachingSecurityManager enables acceptable performance for 
 * Li Gong's method guard pattern.
 * 
 * See "Inside Java 2 Platform Security" 2nd Edition, ISBN:0-201-78791-1, page 176.
 * 
 * The method guard pattern is better suited to application developers, since
 * most uses of standard java permissions allow the guarded object
 * reference to escape.
 * 
 * DelegatePermission can be used to encapsulate another Permission to assist
 * implementing the method guard pattern as an alternative for existing 
 * Permissions that allow guarded objects to escape.
 * 
 * Apart from allowing implementations of the method guard pattern, the
 * CachingSecurityManager can improve concurrency by caching repeated
 * security checks.
 * 
 * 
 * @see Permission
 * @see DelegatePermission
 * @author Peter Firmstone.
 */
public interface CachingSecurityManager {

    /**
     * This method clears permissions from the checked cache, it should be
     * called after calling Policy.refresh();
     *
     * @throws SecurityException 
     */
    void clearCache() throws SecurityException;
    
}
