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
 * @bug 6232019
 * @summary Untrusted code should not be able to use an instance of
 * PreferredClassProvider for which the constructor's security check
 * did not succeed, such as by a having a subclass that overrides
 * finalize() to provide a reference to the instance and through which
 * dangerous protected methods can be invoked.
 *
 * @build FinalizerAttack
 * @run main/othervm FinalizerAttack
 */

import java.net.URL;
import net.jini.loader.pref.PreferredClassProvider;

public class FinalizerAttack {
    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 6232019\n");

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	try {
	    new PCP();
	    throw new RuntimeException("TEST FAILED: " +
				       "created PCP successfully");
	} catch (SecurityException e) {
	    e.printStackTrace();
	}
	System.gc();
	System.runFinalization();
	synchronized (lock) {
	    if (createdLoader == null) {
		lock.wait(1000);
	    }
	    if (createdLoader != null) {
		throw new RuntimeException("TEST FAILED: created loader " +
					   createdLoader);
	    } else {
		System.err.println("TEST PASSED");
	    }
	}
    }

    private static final Object lock = new Object();
    private static ClassLoader createdLoader = null;

    private static class PCP extends PreferredClassProvider {
	PCP() { super(); }
	public void finalize() throws Throwable {
	    synchronized (lock) {
		System.err.println("PCP.finalize invoked!");
		try {
		    ClassLoader l = createClassLoader(new URL[0], null, false);
		    System.err.println("PCP.finalize created loader: " + l);
		    createdLoader = l;
		} catch (Throwable t) {
		    t.printStackTrace();
		    throw t;
		} finally {
		    lock.notifyAll();
		}
	    }
	}
    }
}
