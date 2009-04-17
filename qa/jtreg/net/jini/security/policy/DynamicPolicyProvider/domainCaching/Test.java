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
 * @bug 5054114
 * @summary Verify that ProtectionDomain is not pinned unnecessarily.
 * @library ../../../../../../testlibrary
 * @build Test Foo
 * @run main/othervm/policy=policy Test
 */

import java.lang.ref.WeakReference;
import java.net.*;
import java.security.*;
import java.util.*;
import net.jini.security.policy.DynamicPolicyProvider;

public class Test {

    public static void main(String args[]) throws Exception {
	Policy p = new DynamicPolicyProvider();

	URLClassLoader ucl = URLClassLoader.newInstance(new URL[] {
	    TestLibrary.installClassInCodebase("Foo", "cb1", true)});
	Class c = Class.forName("Foo", false, ucl);
	// Force protection domain caching.
	p.getPermissions(c.getProtectionDomain());
	WeakReference w = new WeakReference(c.getProtectionDomain());
	ucl = null;
	c = null;
	System.gc();
	if (w.get() != null) {
	    throw new Exception("ProtectionDomain is pinned");
	}
    }

}
