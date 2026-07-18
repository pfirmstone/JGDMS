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
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
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
import org.junit.Assume;
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
     * F1 access-gate assertion, per-platform.  The bound socket file created by
     * {@link UdsServerEndpoint} must actually carry the owner-only gate on THIS
     * platform: on POSIX assert mode {@code rwx------}; on Windows (non-POSIX
     * NTFS) assert the owner-only ACL.  This test must FAIL LOUDLY, not
     * error/skip, if the gate is absent -- so each branch ends in an explicit
     * assertion and there is no silent no-op path (the old version errored on
     * Windows because it unconditionally called {@code getPosixFilePermissions}).
     */
    @Test
    public void testBoundSocketFileIsOwnerOnly() throws Exception {
        Exporter exporter = newExporter();
        exporter.export(new EchoImpl());
        try {
            Assert.assertTrue("socket file should exist while listening",
                    Files.exists(socketPath));

            if (isPosix(socketPath)) {
                assertPosixOwnerOnly(socketPath);
            } else {
                // Non-POSIX (Windows/NTFS): the fail-closed listen must have
                // applied an owner-only ACL, else it would have thrown.  Assert
                // the ACL is actually owner-only (no ALLOW entry for a
                // non-owner principal).
                assertAclOwnerOnly(socketPath);
            }
        } finally {
            exporter.unexport(true);
        }
    }

    // ------------------------------------------------------------ F3: live incumbent

    /**
     * F3 (live incumbent): a transport listen on a path a LIVE server already
     * owns must FAIL, not hijack it.
     *
     * <p>The incumbent is a <em>raw</em>, live {@link ServerSocketChannel} bound
     * on the path (modelling a foreign / out-of-process server), rather than a
     * second JERI exporter: two JERI exporters on an equal {@code
     * UdsServerEndpoint} share a single pooled listen within one JVM by design,
     * so a second export would legitimately reuse the first listen and never
     * reach {@code bind} -- which is not the hijack scenario F3 addresses.  With
     * a raw incumbent the pool cannot intervene, so the transport must go
     * through {@code listen -> bind -> BindException -> live-probe} and fail.
     * The raw incumbent must remain bound and connectable afterwards (it was not
     * unlinked and stolen).
     */
    @Test
    public void testListenOverLiveIncumbentFailsNotHijacks() throws Exception {
        ServerSocketChannel incumbent =
                ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        incumbent.bind(UnixDomainSocketAddress.of(socketPath.toString()));
        Assert.assertTrue("precondition: incumbent is bound and open",
                incumbent.isOpen() && Files.exists(socketPath));
        Exporter exporter = newExporter();
        boolean exportFailed = false;
        try {
            exporter.export(new EchoImpl());
        } catch (Exception expected) {
            exportFailed = true;
        }
        try {
            if (!exportFailed) {
                try { exporter.unexport(true); } catch (Exception ignore) { }
            }
            Assert.assertTrue("a transport listen over a LIVE incumbent socket "
                    + "must fail (not delete-and-rebind / hijack it)",
                    exportFailed);

            // The live incumbent must survive: still open, and a client can
            // still connect to it (it was not unlinked out from under it).
            Assert.assertTrue("incumbent must still be open after the refused "
                    + "takeover", incumbent.isOpen());
            SocketChannel probe =
                    SocketChannel.open(StandardProtocolFamily.UNIX);
            try {
                probe.connect(UnixDomainSocketAddress.of(socketPath.toString()));
                Assert.assertTrue("a client must still be able to connect to the "
                        + "surviving incumbent", probe.isConnected());
            } finally {
                try { probe.close(); } catch (IOException ignore) { }
            }
        } finally {
            try { incumbent.close(); } catch (IOException ignore) { }
        }
    }

    /**
     * F3 (stale reclaim): a leftover socket file with NO live listener (a stale
     * inode) must be reclaimed -- listen succeeds by removing the owner-matched
     * stale file and rebinding.  We simulate a stale leftover by binding a raw
     * {@link ServerSocketChannel}, then closing it WITHOUT unlinking (leaving
     * the file), then exporting over the transport on the same path.
     */
    @Test
    public void testStaleSocketFileIsReclaimed() throws Exception {
        // Leave a stale socket file: bind then close a raw channel without
        // unlinking the file.
        ServerSocketChannel stale =
                ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        stale.bind(UnixDomainSocketAddress.of(socketPath.toString()));
        stale.close(); // JDK may or may not unlink; ensure the file is present
        if (!Files.exists(socketPath)) {
            // Recreate a plain (non-socket) leftover to still exercise reclaim
            // of an owner-owned leftover file.
            Files.createFile(socketPath);
        }
        Assert.assertTrue("precondition: a stale leftover file exists",
                Files.exists(socketPath));

        Exporter exporter = newExporter();
        Echo proxy = (Echo) exporter.export(new EchoImpl());
        try {
            Assert.assertEquals("listen must reclaim an owner-owned stale file",
                    "echo:hi", proxy.echo("hi"));
        } finally {
            exporter.unexport(true);
        }
    }

    // ------------------------------------------------------------ F6: path validation

    @Test
    public void testOversizePathRejectedAtGetInstance() {
        String tooLong = "/tmp/" + repeat('x', 200) + ".sock"; // > 103 bytes
        assertServerPathRejected(tooLong, "oversize path");
        assertClientPathRejected(tooLong, "oversize path");
    }

    @Test
    public void testEmbeddedNulPathRejectedAtGetInstance() {
        String withNul = "/tmp/a\0b.sock";
        assertServerPathRejected(withNul, "embedded-NUL path");
        assertClientPathRejected(withNul, "embedded-NUL path");
    }

    @Test
    public void testAbstractNamespacePathRejectedAtGetInstance() {
        String abstractNs = "@my-abstract-socket";
        assertServerPathRejected(abstractNs, "abstract-namespace ('@') path");
        assertClientPathRejected(abstractNs, "abstract-namespace ('@') path");
    }

    /**
     * F6 on the deserialization path: a serialized {@link UdsEndpoint} carrying
     * an abstract-namespace path must be rejected when read back (the {@code
     * GetArg}/{@code readObject} path runs the same {@code check}).  We cannot
     * construct such an endpoint via {@code getInstance} (it is rejected there),
     * so we hand-roll a serialized form with a poisoned path field and assert
     * deserialization throws.
     */
    @Test
    public void testPoisonedPathRejectedOnDeserialization() throws Exception {
        byte[] poisoned = serializedUdsEndpointWithPath("@abstract");
        try {
            new java.io.ObjectInputStream(
                    new java.io.ByteArrayInputStream(poisoned)).readObject();
            Assert.fail("deserializing a UdsEndpoint with an abstract-namespace "
                    + "path must be rejected");
        } catch (java.io.InvalidObjectException | IllegalArgumentException expected) {
            // good: checkSerial rejected it
        }
    }

    // ------------------------------------------------------------ F5: parent dir

    /**
     * F5: binding a socket in a world-writable, NON-sticky directory must be
     * rejected (POSIX), because the owner-only socket file could be renamed or
     * symlink-swapped by another local user.  POSIX-only: on Windows the
     * file-level ACL (F1) is the control, so this check is skipped there with an
     * explicit assumption (never a silent pass of the security check).
     */
    @Test
    public void testWorldWritableNonStickyParentRejected() throws Exception {
        Assume.assumeTrue("world-writable-parent policy is POSIX-specific; on "
                + "non-POSIX the file ACL is the control (F1)",
                isPosix(dir));

        Path openDir = Files.createTempDirectory("udsjeri-open");
        // Make it world-writable WITHOUT the sticky bit: rwxrwxrwx.
        Files.setPosixFilePermissions(openDir,
                PosixFilePermissions.fromString("rwxrwxrwx"));
        Path openSocket = openDir.resolve("s.sock");
        Exporter exporter = new BasicJeriExporter(
                UdsServerEndpoint.getInstance(openSocket.toString()),
                new AtomicILFactory(null, null, getClass().getClassLoader()),
                false, true);
        boolean failed = false;
        try {
            exporter.export(new EchoImpl());
        } catch (Exception expected) {
            failed = true;
        } finally {
            try { exporter.unexport(true); } catch (Exception ignore) { }
            Files.deleteIfExists(openSocket);
            Files.deleteIfExists(openDir);
        }
        Assert.assertTrue("binding under a world-writable, non-sticky directory "
                + "must be rejected (F5)", failed);
    }

    /**
     * F5 (group-writable variant): binding a socket in a GROUP-writable,
     * NON-sticky directory ({@code rwxrwx---}) must also be rejected.  The
     * socket file is forced owner-only, so a group member could never use it
     * anyway; but a same-group attacker in such a directory can win the race
     * between the owner-only chmod's NOFOLLOW pre-check and the path-based
     * chmod, redirecting that chmod onto a server-owned file.  The parent-dir
     * check must reject group-write-without-sticky exactly as it rejects
     * world-write-without-sticky, closing that window up front.
     */
    @Test
    public void testGroupWritableNonStickyParentRejected() throws Exception {
        Assume.assumeTrue("group-writable-parent policy is POSIX-specific; on "
                + "non-POSIX the file ACL is the control (F1)",
                isPosix(dir));

        Path openDir = Files.createTempDirectory("udsjeri-grp");
        // Make it group-writable WITHOUT the sticky bit: rwxrwx--- (0770).
        // Not world-writable, so this exercises the GROUP_WRITE branch only.
        Files.setPosixFilePermissions(openDir,
                PosixFilePermissions.fromString("rwxrwx---"));
        Path openSocket = openDir.resolve("s.sock");
        Exporter exporter = new BasicJeriExporter(
                UdsServerEndpoint.getInstance(openSocket.toString()),
                new AtomicILFactory(null, null, getClass().getClassLoader()),
                false, true);
        boolean failed = false;
        try {
            exporter.export(new EchoImpl());
        } catch (Exception expected) {
            failed = true;
        } finally {
            try { exporter.unexport(true); } catch (Exception ignore) { }
            Files.deleteIfExists(openSocket);
            Files.deleteIfExists(openDir);
        }
        Assert.assertTrue("binding under a group-writable, non-sticky directory "
                + "must be rejected (F5)", failed);
    }

    // ------------------------------------------------------ HIGH-1: no leaked socket

    /**
     * HIGH-1 regression: when {@code listen} fails, NO socket file may remain at
     * the target path.  The specific hazard the fix closes is a failure AFTER a
     * successful bind (e.g. a throwing {@code restrictPermissions}), because
     * closing a UDS {@code ServerSocketChannel} does NOT unlink the pathname
     * socket -- so without the cleanup a failed chmod/ACL would leave exactly the
     * unprotected control socket F1 exists to prevent.
     *
     * <p>Forcing {@code restrictPermissions} to throw AFTER a successful bind is
     * not portably achievable from a test (the owner can always chmod/rewrite the
     * ACL of a file it just created), so this test forces a listen failure via a
     * parent directory the current user cannot create children in, and asserts
     * the observable invariant that survives every failure mode: <b>no socket
     * file is left at the path.</b>  Whether the failure lands at bind (no file
     * ever created) or, on a platform/filesystem where bind can still create the
     * inode, at the permission step (file created then removed by the HIGH-1
     * cleanup), the post-condition is identical and is what this asserts.
     */
    @Test
    public void testFailedListenLeavesNoSocketFile() throws Exception {
        Path lockedDir = Files.createTempDirectory("udsjeri-locked");
        Path lockedSocket = lockedDir.resolve("s.sock");
        boolean restricted = denyChildCreation(lockedDir);
        Assume.assumeTrue("could not make a directory that denies child creation "
                + "on this platform; cannot force a listen failure deterministically",
                restricted);

        Exporter exporter = new BasicJeriExporter(
                UdsServerEndpoint.getInstance(lockedSocket.toString()),
                new AtomicILFactory(null, null, getClass().getClassLoader()),
                false, true);
        boolean failed = false;
        try {
            exporter.export(new EchoImpl());
        } catch (Exception expected) {
            failed = true;
        } finally {
            try { exporter.unexport(true); } catch (Exception ignore) { }
        }
        try {
            Assert.assertTrue("listen into a directory that denies child creation "
                    + "must fail", failed);
            Assert.assertFalse("a FAILED listen must NOT leave a socket file at the "
                    + "path (HIGH-1: no leaked unprotected control socket)",
                    Files.exists(lockedSocket,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS));
        } finally {
            restoreChildCreation(lockedDir);
            Files.deleteIfExists(lockedSocket);
            Files.deleteIfExists(lockedDir);
        }
    }

    /**
     * HIGH-1 regression, the ACTUAL {@code boundHere}-cleanup branch.  Unlike
     * {@link #testFailedListenLeavesNoSocketFile} (whose failure lands at {@code
     * bind}, so the socket is never created), this test installs the package-only
     * test seam {@code UdsServerEndpoint.restrictPermissionsFaultInjector}, which
     * fires ONLY after the real owner-only gate has been applied to a
     * successfully-bound socket.  So the socket file DOES exist on disk when the
     * failure is raised, exercising exactly the {@code if (!ok && boundHere)
     * Files.deleteIfExists(socketPath)} cleanup in {@code LE.listen}.  Asserts
     * (a) the listen/export propagates the failure AND (b) no socket file remains.
     */
    @Test
    public void testRestrictPermissionsFailureAfterBindLeavesNoSocketFile()
            throws Exception {
        final boolean[] fired = { false };
        // The injector runs only after the real gate applied to a bound socket;
        // assert the file is actually present at that moment, then throw.
        UdsServerEndpoint.restrictPermissionsFaultInjector = (Path p) -> {
            fired[0] = true;
            Assert.assertTrue("seam must fire only after the socket was bound and "
                    + "the real owner-only gate applied (file must exist here)",
                    Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS));
            throw new RuntimeException("test-only forced restrictPermissions failure");
        };
        Exporter exporter = newExporter();
        boolean failed = false;
        try {
            exporter.export(new EchoImpl());
        } catch (Exception expected) {
            failed = true;
        } finally {
            try { exporter.unexport(true); } catch (Exception ignore) { }
            // Reset the seam so it cannot leak into any other test.
            UdsServerEndpoint.restrictPermissionsFaultInjector = null;
        }
        Assert.assertTrue("the injected post-gate fault must have actually fired "
                + "(bind succeeded and the real gate ran)", fired[0]);
        Assert.assertTrue("listen must propagate the post-gate failure", failed);
        Assert.assertFalse("a listen that failed AFTER a successful bind must remove "
                + "the socket file it created (HIGH-1 boundHere-cleanup branch); a "
                + "leftover here is the unprotected control socket F1 prevents",
                Files.exists(socketPath, java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }

    // --------------------------------------------------------------------- helpers

    /**
     * Attempts to make {@code dir} deny child-file creation to the current user,
     * so a bind/listen into it fails.  POSIX: strip all write bits ({@code
     * r-xr-xr-x}).  Windows/NTFS: add a leading DENY ADD_FILE ACE for the owner.
     * Returns {@code true} if a restriction was applied.
     */
    private static boolean denyChildCreation(Path dir) throws IOException {
        if (isPosix(dir)) {
            Files.setPosixFilePermissions(dir,
                    PosixFilePermissions.fromString("r-xr-xr-x"));
            return true;
        }
        AclFileAttributeView v =
                Files.getFileAttributeView(dir, AclFileAttributeView.class);
        if (v == null) {
            return false;
        }
        UserPrincipal owner = Files.getOwner(dir);
        AclEntry deny = AclEntry.newBuilder()
                .setType(AclEntryType.DENY)
                .setPrincipal(owner)
                .setPermissions(EnumSet.of(
                        java.nio.file.attribute.AclEntryPermission.ADD_FILE,
                        java.nio.file.attribute.AclEntryPermission.ADD_SUBDIRECTORY))
                .build();
        List<AclEntry> acl = new java.util.ArrayList<>();
        acl.add(deny);                 // DENY first so it takes precedence
        acl.addAll(v.getAcl());
        v.setAcl(acl);
        return true;
    }

    /** Restores child-creation permission so the test dir can be cleaned up. */
    private static void restoreChildCreation(Path dir) {
        try {
            if (isPosix(dir)) {
                Files.setPosixFilePermissions(dir,
                        PosixFilePermissions.fromString("rwx------"));
                return;
            }
            AclFileAttributeView v =
                    Files.getFileAttributeView(dir, AclFileAttributeView.class);
            if (v != null) {
                UserPrincipal owner = Files.getOwner(dir);
                AclEntry allow = AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(owner)
                        .setPermissions(EnumSet.allOf(
                                java.nio.file.attribute.AclEntryPermission.class))
                        .build();
                v.setAcl(List.of(allow));
            }
        } catch (IOException ignore) {
            // best effort; the temp dir cleanup below may still fail harmlessly
        }
    }

    private static boolean isPosix(Path p) {
        return p.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private static void assertPosixOwnerOnly(Path socketPath) throws IOException {
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(socketPath);
        Set<PosixFilePermission> ownerOnly =
                EnumSet.of(PosixFilePermission.OWNER_READ,
                           PosixFilePermission.OWNER_WRITE,
                           PosixFilePermission.OWNER_EXECUTE);
        Assert.assertEquals(
                "bound socket file must be owner-only (rwx------), was "
                        + PosixFilePermissions.toString(perms),
                ownerOnly, perms);
        Assert.assertFalse(perms.contains(PosixFilePermission.GROUP_READ));
        Assert.assertFalse(perms.contains(PosixFilePermission.GROUP_WRITE));
        Assert.assertFalse(perms.contains(PosixFilePermission.GROUP_EXECUTE));
        Assert.assertFalse(perms.contains(PosixFilePermission.OTHERS_READ));
        Assert.assertFalse(perms.contains(PosixFilePermission.OTHERS_WRITE));
        Assert.assertFalse(perms.contains(PosixFilePermission.OTHERS_EXECUTE));
    }

    private static void assertAclOwnerOnly(Path socketPath) throws IOException {
        AclFileAttributeView aclView =
                Files.getFileAttributeView(socketPath, AclFileAttributeView.class);
        Assert.assertNotNull("non-POSIX platform must expose an ACL view for the "
                + "owner-only gate; if it does not, the fail-closed listen should "
                + "have thrown and this test would not run", aclView);
        UserPrincipal owner = Files.getOwner(socketPath);
        List<AclEntry> acl = aclView.getAcl();
        Assert.assertFalse("ACL must not be empty (owner-only gate must be applied)",
                acl.isEmpty());
        for (AclEntry e : acl) {
            if (e.type() == AclEntryType.ALLOW) {
                Assert.assertEquals(
                        "every ALLOW entry in the owner-only DACL must name the "
                                + "owner; found ALLOW for " + e.principal(),
                        owner, e.principal());
            }
        }
    }

    private static void assertServerPathRejected(String path, String what) {
        try {
            UdsServerEndpoint.getInstance(path);
            Assert.fail("UdsServerEndpoint.getInstance must reject " + what);
        } catch (IllegalArgumentException | NullPointerException expected) {
            // good
        }
    }

    private static void assertClientPathRejected(String path, String what) {
        try {
            UdsEndpoint.getInstance(path);
            Assert.fail("UdsEndpoint.getInstance must reject " + what);
        } catch (IllegalArgumentException | NullPointerException expected) {
            // good
        }
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Builds the serialized bytes of a {@link UdsEndpoint} whose {@code path}
     * field is {@code poisonedPath}, bypassing the {@code getInstance} guard, so
     * the deserialization-path {@code checkSerial} can be exercised.  We take a
     * valid serialized endpoint and patch the path string in the byte stream.
     */
    private static byte[] serializedUdsEndpointWithPath(String poisonedPath)
            throws IOException {
        // A valid short path, same byte length as the poisoned one so the
        // stream's length prefix stays correct after substitution.
        String placeholder = repeat('p', poisonedPath.length());
        UdsEndpoint ep = UdsEndpoint.getInstance("/tmp/" + placeholder + ".s");
        // Serialize, then replace the placeholder substring with the poison.
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos)) {
            oos.writeObject(ep);
        }
        byte[] bytes = baos.toByteArray();
        byte[] from = ("/tmp/" + placeholder + ".s")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] to = poisonedPath.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // Only substitute when lengths match, so we do not corrupt the modified
        // UTF length prefix; pad/truncate the poison to the placeholder length.
        int idx = indexOf(bytes, from);
        Assert.assertTrue("placeholder path not found in serialized stream",
                idx >= 0);
        for (int i = 0; i < from.length; i++) {
            bytes[idx + i] = (i < to.length) ? to[i] : (byte) '/';
        }
        return bytes;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
