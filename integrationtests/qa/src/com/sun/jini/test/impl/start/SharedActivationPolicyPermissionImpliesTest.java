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

/**
 * Tests various permutations of in/valid inputs to
 * <code>SharedActivationPolicyPermission.implies()</code>
 */

public class SharedActivationPolicyPermissionImpliesTest 
    extends AbstractStartBaseTest 
{
    public void run() throws Exception {
        String fs = java.io.File.separator;
	SharedActivationPolicyPermission perm = 
	    new SharedActivationPolicyPermission(
                fs + "vob" + fs + "jive" + fs + "policy" + fs + "policy.all");
	SharedActivationPolicyPermission perm_dup = 
	    new SharedActivationPolicyPermission(
                fs + "vob" + fs + "jive" + fs + "policy" + fs + "policy.all");
	SharedActivationPolicyPermission perm_bogus = 
	    new SharedActivationPolicyPermission(
                fs + "vob" + fs + "jive" + fs + "policy" + fs + "policy.bogus");
	SharedActivationPolicyPermission perm_subdir = 
	    new SharedActivationPolicyPermission(
                fs + "vob" + fs + "jive" + fs + "policy" + fs + "subdir" + 
                fs + "policy.bogus");
	FilePermission file_perm = 
	    new FilePermission(
                fs + "vob" + fs + "jive" + fs + "policy" + fs + "policy.all", "read");

	SharedActivationPolicyPermission perm_star = 
	    new SharedActivationPolicyPermission(
                fs + "vob" + fs + "jive" + fs + "policy" + fs + "*");
	SharedActivationPolicyPermission perm_recurse = 
	    new SharedActivationPolicyPermission(
                fs + "vob" + fs + "jive" + fs + "policy" + fs + "-");

        logger.log(Level.INFO, "Null test");
	if (perm.implies(null)) {
	    throw new TestException("Permission failed null-implies test");
	}

        logger.log(Level.INFO, "Identity test");
	if (!perm.implies(perm)) {
	    throw new TestException("Permission failed identity-implies test");
	}

        logger.log(Level.INFO, "Wrong type test");
	if (perm.implies(file_perm) || file_perm.implies(perm)) {
	    throw new TestException("Permission failed wrong type-implies test");
	}

        logger.log(Level.INFO, "Duplicate test");
	if (!perm.implies(perm_dup) || !perm_dup.implies(perm)) {
	    throw new TestException("Permission failed duplicate-implies test");
	}

        logger.log(Level.INFO, "Unequals test");
	if (perm.implies(perm_bogus) || perm_bogus.implies(perm)) {
	    throw new TestException("Permission failed unequals-implies test");
	}

        logger.log(Level.INFO, "* test");
	if (!perm_star.implies(perm)) {
	    throw new TestException("Permission failed *-implies test");
	}

        logger.log(Level.INFO, "reverse * test");
	if (perm.implies(perm_star)) {
	    throw new TestException("Permission failed reverse *-implies test");
	}

        logger.log(Level.INFO, "Recursive test");
	if (!perm_recurse.implies(perm)) {
	    throw new TestException("Permission failed recursive implies test");
	}

        logger.log(Level.INFO, "Reverse recursive test");
	if (perm.implies(perm_recurse)) {
	    throw new TestException("Permission failed reverse recursive implies test");
	}

        logger.log(Level.INFO, "Subdir test");
	if (!perm_recurse.implies(perm_subdir)) {
	    throw new TestException("Permission failed subdir implies test");
	}

        logger.log(Level.INFO, "Reverse subdir test");
	if (perm_subdir.implies(perm_recurse)) {
	    throw new TestException("Permission failed reverse subdir implies test");
	}
        return;
    }
}
