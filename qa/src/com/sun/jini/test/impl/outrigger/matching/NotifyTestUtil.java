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
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.TestException;
import java.io.PrintWriter;

// All other imports
import java.rmi.*;
import net.jini.core.transaction.TransactionException;
import net.jini.core.lease.Lease;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.space.JavaSpace;
import com.sun.jini.qa.harness.QAConfig;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Utility class for writing notify tests.
 */
class NotifyTestUtil {

    private static Logger logger = Logger.getLogger("com.sun.jini.qa.harness.test");

    private long wait = 60000;
    private JavaSpaceAuditor space;
    private MatchTestBase base;
    private QAConfig config;

    /**
     * Creates a NotifyTestUtil(). Parses the <code>argv</code>
     * as a list of command line parameters as follows:
     *
     * <DL>
     * <DT>-notify_wait<DD> Set the amount of time (in milliseconds)
     * the test will wait for after the writes are done before
     * checking to see if the test has passed
     * </DL>
     */
    NotifyTestUtil(QAConfig sysConfig, MatchTestBase base) {
        wait = sysConfig.getLongConfigVal("com.sun.jini.test.impl.outrigger.matching"
                + ".NotifyTestUtil.notify_wait", 60000);
        this.config = (QAConfig) sysConfig;
        this.base = base;
    }

    void init(JavaSpaceAuditor space) {
        this.space = space;
    }

    /**
     * Convince function that registers a
     * <code>TestSpaceListener</code> with the space using the passed
     * <code>Entry</code> as the match template.  The lease is
     * requested to be <code>Lease.ANY</code>, and the template
     * is passed as the passback object.
     * @see TestSpaceLease;
     */
    void registerForNotify(Entry tmpl)
            throws TransactionException, RemoteException, java.io.IOException {
	try {
	    EventRegistration er = 
		space.notify(tmpl,
			     null, 
			     new TestSpaceListener(config.getConfiguration(), 
			                           tmpl),
			     Lease.ANY, 
			     new MarshalledObject(tmpl));
	    QAConfig c = QAConfig.getConfig();
	    if (c.getConfiguration() instanceof com.sun.jini.qa.harness.QAConfiguration) {
		er = (EventRegistration) c.prepare("test.outriggerEventRegistrationPreparer", er);
	    }
	    Lease l = er.getLease();
	    if (c.getConfiguration() instanceof com.sun.jini.qa.harness.QAConfiguration) {
		l = (Lease) c.prepare("test.outriggerLeasePreparer", l);
	    }
            base.addLease(l, false);
	} catch (TestException e) {
	    throw new RemoteException("Configuration error", e);
	}
    }

    /**
     * Waits, and then check with the auditor to see if we pass
     */
    String waitAndCheck() {
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            final String msg = "Sleep was interrupted";
            logger.log(Level.INFO, msg);
            e.printStackTrace();
            return msg;
        }
        final AuditorSummary summery = space.summarize();

        if (summery.eventFailures != null) {
            summery.dump();
            final String msg = "Event errors";
            logger.log(Level.INFO, msg);
            return msg;
        }
        return null;
    }
}
