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
package org.apache.river.test.impl.outrigger.transaction;

import java.util.logging.Level;

// Test harness specific classes
import org.apache.river.qa.harness.TestException;

// All other imports
import net.jini.space.JavaSpace;
import net.jini.core.entry.Entry;
import net.jini.core.transaction.Transaction;
import net.jini.core.event.EventRegistration;
import net.jini.core.lease.Lease;


public class NotifyLogTest extends TransactionTestBase {

    public void run() throws Exception {
        simpleSetup();

        // Set the stage
        final SimpleEntry notifyEntry = new SimpleEntry();
        notifyEntry.id = new Integer(1);
        final SimpleEntry dataEntry = new SimpleEntry();
        notifyEntry.id = new Integer(2);
        final Transaction txn = createTransaction();
        final SimpleEventListener listener = new SimpleEventListener(getConfig().getConfiguration());

        // Force an event registration under transaction
        logger.log(Level.INFO, "Registering for notification");
        final EventRegistration reg = getSpace().notify(notifyEntry, txn,
                listener, Lease.FOREVER, null);
        Lease lease = reg.getLease();
        lease = (Lease)
                getConfig().prepare("test.outriggerLeasePreparer", lease);
        addLease(lease, true);

        // Force a notifiedOp
        logger.log(Level.INFO, "Writing matching entry");
        writeEntry(txn, notifyEntry);

        // Force a renew op
        logger.log(Level.INFO, "Renewing registration lease");
        lease.renew(Lease.FOREVER);

        // Busy wait until we are sure the notification happened
        logger.log(Level.INFO, "Waiting for notification of first write");

        while (listener.getNotifyCount(reg) == 0) {
            Thread.yield();
        }

        // Force a cancel
        logger.log(Level.INFO, "Canceling registration lease");
        lease.cancel();

        // Write the canary
        logger.log(Level.INFO, "Writing entry to read back from log");
        writeEntry(null, dataEntry);

        // Shutdown service to force log read on restart
        logger.log(Level.INFO, "Shuting down");
        shutdown(1);
        Thread.sleep(10000);

        // Try to write the entry we wrote
        logger.log(Level.INFO, "Looking for second write in log");
        final Entry ent = getSpace().takeIfExists(dataEntry, null,
                JavaSpace.NO_WAIT);

        if (ent == null || !ent.equals(dataEntry)) {
            throw new TestException(
                    "Could not get entry we wrote back,"
                    + " log must have failed");
        }
    }

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
        return "Test Name = NotifyLogTest : \n" + "tests notifications.";
    }
}
