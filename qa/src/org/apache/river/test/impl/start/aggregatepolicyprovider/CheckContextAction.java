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
/* @summary Verify basic functionality of AggregatePolicyProvider.getContext()
 */
package org.apache.river.test.impl.start.aggregatepolicyprovider;

import java.security.PrivilegedAction;

/* Auxiliary class for GetContextTest */
public class CheckContextAction implements PrivilegedAction {
    public Object run() {
	if (Thread.currentThread().getContextClassLoader() !=
	    GetContextTest.contextClassLoader)
	{
	    throw new Error("wrong context class loader");
	}

	SecurityManager sm = System.getSecurityManager();
	for (int i = 0; i < GetContextTest.passPermissions.length; i++) {
	    sm.checkPermission(GetContextTest.passPermissions[i]);
	}
	for (int i = 0; i < GetContextTest.failPermissions.length; i++) {
	    try {
		sm.checkPermission(GetContextTest.failPermissions[i]);
		throw new Error("permission check should fail");
	    } catch (SecurityException e) {
	    }
	}
	return null;
    }
}
