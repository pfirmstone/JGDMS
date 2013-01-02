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
package com.sun.jini.test.share;

import java.util.logging.Level;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;

// All other imports
import java.rmi.*;
import java.io.File;
import com.sun.jini.admin.DestroyAdmin;
import net.jini.admin.Administrable;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;


public class DestroyTest extends LookupTestBase {
    /** Directory where the service keeps its persistence */
    private String perdir;

    /** Do we check to see if the dir has been deleted  */
    protected boolean checkDir;

    /** How much we'll wait after destroy() method call (in seconds) */
    private int destroyDelay;

    public Test construct(QAConfig config) throws Exception {
	super.construct(config);
	this.parse();
        return this;
    }

    /**
     * parse the command line args.
     *
     * <DL>
     * <DT>-persistenceDir<DD> <var>string</var> If used the test will
     * check to see if the directory has been removed after destroy
     * has been called
     * </DL>
     */
    protected void parse() throws Exception {
	super.parse();
	perdir = getConfig().getStringConfigVal("com.sun.jini.test.share.persistenceDir", null);
	checkDir = getConfig().getBooleanConfigVal("com.sun.jini.test.share.checkPersistenceDir", false);
	if (perdir != null)
	    checkDir = true;

	destroyDelay = getConfig().getIntConfigVal(
	        "com.sun.jini.qa.harness.destroy.delay", 10);
    }

    public void run() throws Exception {
	init();

	if (checkDir == true && perdir == null)
	    perdir = getConfig().getStringConfigVal("com.sun.jini.outrigger.log",null);

	DestroyAdmin destroyAdmin = (DestroyAdmin)admin;

	try {
	    destroyAdmin.destroy();
	} catch (RemoteException e) {
	    // Ignore, destroy might have happend before call could
	    // have returned
	}

        // wait for a while
        logger.log(Level.INFO, "Destroying worked, sleeping for "
                + destroyDelay + " seconds...");
        sleep(destroyDelay * 1000);
        logger.log(Level.INFO, "...awake");

	try {
	    ((Administrable)service).getAdmin();
	    throw new TestException("Sevice did not go away");
	} catch (RemoteException e) {
	}

	// Make that this service has gone away
	serviceDestroyed(0);

	if (perdir != null) {
	    // Check to see if their persistance data is still present
	    File f = new File(perdir);
	    if (f.exists())
		throw new TestException("Service's persistance data is still present");
	}
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
	return "Test Name = DestroyTest : \n" +
		"Test tests DestroyAdmin features.";
    }

}





