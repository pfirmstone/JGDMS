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
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

// All other imports
import java.util.List;
import java.util.Iterator;
import java.rmi.*;
import net.jini.core.transaction.TransactionException;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import net.jini.core.entry.UnusableEntryException;


/**
 * JavaSpace matching test that writes a number of entries, takes one
 * and the makes sure that the rest are still their.
 */
public class TakeOneTest extends MatchTestBase {

    /**
     * Sets up the testing environment.
     *
     * @param args Arguments from the runner for setup.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
    }

    public void run() throws Exception {
        writeBunch();
        spaceSet();

        /*
         * Now that we have some entries take one and make sure the
         * rest of are still their.
         */
        try {
            Template tmplTemplate;
            Template fromSpaceTemplate;
            int count = 0;
            final List allEntries = ((JavaSpaceAuditor)
                    space).getLoggedEntries(0);
            final Iterator i = allEntries.iterator();

            // Take the first one
            if (!i.hasNext()) {
                fail("Auditor clams space is empty");
            }
            Entry e = (Entry) i.next();
            final Entry taken = spaceTake(e, null, queryTimeOut);
            tmplTemplate = new Template(e);
            fromSpaceTemplate = new Template(taken);

            if (!tmplTemplate.matchFieldAreEqual(fromSpaceTemplate)) {
                fail("Take of entry did not yeild exspected entry");
            }
            logger.log(Level.INFO, "Taken " + ++count);

            // Make sure the rest of the entries are still present
            while (i.hasNext()) {
                e = (Entry) i.next();
                final Entry read = spaceRead(e, null, queryTimeOut);
                tmplTemplate = new Template(e);
                fromSpaceTemplate = new Template(read);

                if (!tmplTemplate.matchFieldAreEqual(fromSpaceTemplate)) {
                    throw new TestException("Read of entry did not yield "
					  + "expected entry");
                }

                if (read == null) {
                    throw new TestException("Could not read all of "
					  + "the non-taken entries");
                }
                logger.log(Level.INFO, "Match " + ++count);
            }
        } catch (UnusableEntryException e) {
            dumpUnusableEntryException(e);
	    throw e;
        }
    }
}
