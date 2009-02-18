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
import java.io.PrintWriter;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

// All other imports
import java.util.List;
import java.util.Iterator;
import java.rmi.*;
import net.jini.core.lease.Lease;
import net.jini.core.entry.Entry;


public class BlockingTest extends MatchTestBase {
    private boolean useRead;
    private long matchTimeout;
    private long writeWait;
    private long testTimeout;

    protected void parse() throws Exception {
        super.parse();
        useRead = getConfig().getBooleanConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.BlockingTest.useRead", false);
        matchTimeout = getConfig().getLongConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.BlockingTest.matchTimeout", 60000);
        writeWait = getConfig().getLongConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.BlockingTest.writeWait", 20000);
    }

    /**
     * Sets up the testing environment.
     *
     * @param args Arguments from the runner for setup.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.parse();
    }

    public void run() throws Exception {
        Matcher[] matchers = new Matcher[writeList.size()];
        Iterator i;
        int j;

        for (i = writeList.iterator(), j = 0; i.hasNext(); j++) {
            final Entry ent = (Entry) i.next();
            logger.log(Level.INFO, "Setup query for " + ent);
            matchers[j] = new Matcher(ent, j);
            matchers[j].start();
        }
        spaceSet();

        try {
            logger.log(Level.INFO, "sleeping for " + writeWait + "...");
            Thread.sleep(writeWait);
            logger.log(Level.INFO, "awake");
        } catch (InterruptedException e) {}
        final long writeStart = System.currentTimeMillis();
        logger.log(Level.INFO, writeStart + " write start");
        writeBunch();
        final long queryStart = System.currentTimeMillis();
        logger.log(Level.INFO, queryStart + " write end");

        for (j = 0; j < matchers.length; j++) {
            Matcher m = matchers[j];
            m.join(matchTimeout);

            if (!m.done) {
                logger.log(Level.INFO, System.currentTimeMillis()
                        + " failed waiting for matcher " + j);
                throw new TestException(
                        "Did not get entry back before timeout");
            } else if (m.msg != null) {
                throw new TestException( m.msg + m.thrown);
            }
        }
        final long queryEnd = System.currentTimeMillis();
        logger.log(Level.INFO, "Total Write Time:" + (queryStart - writeStart));
        logger.log(Level.INFO, "Total Query Time:" + (queryEnd - queryStart));
    }


    private class Matcher extends Thread {
        final private Entry tmpl;
        final private int id;
        private boolean done;
        private Throwable thrown;
        private String msg;

        Matcher(Entry tmpl, int id) {
            this.tmpl = tmpl;
            this.id = id;
        }

        public void run() {
            try {
                while (true) {
                    try {
                        Entry rslt;

                        if (useRead) {
                            rslt = spaceRead(tmpl, null, matchTimeout);
                        } else {
                            rslt = spaceTake(tmpl, null, matchTimeout);
                        }

                        if (rslt == null) {
                            msg = "Got null back from query on " + tmpl;
                        }
                        logger.log(Level.INFO, System.currentTimeMillis() + " " + id
                                + (useRead ? " Read " : " Took ") + rslt);
                        return;
                    } catch (RemoteException e) {

                        // Just try again
                    } catch (Throwable t) {
                        thrown = t;
                        msg = "Got exception in query on " + tmpl;
                        return;
                    }
                }
            } finally {
                done = true;
            }
        }
    }
}
