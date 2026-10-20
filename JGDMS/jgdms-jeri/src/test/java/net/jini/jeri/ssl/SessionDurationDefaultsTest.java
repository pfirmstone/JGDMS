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

    private static final long HOUR = 60L * 60L * 1000L;
    private static final long MINUTE = 60L * 1000L;

    @Test
    public void serverSessionLimitIsAtMostOneHour() {
        long d = SslServerEndpointImpl.SslListenEndpoint.DEFAULT_MAX_SERVER_SESSION_DURATION;
        Assert.assertTrue("server default " + d, d > 0 && d <= HOUR);
    }

    @Test
    public void clientRenegotiatesBeforeServerExpires() {
        long server = SslServerEndpointImpl.SslListenEndpoint.DEFAULT_MAX_SERVER_SESSION_DURATION;
        long client = SslConnection.DEFAULT_MAX_CLIENT_SESSION_DURATION;
        Assert.assertTrue("client " + client + " server " + server,
                client > 0 && server - client >= MINUTE);
    }
}
