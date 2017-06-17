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
import java.io.PrintWriter;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

// All other imports
import java.rmi.*;
import net.jini.core.transaction.TransactionException;
import net.jini.admin.Administrable;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import net.jini.core.entry.UnusableEntryException;
import org.apache.river.admin.JavaSpaceAdmin;
import org.apache.river.admin.AdminIterator;
import org.apache.river.qa.harness.Test;


/**
 * JavaSpace matching test that writes a number of entries and then
 * takes them all using a template that will match all of the written
 * entries. Test is only passed if the space is empty after all the
 * takes and there are only as many successful takes as writes.
 */
public class FieldMatchTest extends MatchTestBase {

    /**
     * Template to be used for takes
     */
    protected Entry nullTemplate = null;

    /**
     * Determen what template the user wanted
     * <DL>
     * <DT>-template <var>string</var><DD> Determines what template to
     * use.  Possiable values for <var>string</var> are:
     *
     * <DL>
     * <DT>null<DD> Use <code>null</code> for the template, this is
     * the defualt if <code>-template</code> is not used.
     *
     * <DT>UniqueEntry<DD> Use a <code>UniqueEntry</code>
     *
     * <DT>UniqueEntryVM<DD> Use a <code>UniqueEntry</code> whos
     * <code>originatingHostVM</code> field is set to the string for
     * the test's VM
     *
     * <DT>NBEUniqueEntry<DD>Use a <code>NBEUniqueEntry</code>
     *
     * <DT>NBEUniqueEntryVM<DD> Use a <code>NBEUniqueEntry</code> whos
     * <code>originatingHostVM</code> field is set to the string for
     * the test's VM
     *
     * </DL>
     * </DL>
     */
    static Entry pickTemplate(QAConfig config) throws Exception {
        Entry rslt = null;
        final String tmplStr = config.getStringConfigVal("org.apache.river.qa."
                + "outrigger.matching.FieldMatchTest.template", "null");

        if (tmplStr.equals("null")) {
            rslt = null;
        } else if (tmplStr.equals("UniqueEntry")) {
            rslt = new UniqueEntry();
        } else if (tmplStr.equals("UniqueEntryVM")) {
            rslt = new UniqueEntry(true);
            ((UniqueEntry) rslt).entryID = null;
        } else if (tmplStr.equals("NBEUniqueEntry")) {
            rslt = new NBEUniqueEntry();
        } else if (tmplStr.equals("NBEUniqueEntryVM")) {
            rslt = new NBEUniqueEntry(true);
            ((NBEUniqueEntry) rslt).entryID = null;
        }
        return rslt;
    }

    protected void parse() throws Exception {
        super.parse();
        nullTemplate = FieldMatchTest.pickTemplate(getConfig());
    }

    /**
     * Sets up the testing environment.
     *
     * @param config Arguments from the runner for construct.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.parse();
        return this;
    }

    public void run() throws Exception {
        writeBunch(nullTemplate);
        spaceSet();

        // Empty the space
        try {
            JavaSpaceAuditor auditor = (JavaSpaceAuditor) space;
            final AuditorSummary rslt = auditor.emptySpace(new TakeQuery() {
                private int count = 0;

                protected Entry doQuery(JavaSpace space, Entry tmpl)
                        throws UnusableEntryException, TransactionException,
                        InterruptedException, RemoteException {
                    Entry rtn;

                    if (useIfExists) {
                        rtn = space.takeIfExists(tmpl, null, queryTimeOut);
                    } else {
                        rtn = space.take(tmpl, null, queryTimeOut);
                    }
                    count++;

                    /*
                     * Don't need to do null test here because the
                     * string concatenation operator is smart enough
                     * to convert a null reference to the string "null"
                     */
                    logger.log(Level.INFO, "Take back #" + count + ":" + rtn);
                    return rtn;
                }
            }, nullTemplate);

            logger.log(Level.INFO, "Auditor Summary after emptySpace call");
            rslt.dump();

            /*
             * In a short test like this with only one client there should
             * be no entries left.
             */
            if (rslt.totalEntries != 0) {
                ((JavaSpaceAuditor) space).dumpLog();
                throw new TestException(
                        "Not all entries that were writen were removed");
            }

            /*
             * There should be one more take than write
             * auditor.emptySpace() detects empty with a take that
             * returns null
             */
            if (rslt.writeAttemptCount + 1 != rslt.takesAttemptCount) {
                throw new TestException(
                        "Did not have an equal number of writes and takes");
            }
        } catch (UnusableEntryException e) {
            dumpUnusableEntryException(e);
	    throw e;
        }

        // Use the Admin to make sure the space is really empty
        JavaSpaceAdmin admin = (JavaSpaceAdmin) ((Administrable)
                services[0]).getAdmin();
        admin = 
	    (JavaSpaceAdmin) getConfig().prepare("test.outriggerAdminPreparer",
						 admin);
	final AdminIterator i = admin.contents(nullTemplate, null, 10);
	
	Entry entry = i.next();
	
	if (entry != null) {
	    logger.log(Level.INFO, "Space is not empty after removing"
		       + " entries:");
	    
	    while (entry != null) {
		logger.log(Level.INFO, "  " + entry);
		entry = i.next();
	    }
	    throw new TestException("Space is not empty after removing all"
				    + " entries");
	}
    }
}
