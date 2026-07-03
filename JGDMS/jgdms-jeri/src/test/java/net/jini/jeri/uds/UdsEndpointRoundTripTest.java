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
package net.jini.jeri.uds;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.jini.export.Exporter;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * End-to-end tests for the plaintext Unix domain socket (UDS) JERI transport
 * ({@link UdsServerEndpoint} / {@link UdsEndpoint}), option (iii): no TLS.
 *
 * <p>The central test ({@link #testAtomicRoundTripOverUds}) mirrors
 * {@code org.apache.river.phoenix.common.AccessILFactoryAtomicRoundTripTest} but
 * swaps {@code TcpServerEndpoint.getInstance(0)} for
 * {@code UdsServerEndpoint.getInstance(path)}: it exports a remote object over
 * the UDS transport with an {@link AtomicILFactory}, obtains the proxy, and
 * makes in-process loopback calls (scalar + object-graph) over the Unix socket.
 */
public class UdsEndpointRoundTripTest {

    /** Remote contract exercised by the tests. */
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

    private Path dir;
    private Path socketPath;

    @Before
    public void setUp() throws IOException {
        // A short private temp dir keeps the sun_path within the ~108-char
        // AF_UNIX limit (SOW section 6) and gives the socket file a
        // non-world-writable home.
        dir = Files.createTempDirectory("udsjeri");
        socketPath = dir.resolve("s.sock");
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(socketPath);
        Files.deleteIfExists(dir);
    }

    private Exporter newExporter() {
        return new BasicJeriExporter(
                UdsServerEndpoint.getInstance(socketPath.toString()),
                new AtomicILFactory(null, null,
                        UdsEndpointRoundTripTest.class.getClassLoader()),
                false, true);
    }

    // ---------------------------------------------------------------- round-trip

    @Test
    public void testAtomicRoundTripOverUds() throws Exception {
        EchoImpl impl = new EchoImpl();
        Exporter exporter = newExporter();
        Echo proxy = (Echo) exporter.export(impl);
        try {
            // The socket file must exist while the endpoint is listening.
            Assert.assertTrue("socket file should exist after export",
                    Files.exists(socketPath));

            // scalar round-trip
            Assert.assertEquals("echo:hi", proxy.echo("hi"));

            // object-graph round-trip (atomic marshalling of a Map both ways)
            Map in = new HashMap();
            in.put("k", "v");
            Map out = proxy.roundTrip(in);
            Assert.assertEquals("v", out.get("k"));
            Assert.assertEquals("seen", out.get("server"));
        } finally {
            exporter.unexport(true);
        }
    }

    // ------------------------------------------------------------ socket cleanup

    @Test
    public void testSocketFileUnlinkedOnUnexport() throws Exception {
        Exporter exporter = newExporter();
        exporter.export(new EchoImpl());
        Assert.assertTrue("socket file should exist while listening",
                Files.exists(socketPath));
        exporter.unexport(true);
        // The accept loop unlinks the socket file on shutdown; give it a moment.
        long deadline = System.currentTimeMillis() + 5000;
        while (Files.exists(socketPath) && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        Assert.assertFalse("socket file should be unlinked after unexport",
                Files.exists(socketPath));
    }

    // ------------------------------------------------------------ connect failure

    @Test
    public void testConnectToMissingSocketFails() throws Exception {
        // A client endpoint for a path with no listener must fail the call,
        // not hang or silently succeed.
        Path missing = dir.resolve("nonexistent.sock");
        UdsEndpoint ep = UdsEndpoint.getInstance(missing.toString());
        net.jini.jeri.OutboundRequestIterator ori =
                ep.newRequest(
                        net.jini.core.constraint.InvocationConstraints.EMPTY);
        try {
            while (ori.hasNext()) {
                ori.next(); // should throw IOException (no such socket file)
            }
            Assert.fail("expected connect to a missing socket to fail");
        } catch (IOException expected) {
            // good: no listener at that path
        }
    }

    // ------------------------------------------------------------ concurrency

    @Test
    public void testConcurrentRequests() throws Exception {
        EchoImpl impl = new EchoImpl();
        Exporter exporter = newExporter();
        final Echo proxy = (Echo) exporter.export(impl);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            java.util.List<Future<String>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 64; i++) {
                final int n = i;
                futures.add(pool.submit(new Callable<String>() {
                    public String call() throws Exception {
                        return proxy.echo("m" + n);
                    }
                }));
            }
            for (int i = 0; i < futures.size(); i++) {
                Assert.assertEquals("echo:m" + i, futures.get(i).get());
            }
        } finally {
            pool.shutdownNow();
            exporter.unexport(true);
        }
    }

    // ------------------------------------------------------------ endpoint plumbing

    @Test
    public void testEndpointEqualityAndSerializedPath() {
        UdsEndpoint a = UdsEndpoint.getInstance("/tmp/x.sock");
        UdsEndpoint b = UdsEndpoint.getInstance("/tmp/x.sock");
        UdsEndpoint c = UdsEndpoint.getInstance("/tmp/y.sock");
        Assert.assertEquals("same path -> equal (and interned)", a, b);
        Assert.assertSame("interned canonical instance", a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());
        Assert.assertNotEquals(a, c);
        Assert.assertEquals("/tmp/x.sock", a.getPath());
        Assert.assertTrue(a.checkTrustEquivalence(b));
        Assert.assertFalse(a.checkTrustEquivalence(c));
    }

    // -------------------------------------------------- UDS-not-TCP + owner-only

    /**
     * Integrity + confidentiality "by locality" proof.  Exports a remote object
     * over the transport (which binds a real {@code ServerSocketChannel} on the
     * socket path), then opens a live client {@link SocketChannel} to that same
     * bound socket and asserts that <em>both</em> ends of the established
     * connection carry {@link UnixDomainSocketAddress} socket addresses and that
     * <em>no</em> address anywhere is an {@link InetSocketAddress} -- i.e. there
     * is no TCP fallback and therefore no network wire to tap or tamper.
     *
     * <p>The live addresses are obtained directly from the connected channel via
     * {@code SocketChannel.getLocalAddress()} / {@code getRemoteAddress()} on the
     * client end (the client's remote address is the transport's bound server
     * socket, i.e. the server endpoint of the connection; the client's local
     * address is the client endpoint).  For UDS both are
     * {@code UnixDomainSocketAddress} (the local end is the unnamed/empty-path
     * variant); a TCP socket would instead report {@code InetSocketAddress}.
     */
    @Test
    public void testConnectionIsUnixDomainNotTcp() throws Exception {
        EchoImpl impl = new EchoImpl();
        Exporter exporter = newExporter();
        Echo proxy = (Echo) exporter.export(impl);
        SocketChannel client = null;
        try {
            // Sanity: the transport really works end-to-end over this socket.
            Assert.assertEquals("echo:hi", proxy.echo("hi"));
            Assert.assertTrue("socket file should exist while listening",
                    Files.exists(socketPath));

            // Open a live client connection to the transport's bound server
            // socket and read BOTH endpoint addresses off the established channel.
            client = SocketChannel.open(StandardProtocolFamily.UNIX);
            client.connect(UnixDomainSocketAddress.of(socketPath.toString()));
            Assert.assertTrue("client channel should be connected",
                    client.isConnected());

            SocketAddress remote = client.getRemoteAddress(); // the server endpoint
            SocketAddress local = client.getLocalAddress();   // the client endpoint

            // Neither end is a TCP/IP address (no InetSocketAddress fallback).
            Assert.assertFalse("server endpoint must not be a TCP address",
                    remote instanceof InetSocketAddress);
            Assert.assertFalse("client endpoint must not be a TCP address",
                    local instanceof InetSocketAddress);

            // Both ends of the live connection are Unix domain socket addresses.
            Assert.assertTrue(
                    "server endpoint must be a UnixDomainSocketAddress, was "
                            + (remote == null ? "null" : remote.getClass().getName()),
                    remote instanceof UnixDomainSocketAddress);
            Assert.assertTrue(
                    "client endpoint must be a UnixDomainSocketAddress, was "
                            + (local == null ? "null" : local.getClass().getName()),
                    local instanceof UnixDomainSocketAddress);

            // The remote (server) address is exactly the bound socket path.
            Assert.assertEquals("server endpoint path",
                    socketPath.toString(),
                    ((UnixDomainSocketAddress) remote).getPath().toString());
        } finally {
            if (client != null) {
                try { client.close(); } catch (IOException ignore) { }
            }
            exporter.unexport(true);
        }
    }

    /**
     * Confidentiality "by locality" proof, filesystem half: the bound socket
     * file created by {@link UdsServerEndpoint} is restricted to owner-only
     * ({@code rwx------}) so no other local user can open (connect to) it.
     */
    @Test
    public void testBoundSocketFileIsOwnerOnly() throws Exception {
        Exporter exporter = newExporter();
        exporter.export(new EchoImpl());
        try {
            Assert.assertTrue("socket file should exist while listening",
                    Files.exists(socketPath));

            Set<PosixFilePermission> perms =
                    Files.getPosixFilePermissions(socketPath);

            Set<PosixFilePermission> ownerOnly =
                    EnumSet.of(PosixFilePermission.OWNER_READ,
                               PosixFilePermission.OWNER_WRITE,
                               PosixFilePermission.OWNER_EXECUTE);
            Assert.assertEquals(
                    "bound socket file must be owner-only (rwx------), was "
                            + PosixFilePermissions.toString(perms),
                    ownerOnly, perms);

            // Explicitly: no group or other bits at all.
            Assert.assertFalse(perms.contains(PosixFilePermission.GROUP_READ));
            Assert.assertFalse(perms.contains(PosixFilePermission.GROUP_WRITE));
            Assert.assertFalse(perms.contains(PosixFilePermission.GROUP_EXECUTE));
            Assert.assertFalse(perms.contains(PosixFilePermission.OTHERS_READ));
            Assert.assertFalse(perms.contains(PosixFilePermission.OTHERS_WRITE));
            Assert.assertFalse(perms.contains(PosixFilePermission.OTHERS_EXECUTE));
        } finally {
            exporter.unexport(true);
        }
    }
}
