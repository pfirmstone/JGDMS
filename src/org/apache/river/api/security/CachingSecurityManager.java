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
import java.util.Set;

/**
 * The CachingSecurityManager is designed to enable the use of DelegatePermission
 * for Delegate Objects to encapsulate security sensitive objects using
 * Li Gong's method guard pattern.
 * 
 * In this manner we can prevent references to security sensitive object's from 
 * escaping.
 * 
 * See "Inside Java 2 Platform Security" 2nd Edition, ISBN:0-201-78791-1, page 176.
 * 
 * @author Peter Firmstone.
 */
public interface CachingSecurityManager {

    /**
     * This method clears permissions from the checked cache, it should be
     * called after calling Policy.refresh();
     * 
     * If the Set provided contains permissions, only those of the same
     * class will be removed from the checked cache.
     * 
     * If the Set is null, the checked cache is cleared completely.
     *
     * @throws java.lang.InterruptedException
     * @throws java.util.concurrent.ExecutionException
     */
    void clearCache() throws SecurityException;
    
}
