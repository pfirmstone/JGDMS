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
package org.apache.river.outrigger;

import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.SHORT_POISON;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.Wide;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.capturing;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.fill;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.newHolder;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.text;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.warnings;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.wire;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.apache.river.outrigger.proxy.EntryRep;
import org.junit.Test;

/**
 * Tests of the evidence the scans keep once they answer correctly: a second field count inside one
 * holder is direct evidence that a rep's class identity does not describe its body (design
 * 2026-11-21, Q2), so it is counted and logged once per holder. The scans themselves are pinned,
 * against the unfixed code too, in {@link EntryRepMalformedBodyRegressionTest}.
 *
 * <p>Runtime tier: fast.
 */
public class EntryHolderMixedFieldCountTest {

    private static final Logger MATCHING = Logger.getLogger(OutriggerServerImpl.matchingLoggerName);

    /** hasMatch scan of one holder; the honest entry must be found. */
    private static void scan(EntryHolder holder, EntryRep honest) throws Exception {
        EntryRep tmpl = wire(new Wide("y", "x"));
        EntryHandle found = holder.hasMatch(tmpl, null, false, null, null, null);
        assertTrue(found != null && found.rep() == honest);
    }

    /** A holder with a short poison beside the honest entries, and the honest Wide(y,x) rep. */
    private static final class Mixed {
        final EntryHolder holder;
        final EntryRep honest;
        Mixed(EntryHolder holder, EntryRep honest) { this.holder = holder; this.honest = honest; }
    }

    /**
     * A hasMatch scan stops at the first match, and the candidates of an index bucket come in hash order,
     * so a scan meets the poison only when the poison is ahead of the honest entry. The order is fixed for
     * one holder. Files holders until the first scan of one meets both field counts (about one in two);
     * that scan has been counted once already.
     */
    private static Mixed holderWhoseScansMeetBothCounts() throws Exception {
        for (int i = 0; i < 200; i++) {
            EntryHolder holder = newHolder();
            EntryRep honest = fill(holder, SHORT_POISON);
            long before = EntryHolder.mixedFieldCountScans();
            scan(holder, honest);
            if (EntryHolder.mixedFieldCountScans() > before) return new Mixed(holder, honest);
        }
        throw new AssertionError("no holder in 200 had the poison ahead of the honest entry");
    }

    /** Each scan that meets a second count is counted; the holder logs it once. */
    @Test
    public void aMixedHolderIsCountedPerScanAndLoggedOncePerHolder() throws Exception {
        final CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<LogRecord>();
        Handler capture = capturing(records);
        MATCHING.addHandler(capture);
        try {
            Mixed mixed = holderWhoseScansMeetBothCounts();
            assertEquals("one line for the holder, logged by its first counted scan", 1, warnings(records));
            long before = EntryHolder.mixedFieldCountScans();
            scan(mixed.holder, mixed.honest);
            scan(mixed.holder, mixed.honest);
            assertEquals(2L, EntryHolder.mixedFieldCountScans() - before);
            assertEquals("later scans of the same holder do not log again", 1, warnings(records));

            holderWhoseScansMeetBothCounts();
            assertEquals("a second holder logs its own line", 2, warnings(records));
        } finally {
            MATCHING.removeHandler(capture);
        }
    }

    /** Control: a holder of one shape is neither counted nor logged. */
    @Test
    public void aConsistentHolderIsNotCountedOrLogged() throws Exception {
        final CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<LogRecord>();
        Handler capture = capturing(records);
        MATCHING.addHandler(capture);
        try {
            EntryHolder holder = newHolder();
            EntryRep honest = fill(holder, null);
            long before = EntryHolder.mixedFieldCountScans();
            scan(holder, honest);
            scan(holder, honest);
            assertEquals(text("consistent holder counted ", EntryHolder.mixedFieldCountScans() - before, " scans"),
                    0L, EntryHolder.mixedFieldCountScans() - before);
            assertEquals(0, warnings(records));
        } finally {
            MATCHING.removeHandler(capture);
        }
    }

    /** The continuing query keeps the same evidence. */
    @Test
    public void theContinuingQueryCountsAMixedHolderToo() throws Exception {
        EntryHolder holder = newHolder();
        EntryRep honest = fill(holder, SHORT_POISON);
        long before = EntryHolder.mixedFieldCountScans();
        EntryHolder.ContinuingQuery query = holder.continuingQuery(
                new EntryRep[] { wire(new Wide("y", "x")) }, null, false, System.currentTimeMillis());
        EntryHandle found = query.next(null, null, null);
        assertTrue(found != null && found.rep() == honest);
        assertEquals(1L, EntryHolder.mixedFieldCountScans() - before);
    }
}
