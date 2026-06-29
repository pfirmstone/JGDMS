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
package org.apache.river.phoenix.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import junit.framework.TestCase;
import net.jini.export.Exporter;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

/**
 * End-to-end round-trip test for {@link AccessILFactory} after its migration
 * from {@code BasicILFactory}/{@code BasicInvocationDispatcher} (JOSS) to
 * {@code AtomicILFactory}/{@code AtomicInvocationDispatcher} (AtomicSerial).
 *
 * <p>It exports a remote object through {@code AccessILFactory} and makes an
 * in-process loopback call, exercising:
 * <ul>
 *   <li>the client-side {@code AtomicInvocationHandler} (the factory now extends
 *       {@code AtomicILFactory}),</li>
 *   <li>the server-side {@code AccessDispatcher} (now extends
 *       {@code AtomicInvocationDispatcher}), i.e. atomic marshalling of the
 *       arguments and return value, and</li>
 *   <li>the preserved local-access check (a loopback client is local).</li>
 * </ul>
 * A successful object-graph round-trip confirms the atomic marshalling path
 * works without the JOSS invocation layer.
 */
public class AccessILFactoryAtomicRoundTripTest extends TestCase {

    /** Remote contract exercised by the test. */
    public interface Echo extends Remote {
        String echo(String s) throws RemoteException;
        Map roundTrip(Map m) throws RemoteException;
    }

    public static final class EchoImpl implements Echo {
        public String echo(String s) { return "echo:" + s; }
        public Map roundTrip(Map m) {
            Map r = new HashMap(m);
            r.put("server", "seen");
            return r;
        }
    }

    public void testAtomicRoundTripThroughAccessILFactory() throws Exception {
        EchoImpl impl = new EchoImpl();
        Exporter exporter = new BasicJeriExporter(
                TcpServerEndpoint.getInstance(0),
                new AccessILFactory(AccessILFactoryAtomicRoundTripTest.class.getClassLoader()),
                false, true);
        Echo proxy = (Echo) exporter.export(impl);
        try {
            // scalar round-trip
            assertEquals("echo:hi", proxy.echo("hi"));
            // object-graph round-trip (atomic marshalling of a Map both ways)
            Map in = new HashMap();
            in.put("k", "v");
            Map out = proxy.roundTrip(in);
            assertEquals("v", out.get("k"));
            assertEquals("seen", out.get("server"));
        } finally {
            exporter.unexport(true);
        }
    }
}
