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

package net.jini.security.proxytrust;

import net.jini.security.SecurityContext;

/**
 * A trust verifier context element that provides a security context
 * to use to restrict privileges when invoking methods on untrusted objects.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.1
 */
public interface UntrustedObjectSecurityContext {
    /**
     * Returns a security context to use to restrict privileges when
     * invoking methods on untrusted objects. The returned context may be
     * based on the current security context in effect when this
     * method is invoked.
     *
     * @return a security context to use to restrict privileges when
     * invoking methods on untrusted objects
     */
    SecurityContext getContext();
}
