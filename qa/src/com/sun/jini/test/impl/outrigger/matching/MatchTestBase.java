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
import net.jini.core.lease.Lease;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import com.sun.jini.test.share.TestBase;


/**
 * Base class for matching tests.  Sets up an audited JavaSpace and
 * provides a number of convince methods.
 */
public abstract class MatchTestBase extends MatchTestCore {

    /**
     * List of all the Enries that get writen by <code>writeBunch()</code>.
     */
    protected List writeList = new LinkedList();

    /**
     * Array of <code>Class</code> objects that corspond to the clases
     * of the objects in the <code>writeList</code>
     * @see MatchTestBase#writeList
     */
    protected Class classList[];

    /**
     * Sets up the testing environment.
     *
     * @param config Arguments from the runner for setup.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        /*
         * Would like to this next bit in an initlize but they
         * can throw exception...
         * Set-up the write list
         */
        final StringMatch aStringMatch = new StringMatch(true);
        aStringMatch.aString = "FOO";
        writeList.add(aStringMatch);
        final IntegerMatch aIntegerMatch = new IntegerMatch(true);
        aIntegerMatch.aInt = new Integer(1);
        writeList.add(aIntegerMatch);
        final UserDefMatch aUserDefMatch = new UserDefMatch(true);
        aUserDefMatch.aRUDO = new RandomUserDefinedClass(1, 2.0f, "three");
        writeList.add(aUserDefMatch);

	final RemoteMatch aRemoteMatch = new RemoteMatch(true);
	aRemoteMatch.aRI = 
	    new ARemoteInterfaceImpl(getConfig().getConfiguration(),
				     "The quick brown fox"
				     + " jumps over the lazy dog");
	writeList.add(aRemoteMatch);
        ChildOfStringMatch aChildStringEntry = new ChildOfStringMatch(true);
        aChildStringEntry.aString = "BAR";
        aChildStringEntry.aFloat = new Float(17.5);
        writeList.add(aChildStringEntry);
        GrandChildOfStringMatch aGrandChildOfStringEntry = new
                GrandChildOfStringMatch(true);
        aGrandChildOfStringEntry.aString = "Who do we appreciate?";
        aGrandChildOfStringEntry.aFloat = new Float(6.19);
        aGrandChildOfStringEntry.arrayOfShorts = new short[] {
            2, 4, 6, 8 };
        writeList.add(aGrandChildOfStringEntry);
        ChildOfIntegerMatch aChildOfIntegerMatch = new
                ChildOfIntegerMatch(true);
        aChildOfIntegerMatch.aInt = new Integer(2);
        writeList.add(aChildOfIntegerMatch);
        GrandChildOfIntegerMatch aGrandChildOfIntegerMatch = new
                GrandChildOfIntegerMatch(true);
        aGrandChildOfIntegerMatch.aInt = new Integer(3);
        writeList.add(aGrandChildOfIntegerMatch);
        NBEEmpty aNBEEmpty = new NBEEmpty(true);
        writeList.add(aNBEEmpty);
        NBEComplex aNBEComplex = new NBEComplex(true);
        aNBEComplex.aString = "Now is the time for all good men to "
                + "come to the aid of the party";
        aNBEComplex.aFloat = new Float(17.5 * 6.3);
        aNBEComplex.arrayOfShorts = new short[] {
            1, 1, 2, 3, 5, 8, 13, 21 };
        writeList.add(aNBEComplex);
        writeList.add(new LoneEntry(true));

        // Fill in class array
        classList = new Class[writeList.size()];
        int j = 0;

        for (Iterator i = writeList.iterator(); i.hasNext();) {
            classList[j++] = i.next().getClass();
        }
        space = new JavaSpaceAuditor(getConfig().getConfiguration(), space);
        logger.log(Level.INFO, "Have JavaSpace and re-bound to Auditor");
    }

    /**
     * Write a bunch of test entries to the JavaSpace.
     * Not valid until <code>setup()</code> is called.
     * @see MatchTestBase#setup
     */
    protected void writeBunch() throws Exception {
	writeBunch(Class.forName("net.jini.core.entry.Entry"));
    }

    /**
     * Write a bunch of test entries to the JavaSpace that are
     * instances of the passed class.
     * Not valid until <code>setup()</code> is called.
     * @see MatchTestBase#setup
     */
    protected void writeBunch(Class filter) throws Exception {
	for (Iterator i = writeList.iterator(); i.hasNext();) {
	    final Entry ent = (Entry) i.next();
	    
	    if (filter.isInstance(ent)) {
		addOutriggerLease(space.write(ent, null, Lease.ANY), true);
		logger.log(Level.INFO, 
			   "Wrote " + ent.getClass().getName() + " Entry");
	    }
	}
    }

    /**
     * Write a bunch of test entries to the JavaSpace that are
     * instances of the class of the passed entry. <code>null</code>
     * is considered to have the class <code>Entery</code>.
     * Not valid until <code>setup()</code> is called.
     * @see MatchTestBase#setup
     */
    protected void writeBunch(Entry filter) throws Exception {
        if (filter == null) {
            writeBunch();
        } else {
            writeBunch(filter.getClass());
        }
    }
}
