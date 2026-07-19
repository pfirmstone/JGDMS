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
package au.net.zeus.jgdms.policy.proxy;

import java.rmi.Remote;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.export.CodebaseAccessor;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import org.apache.river.admin.DestroyAdmin;
import au.net.zeus.jgdms.api.policy.RemotePolicyService;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Pins the backend-interface convention for the policy service: a JERI stub for
 * {@link RemotePolicyServiceBackend} transitively implements exactly the contract
 * {@code AbstractSmartProxy.checkServer} requires, so the exported stub passes
 * deserialization and admin operations are invocable over the wire.  The
 * AtomicSerial marshal/checkServer mechanics themselves are proven generically
 * by the hello-world example's round-trip test.
 */
public class RemotePolicyServiceBackendContractTest {

    @Test
    public void testBackendAggregatesCheckServerContract() {
        Class<?> b = RemotePolicyServiceBackend.class;
        for (Class<?> required : new Class<?>[]{
                Remote.class, RemotePolicyService.class,
                ServiceProxyAccessor.class, ServiceAttributesAccessor.class,
                ServiceIDAccessor.class, CodebaseAccessor.class,
                Administrable.class, JoinAdmin.class, DestroyAdmin.class}) {
            assertTrue(required.getName()
                    + " must be aggregated by RemotePolicyServiceBackend",
                    required.isAssignableFrom(b));
        }
    }
}
