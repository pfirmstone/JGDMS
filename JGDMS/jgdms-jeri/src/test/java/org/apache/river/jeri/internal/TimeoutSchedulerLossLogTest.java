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

import org.junit.Assert;
import org.junit.Test;

/**
 * The line about tasks that the timeout scheduler lost: the first at once,
 * then one for each interval at most, with the number lost since the last.
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
}
