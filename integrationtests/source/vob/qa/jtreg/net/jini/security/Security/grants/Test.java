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
 * @summary Verify basic functionality of the grantSupported() and grant()
 * 	    methods of class Security.
 * @run main/othervm/policy=policy Test
 */

import java.security.*;
import java.util.*;
import javax.security.auth.Subject;
import net.jini.security.Security;
import net.jini.security.policy.DynamicPolicyProvider;

public class Test {
    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	final SecurityManager sm = System.getSecurityManager();
	
	if (Security.grantSupported()) {
	    throw new Error();
	}
	try {
	    Security.grant(Test.class, 
			   new Permission[]{ new RuntimePermission("foo")});
	    throw new Error();
	} catch (UnsupportedOperationException ex) {
	}
	try {
	    Security.grant(Test.class, null, 
			   new Permission[]{ new RuntimePermission("foo")});
	    throw new Error();
	} catch (UnsupportedOperationException ex) {
	}
	
	Policy.setPolicy(new DynamicPolicyProvider());
	
	Principal p = new StringPrincipal("foo");
	Set ps = new HashSet();
	ps.add(p);
	Subject s = new Subject(
	    true, ps, Collections.EMPTY_SET, Collections.EMPTY_SET);

	if (!Security.grantSupported()) {
	    throw new Error();
	}
	Security.grant(null, new Permission[]{ new RuntimePermission("0") });
	Security.grant(null, new Principal[]{ p }, 
		       new Permission[]{ new RuntimePermission("1") });
	
	sm.checkPermission(new RuntimePermission("0"));
	try {
	    sm.checkPermission(new RuntimePermission("1"));
	    throw new Error();
	} catch (SecurityException ex) {
	}
	
	Subject.doAsPrivileged(s, new PrivilegedAction() {
	    public Object run() {
		sm.checkPermission(new RuntimePermission("0"));
		sm.checkPermission(new RuntimePermission("1"));
		
		Security.grant(null, 
			       new Permission[]{ new RuntimePermission("2") });
		Security.grant(null, null,
			       new Permission[]{ new RuntimePermission("3") });

		sm.checkPermission(new RuntimePermission("2"));
		sm.checkPermission(new RuntimePermission("3"));
		
		return null;
	    }
	}, null);

	try {
	    sm.checkPermission(new RuntimePermission("2"));
	    throw new Error();
	} catch (SecurityException ex) {
	}
	sm.checkPermission(new RuntimePermission("3"));
    }
}
