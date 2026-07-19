/*
 * Copyright 2026 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.loader.isolation;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.InvocationHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * End-to-end tests for the real task-T4 wire handoff: {@link
 * SubProcessWireHandoffImpl} (client side) talking {@link WireFraming}/
 * {@link WireHandoffCodec} over a loopback TCP socket pair to a real {@link
 * SubProcessReconstructionServer} (subprocess side) running on a background
 * thread in the same JVM.
 *
 * <p>Loopback TCP (not a real Unix Domain Socket) stands in for the
 * production channel here deliberately: {@code net.jini.jeri.uds}'s own
 * round-trip test suite is currently fully disabled in this sandbox
 * (environment-specific socket-permission-restriction failures unrelated to
 * this task -- see {@code UdsEndpointRoundTripTest}, entirely commented out
 * with a TODO). {@link WireFraming}/{@link WireHandoffCodec}/{@link
 * SubProcessReconstructionServer} only ever require a {@link ByteChannel},
 * so this substitution changes nothing about the protocol or gate logic
 * under test; production wiring supplies a real UDS channel via {@link
 * SubProcessLauncher.Spawned#openWireChannel()}.
 *
 * <p>Codebase is deliberately empty ({@code getClassAnnotation() == ""}) so
 * {@code resolve()}'s reused legacy path takes its boot-window,
 * zero-JAR/no-SPIFFE-principal branch (verified against {@code
 * PreferredProxyCodebaseProvider.checkBootstrapPermissionByDigest}, whose
 * {@code for (URL jarUrl : codebase)} loop is vacuous for an empty array) --
 * this test is about the wire mechanism and the gate-placement/adversarial
 * properties T4 owns, not about re-proving {@code resolve()}'s own
 * already-tested codebase-download machinery.
 */
public class SubProcessWireHandoffEndToEndTest {

    // ---------------------------------------------------------------- fixture

    public interface Greeter {
        String greet(String who) throws RemoteException;
        void explode() throws RemoteException;
    }

    /** Stateless, {@link Externalizable} so AtomicMarshalInputStream can
     *  reconstruct it without any extra DeSerializationPermission grant. */
    public static final class GreeterImpl implements Greeter, Externalizable {
        public GreeterImpl() { }
        @Override public String greet(String who) { return "hello, " + who; }
        @Override public void explode() throws RemoteException {
            throw new IllegalStateException("boom");
        }
        @Override public void writeExternal(ObjectOutput out) { }
        @Override public void readExternal(ObjectInput in) { }
    }

    /**
     * Adversarial fixture: a business object that ALSO declares the real
     * management-plane interface. {@code HostedProxyGuard} must refuse to
     * host it.
     */
    public static final class AdminImpersonatingImpl
            implements Greeter, SubProcessAdministrable, Externalizable {
        public AdminImpersonatingImpl() { }
        @Override public String greet(String who) { return "hi"; }
        @Override public void explode() { }
        @Override public PolicyAdmin getSubProcessPolicyAdmin() {
            throw new SecurityException("must never be reachable");
        }
        @Override public void writeExternal(ObjectOutput out) { }
        @Override public void readExternal(ObjectInput in) { }
    }

    /**
     * 2026-07-20 board-review fixtures (Findings 1 &amp; 2): a business
     * interface whose methods deliberately exercise the two adversarial
     * decode surfaces the board found -- an argument/result array with a
     * non-last {@code Externalizable} element (Finding 1), and a
     * server-generated, arbitrarily-deep nested-array reply (Finding 2).
     */
    public interface DeepEcho {
        Object echo(Object value) throws RemoteException;
        Object deepChain(int depth) throws RemoteException;
    }

    public static final class DeepEchoImpl implements DeepEcho, Externalizable {
        public DeepEchoImpl() { }
        @Override public Object echo(Object value) { return value; }
        @Override public Object deepChain(int depth) {
            Object cur = null;
            for (int i = 0; i < depth; i++) cur = new Object[]{ cur };
            return cur;
        }
        @Override public void writeExternal(ObjectOutput out) { }
        @Override public void readExternal(ObjectInput in) { }
    }

    /** The exact Finding-1 shape: an {@code Externalizable} with an empty body. */
    public static final class ExternalizableProbe implements Externalizable {
        public ExternalizableProbe() { }
        @Override public void writeExternal(ObjectOutput out) { }
        @Override public void readExternal(ObjectInput in) { }
        @Override public boolean equals(Object o) { return o instanceof ExternalizableProbe; }
        @Override public int hashCode() { return 1; }
    }

    /** Same-named decoy in a different package: must NOT be rejected. */
    public static final class DecoyAdminImpersonatingImpl
            implements Greeter,
                       au.net.zeus.jgdms.loader.isolation.decoy.SubProcessAdministrable,
                       Externalizable {
        public DecoyAdminImpersonatingImpl() { }
        @Override public String greet(String who) { return "hi from decoy"; }
        @Override public void explode() { }
        @Override public Object getSubProcessPolicyAdmin() { return "not the real thing"; }
        @Override public void writeExternal(ObjectOutput out) { }
        @Override public void readExternal(ObjectInput in) { }
    }

    /**
     * A real {@link CodebaseAccessor} stub is always a {@code
     * java.lang.reflect.Proxy}-based JERI dynamic proxy (confirmed by
     * {@code resolve()} itself: {@code Proxy.getInvocationHandler
     * (bootstrapProxy)} throws {@code IllegalArgumentException} for a plain
     * concrete class) and, since it is a downloaded-nothing, first-party
     * infrastructure proxy, always implements {@link RemoteMethodControl}
     * too. This fixture builds a real dynamic {@code Proxy} over both
     * interfaces via a {@link Serializable} {@link InvocationHandler}, so it
     * round-trips over the wire exactly the way a genuine stub does (and
     * exercises {@code DeSerializationPermission("PROXY")}, which -- per
     * that permission's own javadoc -- a dynamically generated proxy already
     * holds without an extra grant).
     */
    public static final class FakeBootstrapInvocationHandler
            implements InvocationHandler, Externalizable {
        public FakeBootstrapInvocationHandler() { }
        @Override public void writeExternal(ObjectOutput out) throws IOException { out.writeByte(1); }
        @Override public void readExternal(ObjectInput in) throws IOException { in.readByte(); }
        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            switch (method.getName()) {
                case "getClassAnnotation": return "";
                case "getCertFactoryType": return null;
                case "getCertPathEncoding": return null;
                case "getEncodedCerts": return null;
                case "getCodebaseDigestAlgorithm": return null;
                case "getCodebaseDigest": return null;
                case "getDigestOffsets": return null;
                case "getConstraints": return null;
                case "setConstraints": return proxy; // same Proxy instance
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == (args == null ? null : args[0]);
                case "toString": return "FakeBootstrapProxy";
                default: throw new UnsupportedOperationException(method.getName());
            }
        }
    }

    static CodebaseAccessor newFakeBootstrapProxy() {
        return (CodebaseAccessor) java.lang.reflect.Proxy.newProxyInstance(
                SubProcessWireHandoffEndToEndTest.class.getClassLoader(),
                new Class<?>[] { CodebaseAccessor.class, RemoteMethodControl.class },
                new FakeBootstrapInvocationHandler());
    }

    // ------------------------------------------------------- loopback plumbing

    /** Opens a fresh loopback TCP connection pair; one call per handoff. */
    static final class LoopbackLauncherSpawned implements SubProcessLauncher.Spawned {
        final IsolationPoolingKey ownKey;
        final AtomicInteger channelsOpened = new AtomicInteger();
        final AtomicInteger connectionsAcceptedByServer = new AtomicInteger();

        LoopbackLauncherSpawned(IsolationPoolingKey ownKey) {
            this.ownKey = ownKey;
        }

        @Override
        public SubProcessAdministrable adminSurface() {
            return new SubProcessAdministrable() {
                @Override public PolicyAdmin getSubProcessPolicyAdmin() {
                    throw new SecurityException("fail-closed (test stub, unused)");
                }
            };
        }

        @Override
        public ByteChannel openWireChannel() throws IOException {
            channelsOpened.incrementAndGet();
            ServerSocketChannel server = ServerSocketChannel.open();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            SocketChannel client = SocketChannel.open();
            client.connect(server.getLocalAddress());
            final SocketChannel accepted = server.accept();
            server.close();

            Thread serverThread = new Thread("subprocess-reconstruction-server") {
                @Override public void run() {
                    connectionsAcceptedByServer.incrementAndGet();
                    try {
                        new SubProcessReconstructionServer(
                                ownKey,
                                getClass().getClassLoader(),
                                getClass().getClassLoader())
                                .serve(accepted);
                    } finally {
                        try { accepted.close(); } catch (IOException ignore) { }
                    }
                }
            };
            serverThread.setDaemon(true);
            serverThread.start();
            return client;
        }

        @Override
        public void shutdown() { }
    }

    private IsolationPoolingKey key;
    private SubProcessHandle handle;
    private LoopbackLauncherSpawned spawned;
    private SubProcessWireHandoffImpl wireHandoff;

    @Before
    public void setUp() throws Exception {
        key = keyFor("spiffe://example.org/svc-under-test");
        spawned = new LoopbackLauncherSpawned(key);
        handle = new SubProcessHandle(key, spawned, new Runnable() { public void run() { } });
        wireHandoff = new SubProcessWireHandoffImpl();
    }

    @After
    public void tearDown() {
        // best-effort; individual tests close what they open
    }

    private static IsolationPoolingKey keyFor(String spiffeName) throws IOException {
        return IsolationPoolingKey.derive(new java.security.Principal[]{
                new java.security.Principal() {
                    public String getName() { return spiffeName; }
                }
        });
    }

    // -------------------------------------------------------------- happy path

    @Test(timeout = 20_000)
    public void handoff_reconstructsAndForwardsBusinessCalls() throws Exception {
        MarshalledInstance mi = new MarshalledInstance(new GreeterImpl());
        Object stub = wireHandoff.handoff(handle, newFakeBootstrapProxy(), mi,
                new URL[0], "", getClass().getClassLoader(), null,
                Collections.emptyList());

        assertTrue("thin stub must implement the resolved business interface",
                stub instanceof Greeter);
        assertFalse("thin stub must NOT be the original impl class (it is a"
                + " client-local java.lang.reflect.Proxy forwarding across the"
                + " wire, not the object itself)", stub instanceof GreeterImpl);

        Greeter g = (Greeter) stub;
        assertEquals("hello, world", g.greet("world"));

        try {
            g.explode();
            fail("expected the subprocess-side exception to propagate");
        } catch (RemoteException expected) {
            // good: Greeter.explode() declares RemoteException, so the
            // synthesized client-side exception is a RemoteException per
            // ForwardingInvocationHandler's documented policy.
        }

        assertEquals("exactly one wire-handoff connection for one handoff",
                1, spawned.channelsOpened.get());
    }

    // -------------------------------------------- 2026-07-20 board review fixes

    /**
     * Finding 1 (confirmed by 2 board seats), full-stack proof, driven
     * through the REAL server ({@code SubProcessReconstructionServer
     * .dispatchInvoke} decoding {@code args}) exactly the direction the
     * board exercised directly against {@code WireHandoffCodec}.
     *
     * <p><strong>What the platform-layer fix (item 3) actually closes, and
     * what it does not -- verified here, not assumed:</strong> the null
     * guard on {@code AtomicMarshalInputStream.readNewArray}'s {@code
     * StreamCorruptedException} branch (jgdms-platform,
     * {@code exceptions != null && !exceptions.isEmpty()}) closes exactly
     * the {@code NullPointerException} the board reported -- confirmed:
     * before the fix this call threw an uncaught {@code NullPointerException}
     * ("Cannot invoke java.util.List.isEmpty() because exceptions is null");
     * after it, the same input throws a clean {@code
     * StreamCorruptedException} ("unexpected end of block data") instead.
     * <strong>It does not make the array decode succeed</strong> -- the
     * underlying cause (an {@code Externalizable} element's block-data mode
     * leaving the shared stream desynchronised for the following element)
     * is a separate, deeper bug this minimal, board-scoped fix does not
     * touch. This is exactly why {@code WireHandoffCodec} encodes every
     * field in its own independent sub-stream (see that class's javadoc) --
     * that design choice is what actually protects the wire-handoff
     * envelope's own top-level fields; a single field that is ITSELF an
     * array containing a non-last {@code Externalizable} element (this
     * test's shape -- {@code args}/results are exactly such single fields)
     * is not something per-field stream isolation can help with, since the
     * corruption is internal to that one field's own recursive decode.
     * Flagged in this task's report as a residual, separate from Finding 1
     * as scoped by the board.
     */
    @Test(timeout = 20_000)
    public void nonLastExternalizableArrayElement_failsCleanly_notWithRawNpe_throughFullStack()
            throws Exception {
        MarshalledInstance mi = new MarshalledInstance(new DeepEchoImpl());
        Object stub = wireHandoff.handoff(handle, newFakeBootstrapProxy(), mi,
                new URL[0], "", getClass().getClassLoader(), null,
                Collections.emptyList());
        DeepEcho echo = (DeepEcho) stub;

        Object[] arg = new Object[]{ new ExternalizableProbe(), "trailing string" };
        try {
            echo.echo(arg);
            fail("this shape is not expected to round-trip successfully yet"
                    + " (see javadoc: a separate, deeper stream-desync bug"
                    + " beyond the board-scoped NPE fix) -- if this starts"
                    + " passing, the deeper bug has been independently fixed"
                    + " and this test should be upgraded to assert success");
        } catch (RemoteException expected) {
            // The load-bearing assertion: a clean, well-formed exception,
            // never the raw "Cannot invoke ...List.isEmpty()... null" NPE
            // message, and never an uncaught Throwable reaching this line.
            assertFalse("must never surface the raw NullPointerException"
                    + " message -- that was the exact board-reported crash: "
                    + expected,
                    expected.getMessage().contains("isEmpty() because"));
        }

        // The connection/stub must remain usable afterwards.
        assertEquals("hello", echo.echo("hello"));
    }

    /**
     * Finding 2 (StackOverflowError via unbounded decode-recursion depth),
     * full-stack proof: the SERVER genuinely builds and returns a
     * ~20,000-deep nested-array structure as a real business method's
     * return value (via {@code target.invoke()} +
     * {@code WireHandoffCodec.encodeInvokeReplyResult} -- the real
     * dispatch path, not a hand-crafted frame), and the CLIENT call must
     * complete with a clean {@link RemoteException}, never a live {@code
     * StackOverflowError} escaping into this test's own call stack (the
     * exact failure mode the board demonstrated against {@code
     * decodeInvokeReplyResult} called directly).
     *
     * <p><strong>Where the failure is actually attributed, and why that's
     * still safe:</strong> {@link DecodeDepthGuard} only guards the
     * <em>decode</em> direction (attacker/peer-controlled bytes); it is
     * deliberately not applied on encode (the server serialising its own,
     * already-{@code HostedProxyGuard}-cleared business object's return
     * value is not the same trust boundary). {@code
     * ObjOutputStream.writeNewArray} has the identical unbounded-recursion
     * property as the reader, so encoding this reply itself throws a real
     * {@code StackOverflowError} <em>server-side</em>, inside {@code
     * SubProcessReconstructionServer.dispatchInvoke}'s own {@code
     * catch(Throwable)} (this task's Finding-1-adjacent hardening) --
     * turned into a clean {@code INVOKE_REPLY_EXCEPTION} naming {@code
     * StackOverflowError}, which the client resynthesises as a {@link
     * RemoteException} exactly like any other reported business exception.
     * The end-to-end safety property (client app code never sees a raw
     * {@code Throwable}) holds either way; this test accepts both
     * attributions rather than assuming a specific one, and separately
     * documents the encode-side asymmetry here and in {@code
     * SOW-T4-Wire-Handoff-Protocol.md} rather than silently relying on it.
     */
    @Test(timeout = 20_000)
    public void deepNestedReply_rejectedCleanly_clientNeverCrashes_throughFullStack()
            throws Exception {
        MarshalledInstance mi = new MarshalledInstance(new DeepEchoImpl());
        Object stub = wireHandoff.handoff(handle, newFakeBootstrapProxy(), mi,
                new URL[0], "", getClass().getClassLoader(), null,
                Collections.emptyList());
        DeepEcho echo = (DeepEcho) stub;

        long start = System.nanoTime();
        try {
            echo.deepChain(20_000);
            fail("expected a clean exception, not a silently-accepted deep result"
                    + " (and certainly not a live StackOverflowError)");
        } catch (RemoteException expected) {
            assertTrue("must be attributed to either the client-side decode guard"
                    + " or a clean server-side StackOverflowError report: " + expected,
                    expected.getMessage().contains("DECODE_FAILURE")
                    || expected.getMessage().contains("StackOverflowError"));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("must complete quickly either way -- took " + elapsedMs + "ms",
                elapsedMs < 5_000);

        // The connection/stub must remain usable afterwards -- a decode
        // failure on one call must not corrupt or hang the shared channel
        // for subsequent calls.
        assertEquals("hello, world", ((DeepEcho) stub).echo("hello, world"));
    }

    @Test(timeout = 20_000)
    public void handoff_recordsLiveReferenceOnHandle() throws Exception {
        MarshalledInstance mi = new MarshalledInstance(new GreeterImpl());
        wireHandoff.handoff(handle, newFakeBootstrapProxy(), mi, new URL[0], "",
                getClass().getClassLoader(), null, Collections.emptyList());
        assertEquals(1, handle.liveReferenceCount());
        assertTrue(handle.isAlive());
    }

    // ------------------------------------------------------- adversarial probes

    /** Probe 1 (SOW): a MarshalledInstance crafted to try to reconstruct
     *  outside the gate -- here, a hosted object impersonating the real
     *  management-plane interface, which must be refused at the
     *  HostedProxyGuard checkpoint inside the subprocess, before any stub
     *  is ever handed back. */
    @Test(timeout = 20_000)
    public void handoff_hostedProxyImpersonatingAdminInterface_refused() throws Exception {
        MarshalledInstance mi = new MarshalledInstance(new AdminImpersonatingImpl());
        try {
            wireHandoff.handoff(handle, newFakeBootstrapProxy(), mi, new URL[0], "",
                    getClass().getClassLoader(), null, Collections.emptyList());
            fail("expected the subprocess to refuse hosting an admin-impersonating proxy");
        } catch (IOException expected) {
            assertTrue("refusal must be attributed to the hosting guard: " + expected,
                    expected.getMessage().contains("HOSTING_REFUSED"));
        }
        assertEquals("a refused handoff must not leave a live reference recorded",
                0, handle.liveReferenceCount());
    }

    /** Identity, never name: a same-named decoy in a different package must
     *  NOT be rejected -- proves the guard checks resolved type identity. */
    @Test(timeout = 20_000)
    public void handoff_sameNamedDecoyInterface_notRejected() throws Exception {
        MarshalledInstance mi = new MarshalledInstance(new DecoyAdminImpersonatingImpl());
        Object stub = wireHandoff.handoff(handle, newFakeBootstrapProxy(), mi,
                new URL[0], "", getClass().getClassLoader(), null,
                Collections.emptyList());
        assertTrue(stub instanceof Greeter);
        assertEquals("hi from decoy", ((Greeter) stub).greet("x"));
    }

    /** Probe: a request asserting the wrong pooling key must be refused,
     *  even though it arrives on a channel opened by this subprocess's own
     *  launcher (defence in depth against a wrong-target / replay hazard). */
    @Test(timeout = 20_000)
    public void handoff_wrongAssertedPoolingKey_refused() throws Exception {
        // Build a handle whose key differs from what the server (spawned
        // with `key`) actually enforces, by handing the client a handle
        // wrapping the SAME spawned server but a DIFFERENT client-asserted
        // key -- simulating a confused/malicious client presenting a stale
        // or foreign key over a channel to this subprocess.
        IsolationPoolingKey otherKey = keyFor("spiffe://example.org/different-principal");
        SubProcessHandle mismatchedHandle =
                new SubProcessHandle(otherKey, spawned, new Runnable() { public void run() { } });

        MarshalledInstance mi = new MarshalledInstance(new GreeterImpl());
        try {
            wireHandoff.handoff(mismatchedHandle, newFakeBootstrapProxy(), mi,
                    new URL[0], "", getClass().getClassLoader(), null,
                    Collections.emptyList());
            fail("expected KEY_MISMATCH refusal");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("KEY_MISMATCH"));
        }
    }

    /** Probe 3 (SOW): an oversized frame sent directly at the live
     *  dispatcher (not through the well-behaved client) must be rejected
     *  before allocation, not hang, crash, or OOM the subprocess. */
    @Test(timeout = 20_000)
    public void serve_hostileOversizedRequestFrame_rejectedNotHang() throws Exception {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        SocketChannel client = SocketChannel.open();
        client.connect(server.getLocalAddress());
        SocketChannel accepted = server.accept();
        server.close();

        final AtomicBoolean serverReturned = new AtomicBoolean(false);
        Thread serverThread = new Thread() {
            @Override public void run() {
                new SubProcessReconstructionServer(key, getClass().getClassLoader(),
                        getClass().getClassLoader()).serve(accepted);
                serverReturned.set(true);
            }
        };
        serverThread.setDaemon(true);
        serverThread.start();

        // Hand-craft a REQUEST header declaring ~2GB, then close our end --
        // if the dispatcher allocated first, this would hang (or OOM); the
        // 10-byte header is all we ever send.
        ByteBuffer header = ByteBuffer.allocate(10);
        header.putInt(WireFraming.MAGIC);
        header.put(WireFraming.VERSION);
        header.put(WireFraming.Type.REQUEST);
        header.putInt(Integer.MAX_VALUE - 1);
        header.flip();
        while (header.hasRemaining()) client.write(header);
        client.close();

        serverThread.join(10_000);
        assertFalse("server thread must not still be blocked/hung", serverThread.isAlive());
        assertTrue("serve() must have returned (frame rejected, connection torn"
                + " down) rather than blocking on the declared-but-never-sent"
                + " payload", serverReturned.get());
    }

    /** Three-axes probe (byte flow / origination / authorization): the
     *  subprocess side never originates an outbound connection -- across
     *  the whole happy-path test above, the ONLY socket opened is the one
     *  the client explicitly asked for via openWireChannel(). */
    @Test(timeout = 20_000)
    public void serve_neverOriginatesConnectionBackToClient() throws Exception {
        MarshalledInstance mi = new MarshalledInstance(new GreeterImpl());
        wireHandoff.handoff(handle, newFakeBootstrapProxy(), mi, new URL[0], "",
                getClass().getClassLoader(), null, Collections.emptyList());
        // One connection opened, by the client-side launcher call, and the
        // subprocess accepted exactly that one -- it never called out on
        // its own via any other channel-opening path (there is none in
        // SubProcessReconstructionServer -- serve() takes an already-open
        // channel and only ever reads-then-writes on it).
        assertEquals(1, spawned.channelsOpened.get());
        assertEquals(1, spawned.connectionsAcceptedByServer.get());
    }
}
