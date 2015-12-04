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

import java.io.File;
import java.io.FilePermission;

/**
 * Tests various permutations of in/valid inputs to
 * <code>SharedActivationPolicyPermission.equals()</code>
 */

public class SharedActivationPolicyPermissionEqualsTest 
    extends AbstractStartBaseTest
{

    public void run() throws Exception {
        String fs = File.separator;
	SharedActivationPolicyPermission perm = 
	    new SharedActivationPolicyPermission(fs + "vob" + fs + "jive" + fs + "policy" + fs + "policy.all");
	SharedActivationPolicyPermission perm_dup = 
	    new SharedActivationPolicyPermission(fs + "vob" + fs + "jive" + fs + "policy" + fs + "policy.all");
	SharedActivationPolicyPermission perm_bogus = 
	    new SharedActivationPolicyPermission(fs + "vob" + fs + "jive" + fs + "policy" + fs + "policy.bogus");
	FilePermission file_perm = 
	    new FilePermission(fs + "vob" + fs + "jive" + fs + "policy" + fs + "policy.all", "read");

        logger.log(Level.INFO, "Null test");
	if (perm.equals(null)) {
	    throw new TestException("Permission failed null-equals test");
	}

        logger.log(Level.INFO, "Identity test");
	if (!perm.equals(perm)) {
	    throw new TestException("Permission failed identity-equals test");
	}

        logger.log(Level.INFO, "Type test");
	if (perm.equals(file_perm) || file_perm.equals(perm)) {
	    throw new TestException("Permission failed type-equals test");
	}

        logger.log(Level.INFO, "Duplicate test");
	if (!perm.equals(perm_dup) || !perm_dup.equals(perm)) {
	    throw new TestException("Permission failed duplicate test");
	}

        logger.log(Level.INFO, "Duplicate hash test");
	if (perm.hashCode() != perm_dup.hashCode()) {
	    throw new TestException("Hash code failed duplicate test");
	}

        logger.log(Level.INFO, "Bogus test");
	if (perm.equals(perm_bogus) || perm_bogus.equals(perm)) {
	    throw new TestException("Permission failed bogus equals test");
	}
	return;
    }
}
