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

import java.io.IOException;
import java.rmi.MarshalledObject;

/**
 * A <code>SlaveTestRequest</code> used to kill the activation group of a 
 * service.
 */
class KillVMRequest implements SlaveRequest {

    /** 
     * A MarshalledObject wrapping the proxy of the service to kill.
     * The proxy identifies to the slave the service whose activation
     * group is to be killed. The proxy must be wrapped in a MarshalledObject
     * so that the codebase is not lost. Failure to do so results in a
     * <code>ClassNotFoundException</code> in the slave.
     */
    private MarshalledObject marshalledProxy;

    /**
     * Construct the request. The proxy is wrapped.
     *
     * @param proxy the proxy whose group is to be killed
     * @throws TestException which wraps an IOException which can
     *         occur when the MarshalledObject is instantiated.
     */
    public KillVMRequest(Object proxy) throws TestException {
	try {
	    marshalledProxy = new MarshalledObject(proxy);
	} catch (IOException e) {
	    throw new TestException("Unexpected exception", e);
	}
    }

    /**
     * Execute the request. The proxy is unwrapped and the local
     * AdminManager is called to kill the activation group vm.
     *
     * @param slaveTest the SlaveTest instance
     * @return a Boolean whose value is true if the group was killed,
     *         or false if not, or if the call to kill the group threw
     *         an exception.
     */
    public Object doSlaveRequest(SlaveTest slaveTest) {
	AdminManager manager = slaveTest.getAdminManager();
	Boolean b = new Boolean(false);
	try {
	    b = new Boolean(manager.killVM(marshalledProxy.get()));
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return b;
    }
}
