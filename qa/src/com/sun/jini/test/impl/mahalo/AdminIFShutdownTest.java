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
package com.sun.jini.test.impl.mahalo;

import java.util.logging.Level;

// Test harness specific classes

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Arrays;

import com.sun.jini.admin.DestroyAdmin;
import com.sun.jini.qa.harness.Test;

import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.entry.Entry;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.discovery.LookupDiscovery;
import net.jini.lookup.entry.Name;


public class AdminIFShutdownTest extends AdminIFBase {

    public void run() throws Exception {

	TransactionManager mb = getTransactionManager();

	/////////////////////////
	// Administrable Methods
	/////////////////////////
	Object admin = getTransactionManagerAdmin(mb);

	//////////////////////
	// JoinAdmin Methods
	//////////////////////

	//
	// Attributes
	//

	// Uncomment the following in order to delay test progress.
	// This allows you to obtain the mailbox's PID, for example
	// before it gets destroyed at the end of this test.

	//waitOnInput(); // wait

	logger.log(Level.INFO, "\tCalling JoinAdmin methods");
	JoinAdmin ja = (JoinAdmin)admin;
	Entry[] attrs = ja.getLookupAttributes();
	logger.log(Level.INFO, "Calling JoinAdmin::getLookupAttributes: got " + 
	    attrs.length + "items");
	String name = "Spanguini";
	Entry[] newAttrs = { new Name(name) };
	logger.log(Level.INFO, "Calling JoinAdmin::addLookupAttributes()");
	ja.addLookupAttributes(newAttrs);
	logger.log(Level.INFO, 
		   "Checking addLookupAttributes call via get call");
	attrs = ja.getLookupAttributes();
	if (!assertContainsName(attrs, name)) {
	    throw new TestException( "Did not receive proper attribute "
				   + "setting for Name after adding");
	}

	shutdown(0);

	logger.log(Level.INFO, 
		   "Checking addLookupAttributes call after shutdown");
	attrs = ja.getLookupAttributes();
	if (!assertContainsName(attrs, name)) {
	    throw new TestException( "Did not receive proper attribute "
				   + "setting for Name after shutdown");
	}

	name = name + "2";
	Entry[] templates = { new Name() };
	newAttrs[0] = new Name(name);
	logger.log(Level.INFO, "Calling JoinAdmin::modifyLookupAttributes()");
	ja.modifyLookupAttributes(templates, newAttrs);
	logger.log(Level.INFO, "Checking modifyLookupAttributes call via " 
		             + "getLookupAttributes");
	attrs = ja.getLookupAttributes();
	if (!assertContainsName(attrs, name)) {
	    throw new TestException("Did not receive proper attribute "
				  + "setting for Name after modifying");
	}

	shutdown(0);

	logger.log(Level.INFO, "Checking modifyLookupAttributes call via " 
		             + "getLookupAttributes after shutdown");
	attrs = ja.getLookupAttributes();
	if (!assertContainsName(attrs, name)) {
	    throw new TestException("Did not receive proper attribute "
				  + "setting for Name after shutdown");
	}

	// double check assert mechanism
	if (assertContainsName(attrs, name + "2")) {
	    throw new TestException( "assertContainsName returned true "
				   + "for a bogus value");
	}

	//
	// LookupGroups
	//
	String[] luGroups = null;
	/*
	 * TestBase utilities automatically create and join the service(s)
	 * under test to one (unique) group. So, this section of of code 
	 * is no longer valid.
	luGroups = ja.getLookupGroups(); 
	logger.log(Level.INFO, "Calling JoinAdmin::getLookupGroups got " + 
		    luGroups.length + " items");
	dumpGroups(luGroups);
	if (!assertLookupGroups(luGroups, LookupDiscovery.NO_GROUPS))
	    throw new TestException( "getLookupGroups did not return NO_GROUPS upon startup");
	 */

	String[] groups = { "group1", "group2", "group3" };
	logger.log(Level.INFO, "Calling JoinAdmin::addLookupGroups()");
	dumpGroups(groups);
	ja.addLookupGroups(groups);
	logger.log(Level.INFO, "Verifying group set: ");
	luGroups = ja.getLookupGroups(); 
	dumpGroups(luGroups);
	if (luGroups.length < groups.length) 
	    throw new TestException( "Invalid length for returned group "
				   + "set after add");
	if (!assertLookupGroups(luGroups, groups))
	    throw new TestException( "getLookupGroups did not contain "
				   + "added group set");

	shutdown(0);

	luGroups = ja.getLookupGroups(); 
	dumpGroups(luGroups);
	if (luGroups.length < groups.length) 
	    throw new TestException( "Invalid length for returned group "
				   + "set after shutdown");
	if (!assertLookupGroups(luGroups, groups))
	    throw new TestException( "getLookupGroups did not contain "
				   + "added group after shutdown");

	logger.log(Level.INFO, "Calling JoinAdmin::removeLookupGroups()");
	dumpGroups(groups);
	ja.removeLookupGroups(groups);
	logger.log(Level.INFO, "Verifying returned groups: ");
	luGroups = ja.getLookupGroups(); 
	dumpGroups(luGroups);
	if (assertLookupGroups(luGroups, groups))
	    throw new TestException("getLookupGroups contained removed group ");

	shutdown(0);

	String[] recoveredluGroups = ja.getLookupGroups(); 
	dumpGroups(recoveredluGroups);
	if (!assertLookupGroups(recoveredluGroups, luGroups))
	    throw new TestException( "group set didn't match after shutdown");

	logger.log(Level.INFO, "Calling JoinAdmin::setLookupGroups()");
	dumpGroups(groups);
	ja.setLookupGroups(groups);
	logger.log(Level.INFO, "Verifying group set: ");
	luGroups = ja.getLookupGroups(); 
	dumpGroups(luGroups);
	if (groups.length != luGroups.length) 
	    throw new TestException( "Invalid length for returned group "
				   + "set after set");
	if (!assertLookupGroups(luGroups, groups))
	    throw new TestException( "getLookupGroups did not contain "
				   + "required group set");
	// double check assert mechanism
	String[] bogus = { groups[0], "bogusGroup", groups[1] };
	if (assertLookupGroups(luGroups, bogus))
	    throw new TestException( "assertLookupGroups returned true "
				   + "for bogus data");

	shutdown(0);

	recoveredluGroups = ja.getLookupGroups(); 
	dumpGroups(recoveredluGroups);
	if (!assertLookupGroups(recoveredluGroups, groups))
	    throw new TestException( "group set didn't match after shutdown");


	/*
	 * The following test causes cross-talk between concurrently
	 * running test suite -- resulting in spurrious failures.
	 * Setting grops=ALL causes the service to register with
	 * other LUS w/i multicast radius. Other tests can potentially
	 * get references to the service, which goes away shortly
	 * thereafter.
	logger.log(Level.INFO, "Calling JoinAdmin::setLookupGroups(ALL_GROUPS) ");
	ja.setLookupGroups(LookupDiscovery.ALL_GROUPS);
	logger.log(Level.INFO, "Verifying group set: ");
	luGroups = ja.getLookupGroups(); 
	dumpGroups(luGroups);
	if (!assertLookupGroups(luGroups, LookupDiscovery.ALL_GROUPS))
	    throw new TestException( "getLookupGroups did not contain ALL_GROUPS after set");

	shutdown(0);

	recoveredluGroups = ja.getLookupGroups(); 
	dumpGroups(recoveredluGroups);
	if (!assertLookupGroups(recoveredluGroups, LookupDiscovery.ALL_GROUPS))
	    throw new TestException( "getLookupGroups did not contain ALL_GROUPS after shutdown");
	 */

	//
	// Locators
	//
	logger.log(Level.INFO, "Calling JoinAdmin::getLookupLocators ");
	if (!assertLocators(ja.getLookupLocators(), new LookupLocator[0]))
	    throw new TestException( "Did not receive empty set of "
				   + "locators upon startup");
	logger.log(Level.INFO, "Calling JoinAdmin::addLookupLocators()");
	LookupLocator[] locators = {
	    QAConfig.getConstrainedLocator("jini://resendes:8080/"),
	    QAConfig.getConstrainedLocator("jini://resendes:8081/"),
	    };
	ja.addLookupLocators(locators);
	logger.log(Level.INFO, "Verifying JoinAdmin::addLookupLocators()");
	if (!assertLocators(ja.getLookupLocators(), locators))
	    throw new TestException( "Did not receive expected set of "
				   + "locators after add");

	shutdown(0);

	if (!assertLocators(ja.getLookupLocators(), locators))
	    throw new TestException( "Did not receive expected set of "
				   + "locators after shutdown");

	logger.log(Level.INFO, "Calling JoinAdmin::removeLookupLocators()");
	ja.removeLookupLocators(locators);
	if (!assertLocators(ja.getLookupLocators(), new LookupLocator[0]))
	    throw new TestException( "Did not receive empty set of locators "
				   + "after remove");

	shutdown(0);

	if (!assertLocators(ja.getLookupLocators(), new LookupLocator[0]))
	    throw new TestException( "Did not receive empty set of "
				   + "locators after shutdown");

	logger.log(Level.INFO, "Calling JoinAdmin::setLookupLocators()");
	ja.setLookupLocators(locators);
	if (!assertLocators(ja.getLookupLocators(), locators))
	    throw new TestException( "Did not receive expected set of "
				   + "locators after set");
	// double check assert mechanism
	LookupLocator[] bogusLoc = {
	    locators[1],
	    QAConfig.getConstrainedLocator("jini://bogus:8080/"),
	    locators[0],
	    };
	if (assertLocators(ja.getLookupLocators(), bogusLoc))
	    throw new TestException( "assertLocators returned true "
				   + "for bogus values");

	shutdown(0);

	if (!assertLocators(ja.getLookupLocators(), locators))
	    throw new TestException( "Did not receive expected set "
				   + "of locators after shutdown");


	/////////////////////////
	// DestroyAdmin Methods
	/////////////////////////
	logger.log(Level.INFO, "\tCalling DestroyAdmin methods");
	DestroyAdmin da = (DestroyAdmin)admin;
	logger.log(Level.INFO, "Calling DestroyAdmin::destroy()");
	da.destroy();

	/* Delay for a bit before returning.  The destroy call
	 * starts a "destroy" thread on the mailbox process. 
	 * One part of this clean up process
	 * is to cancel any registration leases with the lookup service.
	 * Since the lookup service is killed upon returning
	 * from this function, we can sometimes get nasty 
	 * activation error messages like "NoSuchObject".
	 */
	Thread.sleep(10000);
    }

    /**
     * Invoke parent's construct and parser
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	parse();
        return this;
    }
}
