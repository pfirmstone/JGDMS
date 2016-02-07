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

import java.util.ArrayList;
import java.util.Arrays;

/**
 * The messages which can be sent to a <code>SlaveTest.</code>
 */
public class ServiceListRequest implements InboundAutotRequest {

    public Object doRequest(QAConfig config, AdminManager manager) 
	throws Exception
    {
	if (manager == null) { // manager is null till setup is complete
	    return null;
	}
	AbstractServiceAdmin[] admins = manager.getAllAdmins();
	ArrayList list = new ArrayList();
	for (int i = 0; i < admins.length; i++) {
	    AbstractServiceAdmin admin = admins[i];
	    if (admin instanceof ActivatableServiceStarterAdmin
		|| admin instanceof NonActivatableServiceStarterAdmin) 
	    {
		String impl = admin.getImpl();
		if (impl == null) {
		    impl = admin.getName();
		}
		if (impl.indexOf(".") > 0) {
		    impl = impl.substring(impl.lastIndexOf(".") + 1);
		}
		list.add(impl);
	    }
	    if (admin instanceof RemoteServiceAdmin) {
		String host = ((RemoteServiceAdmin) admin).getHost();
		int index = host.indexOf("."); // strip domain name
		if (index >0) {
		    host = host.substring(0, index);
		}
		String impl = admin.getImpl();
		if (impl == null) {
		    impl = admin.getName();
		}
		if (impl.indexOf(".") > 0) {
		    impl = impl.substring(impl.lastIndexOf(".") + 1);
		}
		list.add (impl + " (on " + host + ")");
	    }
	}
	String[] names = (String[]) list.toArray(new String[list.size()]);
	Arrays.sort(names);
	return names;
    }
}
