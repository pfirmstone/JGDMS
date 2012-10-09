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

// All imports
import java.util.Iterator;
import java.util.List;

import java.util.logging.Logger;
import java.util.logging.Level;


/**
 *
 * Structure that summarizes the state of a JavaSpaceAuditor.  Much of
 * the data refers to the auditor's log.  The log hold all of the
 * entries that have been written to the underlying space through the
 * auditor and not removed through the auditor.  A couple of
 * conditions can cause the log to be out of sync with the underlying
 * space:
 * <ul>
 * <li> If the write of an entry throws a <code>RemoteException</code>
 *      the entry will be logged but may not have actually been written
 *      into the space.
 * <li> If the entry is <code>equal</code> to another and one copy was
 *      taken then the wrong one might be pulled from the log.  Such
 *      ambiguity can not be detected by an observer until leases start
 *      to expire
 * <li> If a take throws a <code>RemoteException</code> no entries are
 *      removed from the log.
 * </ul>
 *
 * @see com.sun.jini.test.impl.outrigger.matching.JavaSpaceAuditor
 * 
 */
public class AuditorSummary {

   protected static Logger logger = Logger.getLogger("com.sun.jini.qa.harness.test");

    /**
     * Total number of entries in the the Auditor's log
     */
    public int totalEntries = 0;

    /**
     * The number of entries in the log that :
     * <ul>
     * <li> The write completed without throwing an exception
     * <li> There have never been any duplicates of the entry that have
     *      been removed.
     * </ul>
     *
     * These are called "clean" entries
     */
    public int cleanEntries = 0;

    /**
     * The most time remaining on the lease of any entry in the log who's
     * status is "clean" (@see AuditorSummary#cleanEntres)
     *
     * This field is only valid if <code>cleanEntries</code> is
     * non-zero
     */
    public long longestLease = Long.MIN_VALUE;

    /**
     * Number of entries in the log that were written by a write
     * that threw a <code>RemoteException</code>.
     */
    public int writesInDoubtCount = 0;

    /**
     * Number of entries in the log which have been duplicated and at
     * least one of the duplicates has been removed.
     */
    public int ambiguousEntryTakeCount = 0;

    /**
     * Number of takes from the underlying space that threw a
     * <code>RemoteException</code>
     */
    public int failedTakeCount = 0;

    /**
     * Number of writes that have been tried through the space
     */
    public int writeAttemptCount = 0;

    /**
     * Number of writes that have succeded without an exception
     */
    public int successfulWriteCount = 0;

    /**
     * Number of takes that have been tried through this space
     */
    public int takesAttemptCount = 0;

    /**
     * Number of takes that have succeded without an exception
     */
    public int successfulTakeCount = 0;

    /**
     * Number of times <code>take</code> or <code>takeIfExists</code>
     * has return a non-<code>null</code> and the auditor was not able
     * to find the returned entry in its log
     */
    public int logRemovalFailureCount = 0;

    /**
     * Description of any event failures. A <code>List</code> of
     * <code>String</code>s. <code>null</code> if there were no
     * detected event failures.
     */
    public List eventFailures = null;

    /**
     * Dump the summary to the specified stream
     */
    public void dump() {
        logger.log(Level.INFO, "  Total Entries in log:" + totalEntries);
        logger.log(Level.INFO, "  Clean Entries in log:" + cleanEntries);
        logger.log(Level.INFO, "  Longest Lease of clean entry in log:" + longestLease);
        logger.log(Level.INFO, "  Number of entries in log who's writes are in doubt:"
                + writesInDoubtCount);
        logger.log(Level.INFO, "  Number of entries in log who's takes are in doubt:"
                + ambiguousEntryTakeCount);
        logger.log(Level.INFO, "  Number of failed takes:" + failedTakeCount);
        logger.log(Level.INFO, "  Number of unlogged takes:" + logRemovalFailureCount);
        logger.log(Level.INFO, "  Number of attempted writes:" + writeAttemptCount);
        logger.log(Level.INFO, "  Number of successful writes:" + successfulWriteCount);
        logger.log(Level.INFO, "  Number of attempted takes:" + takesAttemptCount);
        logger.log(Level.INFO, "  Number of successful takes:" + successfulTakeCount);

        if (eventFailures == null) {
            logger.log(Level.INFO, "  No Event failures");
        } else {
            logger.log(Level.INFO, "  Event failures:");

            for (Iterator i = eventFailures.iterator(); i.hasNext();) {
                logger.log(Level.INFO, "    " + (String) i.next());
            }
        }
    }
}
