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

import java.io.File;
import java.security.PermissionCollection;

/**
 * Verifies that a collection of <code>SharedActivationPolicyPermission</code>
 * objects must all imply any given permission.
 */
public class SharedActivationPolicyPermissionCollectionTest 
    extends AbstractStartBaseTest 
{
    public void run() throws Exception {

        String fs = File.separator;

	SharedActivationPolicyPermission perm = 
	    new SharedActivationPolicyPermission(
                fs + "vob" + fs + "jive" + fs + "policy" + fs + "policy.all");
	SharedActivationPolicyPermission perm_dup = 
	    new SharedActivationPolicyPermission(
                fs + "vob" + fs + "jive" + fs + "policy" + fs + "policy.all");
	SharedActivationPolicyPermission perm_bogus = 
	    new SharedActivationPolicyPermission(
                fs +"vob" + fs + "jive" + fs + "policy" + fs + "policy.bogus");
	SharedActivationPolicyPermission perm_subdir = 
	    new SharedActivationPolicyPermission(
                fs +"vob" + fs + "jive" + fs + "policy" + fs + "subdir" 
                + fs + "policy.bogus");

	SharedActivationPolicyPermission perm_star = 
	    new SharedActivationPolicyPermission(
                fs +"vob" + fs + "jive" + fs + "policy" + fs + "*");
	SharedActivationPolicyPermission perm_recurse = 
	    new SharedActivationPolicyPermission(
                fs +"vob" + fs + "jive" + fs + "policy" + fs + "-");

	logger.log(Level.INFO, "Creating new permission collection.");
        PermissionCollection col = perm.newPermissionCollection();
	
        logger.log(Level.INFO, "Empty test");
	if (col.implies(perm)) {
	    throw new TestException("Permission failed empty-implies test");
	}

	logger.log(Level.INFO, "Adding permission.");
	col.add(perm);

        logger.log(Level.INFO, "Identity test");
	if (!col.implies(perm)) {
	    throw new TestException("Permisson failed identity-implies test");
	}
	
        logger.log(Level.INFO, "Duplicate test");
	if (!col.implies(perm_dup)) {
	    throw new TestException("Permisson failed duplicate-implies test");
	}

        logger.log(Level.INFO, "Bogus test");
	if (col.implies(perm_bogus)) {
	    throw new TestException("Permisson failed bogus-implies test");
	}
        
	logger.log(Level.INFO, "Star test");
	if (col.implies(perm_star)) {
	    throw new TestException("Permisson failed star-implies test");
	}
	
        logger.log(Level.INFO, "recurse test");
	if (col.implies(perm_recurse)) {
	    throw new TestException("Permisson failed recurse-implies test");
	}

	logger.log(Level.INFO, "Creating another permission collection.");
	PermissionCollection col2 = perm.newPermissionCollection();

	logger.log(Level.INFO, "Adding permission: " + perm_recurse);
	col2.add(perm_recurse);
	
        logger.log(Level.INFO, "reverse recurse test");
	if (!col2.implies(perm)) {
	    throw new TestException("Permisson failed reverse recurse-implies test");
	}

        // Note: All permissions need to imply the given permission, not just one of them
	logger.log(Level.INFO, "Adding permission: " + perm_star);
	col2.add(perm_star);
	
        logger.log(Level.INFO, "reverse star test");
	if (!col2.implies(perm)) {
	    throw new TestException("Permisson failed reverse star-implies test");
	}
	
	return;
    }
}
