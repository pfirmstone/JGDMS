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

/**
 * A CachingSecurityManager caches the result of check permission calls for
 * AccessControlContexts.
 * 
 * @author Peter Firmstone.
 * @since 3.0.0
 */
public interface CachingSecurityManager {

    /**
     * Clears permissions from the checked cache, it must be
     * called after calling Policy.refresh();  It is recommended that it
     * be called by a Policy provider, rather than application code.
     */
    void clearCache() throws SecurityException;
    
}
