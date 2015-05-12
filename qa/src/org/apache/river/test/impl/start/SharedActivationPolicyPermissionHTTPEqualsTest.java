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
package org.apache.river.test.impl.start;

import java.util.logging.Level;

import org.apache.river.start.*;
import org.apache.river.qa.harness.TestException;

import java.io.FilePermission;

/**
 * Tests various permutations of in/valid URL-based inputs to
 * <code>SharedActivationPolicyPermission.equals()</code>
 */
public class SharedActivationPolicyPermissionHTTPEqualsTest 
    extends AbstractStartBaseTest 
{

    public void run() throws Exception {
	SharedActivationPolicyPermission perm_http = 
	    new SharedActivationPolicyPermission("http://resendes:8080/policy.all");
	SharedActivationPolicyPermission perm_http_dup = 
	    new SharedActivationPolicyPermission("http://resendes:8080/policy.all");
	SharedActivationPolicyPermission perm_http_bogus = 
	    new SharedActivationPolicyPermission("http://resendes:8080/policy.bogus");

        logger.log(Level.INFO, "Identity test");
	if (!perm_http.equals(perm_http)) {
	    throw new TestException("Permission failed identity-equals test");
	}

        logger.log(Level.INFO, "Duplicate test");
	if (!perm_http.equals(perm_http_dup) || 
	    !perm_http_dup.equals(perm_http)) {
	    throw new TestException("Permission failed duplicate test");
	}

        logger.log(Level.INFO, "Duplicate hash test");
	if (perm_http.hashCode() != perm_http_dup.hashCode()) {
	    throw new TestException("Hash code failed duplicate test");
	}

        logger.log(Level.INFO, "Bogus test");
	if (perm_http.equals(perm_http_bogus) || 
	    perm_http_bogus.equals(perm_http)) {
	    throw new TestException("Permission failed bogus equals test");
	}

	return;
    }
}
