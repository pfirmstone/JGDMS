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
 * @summary Verify that PolicyInitializationException results if base policy
 *          class is not found
 * @run main/othervm/policy=policy Test
 */

import java.security.*;
import net.jini.security.policy.*;

public class Test {
    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	Security.setProperty(
	    "net.jini.security.policy.PolicyFileProvider." +
	    "basePolicyClass", "foo");
	try {
	    new PolicyFileProvider();
	    throw new Error();
	} catch (PolicyInitializationException e) {
	}
    }
}
