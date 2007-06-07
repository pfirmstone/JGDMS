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

import java.rmi.activation.ActivationException;
import java.rmi.RemoteException;

/**
 * The {@linkplain com.sun.jini.start service starter}
 * supported interface for shared group objects. 
 * Shared group objects are used to destroy the VM in which they are 
 * placed.
 *
 * @author Sun Microsystems, Inc.
 *
 */

public interface SharedGroup {
    /**
     * Cause the hosting VM to exit. 
     * This method should (in effect) spawn a
     * separate thread to do the actual work asynchronously, 
     * and make a reasonable attempt to let this remote call
     * return successfully. As such, a successful return from this 
     * method does not mean that the VM has been
     * destroyed. Although the service should make a reasonable 
     * attempt to let this remote call return successfully,
     * the service must not wait indefinitely for other 
     * (in-progress and subsequent) remote calls to finish before
     * proceeding to destroy its hosting VM. Once this method has been called, 
     * the service can, but need not, reject all other
     * (in-progress and subsequent) remote calls to the service. 
     *
     * @throws RemoteException if there was a problem communicating with
     *     the shared group object
     * @throws ActivationException if there was a problem creating an
     *     activatable shared group instance
     */
    public void destroyVM() throws RemoteException, ActivationException;
}
