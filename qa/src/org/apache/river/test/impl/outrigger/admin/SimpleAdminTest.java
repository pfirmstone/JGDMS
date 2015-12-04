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
package org.apache.river.test.impl.outrigger.admin;

import java.util.logging.Level;

// java classes
import java.rmi.*;

// jini classes
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.transaction.TransactionException;
import net.jini.admin.Administrable;
import net.jini.space.JavaSpace;

// Test harness specific classes
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

// Shared classes
import org.apache.river.qa.harness.Test;
import org.apache.river.test.share.TestBase;
import org.apache.river.test.share.UninterestingEntry;


/**
 * Simple test that pings the JavaSpace admin interfaces.  Does not
 * test the contence() method as this is exercies by the
 * AdminIteratorTest in the matching package.
 */
public class SimpleAdminTest extends TestBase implements Test {
    private boolean activatable;
    private int numRestarts;

    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.parse();
        return this;
    }

    /**
     * Parse non-generic option(s).
     *
     * <DL>
     * <DT>-activatable<DD> Tells the test that the space under
     * test is activatable.  Defaults to on.
     * </DL>
     */
    protected void parse() throws Exception {
        super.parse();
        activatable = getConfig().getBooleanConfigVal("org.apache.river.qa.outrigger."
                + "admin.SimpleAdminTest.activatable", false);
        numRestarts = getConfig().getIntConfigVal("org.apache.river.qa.outrigger."
                + "admin.SimpleAdminTest.numRestarts", 1);

        // Log out test options.
        logger.log(Level.INFO, "activatable = " + activatable);
        logger.log(Level.INFO, "numRestarts = " + numRestarts);
    }

    public void run() throws Exception {
        specifyServices(new Class[] {JavaSpace.class});

        // Try to get space
        final JavaSpace space = (JavaSpace) services[0];
        logger.log(Level.INFO, "Got Space");

        for (int i = 0; i < numRestarts; i++) {
	    addOutriggerLease(space.write(new UninterestingEntry(), null,
					  Lease.FOREVER), true);
            logger.log(Level.INFO, "Wrote UninterestingEntry");

            // Shutdown the space
            logger.log(Level.INFO, "Trying shutDown()...");

	    shutdown(0);

            // Trying to re-activate and read UninterestingEntry
            logger.log(Level.INFO, 
		       "trying to re-activate and read UninterestingEntry");

            if (activatable) {

                // try to read the entry we wrote
                String msg = "Expected space to re-activate after shutdown.\n"
                        + "Got : ";
                Entry en = null;

		en = space.takeIfExists(new UninterestingEntry(), null, 0);

                // Check for read the entry we wrote
                if (en == null) {
                    throw new TestException(
                            "Could not get entry we wrote before shutdown");
                }
            } else {
                try {
                    space.takeIfExists(new UninterestingEntry(), null, 0);
                    throw new TestException("Space did not go away");
                } catch (RemoteException e) {
                    // This is what we are looking for
                }
            }
        }
    }
}
