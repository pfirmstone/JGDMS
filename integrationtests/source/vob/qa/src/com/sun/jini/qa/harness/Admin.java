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

package com.sun.jini.qa.harness;

import java.rmi.RemoteException;

/**
 * An interface for managing services which can be started
 * and stopped. Examples include contributed services, the
 * activation system, and class servers.
 */

public interface Admin {

    /**
     * Perform the actions necessary to start the service.
     * 
     * @throws RemoteException if there was a communications failure
     *         while attempting to start the entity
     * @throws TestException if there was an environmental failure
     *         while attempting to start the entity. This might
     *         result from a parameter having an invalid value, or
     *         the class server being unavailable, for instance.
     */
    abstract public void start() throws RemoteException, TestException;

    /**
     * Perform the actions necessary to stop the service
     *
     * @throws RemoteException if there was a communications failure
     *         while attempting to stop the service.
     */
    abstract public void stop() throws RemoteException;
        
    /**
     * Return a reference to the service proxy or object created by this admin.
     *
     * @return the service referece or proxy
     */
    abstract public Object getProxy();
                
}
