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
 * @summary Test proper functioning when DynamicPolicyProvider is used with a
 * 	    base policy that is also dynamic.
 * @run main/othervm/policy=policy Test
 */

import net.jini.security.policy.DynamicPolicyProvider;
import java.security.*;

public class Test {

    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	DynamicPolicyProvider policy1 = new DynamicPolicyProvider();
	DynamicPolicyProvider policy2 = new DynamicPolicyProvider(policy1);
	Class cl = Test.class;
	ProtectionDomain pd = cl.getProtectionDomain();

	Permission p = new RuntimePermission("A");
	if (!(policy1.implies(pd, p) && policy2.implies(pd, p))) {
	    throw new Error();
	}

	p = new RuntimePermission("B");
	if (policy1.implies(pd, p) || policy2.implies(pd, p)) {
	    throw new Error();
	}
	policy1.grant(cl, null, new Permission[]{ p });
	if (!(policy1.implies(pd, p) && policy2.implies(pd, p))) {
	    throw new Error();
	}

	p = new RuntimePermission("C");
        if (policy1.implies(pd, p)) throw new Error();
	policy2.grant(cl, null, new Permission[]{ p });
	if (policy1.implies(pd, p)) throw new Error();
        if (!policy2.implies(pd, p)) throw new Error();
    }
}
