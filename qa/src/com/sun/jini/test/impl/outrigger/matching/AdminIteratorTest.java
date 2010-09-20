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
package com.sun.jini.test.impl.outrigger.matching;

import java.util.logging.Level;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

// All other imports
import java.util.List;
import java.util.Iterator;
import java.util.LinkedList;
import java.rmi.*;
import net.jini.core.transaction.TransactionException;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import net.jini.admin.Administrable;
import com.sun.jini.outrigger.JavaSpaceAdmin;
import com.sun.jini.outrigger.AdminIterator;


/**
 * Writes a number of entries and uses the admin iterator to make sure
 * they are all there. Optionally removes them using the iterator.
 * Makes sure the entries are there (or not) after closing the
 * iterator.
 */
public class AdminIteratorTest extends MatchTestBase {
    private boolean deleteAsWeGo;

    /**
     * Parse our command line options
     *
     * <DL>
     * <DT>-delete<DD> Sets the test to delete the entries as it reads
     * them through the admin iterator.  Default to off.
     * </DL>
     */
    protected void parse() throws Exception {
        super.parse();
        deleteAsWeGo = 
	    getConfig().getBooleanConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.AdminIteratorTest.delete", false);
    }

    /**
     * Sets up the testing environment.
     *
     * @param config Arguments from the runner for setup.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.parse();
    }

    public void run() throws Exception {
        writeBunch();
        spaceSet();
        int count = 0;
        final List allEntries = ((JavaSpaceAuditor)
                space).getLoggedEntries(0);
        final List copyOfAllEntries = new LinkedList(allEntries);
        JavaSpaceAdmin admin = (JavaSpaceAdmin) ((Administrable)
                services[0]).getAdmin();
	admin = (JavaSpaceAdmin) getConfig().prepare("test.outriggerAdminPreparer",
						     admin);
        final AdminIterator i = admin.contents(null, null, 10);
        Entry weGot = i.next();

        while (weGot != null) {
            boolean accounted = false;
            final Template weGotTemplate = new Template(weGot);
            logger.log(Level.INFO, "Got " + weGot + " from the interator");

            for (Iterator k = allEntries.iterator(); k.hasNext();) {
                final Entry weWrote = (Entry) k.next();
                final Template weWroteTemplate = new Template(weWrote);

                if (weWroteTemplate.matchFieldAreEqual(weGotTemplate)) {
                    k.remove();

                    if (deleteAsWeGo) {
                        i.delete();
                    }
                    accounted = true;
                    break;
                }
            }

            /*
             * At this point we have found it in the list of
             * entries we wrote or its not their
             */
            if (!accounted) {
                throw new TestException(
                        "Extra entries in space!");
            }
            weGot = i.next();
        }
        i.close();

        if (!allEntries.isEmpty()) {
            throw new TestException(
                    "Did not get back all the entries we wrote");
        }

        // Check to see if the entries are still their (or not there...)
        if (deleteAsWeGo) {

            // do a null take and complane if we get something back...
            if (spaceTake(null, null, 20000) != null) {
                throw new TestException(
                        "Deleting all the entries with the interator did"
                        + " not work");
            }
        } else {
            for (Iterator j = copyOfAllEntries.iterator(); j.hasNext();) {
                final Entry toPull = (Entry) j.next();

                if (spaceTake(toPull, null, 20000) == null) {
                    throw new TestException(
                            "Could not find one of the entries we wrote "
                            + "after using iterator");
                }
            }
        }
    }
}
