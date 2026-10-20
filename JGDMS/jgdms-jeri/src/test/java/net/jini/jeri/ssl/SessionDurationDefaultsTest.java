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

/**
 * The default TLS session limits bound how long a resumption chain can go on
 * without a full handshake, and so how long a revoked client certificate or
 * trust anchor can keep being accepted: the server does not call its trust
 * manager when it resumes.  They are measured from the last full handshake.
 */
public class SessionDurationDefaultsTest {

    private static final long MINUTE = 60L * 1000L;

    @Test
    public void serverSessionLimitIsAtMostThirtyTwoMinutes() {
        long d = SslServerEndpointImpl.SslListenEndpoint.DEFAULT_MAX_SERVER_SESSION_DURATION;
        Assert.assertTrue("server default " + d, d > 0 && d <= 32L * MINUTE);
    }

    @Test
    public void clientRenegotiatesBeforeServerExpires() {
        long server = SslServerEndpointImpl.SslListenEndpoint.DEFAULT_MAX_SERVER_SESSION_DURATION;
        long client = SslConnection.DEFAULT_MAX_CLIENT_SESSION_DURATION;
        Assert.assertTrue("client " + client + " server " + server,
                client > 0 && server - client >= MINUTE);
    }

    @Test
    public void clientMinimumIsBelowMaximum() {
        Assert.assertTrue(SslConnection.DEFAULT_MIN_CLIENT_SESSION_DURATION > 0
                && SslConnection.DEFAULT_MIN_CLIENT_SESSION_DURATION
                < SslConnection.DEFAULT_MAX_CLIENT_SESSION_DURATION);
    }

    @Test
    public void limitStaysInRangeAndSpreads() {
        long min = SslConnection.DEFAULT_MIN_CLIENT_SESSION_DURATION;
        long max = SslConnection.DEFAULT_MAX_CLIENT_SESSION_DURATION;
        long lo = Long.MAX_VALUE, hi = Long.MIN_VALUE;
        java.util.Set<Long> distinct = new java.util.HashSet<>();
        long base = 1_700_000_000_000L;
        for (int i = 0; i < 10_000; i++) {
            long l = SslConnection.sessionLimit(base + i * 7L, min, max);
            Assert.assertTrue("limit " + l, l >= min && l <= max);
            lo = Math.min(lo, l);
            hi = Math.max(hi, l);
            distinct.add(l);
        }
        // Fleet started within 70 s: the limits must cover most of the range.
        Assert.assertTrue("lowest " + lo, lo < min + MINUTE);
        Assert.assertTrue("highest " + hi, hi > max - MINUTE);
        Assert.assertTrue("distinct " + distinct.size(), distinct.size() > 5_000);
    }

    @Test
    public void limitIsTheSameForEveryConnectionOfASession() {
        long min = SslConnection.DEFAULT_MIN_CLIENT_SESSION_DURATION;
        long max = SslConnection.DEFAULT_MAX_CLIENT_SESSION_DURATION;
        long created = 1_700_000_123_456L;
        long first = SslConnection.sessionLimit(created, min, max);
        for (int i = 0; i < 100; i++) {
            Assert.assertEquals(first, SslConnection.sessionLimit(created, min, max));
        }
    }

    @Test
    public void noJitterWhenMinimumIsNotBelowMaximum() {
        Assert.assertEquals(900_000L, SslConnection.sessionLimit(123L, 900_000L, 900_000L));
        Assert.assertEquals(900_000L, SslConnection.sessionLimit(123L, 1_000_000L, 900_000L));
        Assert.assertEquals(900_000L, SslConnection.sessionLimit(123L, 0L, 900_000L));
    }
}
