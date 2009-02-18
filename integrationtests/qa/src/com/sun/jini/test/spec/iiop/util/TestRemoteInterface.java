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
package com.sun.jini.test.spec.iiop.util;

import java.util.logging.Level;

// java.rmi
import java.rmi.Remote;
import java.rmi.RemoteException;


/**
 * Remote interface providing 3 methods.
 */
public interface TestRemoteInterface extends Remote {

    /**
     * Wait for a specified time and then return.
     *
     * @param duration period of time for waiting in milliseconds
     */
    public void wait(Integer duration) throws RemoteException;

    /**
     * Simple method for testing remote call functionality.
     *
     * @param val incoming integer value
     *
     * @return incoming val + 1
     */
    public int incr(int val) throws RemoteException;
}
