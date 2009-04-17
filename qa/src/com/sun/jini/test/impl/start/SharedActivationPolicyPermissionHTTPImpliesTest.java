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
package com.sun.jini.test.impl.start;

import java.util.logging.Level;

import com.sun.jini.start.*;
import com.sun.jini.qa.harness.TestException;

import java.io.FilePermission;

public class SharedActivationPolicyPermissionHTTPImpliesTest 
     extends AbstractStartBaseTest {

    public void run() throws Exception {
	SharedActivationPolicyPermission perm_http = 
	    new SharedActivationPolicyPermission("http://resendes:8080/policy.all");
	SharedActivationPolicyPermission perm_http_dup = 
	    new SharedActivationPolicyPermission("http://resendes:8080/policy.all");
	SharedActivationPolicyPermission perm_http_bogus = 
	    new SharedActivationPolicyPermission("http://resendes:8080/policy.bogus");

	SharedActivationPolicyPermission perm_http_subdir = 
	    new SharedActivationPolicyPermission("http://resendes:8080/subdir/policy.bogus");

	SharedActivationPolicyPermission perm_http_star = 
	    new SharedActivationPolicyPermission("http://resendes:8080/*");
	SharedActivationPolicyPermission perm_http_recurse = 
	    new SharedActivationPolicyPermission("http://resendes:8080/-");


        logger.log(Level.INFO, "Null test");
	if (perm_http.implies(null)) {
	    throw new TestException("Permission failed null-implies test");
	}

        logger.log(Level.INFO, "Identity test");
	if (!perm_http.implies(perm_http)) {
	    throw new TestException("Permission failed identity-equals test");
	}

        logger.log(Level.INFO, "Duplicate test");
	if (!perm_http.implies(perm_http_dup) || 
	    !perm_http_dup.implies(perm_http)) {
	    throw new TestException("Permission failed duplicate test");
	}

        logger.log(Level.INFO, "Bogus test");
	if (perm_http.implies(perm_http_bogus) || 
	    perm_http_bogus.implies(perm_http)) {
	    throw new TestException("Permission failed bogus implies test");
	}

        logger.log(Level.INFO, "* test");
	if (!perm_http_star.implies(perm_http)) {
	    throw new TestException("Permission failed *-implies test");
	}

        logger.log(Level.INFO, "reverse * test");
	if (perm_http.implies(perm_http_star)) {
	    throw new TestException("Permission failed reverse *-implies test");
	}

        logger.log(Level.INFO, "Recursive test");
	if (!perm_http_recurse.implies(perm_http)) {
	    throw new TestException("Permission failed recursive implies test");
	}

        logger.log(Level.INFO, "Reverse recursive test");
	if (perm_http.implies(perm_http_recurse)) {
	    throw new TestException("Permission failed reverse recursive implies test");
	}

        logger.log(Level.INFO, "Subdir test");
	if (!perm_http_recurse.implies(perm_http_subdir)) {
	    throw new TestException("Permission failed subdir implies test");
	}

        logger.log(Level.INFO, "Reverse subdir test");
	if (perm_http_subdir.implies(perm_http_recurse)) {
	    throw new TestException("Permission failed reverse subdir implies test");
	}

	return;
    }

}
