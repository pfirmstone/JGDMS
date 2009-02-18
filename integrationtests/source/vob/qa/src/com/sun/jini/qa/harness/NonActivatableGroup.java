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

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * An interface for an object capable of starting a non-activatable
 * service in it's own VM.
 */
interface NonActivatableGroup extends Remote {
 
    /**
     * Start a nonactivatable service.
     *
     * @param codebase the codebase to assign the service
     * @param policyFile the name of the security policy file   
     * @param classpath the classpath for the service
     * @param serviceImpl the implementation class name for the service
     * @param configArgs the configuration arguments
     * @param starterConfigName the name of the starter configuration file
     *
     * @throws RemoteException if a failure occurs
     */
    public Object startService(String codebase,
			       String policyFile,
			       String classpath,
			       String serviceImpl,
			       String[] configArgs,
			       String starterConfigName,
			       ServiceDescriptorTransformer transformer)
	throws RemoteException;

    /**
     * Stop the group.
     */
    public void stop() throws RemoteException;
}
