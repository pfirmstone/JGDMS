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
 * @summary Verify that AggregatePolicyProvider does not invoke
 *          Thread.getContextClassLoader if overridden by subclass
 * @run main/othervm/policy=policy Test
 */
package com.sun.jini.test.impl.start.aggregatepolicyprovider;

import com.sun.jini.start.AggregatePolicyProvider;
import net.jini.security.policy.*;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.TestException;

public class UntrustedGetContextClassLoader extends QATest {

    static AggregatePolicyProvider policy;

    public void run() throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	policy = new AggregatePolicyProvider();
	ClassLoader ldr = UntrustedGetContextClassLoader.class.getClassLoader();
	policy.setPolicy(ldr, new PolicyFileProvider());
	Thread.currentThread().setContextClassLoader(ldr);
	Test test = new Test();
	test.start();

	try { test.join(); } catch (InterruptedException ex) {}

	if (test.getContextClassLoaderCalled) {
	    throw new TestException("getContextClassLoader has been called.");
	}
    }


    class Test extends Thread {
        public boolean getContextClassLoaderCalled;

        public ClassLoader getContextClassLoader() {
            getContextClassLoaderCalled = true;
            return super.getContextClassLoader();
        }
    
        public void run() {
            policy.getPermissions(getClass().getProtectionDomain());
        }
    }
}
