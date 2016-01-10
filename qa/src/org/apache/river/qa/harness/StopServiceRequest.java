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

package org.apache.river.qa.harness;

import java.io.IOException;
import java.io.Serializable;
import java.rmi.MarshalledObject;
import net.jini.io.MarshalledInstance;

/**
 * A <code>SlaveRequest</code> to stop a service previously started
 * on the slave.
 */
class StopServiceRequest implements SlaveRequest {

    /** the service proxy wrapped in a <code>MarshalledInstance</code> */
    final MarshalledInstance marshalledServiceRef;

    /**
     * Construct the request. Wrap the given proxy in a 
     * <code>MarshalledObject</code> so the correct codebase is
     * applied when this object is sent to the slave.
     *
     * @param serviceRef the proxy of the service to stop
     */
    StopServiceRequest(Object serviceRef) {
	try {
	    marshalledServiceRef = new MarshalledInstance(serviceRef);
	} catch (IOException e) {
	    throw new RuntimeException("Marshalling problem", e);
	}
    }

    /**
     * Called by the <code>SlaveTest</code> after unmarshalling this
     * object. The <code>AdminManager</code> is retrieved from the
     * slave test and its <code>destroyService</code> method called
     * for the service proxy provided in the constructor.
     *
     * @param slaveTest a reference to the slave test
     * @return null
     * @throws Exception if an exception is thrown stopping the service
     */
    public Object doSlaveRequest(SlaveTest slaveTest) throws Exception {
	slaveTest.getAdminManager().destroyService(marshalledServiceRef.get(false));
	return null;
    }
}
