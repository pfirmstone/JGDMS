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
package net.jini.jeri.ssl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.AssumptionViolatedException;

/**
 * The helpers of the accept path tests that count descriptors: where they
 * cannot, the test is skipped, not passed.
 */
public class S0FixtureTest {

    @Test
    public void testAnUncountedBaselineSkipsTheTest() throws Exception {
        boolean counted = S0Fixture.assertFdsBack(-1, 10);
        Assert.assertFalse("a baseline of -1 means that nothing was checked", counted);
        try {
            S0Fixture.assumeCounted(counted);
            Assert.fail("the test must be skipped");
        } catch (AssumptionViolatedException expected) {
        }
    }

    @Test
    public void testACountedBaselineIsChecked() throws Exception {
        int fds = S0Fixture.openFds();
        org.junit.Assume.assumeTrue("descriptors cannot be counted here", fds >= 0);
        Assert.assertTrue(S0Fixture.assertFdsBack(fds + 1000, 10));
        try {
            S0Fixture.assertFdsBack(0, 10);
            Assert.fail("more descriptors than the baseline must fail");
        } catch (AssertionError expected) {
        }
        S0Fixture.assumeCounted(true);
    }
}
