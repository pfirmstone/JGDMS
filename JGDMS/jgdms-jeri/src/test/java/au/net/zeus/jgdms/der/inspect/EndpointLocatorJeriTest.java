/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package au.net.zeus.jgdms.der.inspect;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.marshal.DerMarshalledInstance;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.AtomicDerInvocationHandler;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.BasicObjectEndpoint;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.ssl.SslEndpoint;
import net.jini.jeri.tcp.TcpEndpoint;
import org.apache.river.api.io.AtomicSerial;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link EndpointLocator} with its default configuration against the real JERI classes: a
 * dynamic proxy over {@code BasicInvocationHandler} / {@code AtomicDerInvocationHandler} with a
 * {@code BasicObjectEndpoint} over an {@code SslEndpoint} or a {@code TcpEndpoint}, marshalled
 * with {@code DerMarshalledInstance}.
 *
 * <p><b>Invariant defended.</b> The default configuration names the JERI endpoint classes and
 * their field names correctly, so every endpoint of a real proxy is found with its host and
 * port, and an unrecognised endpoint in {@code BasicObjectEndpoint.ep} is refused.
 */
public class EndpointLocatorJeriTest {

    private static final ClassLoader LOADER = EndpointLocatorJeriTest.class.getClassLoader();

    public interface Svc extends Remote {
        String hello() throws RemoteException;
    }

    /** An endpoint class the default configuration does not recognise. */
    @AtomicSerial
    public static final class UnknownEndpoint implements Endpoint {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {new AtomicSerial.SerialForm("where", String.class)};
        }

        public static void serialize(AtomicSerial.PutArg arg, UnknownEndpoint o) throws IOException {
            arg.put("where", o.where);
            arg.writeArgs();
        }

        private final String where;

        public UnknownEndpoint(String where) {
            this.where = where;
        }

        @Override
        public OutboundRequestIterator newRequest(InvocationConstraints constraints) {
            throw new UnsupportedOperationException();
        }
    }

    private static Object proxy(InvocationHandler h) {
        return Proxy.newProxyInstance(LOADER, new Class<?>[] {Svc.class}, h);
    }

    private static BasicObjectEndpoint oe(Endpoint ep) {
        return new BasicObjectEndpoint(ep, UuidFactory.generate(), false);
    }

    /** The MarshalledInstance's own serialized fields, read class-free. */
    private static Map<String, Object> fields(MarshalledInstance mi) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(mi.getClass());
        byte[] payload = ObjectCodec.encodeHierarchy(mi, chain);
        return ObjectCodec.decodeToFieldMap(chain, payload).get(MarshalledInstance.class.getName());
    }

    private static List<EndpointFinding> locateFields(MarshalledInstance mi) throws Exception {
        Map<String, Object> f = fields(mi);
        return new EndpointLocator().locateMarshalledPayload((byte[]) f.get("payloadBytes"),
                (byte[]) f.get("schemaBytes"), (String) f.get("payloadFormat"));
    }

    /** The MarshalledInstance itself as a top-level record (as when it is a field of a carrier). */
    private static List<EndpointFinding> locateAsRecord(MarshalledInstance mi) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(mi.getClass());
        return new EndpointLocator().locate(
                MarshalledInstanceRecord.fromChain(chain, ObjectCodec.encodeHierarchy(mi, chain)));
    }

    private static void assertOne(List<EndpointFinding> fs, String path, String className,
                                  String host, int port) {
        assertEquals(1, fs.size());
        EndpointFinding f = fs.get(0);
        assertEquals(path, f.path());
        assertEquals(className, f.className());
        assertEquals(host, f.host());
        assertEquals(Integer.valueOf(port), f.port());
        assertNull(f.socketFactory());
    }

    @Test
    public void sslEndpointBehindBasicInvocationHandler() throws Exception {
        for (boolean substitute : new boolean[] {true, false}) {
            Object p = proxy(new BasicInvocationHandler(
                    oe(SslEndpoint.getInstance("ssl.example.org", 4433)), null));
            MarshalledInstance mi = new DerMarshalledInstance(p, Collections.emptyList(), substitute);
            Map<String, Object> f = fields(mi);
            assertEquals(MarshalledInstanceRecord.PAYLOAD_FORMAT, f.get("payloadFormat"));
            // With no ProxyCodebaseSpi provider registered a plain proxy is not substituted
            // either way: it travels as a bare [8] object-stream item with empty schemaBytes.
            assertEquals("substitute=" + substitute, 0, ((byte[]) f.get("schemaBytes")).length);

            assertOne(locateFields(mi), "$.<handler>.oe.ep", "net.jini.jeri.ssl.SslEndpoint",
                    "ssl.example.org", 4433);
            assertOne(locateAsRecord(mi), "$.<payload>.<handler>.oe.ep",
                    "net.jini.jeri.ssl.SslEndpoint", "ssl.example.org", 4433);
        }
    }

    @Test
    public void tcpEndpointBehindAtomicDerInvocationHandler() throws Exception {
        for (boolean substitute : new boolean[] {true, false}) {
            Object p = proxy(new AtomicDerInvocationHandler(
                    oe(TcpEndpoint.getInstance("tcp.example.org", 4160)), null));
            MarshalledInstance mi = new DerMarshalledInstance(p, Collections.emptyList(), substitute);

            assertOne(locateFields(mi), "$.<handler>.oe.ep", "net.jini.jeri.tcp.TcpEndpoint",
                    "tcp.example.org", 4160);
        }
    }

    @Test
    public void endpointMarshalledDirectly() throws Exception {
        MarshalledInstance mi = new DerMarshalledInstance(SslEndpoint.getInstance("direct.example.org", 1));
        assertTrue("an endpoint has a separable schema", ((byte[]) fields(mi).get("schemaBytes")).length > 0);

        List<EndpointFinding> fs = locateFields(mi);

        assertOne(fs, "$", "net.jini.jeri.ssl.SslEndpoint", "direct.example.org", 1);
        assertEquals(EndpointFinding.Form.PAYLOAD, fs.get(0).location().form());
    }

    @Test
    public void unrecognisedEndpointInObjectEndpointIsRefused() throws Exception {
        Object p = proxy(new BasicInvocationHandler(oe(new UnknownEndpoint("elsewhere")), null));
        MarshalledInstance mi = new DerMarshalledInstance(p, Collections.emptyList(), false);
        try {
            locateFields(mi);
            fail("an unrecognised endpoint in BasicObjectEndpoint.ep was not refused");
        } catch (DerException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("$.<handler>.oe.ep"));
        }
    }
}
