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
import java.lang.reflect.Method;
import java.rmi.MarshalledObject;

/**
 * A <code>SlaveRequest</code> which calls an admin accessor method
 * on the slave host and returns the accessor value.
 */
class AdminAccessorRequest implements SlaveRequest {

    /** the service proxy who's admin is to be access */
    MarshalledObject marshalledServiceRef;

    /** the name of the accessor method to call */
    String methodName;

    /**
     * Construct the request for the given method and proxy. The
     * proxy is wrapped in a <code>MarshalledObject</code> so
     * that the codebase will be retained.
     *
     * @param methodName the name of the admin accessor method to call
     * @param serviceRef the proxy who's admin is to be called
     * @throws RuntimeException if creation of the <code>MarshalledObject</code>
     *                          fails
     */
    AdminAccessorRequest(String methodName, Object serviceRef) {
	try {
	    marshalledServiceRef = new MarshalledObject(serviceRef);
	} catch (IOException e) {
	    throw new RuntimeException("Marshalling problem", e);
	}
	this.methodName = methodName;
    }

    /**
     * The <code>SlaveTest</code> calls this method to obtain the
     * appropriate admin and reflectively invoke the requested
     * accessor method.
     *
     * @param slaveTest the SlaveTest reference 
     * @return the result of reflectively calling the admin accessor
     */
    public Object doSlaveRequest(SlaveTest slaveTest) throws Exception {
	AdminManager manager = slaveTest.getAdminManager();
	AbstractServiceAdmin admin = 
            (AbstractServiceAdmin) manager.getAdmin(marshalledServiceRef.get());
	Method accessor = admin.getClass().getMethod(methodName, null);
	return accessor.invoke(admin, null);
    }
}
