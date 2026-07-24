/*
 * Copyright 2026 peter.
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
package net.jini.jeri;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.ServerError;
import java.rmi.ServerException;
import net.jini.core.transaction.TransactionException;
import net.jini.export.Exporter;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.tcp.TcpServerEndpoint;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.DerThrowableForm;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * End-to-end remote-fault round-trips over a REAL exported
 * {@link AtomicDerILFactory} endpoint (TCP loopback) -- U1b finding 8a
 * (fix/der-throwable-marshalling).
 *
 * <p>Before the {@link DerThrowableForm} carrier, every
 * non-schema-typable fault failed to marshal on the pure DER response stream,
 * degrading to {@code request.abort()} and a generic connection failure at the
 * client. These tests pin the new behaviour: faults surface at the client AS
 * their own type (or the documented {@code ServerException}/{@code ServerError}
 * wrapper), with message, cause chain, suppressed list and stack trace carried.
 *
 * <p>Pinned dispatch rule (see {@code AtomicDerInvocationDispatcher.marshalThrow}):
 * {@code @AtomicSerial} + DER-schema-typable faults travel natively (the
 * {@code (GetArg)} constructor runs -- pinned via {@link NativeAtomicFault}'s
 * marker); everything else -- including {@code AtomicException} subclasses such
 * as {@link TransactionException}, whose inherited serial form is not
 * schema-typable -- rides the carrier's constructor-matching rebuild.
 */
public class AtomicDerFaultRoundTripTest {

    // =========================================================================
    // Fixtures
    // =========================================================================

    /**
     * An {@code @AtomicSerial} exception with a DER-schema-typable form and a
     * native-path marker: {@code viaGetArg} is set ONLY by the {@code (GetArg)}
     * deserialization constructor. If this fault ever rode the carrier, the
     * carrier's constructor-matching rebuild would use the {@code (String)}
     * constructor and the marker would be false.
     */
    @AtomicSerial
    public static class NativeAtomicFault extends Exception {
        private static final long serialVersionUID = 1L;

        public static SerialForm[] serialForm() {
            return new SerialForm[]{
                new SerialForm("message", String.class),
            };
        }

        public static void serialize(PutArg arg, NativeAtomicFault e) throws IOException {
            arg.put("message", e.getMessage());
            arg.writeArgs();
        }

        /** true only when reconstructed by the (GetArg) ctor (the native path). */
        public final transient boolean viaGetArg;

        public NativeAtomicFault(String message) {
            super(message);
            this.viaGetArg = false;
        }

        public NativeAtomicFault(GetArg arg) throws IOException, ClassNotFoundException {
            super(arg.get("message", null, String.class));
            this.viaGetArg = true;
        }
    }

    /** Remote contract: one method per fault shape. */
    public interface FaultService extends Remote {
        void remoteFault() throws RemoteException;
        void errorFault() throws RemoteException;
        void npeFault() throws RemoteException;
        void iaeFault() throws RemoteException;
        void nativeAtomicFault() throws NativeAtomicFault, RemoteException;
        void transactionFault() throws TransactionException, RemoteException;
        void constraintRefusal() throws UnsupportedConstraintException, RemoteException;
        void deepFault(int causeDepth) throws Exception;
        void suppressedFault() throws Exception;
        void sentinelFault() throws Exception;
    }

    public static final class FaultServiceImpl implements FaultService {
        @Override
        public void remoteFault() throws RemoteException {
            throw new RemoteException("remote boom",
                    new IllegalStateException("underlying state"));
        }

        @Override
        public void errorFault() {
            throw new InternalError("server internal error");
        }

        @Override
        public void npeFault() {
            throw new NullPointerException("server npe");
        }

        @Override
        public void iaeFault() {
            throw new IllegalArgumentException("server iae",
                    new NullPointerException("iae cause"));
        }

        @Override
        public void nativeAtomicFault() throws NativeAtomicFault {
            throw new NativeAtomicFault("native fault");
        }

        @Override
        public void transactionFault() throws TransactionException {
            throw new TransactionException("tx refused");
        }

        @Override
        public void constraintRefusal() throws UnsupportedConstraintException {
            // The SOW's mandated loud refusal shape: the fail-loud posture case.
            throw new UnsupportedConstraintException(
                    "cannot satisfy unfulfilled constraint: ATOMIC_DER required");
        }

        @Override
        public void deepFault(int causeDepth) throws Exception {
            Exception t = new Exception("depth-" + causeDepth);
            for (int i = causeDepth - 1; i >= 0; i--) {
                t = new Exception("depth-" + i, t);
            }
            throw t;
        }

        @Override
        public void suppressedFault() throws Exception {
            Exception e = new Exception("primary");
            e.addSuppressed(new IllegalStateException("sup-1"));
            e.addSuppressed(new RuntimeException("sup-2", new Exception("sup-2-cause")));
            throw e;
        }

        @Override
        public void sentinelFault() throws Exception {
            throw new Exception("no cause ever set"); // cause == this sentinel
        }
    }

    // =========================================================================
    // Export plumbing
    // =========================================================================

    /*
     * ONE export shared by the whole class: rapid per-test export/unexport
     * cycles are flaky on Windows -- the client-side mux connection cache is
     * keyed by (host, port), and ephemeral-port reuse can hand a fresh export a
     * just-closed port whose cached dying connection the next call then reuses
     * (intermittent EOF). A single long-lived endpoint also matches production
     * shape, and any fault that DID abort the connection (the pre-carrier
     * regression this class pins against) poisons subsequent tests loudly.
     */
    private static Exporter exporter;
    /** STRONG ref: the export table holds the impl weakly (DGC disabled). */
    private static FaultServiceImpl impl;
    private static FaultService proxy;

    @BeforeClass
    public static void setUpClass() throws Exception {
        exporter = new BasicJeriExporter(
                TcpServerEndpoint.getInstance(0),
                new AtomicDerILFactory(null, null,
                        AtomicDerFaultRoundTripTest.class.getClassLoader()),
                false, true);
        impl = new FaultServiceImpl();
        proxy = (FaultService) exporter.export(impl);
    }

    @AfterClass
    public static void tearDownClass() {
        if (exporter != null) exporter.unexport(true);
    }

    // =========================================================================
    // Fault shapes over the wire
    // =========================================================================

    /** RemoteException in the server thread arrives as ServerException(cause=RemoteException). */
    @Test
    public void remoteExceptionWrappedAsServerException() throws Exception {
        try {
            proxy.remoteFault();
            Assert.fail("expected ServerException");
        } catch (ServerException e) {
            // RemoteException.getMessage() decorates with the nested detail.
            Assert.assertTrue("unexpected message: " + e.getMessage(),
                    e.getMessage().startsWith("RemoteException in server thread"));
            Assert.assertTrue("cause must be the original RemoteException, got "
                    + e.getCause(), e.getCause() instanceof RemoteException);
            RemoteException cause = (RemoteException) e.getCause();
            // The inner RemoteException also decorates: it carries the ISE detail.
            Assert.assertTrue("unexpected cause message: " + cause.getMessage(),
                    cause.getMessage().startsWith("remote boom"));
            Assert.assertTrue("nested cause chain must survive, got " + cause.getCause(),
                    cause.getCause() instanceof IllegalStateException);
            Assert.assertEquals("underlying state", cause.getCause().getMessage());
        }
    }

    /** Error in the server thread arrives as ServerError(cause=the Error). */
    @Test
    public void errorWrappedAsServerError() throws Exception {
        try {
            proxy.errorFault();
            Assert.fail("expected ServerError");
        } catch (ServerError e) {
            // ServerError extends RemoteException: decorated getMessage().
            Assert.assertTrue("unexpected message: " + e.getMessage(),
                    e.getMessage().startsWith("Error in server thread"));
            Assert.assertTrue("cause must be the original InternalError, got "
                    + e.getCause(), e.getCause() instanceof InternalError);
            Assert.assertEquals("server internal error", e.getCause().getMessage());
        }
    }

    /** Unchecked NPE passes unwrapped with its message and a server-side stack. */
    @Test
    public void npeSurfacesTyped() throws Exception {
        try {
            proxy.npeFault();
            Assert.fail("expected NullPointerException");
        } catch (NullPointerException e) {
            Assert.assertEquals("server npe", e.getMessage());
            Assert.assertTrue("server-side stack must be carried",
                    stackMentions(e, FaultServiceImpl.class.getName()));
        }
    }

    /** Unchecked IAE passes unwrapped with its cause chain. */
    @Test
    public void iaeSurfacesTypedWithCause() throws Exception {
        try {
            proxy.iaeFault();
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("server iae", e.getMessage());
            Assert.assertTrue(e.getCause() instanceof NullPointerException);
            Assert.assertEquals("iae cause", e.getCause().getMessage());
        }
    }

    /**
     * An {@code @AtomicSerial} fault with a schema-typable form travels
     * NATIVELY: the client-received instance was built by the {@code (GetArg)}
     * constructor (marker true), never double-wrapped by the carrier.
     */
    @Test
    public void atomicSerialFaultTravelsNative() throws Exception {
        try {
            proxy.nativeAtomicFault();
            Assert.fail("expected NativeAtomicFault");
        } catch (NativeAtomicFault e) {
            Assert.assertEquals("native fault", e.getMessage());
            Assert.assertTrue("an @AtomicSerial schema-typable fault must travel"
                    + " natively (GetArg ctor), not ride the carrier", e.viaGetArg);
        }
    }

    /**
     * {@link TransactionException} -- {@code @AtomicSerial} but NOT
     * DER-schema-typable ({@code AtomicException}'s inherited serial form
     * declares raw {@code Throwable}-typed fields) -- rides the CARRIER and
     * still surfaces as its own type with its message. (Pre-carrier this fault
     * did not travel natively either: schema generation failed mid-marshal and
     * the call degraded to an abort.)
     */
    @Test
    public void transactionExceptionSurfacesTypedViaCarrier() throws Exception {
        try {
            proxy.transactionFault();
            Assert.fail("expected TransactionException");
        } catch (TransactionException e) {
            Assert.assertEquals("tx refused", e.getMessage());
        }
    }

    /**
     * THE FAIL-LOUD POSTURE CASE: a constraint refusal surfaces at the client AS
     * {@link UnsupportedConstraintException} -- not as a generic connection
     * failure from {@code request.abort()}.
     */
    @Test
    public void unsupportedConstraintExceptionSurfacesTyped() throws Exception {
        try {
            proxy.constraintRefusal();
            Assert.fail("expected UnsupportedConstraintException");
        } catch (UnsupportedConstraintException e) {
            Assert.assertEquals(
                    "cannot satisfy unfulfilled constraint: ATOMIC_DER required",
                    e.getMessage());
        } catch (RemoteException e) {
            Assert.fail("the refusal degraded to a generic remote failure -- the"
                    + " pre-carrier abort behaviour: " + e);
        }
    }

    /** Fencepost pair over the wire: cause chain at the ceiling intact; one over marked. */
    @Test
    public void deepCauseChainFencepostOverWire() throws Exception {
        int ceiling = DerThrowableForm.MAX_CAUSE_DEPTH;

        try {
            proxy.deepFault(ceiling - 1);
            Assert.fail("expected Exception");
        } catch (Exception e) {
            Throwable deepest = e;
            int depth = 0;
            while (deepest.getCause() != null) { deepest = deepest.getCause(); depth++; }
            Assert.assertEquals(ceiling - 1, depth);
            Assert.assertEquals("at-ceiling chain must arrive untruncated",
                    "depth-" + (ceiling - 1), deepest.getMessage());
        }

        try {
            proxy.deepFault(ceiling);
            Assert.fail("expected Exception");
        } catch (Exception e) {
            Throwable deepest = e;
            while (deepest.getCause() != null) deepest = deepest.getCause();
            Assert.assertTrue("over-ceiling chain must end in an explicit marker, got: "
                    + deepest.getMessage(),
                    deepest.getMessage() != null && deepest.getMessage()
                            .contains("truncated at depth " + ceiling));
        }
    }

    /** Suppressed exceptions survive the wire, recursively. */
    @Test
    public void suppressedSurviveOverWire() throws Exception {
        try {
            proxy.suppressedFault();
            Assert.fail("expected Exception");
        } catch (Exception e) {
            Assert.assertEquals("primary", e.getMessage());
            Throwable[] sup = e.getSuppressed();
            Assert.assertEquals(2, sup.length);
            Assert.assertTrue(sup[0] instanceof IllegalStateException);
            Assert.assertEquals("sup-1", sup[0].getMessage());
            Assert.assertTrue(sup[1] instanceof RuntimeException);
            Assert.assertEquals("sup-2-cause", sup[1].getCause().getMessage());
        }
    }

    /** The JDK cause self-sentinel never travels: the received fault's cause is null. */
    @Test
    public void selfSentinelCauseArrivesNull() throws Exception {
        try {
            proxy.sentinelFault();
            Assert.fail("expected Exception");
        } catch (Exception e) {
            Assert.assertEquals("no cause ever set", e.getMessage());
            Assert.assertNull(e.getCause());
        }
    }

    private static boolean stackMentions(Throwable t, String className) {
        for (StackTraceElement frame : t.getStackTrace()) {
            if (frame.getClassName().equals(className)) return true;
        }
        return false;
    }
}
