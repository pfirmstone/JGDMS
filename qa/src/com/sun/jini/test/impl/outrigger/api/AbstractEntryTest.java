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
package com.sun.jini.test.impl.outrigger.api;

import java.util.logging.Level;

// jini classes
import net.jini.core.entry.*;
import net.jini.entry.AbstractEntry;

// java classes
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;


/**
 * Test to make sure that extended AbstractEntry objects and
 * implemented Entry objects work as specified.
 */
public class AbstractEntryTest extends QATestEnvironment implements Test {
    private int failed = 0; // count of failed tests
    private boolean verbose;


    public static class NameAE extends AbstractEntry {
        public String name;
        public transient int ignored = nextIgnored++;
        private static int nextIgnored = 1;

        public NameAE() {}

        public NameAE(String name) {
            this.name = name;
        }
    }


    public static class FriendlyAE extends NameAE {
        public HashMap friends = new HashMap();

        public FriendlyAE() {}

        public FriendlyAE(String name) {
            super(name);
        }
    }


    public static class NameE implements Entry {
        public String name;
        public transient int ignored = nextIgnored++;
        private static int nextIgnored = 1;

        public NameE() {}

        public NameE(String name) {
            this.name = name;
        }
    }


    public static class FriendlyE extends NameE {
        public HashMap friends = new HashMap();

        public FriendlyE() {}

        public FriendlyE(String name) {
            super(name);
        }
    }

    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.parse();
        return this;
    }

    /**
     *  Parse non-generic option(s).
     *
     * <DL>
     * <DT>-verbose<DD> To debug purpose.
     * </DL>
     */
    protected void parse() throws Exception {
        verbose = getConfig().getBooleanConfigVal("com.sun.jini.qa.outrigger."
                + "api.AbstractEntryTest.verbose", false);
        logger.log(Level.INFO, "verbose = " + verbose);
    }

    public void run() throws Exception {

        // Create extended AbstractEntry objects (AE entries)
        NameAE bobAE = new NameAE("Bob");
        NameAE carolAE = new NameAE("Carol");
        FriendlyAE tedAE = new FriendlyAE("Ted");
        FriendlyAE aliceAE = new FriendlyAE("Alice");
        NameAE bobTwinAE = new NameAE("Bob");
        FriendlyAE tedTwinAE = new FriendlyAE("Ted");

        // Modify extended AbstractEntry objects
        tedAE.friends.put(aliceAE.name, aliceAE);
        tedTwinAE.friends.put(aliceAE.name, aliceAE);
        aliceAE.friends = null;

        // Create implemented Entry objects (E entries)
        NameE bobE = new NameE("Bob");
        NameE carolE = new NameE("Carol");
        FriendlyE tedE = new FriendlyE("Ted");
        FriendlyE aliceE = new FriendlyE("Alice");
        NameE bobTwinE = new NameE("Bob");
        FriendlyE tedTwinE = new FriendlyE("Ted");

        // Modify implemented Entry objects
        tedE.friends.put(aliceE.name, aliceE);
        tedTwinE.friends.put(aliceE.name, aliceE);
        aliceE.friends = null;

        // test NameAE
        checkEquals(true, bobAE, bobAE);
        checkEquals(true, bobAE, bobTwinAE);
        checkEquals(false, bobAE, carolAE);
        checkEquals(false, bobAE, tedAE);
        checkEquals(false, bobAE, aliceAE);

        // test FriendlyAE
        checkEquals(false, tedAE, bobAE);
        checkEquals(false, tedAE, carolAE);
        checkEquals(true, tedAE, tedAE);
        checkEquals(true, tedAE, tedTwinAE);
        checkEquals(false, tedAE, aliceAE);

        // test NameE
        checkEquals(true, bobE, bobE);
        checkEquals(true, bobE, bobTwinE);
        checkEquals(false, bobE, carolE);
        checkEquals(false, bobE, tedE);
        checkEquals(false, bobE, aliceE);

        // test FriendlyE
        checkEquals(false, tedE, bobE);
        checkEquals(false, tedE, carolE);
        checkEquals(true, tedE, tedE);
        checkEquals(true, tedE, tedTwinE);
        checkEquals(false, tedE, aliceE);

        // test cross-class with same field names and content
        checkEquals(false, bobAE, bobE);
        checkEquals(false, bobTwinAE, bobTwinE);
        checkEquals(false, carolAE, carolE);
        checkEquals(false, tedAE, tedE);
        checkEquals(false, tedTwinAE, tedTwinE);
        checkEquals(false, aliceAE, aliceE);

        // all entries
        List all = new ArrayList();

        // add AE entries
        all.add(bobAE);
        all.add(bobTwinAE);
        all.add(carolAE);
        all.add(tedAE);
        all.add(tedTwinAE);
        all.add(aliceAE);

        // add E entries
        all.add(bobE);
        all.add(bobTwinE);
        all.add(carolE);
        all.add(tedE);
        all.add(tedTwinE);
        all.add(aliceE);

        // Check all entries
        checkHashCode(all);
        checkToString(all);

        if (failed != 0) {
            throw new TestException(failed + " test(s) failed");
        }
    }

    private void checkSuccess(boolean success, String show) {
        if (!success) {
            failed++;
        }

        if (verbose || !success) { // show all failures
            logger.log(Level.INFO, success ? "  " : "! ");
            logger.log(Level.INFO, show);
        }
    }

    private void checkEquals(boolean shouldBe, Entry e1, Entry e2) {
        boolean is = AbstractEntry.equals(e1, e2);
        checkSuccess(shouldBe == is, "equals(" + e1 + ", " + e2 + ") is " + is);

        if (e1 instanceof AbstractEntry) {
            boolean ns = e1.equals(e2);
            checkSuccess(ns == is, "non-static " + ns + ", static " + is);
        }
    }

    private void checkHashCode(List all) {
        Iterator it = all.iterator();

        while (it.hasNext()) {
            Entry entry = (Entry) it.next();
            int shouldBe;

            if (entry instanceof FriendlyAE) {
                FriendlyAE fae = (FriendlyAE) entry;
                shouldBe = 0 ^ fae.name.hashCode();

                if (fae.friends != null) {
                    shouldBe ^= fae.friends.hashCode();
                }
            } else if (entry instanceof FriendlyE) {
                FriendlyE fe = (FriendlyE) entry;
                shouldBe = 0 ^ fe.name.hashCode();

                if (fe.friends != null) {
                    shouldBe ^= fe.friends.hashCode();
                }
            } else if (entry instanceof NameAE) {
                NameAE nae = (NameAE) entry;
                shouldBe = 0 ^ nae.name.hashCode();
            } else if (entry instanceof NameE) {
                NameE ne = (NameE) entry;
                shouldBe = 0 ^ ne.name.hashCode();
            } else {
                failed++;
                logger.log(Level.INFO, "! entry type is " + entry.getClass().getName());
                return;
            }
            int is = AbstractEntry.hashCode(entry);
            checkSuccess(shouldBe == is,
                    "hashCode " + is + ", should be " + shouldBe + ", entry = "
                    +entry);

            if (entry instanceof AbstractEntry) {
                int ns = entry.hashCode();
                checkSuccess(ns == is, "non-static " + ns + ", static " + is);
            }
        }
    }

    private void checkToString(List all) {
	Iterator it = all.iterator();
	while (it.hasNext()) {
	    Entry entry = (Entry) it.next();
	    String name;
	    String friends = null;
	    boolean isFriendly = false;
	    if (entry instanceof FriendlyAE) {
		FriendlyAE fae = (FriendlyAE) entry;
		name = fae.name;
		friends = (fae.friends == null ? "null" : fae.friends.toString());
		isFriendly = true;
	    } else if (entry instanceof FriendlyE) {
		FriendlyE fe = (FriendlyE) entry;
		name = fe.name;
		friends = (fe.friends == null ? "null" : fe.friends.toString());
		isFriendly = true;
	    } else if (entry instanceof NameAE) {
		NameAE nae = (NameAE) entry;
		name = nae.name;
	    } else if (entry instanceof NameE) {
		NameE ne = (NameE) entry;
		name = ne.name;
	    } else {
		failed++;
		logger.log(Level.INFO, "! entry type is " + entry.getClass().getName());
		return;
	    }
	    String is = AbstractEntry.toString(entry);
            if (isFriendly) {
                checkSuccess( (is.regionMatches(is.indexOf(friends),friends,0,
                    friends.length())&& is.regionMatches(is.indexOf(name),
                    name, 0, name.length())),
                    "toString contains \"" +
                    name + "\" and \"" + friends + "/");
            } else {
                checkSuccess( is.regionMatches(is.indexOf(name),name,0,
                    name.length()), "toString contains \"" +
                    name + "/");
            }
	    if (entry instanceof AbstractEntry) {
		String ns = entry.toString();
		checkSuccess(ns.equals(is),
		    "non-static " + ns + ", static " + is);
	    }
	}
    }
}
