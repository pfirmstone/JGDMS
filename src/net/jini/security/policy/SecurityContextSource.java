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

package net.jini.security.policy;

import net.jini.security.SecurityContext;

/**
 * Interface that a security manager or policy provider can optionally
 * implement in order to support the saving and restoring of custom security
 * context state.  If the installed security manager or policy provider
 * implements this interface, then its <code>getContext</code> method is
 * delegated to by the corresponding method of the
 * {@link net.jini.security.Security} class, with precedence given to the
 * security manager.
 * <p>
 * This interface is intended to be implemented by security managers and policy
 * providers whose security contexts include state in addition to that provided
 * by {@link java.security.AccessControlContext}.  For example, a security
 * policy provider that considers thread local values when evaluating security
 * checks may want to include those values in snapshots of the current security
 * context, so that they can be properly restored when the context is
 * reapplied--it can achieve this by implementing the <code>getContext</code>
 * method to return a {@link SecurityContext} instance containing the
 * snapshotted thread local values as well as the current access control
 * context, with <code>wrap</code> methods implemented to return privileged
 * action wrappers that properly restore the thread local state.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public interface SecurityContextSource {

    /**
     * Returns a snapshot of the current security context, which can be used to
     * restore the context at a later time.
     *
     * @return snapshot of the current security context
     */
    SecurityContext getContext();
}
