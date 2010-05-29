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
/* @test
 * @summary Verify that DynamicPolicyProvider handles null inputs properly
 * @run main/othervm/policy=policy Test
 */

import java.security.*;
import net.jini.security.policy.DynamicPolicyProvider;

public class Test {
    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	DynamicPolicyProvider policy = new DynamicPolicyProvider();
	policy.grant((Class) null,(Principal[]) null, (Permission[]) null);
	try {
	    policy.grant((Class) null, new Principal[]{ null }, (Permission[]) null);
	    throw new Error();
	} catch (NullPointerException e) {
	}
	try {
	    policy.grant((Class) null, (Principal[]) null, new Permission[]{ null });
	    throw new Error();
	} catch (NullPointerException e) {
	}
	policy.getGrants(null, null);
	try {
	    policy.getGrants(null, new Principal[]{ null });
	    throw new Error();
	} catch (NullPointerException e) {
	}
	PermissionCollection pc = 
	    policy.getPermissions((ProtectionDomain) null);
	if (pc.elements().hasMoreElements()) {
	    throw new Error("permissions returned for null protection domain");
	}
	if (policy.implies(null, new RuntimePermission("foo"))) {
	    throw new Error();
	}
    }
}
