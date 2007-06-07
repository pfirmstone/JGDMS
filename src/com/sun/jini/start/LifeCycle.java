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

package com.sun.jini.start;

/**
 * The interface used to manage the lifecycle of shared, non-activatable
 * services started by the 
 * {@linkplain com.sun.jini.start service starter}. Services
 * started via a 
 * {@link com.sun.jini.start.NonActivatableServiceDescriptor} get passed a 
 * reference to a <code>LifeCycle</code> object, which can be used by the
 * server to inform the hosting environment that it can release any resources
 * associated with the server (presumably because the server is terminating).
 *
 * @see com.sun.jini.start.NonActivatableServiceDescriptor
 * @see com.sun.jini.start.ServiceStarter
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.0
 */
public interface LifeCycle {

    /**
     * Method invoked by a server to inform the <code>LifeCycle</code>
     * object that it can release any resources associated with the server.
     * 
     * @param impl Object reference to the implementation object 
     *        created by the <code>NonActivatableServiceDescriptor</code>.
     *        This reference must be equal, in the "==" sense, to the
     *        object created by the 
     *        <code>NonActivatableServiceDescriptor</code>.
     * @return true if the invocation was successfully processed and
     *         false otherwise.
     */
     public boolean unregister(Object impl); 
}

