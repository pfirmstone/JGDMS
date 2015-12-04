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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;

/**
 * The interface implemented by the smart proxy. All calls are
 * forwarded to the underlying RMI stub with the exception of
 * the countEndpoints method.
 */
public interface SmartInterface extends RemoteMethodControl {

    /**
     * Obtain a new copy of the service.
     *
     * @param coordinator the <code>TestCoordinator</code> for this run
     *
     * @return a new proxy which implements this interface
     */
    public SmartInterface newProxy(TestCoordinator coordinator);

    /**
     * Set the test coordinator in the proxy
     *
     * @param coordinator the <code>TestCoordinator</code>
     */
    public void setCoordinator(TestCoordinator coordinator);

    /**
     * Invoke a method implemented by the underlying RMI stub.
     *
     * @param method the method to invoke
     * @param args the arguments to the method to invoke
     *
     * @return the Object returned by Remote call
     */
    public Object invoke(TestMethod method,Object[] args)
                  throws RemoteException,
             InvocationTargetException,
             IllegalAccessException,
             IllegalArgumentException;

    /**
     * Unexport the remote service.
     */
    public void unexport() throws RemoteException;

    /**
     * A method to be called after an unexport has occured. This call
     * should always fail. It was not necessary to define a special
     * method for this test; it was for the sake of clarity.
     */
    public void callAfterUnexport() throws RemoteException;
}
