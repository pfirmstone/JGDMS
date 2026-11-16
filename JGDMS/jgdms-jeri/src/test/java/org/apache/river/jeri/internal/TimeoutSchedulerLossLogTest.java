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
package org.apache.river.jeri.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 * The line about tasks that the timeout scheduler lost: the first at once,
 * then one for each interval at most, with the number lost since the last,
 * and the last losses of a burst when the interval has passed although no
 * more come.
 */
public class TimeoutSchedulerLossLogTest {

    @Test
    public void testTheFirstLossIsLoggedAtOnceAndTheRestOncePerInterval() {
        TimeoutScheduler.LossLog log = new TimeoutScheduler.LossLog(1000L);
        Assert.assertEquals("the first at once", 1L, log.report(5_000L));
        Assert.assertEquals(0L, log.report(5_001L));
        Assert.assertEquals(0L, log.report(5_500L));
        Assert.assertEquals(0L, log.report(5_999L));
        Assert.assertEquals("the next line has the count since the last", 4L, log.report(6_000L));
        Assert.assertEquals(0L, log.report(6_001L));
        Assert.assertEquals("and the interval counts from the line", 2L, log.report(7_000L));
        Assert.assertEquals("a loss after a quiet time is logged at once", 1L, log.report(60_000L));
    }

    @Test
    public void testTheClockMayWrap() {
        TimeoutScheduler.LossLog log = new TimeoutScheduler.LossLog(1000L);
        long near = Long.MAX_VALUE - 500L;
        Assert.assertEquals(1L, log.report(near));
        Assert.assertEquals("500 later, across the wrap", 0L, log.report(Long.MIN_VALUE + 0L));
        Assert.assertEquals(2L, log.report(Long.MIN_VALUE + 600L));
    }

    @Test
    public void testLossesThatNoLineWasWrittenForAreFlushedWhenTheyAreDue() {
        TimeoutScheduler.LossLog log = new TimeoutScheduler.LossLog(1000L);
        Assert.assertEquals("nothing to report", 0L, log.flush(5_000L));
        Assert.assertEquals(-1L, log.dueIn(5_000L));
        Assert.assertEquals(1L, log.report(5_000L));
        Assert.assertEquals(-1L, log.dueIn(5_100L));
        Assert.assertEquals(0L, log.report(5_100L));
        Assert.assertEquals(0L, log.report(5_200L));
        Assert.assertEquals("due when the interval from the line has passed", 800L, log.dueIn(5_200L));
        Assert.assertEquals("not yet", 0L, log.flush(5_999L));
        Assert.assertEquals("due now", 0L, log.dueIn(7_000L));
        Assert.assertEquals("the two that came after the line", 2L, log.flush(6_000L));
        Assert.assertEquals("and only once", 0L, log.flush(60_000L));
        Assert.assertEquals(-1L, log.dueIn(60_000L));
        Assert.assertEquals("the interval counts from the flushed line", 0L, log.report(6_500L));
        Assert.assertEquals(1L, log.flush(7_000L));
    }

    /**
     * The last losses of a burst are written without a later loss to carry
     * them: the first at once and the others, counted, when the interval has
     * passed, by the timer, which is not counted among the tasks that wait.
     */
    @Test(timeout = 30000)
    public void testTheLastLossesOfABurstAreWrittenWhenNoMoreCome() throws Exception {
        Logger logger = Logger.getLogger(TimeoutScheduler.class.getName());
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        Handler handler = new Handler() {
            public void publish(LogRecord r) {
                if (r.getLevel() == Level.SEVERE) {
                    Object[] p = r.getParameters();
                    lines.add(p[0] + " since the last line");
                }
            }
            public void flush() { }
            public void close() { }
        };
        logger.addHandler(handler);
        TimeoutScheduler.resetLossLog(500_000_000L);
        try {
            int waiting = TimeoutScheduler.pending();
            for (int i = 0; i < 5; i++) {
                TimeoutScheduler.noteLoss();
            }
            Assert.assertEquals("the first at once", 1, awaitLines(lines, 1, 5000));
            Assert.assertEquals("and the timer is not among the tasks that wait",
                                waiting, TimeoutScheduler.pending());
            Assert.assertEquals("the others when the interval has passed, with no more losses",
                                2, awaitLines(lines, 2, 10_000));
            Assert.assertEquals("1 since the last line", lines.get(0));
            Assert.assertEquals("4 since the last line", lines.get(1));
            TimeUnit.MILLISECONDS.sleep(1200);
            Assert.assertEquals("and no more: " + lines, 2, lines.size());
            Assert.assertEquals("nothing waits", waiting, TimeoutScheduler.pending());
        } finally {
            TimeoutScheduler.resetLossLog(0L);
            logger.removeHandler(handler);
        }
    }

    /**
     * A flush that finds that the line is not due, because a loss wrote one
     * since the flush was scheduled, schedules itself again for the losses
     * that came after that line; one that finds none to report does not.
     */
    @Test
    public void testAFlushThatFindsNoLineDueAsksForAnother() {
        TimeoutScheduler.resetLossLog(0L);
        try {
            TimeoutScheduler.LossLog log = new TimeoutScheduler.LossLog(60_000_000_000L);
            long now = System.nanoTime();
            Assert.assertEquals(1L, log.report(now));
            Assert.assertEquals(0L, log.report(now + 1L));
            Assert.assertFalse(TimeoutScheduler.flushScheduled());
            TimeoutScheduler.flushLosses(log);
            Assert.assertTrue("the loss is not forgotten", TimeoutScheduler.flushScheduled());
            TimeoutScheduler.resetLossLog(0L);
            Assert.assertFalse(TimeoutScheduler.flushScheduled());
            TimeoutScheduler.LossLog none = new TimeoutScheduler.LossLog(1L);
            TimeoutScheduler.flushLosses(none);
            Assert.assertFalse("nothing to report, nothing to schedule",
                               TimeoutScheduler.flushScheduled());
        } finally {
            TimeoutScheduler.resetLossLog(0L);
        }
    }

    private static int awaitLines(List<String> lines, int n, long millis) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (lines.size() < n && System.nanoTime() < end) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        return lines.size();
    }
}
