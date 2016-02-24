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
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * A <code>SlaveRequest</code> to start a service.
 */
@AtomicSerial
class StartServiceRequest implements SlaveRequest {

    /** the service name */
    private String serviceName;

    /** the service instance count (from the master point of view) */
    private int count;

    /**
     * Construct the request.
     *
     * @param serviceName the service name
     * @param count       the service instance count
     */
    StartServiceRequest(String serviceName, int count) {
	this.serviceName = serviceName;
	this.count = count;
    }
    
    StartServiceRequest(GetArg arg) throws IOException{
	this(arg.get("serviceName", null, String.class),
	     arg.get("count", 0));
    }

    /**
     * Called by the <code>SlaveTest</code> after unmarshalling this object.
     * The <code>AdminManager</code> is retrieved from the slave test,
     * an admin is retrieved from the manager, and the admins <code>start</code>
     * method is called. The service proxy is return wrapped in
     * a <code>MarshalledObject</code>.
     *
     * @param slaveTest a reference to the <code>SlaveTest</code>
     * @return the service proxy wrapped in a <code>MarshalledObject</code>
     * @throws Exception if an error occurs starting the service
     */
    public Object doSlaveRequest(SlaveTest slaveTest) throws Exception {
	Admin admin = slaveTest.getAdminManager().getAdmin(serviceName, count);
	admin.start();
	MarshalledInstance mo = new MarshalledInstance(admin.getProxy());
	return mo;
    }
}
