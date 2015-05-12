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
import java.util.Set;
import net.jini.admin.JoinAdmin;
import net.jini.core.entry.Entry;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import net.jini.lookup.entry.ServiceControlled;

public class LookupAttributeAdminTest extends LookupTestBase {
    private JoinAdmin joinAdmin;
    static final private Entry[] entryarray = new Entry[0];
    private Entry[] originalState;

    public Test construct(QAConfig config) throws Exception {
	super.construct(config);
	this.parse();
        return this;
    }

    protected static void fill(Set aSet, Object[] aArray) {
	for (int i=0; i<aArray.length; i++)
	    aSet.add(aArray[i]);
    }

    protected void checkAttributes(Set attributes, String op)
	throws RemoteException, TestException
    {
	Entry[] fromService = joinAdmin.getLookupAttributes();
	Entry[] shouldBe = (Entry[])attributes.toArray(entryarray);
	if (!arraysEqual(shouldBe, fromService)) {
	    logger.log(Level.INFO, op + " did not work");
	    logger.log(Level.INFO, "Should have:\n\t");
	    dumpArray(shouldBe, "\n\t");
	    logger.log(Level.INFO, "has:\n\t");
	    dumpArray(fromService, "\n\t");
	    throw new TestException(op + " did not work");
	}
    }

    public void run() throws Exception {
	init();
	joinAdmin = (JoinAdmin)admin;

	if (!noCleanup) {
	    try {
		originalState = joinAdmin.getLookupAttributes();
	    } catch (Throwable t) {
		setupFailure(
		    "Could not get inital state for future restoration", t);
	    }
	}

	Set attributes = new java.util.HashSet();

	// Add

	// get the state
	fill(attributes, joinAdmin.getLookupAttributes());

	Entry aEntry = new AttrOne("LOOP");
	attributes.add(aEntry);
	joinAdmin.addLookupAttributes(new Entry[]{aEntry});
	checkAttributes(attributes, "Add");

	Entry entry2 = new AttrTwo(4);
	Entry entry3 = new SunOfAttrOne("AIU", 5);
	Entry entry4 = new AttrTwo(6);
	Entry entry5 = new SunOfAttrOne("Cool RTI", 7);
	attributes.add(entry2);
	attributes.add(entry3);
	attributes.add(entry4);
	attributes.add(entry5);
	joinAdmin.addLookupAttributes(
	    new Entry[]{aEntry, entry2, entry3,  entry4,  entry5});
	checkAttributes(attributes, "Add 2");

	attributes.remove(entry2);
	attributes.remove(entry4);
	((AttrOne)entry3).aString = "OPSIN";

	Entry[] templates = new Entry[]{
	    new AttrTwo(),
	    entry5,
	    new AttrOne("AIU")
	};
	Entry[] val = new Entry[]{
	    null,
	    new SunOfAttrOne("Cool but unappreciated RTI", 7),
	    new AttrOne("OPSIN")
	};

	joinAdmin.modifyLookupAttributes(templates, val);
	((AttrOne)entry5).aString = "Cool but unappreciated RTI";
	checkAttributes(attributes, "Modify");

	if (tryShutdown) {
	    shutdown();

	    if (activatable) {
		logger.log(Level.INFO, "Checking attributes");
		checkAttributes(attributes, "Restart");
	    } else {
		try {
		    joinAdmin.getLookupAttributes();
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
	if (!noCleanup) {
	    try {
		JoinAdmin joinAdmin = (JoinAdmin)admin;

		// In case something from the orginal state got removed
		int nonCtrlCount = 0;
		for (int i=0; i<originalState.length; i++)
		    if (!(originalState[i] instanceof ServiceControlled))
			nonCtrlCount++;

		Entry[] subState = new Entry[nonCtrlCount];
		int s = 0; // Index into substate
		for (int i=0; i<originalState.length; i++)
		    if (!(originalState[i] instanceof ServiceControlled))
			subState[s++] = originalState[i];
		// Add is idempotent, so even if was still there
		// adding it again won't hurt
		joinAdmin.addLookupAttributes(subState);


		Entry[] curAttrs = joinAdmin.getLookupAttributes();
		int numToRemove = curAttrs.length - originalState.length;

		if (numToRemove > 0) {
		    Entry[] templates = new Entry[numToRemove];
		    // These are all null
		    Entry[] newAttrs = new Entry[numToRemove];

		    int t = 0; // index into templates
		    // For each attribute in curAttrs, but not in
		    // originalState stick it into template
		    for (int i=0; i<curAttrs.length; i++) {
			boolean match = false;
			for (int j=0; j<originalState.length; j++) {
			    if (curAttrs[i].equals(originalState[j])) {
				match = true;
				break;
			    }
			}

			if (!match)
			    // Must not have been in original state
			    templates[t++] = curAttrs[i];
		    }
		    joinAdmin.modifyLookupAttributes(templates, newAttrs);
		}
	    } catch (Throwable t) {
		try {
		    cleanupFailure("Could not restore inital state", t);
		} catch (Exception ex) {
		    String message = "Warning: Test LookupLocatorAdminTest did not shutdown " +
				"cleanly!\n" + "Reason: " + ex.getMessage();
		    logger.log(Level.INFO, message);
		    ex.printStackTrace();
		}
	    }
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
	return "Test Name = LookupAttributeAdminTest : \n" +
		"Test tests different operations with attributes.";
    }

}



