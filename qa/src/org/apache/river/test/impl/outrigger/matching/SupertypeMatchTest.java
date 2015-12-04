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
package org.apache.river.test.impl.outrigger.matching;

import java.util.logging.Level;

// Test harness specific classes
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

// All other imports
import org.apache.river.qa.harness.Test;
import java.util.List;
import java.util.Iterator;
import java.rmi.*;
import net.jini.core.transaction.TransactionException;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import net.jini.core.entry.UnusableEntryException;


/**
 * JavaSpace matching test that writes a number of entries and then
 * reads each one with one or more superclass templates and makes sure
 * that the correct class is returned.
 */
public abstract class SupertypeMatchTest extends MatchTestBase {

    /**
     * Perform a read an make sure the class of the returned object
     * matches <code>orginalClass</code>.
     */
    protected Entry matchRead(Entry tmpl, Entry orginal, Class orginalClass)
            throws UnusableEntryException, TransactionException,
            InterruptedException, RemoteException, TestException {
        final Entry rdRslt = spaceRead(tmpl, null, queryTimeOut);
        logger.log(Level.INFO, "Attempting to pull out " + orginal + " with the template "
                + tmpl);

        if (rdRslt == null) {
            fail("Was not able to pull all the entries put in, got a "
                    + "null read");
        }

        if (!orginalClass.equals(rdRslt.getClass())) {
            fail("Did not get the right class out of JavaSpace, put in "
                    + orginalClass + " got out " + rdRslt.getClass());
        }
        return rdRslt;
    }

    /**
     * Sets up the testing environment.
     *
     * @param config Arguments from the runner for construct.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        return this;
    }

    public void run() throws Exception {
        writeBunch();
        spaceSet();

        /*
         * Now that we have some entries read them all out make sure that
         * we get object that is the same class as the orginal.  Will
         * only do this with unique entries, since that is the only way we
         * can make sure that we get the "same" entry we put in
         */
        try {
            int count = 0;
            List allEntries = ((JavaSpaceAuditor)
                    space).getLoggedEntries(0);

            for (Iterator i = allEntries.iterator(); i.hasNext();) {
                final Entry toPull = (Entry) i.next();
                final Class orginalClass = toPull.getClass();
                final TemplateGenerator gen = new SuperclassTmplGen(toPull);

                if (gen != null) {
                    Entry tmpl;

                    while ((tmpl = gen.next()) != null) {
                        matchRead(tmpl, toPull, orginalClass);
                    }
                }
                logger.log(Level.INFO, "Match " + ++count + "!");
            }
        } catch (UnusableEntryException e) {
            dumpUnusableEntryException(e);
	    throw e;
        }
    }
}
