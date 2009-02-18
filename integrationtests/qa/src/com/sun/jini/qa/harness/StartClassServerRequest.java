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

import java.rmi.MarshalledObject;

import java.io.Serializable;

/**
 * A <code>SlaveRequest</code> to start a service.
 */
class StartClassServerRequest implements SlaveRequest {

    /** the service name */
    private String serviceName;

    /**
     * Construct the request.
     *
     * @param serviceName the service name
     */
    StartClassServerRequest(String serviceName) {
	this.serviceName = serviceName;
    }

    /**
     * Called by the <code>SlaveTest</code> after unmarshalling this object.
     * The <code>AdminManager</code> is retrieved from the slave test,
     * an admin is retrieved from the manager, and the admins <code>start</code>
     * method is called. The <code>serviceName</code> should be the name
     * of a class server, although no check is performed to verify this.
     * <code>null</code> is returned since the class server 'proxy' is
     * a local reference which is not serializable.
     *
     * @param slaveTest a reference to the <code>SlaveTest</code>
     * @return null
     * @throws Exception if an error occurs starting the service
     */
    public Object doSlaveRequest(SlaveTest slaveTest) throws Exception {
	Admin admin = slaveTest.getAdminManager().getAdmin(serviceName, 0);
	admin.start();
	return null;
    }
}
