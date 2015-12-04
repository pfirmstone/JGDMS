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

package org.apache.river.test.share;

import java.util.logging.Level;

// Test harness specific classes
import org.apache.river.qa.harness.TestException;

// All other imports
import java.rmi.*;
import net.jini.admin.JoinAdmin;
import java.util.Set;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

public class LookupGroupAdminTest extends LookupTestBase {
    private JoinAdmin joinAdmin;
    static final private String[] strarray = new String[0];
    private String[] originalState;

    public Test construct(QAConfig config) throws Exception {
	super.construct(config);
	this.parse();
        return this;
    }

    protected void checkGroups(Set groups, String op) throws RemoteException, TestException {
	String[] fromService = joinAdmin.getLookupGroups();
	String[] shouldBe = (String[])groups.toArray(strarray);
	if (!arraysEqual(shouldBe, fromService)) {
	    logger.log(Level.INFO, op + " did not work");
	    logger.log(Level.INFO, "Should have:");
	    dumpArray(shouldBe, ",");
	    logger.log(Level.INFO, "has:");
	    dumpArray(fromService, ",");
	    throw new TestException (op + " did not work");
	}
    }

    public void run() throws Exception {
	init();
	joinAdmin = (JoinAdmin)admin;

	if (!noCleanup) {
	    try {
		originalState = joinAdmin.getLookupGroups();
	    } catch (Throwable t) {
		setupFailure(
		    "Could not get inital state for future restoration", t);
	    }
	}

	Set groups = new java.util.HashSet();
	groups.add("Jim");
	groups.add("Ann");
	groups.add("Ken");

	// Put the groups in a known state
	joinAdmin.setLookupGroups((String[])groups.toArray(strarray));
	checkGroups(groups, "Set");

	joinAdmin.setLookupGroups((String[])groups.toArray(strarray));
	checkGroups(groups, "Set Two");

	// Add
	groups.add("Bob");
	groups.add("Floor polish");
	groups.add("desert topping");
	joinAdmin.addLookupGroups(new String[]{"Floor polish"});
	joinAdmin.addLookupGroups((String[])groups.toArray(new String[0]));
	checkGroups(groups, "Add");

	// Remove
	groups.remove("Floor polish");
	groups.remove("desert topping");
	joinAdmin.removeLookupGroups(new String[]{"Floor polish",
						  "desert topping"});
	checkGroups(groups, "Remove");

	if (tryShutdown) {
	    shutdown();

	    if (activatable) {
		logger.log(Level.INFO, "Checking groups");
		checkGroups(groups, "Restart");
	    } else {
		try {
		    joinAdmin.getLookupGroups();
		    throw new TestException("Sevice did not go away");
		} catch (RemoteException e) {
		    // This is what we are looking for
		}
	    }
	}
    }

    /** Performs cleanup actions necessary to achieve a graceful exit of
     *  the current QA test.
     *  @exception TestException will usually indicate an "unresolved"
     *  condition because at this point the test has completed.
     */
    public void tearDown() {
	try {
	    if (!noCleanup) {
		try {
		    ((JoinAdmin)admin).setLookupGroups(originalState);
		} catch (Throwable t) {
		    cleanupFailure("Could not restore inital state", t);
		}
	    }
	} catch (Exception ex) {
	    String message = "Warning: Test LookupGroupAdminTest did not shutdown " +
			     "cleanly!\n" + "Reason: " + ex.getMessage();
	    logger.log(Level.INFO, message);
	    ex.printStackTrace();
	}
	super.tearDown();
    }

    /**
     * Return an array of String whose elements comprise the
     * categories to which this test belongs.
     */
    public String[] getCategories() {
	return new String[] { "admin" };
    }

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
	return "Test Name = LookupGroupAdminTest : \n" +
		"Test tests different operations with groups.";
    }

}



