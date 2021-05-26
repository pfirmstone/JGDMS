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

package org.apache.river.test.impl.end2end.e2etest;

import net.jini.io.MarshalledInstance;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * An interface defining calls which must be implemented by
 * the remote service. The <code>ServiceHandler</code> should test
 * for these methods before testing for methods defined in
 * ConstraintsInterface in case there is a match between one
 * of these methods and the naming convention used to create the
 * method names in ConstraintsInterface.
 */
public interface CoreInterface extends Remote {

    /**
     * Unexport the remote service.
     *
     * @throws RemoteException if a communication failure occurs
     */
    public void unexport() throws RemoteException;

    /**
     * Called after an unexport is performed to verify
     * that the remote service is no longer reachable. An explicit
     * method is defined for this test just for clarity.
     *
     * @throws RemoteException if a communication failure occurs
     */
    public void callAfterUnexport() throws RemoteException;

    /**
     * Get a new proxy from the remote service.
     *
     * @return a pickled copy of the exported service stub
     *
     * @throws RemoteException if a communication failure occurs
     */
    public MarshalledInstance newProxy() throws RemoteException;
}
