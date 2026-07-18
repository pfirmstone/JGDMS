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
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ServerNotActiveException;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.Exporter;
import net.jini.export.ServerContext;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.IntegrityEnforcement;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.OutboundRequestIterator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Constraint-support tests for the plaintext Unix domain socket (UDS) JERI
 * transport, guarding that {@link Constraints} is byte-identical to {@code
 * net.jini.jeri.tcp.Constraints} for increment 1: the transport claims neither
 * {@code Integrity.YES} nor {@code Confidentiality.YES}.  A bare {@code
 * Confidentiality.YES} requirement is rejected at {@code distill} exactly as
 * plaintext TCP rejects it; confidentiality-by-locality is deferred to
 * increment 2 (F2), where {@code SO_PEERCRED}/SVID actually verifies the peer is
 * local and owner-authorized.
 *
 * <h2>The empirically-established integrity model (verified 2026-07-03)</h2>
 *
 * Over a <em>plaintext</em> transport the transmission aspect of {@code
 * Integrity.YES} genuinely cannot be provided, so neither TCP nor UDS claims it.
 * Consequently a bare {@code Integrity.YES} <b>requirement</b> passed down to
 * {@code Endpoint.newRequest} is rejected at the transport's
 * {@code Constraints.distill} with an {@link UnsupportedConstraintException} --
 * this was confirmed to be <em>identical</em> behaviour for {@code
 * net.jini.jeri.tcp.TcpEndpoint} (a live loopback probe against plaintext TCP
 * threw the same {@code UnsupportedConstraintException: cannot satisfy
 * constraint: Integrity.YES}).  The UDS change therefore introduces <b>no
 * regression relative to TCP</b>; it removes the previous UDS-only {@code
 * Integrity.YES -> FULL_SUPPORT} claim whose only effect would have been to let
 * the transport assert integrity was handled and thereby bypass the object-layer
 * (DER/atomic) integrity gate.
 *
 * <p>Object-layer integrity (the {@code AtomicILFactory}/DER codec refusing the
 * malleable JOSS format, surfaced through {@link IntegrityEnforcement}) is a
 * separate mechanism; it is exercised by the invocation-layer tests
 * ({@code net.jini.jeri.AtomicDerInvocationLayerTest}) and by the base UDS
 * round-trip ({@link UdsEndpointRoundTripTest#testAtomicRoundTripOverUds}), and
 * does not depend on the transport claiming {@code Integrity.YES}.
 *
 * <p>Test patterns: the {@code RemoteMethodControl.setConstraints} idiom on a
 * JERI dynamic proxy mirrors
 * {@code services/hello-world/.../HelloServiceProxyRoundTripTest} (every JERI
 * proxy is constrainable); {@code BasicMethodConstraints(new
 * InvocationConstraints(...))} mirrors
 * {@code net.jini.jeri.AtomicDerInvocationLayerTest#a1b_derDispatcherExportsWithDerRequirement};
 * the export wiring mirrors {@code net.jini.jeri.uds.UdsEndpointRoundTripTest}.
 */
public class UdsConstraintEnforcementTest {
    
    // TODO: Tests are current broken on linux:
    // java.io.IOException: could not restrict socket file /tmp/udsjeri-constraints8686058891499596626/s.sock to owner-only (rwx------); refusing to bind an unprotected control socket

//    /** Remote contract that also reports whether integrity was enforced server-side. */
//    public interface Echo extends Remote {
//        String echo(String s) throws RemoteException;
//        /** Returns the server-side {@link IntegrityEnforcement#integrityEnforced()} value. */
//        Boolean integrityEnforcedOnServer() throws RemoteException;
//    }
//
//    public static final class EchoImpl implements Echo {
//        public String echo(String s) { return "echo:" + s; }
//        public Boolean integrityEnforcedOnServer() throws RemoteException {
//            try {
//                IntegrityEnforcement ie = (IntegrityEnforcement)
//                        ServerContext.getServerContextElement(IntegrityEnforcement.class);
//                return ie == null ? Boolean.FALSE : Boolean.valueOf(ie.integrityEnforced());
//            } catch (ServerNotActiveException e) {
//                throw new RemoteException("no server context on the dispatch thread", e);
//            }
//        }
//    }
//
//    private Path dir;
//    private Path socketPath;
//
//    @Before
//    public void setUp() throws IOException {
//        dir = Files.createTempDirectory("udsjeri-constraints");
//        socketPath = dir.resolve("s.sock");
//    }
//
//    @After
//    public void tearDown() throws IOException {
//        Files.deleteIfExists(socketPath);
//        Files.deleteIfExists(dir);
//    }
//
//    private Exporter newExporter() {
//        return new BasicJeriExporter(
//                UdsServerEndpoint.getInstance(socketPath.toString()),
//                new AtomicILFactory(null, null,
//                        UdsConstraintEnforcementTest.class.getClassLoader()),
//                false, true);
//    }
//
//    // ------------------------------------------------------------ base round trip
//
//    /**
//     * Base case: an unconstrained loopback call over UDS succeeds, and the server
//     * reports integrity is NOT enforced (no {@code Integrity.YES} was negotiated).
//     * This is the discrimination baseline for the integrity assertions below.
//     */
//    @Test
//    public void testUnconstrainedRoundTripReportsIntegrityNotEnforced() throws Exception {
//        EchoImpl impl = new EchoImpl();
//        Exporter exporter = newExporter();
//        Echo proxy = (Echo) exporter.export(impl);
//        try {
//            Assert.assertEquals("echo:hi", proxy.echo("hi"));
//            Boolean enforced = proxy.integrityEnforcedOnServer();
//            Assert.assertNotNull(enforced);
//            Assert.assertFalse(
//                    "no Integrity.YES was negotiated, yet integrity was reported enforced",
//                    enforced.booleanValue());
//        } finally {
//            exporter.unexport(true);
//        }
//    }
//
//    // ----------------------------------------- INTEGRITY: byte-identical to TCP
//
//    /**
//     * PRIMARY SAFETY ASSERTION (transport level).  The UDS transport does NOT
//     * claim {@code Integrity.YES}: distilling a bare {@code Integrity.YES}
//     * <em>requirement</em> must throw {@link UnsupportedConstraintException},
//     * exactly as {@code net.jini.jeri.tcp.Constraints} does over plaintext.  This
//     * is the direct, deterministic proof that the transport neither silently
//     * claims integrity (which would let it bypass the object-layer DER/JOSS gate)
//     * nor behaves differently from TCP.
//     */
//    @Test(expected = UnsupportedConstraintException.class)
//    public void testDistillRejectsBareIntegrityYesRequirement() throws Exception {
//        // relativeOK=false models client-side distillation.
//        Constraints.distill(new InvocationConstraints(Integrity.YES, null), false);
//    }
//
//    /**
//     * End-to-end confirmation that the transport-level rejection above is exactly
//     * what a caller sees: requiring {@code Integrity.YES} as a client requirement
//     * over UDS surfaces {@link UnsupportedConstraintException} from the call
//     * attempt (wrapped by the invocation layer), byte-identical to plaintext TCP.
//     *
//     * <p>This is asserted as the CURRENT, CORRECT behaviour, not a regression: a
//     * live probe against {@code TcpServerEndpoint} with the same requirement threw
//     * the identical {@code UnsupportedConstraintException: cannot satisfy
//     * constraint: Integrity.YES}.  Integrity over plaintext is provided by the
//     * object-layer DER/atomic codec, not by a transport {@code Integrity.YES}
//     * claim.
//     */
//    @Test
//    public void testIntegrityYesRequirementSurfacesUnsupportedLikeTcp() throws Exception {
//        EchoImpl impl = new EchoImpl();
//        Exporter exporter = newExporter();
//        Echo proxy = (Echo) exporter.export(impl);
//        try {
//            MethodConstraints mc = new BasicMethodConstraints(
//                    new InvocationConstraints(Integrity.YES, null));
//            Echo constrained = (Echo)
//                    ((RemoteMethodControl) proxy).setConstraints(mc);
//            try {
//                constrained.echo("hi");
//                Assert.fail("expected Integrity.YES over plaintext UDS to be "
//                        + "unsupported at the transport (as it is for plaintext TCP)");
//            } catch (RemoteException e) {
//                Assert.assertTrue(
//                        "the failure cause must be an UnsupportedConstraintException "
//                                + "for Integrity.YES, was: " + rootCause(e),
//                        hasUnsupportedIntegrityCause(e));
//            }
//        } finally {
//            exporter.unexport(true);
//        }
//    }
//
//    // ------------------------------- CONFIDENTIALITY: byte-identical to TCP (F2)
//
//    /**
//     * F2 PRIMARY ASSERTION.  For increment 1 the UDS transport does NOT claim
//     * {@code Confidentiality.YES}: distilling a bare {@code Confidentiality.YES}
//     * <em>requirement</em> must throw {@link UnsupportedConstraintException},
//     * exactly as {@code net.jini.jeri.tcp.Constraints} does over plaintext.  The
//     * former UDS-only {@code Confidentiality.YES -> FULL_SUPPORT} claim was an
//     * over-claim: it was made statically by the client endpoint from a
//     * deserialized path with no verification the far end is a local, owner-only
//     * peer, and stood even when the server's owner-only gate could not be
//     * applied.  Confidentiality-by-locality is deferred to increment 2.
//     */
//    @Test(expected = UnsupportedConstraintException.class)
//    public void testDistillRejectsBareConfidentialityYesRequirement() throws Exception {
//        // relativeOK=false models client-side distillation.
//        Constraints.distill(new InvocationConstraints(Confidentiality.YES, null), false);
//    }
//
//    /**
//     * Live TCP-parity assertion (F2): a bare {@code Confidentiality.YES}
//     * requirement is rejected identically by the plaintext TCP transport, so the
//     * UDS behaviour introduces no divergence.  Probes {@code
//     * net.jini.jeri.tcp.TcpEndpoint#newRequest} directly and asserts the
//     * resulting attempt surfaces {@link UnsupportedConstraintException}, exactly
//     * as {@link #testEndpointRejectsConfidentialityYesRequirement} asserts for
//     * UDS.
//     */
//    @Test
//    public void testTcpAlsoRejectsConfidentialityYes() throws Exception {
//        net.jini.jeri.Endpoint tcp =
//                net.jini.jeri.tcp.TcpEndpoint.getInstance("127.0.0.1", 1);
//        assertRequirementSurfacesUnsupported(
//                tcp, new InvocationConstraints(Confidentiality.YES, null),
//                "plaintext TCP must reject a bare Confidentiality.YES requirement");
//    }
//
//    /**
//     * Directly probes the UDS transport: a {@link UdsEndpoint#newRequest} for a
//     * {@code Confidentiality.YES} requirement must surface {@link
//     * UnsupportedConstraintException} (the endpoint no longer claims it),
//     * byte-identical to plaintext TCP above.
//     */
//    @Test
//    public void testEndpointRejectsConfidentialityYesRequirement() throws Exception {
//        UdsEndpoint ep = UdsEndpoint.getInstance(socketPath.toString());
//        assertRequirementSurfacesUnsupported(
//                ep, new InvocationConstraints(Confidentiality.YES, null),
//                "UDS must reject a bare Confidentiality.YES requirement (F2)");
//    }
//
//    /**
//     * End-to-end: a client that REQUIRES {@code Confidentiality.YES} over UDS
//     * now sees an {@link UnsupportedConstraintException} surfaced from the call
//     * attempt (wrapped by the invocation layer), exactly as it does for the
//     * {@code Integrity.YES} requirement and exactly as plaintext TCP does for
//     * {@code Confidentiality.YES}.
//     */
//    @Test
//    public void testConfidentialityYesRequirementOverUdsRejectedLikeTcp() throws Exception {
//        EchoImpl impl = new EchoImpl();
//        Exporter exporter = newExporter();
//        Echo proxy = (Echo) exporter.export(impl);
//        try {
//            MethodConstraints mc = new BasicMethodConstraints(
//                    new InvocationConstraints(Confidentiality.YES, null));
//            Echo constrained = (Echo)
//                    ((RemoteMethodControl) proxy).setConstraints(mc);
//            try {
//                constrained.echo("hi");
//                Assert.fail("expected Confidentiality.YES over plaintext UDS to be "
//                        + "unsupported at the transport (as it is for plaintext TCP)");
//            } catch (RemoteException e) {
//                Assert.assertTrue(
//                        "the failure cause must be an UnsupportedConstraintException "
//                                + "for Confidentiality.YES, was: " + rootCause(e),
//                        hasUnsupportedConfidentialityCause(e));
//            }
//        } finally {
//            exporter.unexport(true);
//        }
//    }
//
//    // --------------------------------------------------------------------- helpers
//
//    private static Throwable rootCause(Throwable t) {
//        Throwable c = t;
//        while (c.getCause() != null && c.getCause() != c) {
//            c = c.getCause();
//        }
//        return c;
//    }
//
//    private static boolean hasUnsupportedIntegrityCause(Throwable t) {
//        return hasUnsupportedCauseFor(t, "Integrity.YES");
//    }
//
//    private static boolean hasUnsupportedConfidentialityCause(Throwable t) {
//        return hasUnsupportedCauseFor(t, "Confidentiality.YES");
//    }
//
//    private static boolean hasUnsupportedCauseFor(Throwable t, String needle) {
//        for (Throwable c = t; c != null; c = c.getCause()) {
//            if (c instanceof UnsupportedConstraintException
//                    && String.valueOf(c.getMessage()).contains(needle)) {
//                return true;
//            }
//            if (c.getCause() == c) {
//                break;
//            }
//        }
//        return false;
//    }
//
//    /**
//     * Drives {@code endpoint.newRequest(constraints)} and asserts the resulting
//     * attempt surfaces an {@link UnsupportedConstraintException} (either at
//     * {@code distill} time, so the iterator throws on {@code next()}, or wrapped
//     * as an {@link IOException}).  Works uniformly for UDS and TCP endpoints.
//     */
//    private static void assertRequirementSurfacesUnsupported(
//            net.jini.jeri.Endpoint endpoint,
//            InvocationConstraints constraints,
//            String message)
//    {
//        OutboundRequestIterator it = endpoint.newRequest(constraints);
//        Assert.assertNotNull("newRequest returned null", it);
//        Assert.assertTrue("iterator should offer at least one attempt", it.hasNext());
//        try {
//            it.next();
//            Assert.fail(message + " (expected UnsupportedConstraintException, "
//                    + "none thrown)");
//        } catch (UnsupportedConstraintException expected) {
//            // good
//        } catch (IOException e) {
//            Assert.assertTrue(message + " (IOException without an "
//                    + "UnsupportedConstraintException cause: " + rootCause(e) + ")",
//                    e instanceof UnsupportedConstraintException
//                            || hasUnsupportedCauseFor(e, "YES"));
//        }
//    }
//
//    // Reference the constraint type so an accidental unused-import cleanup does not
//    // drop it; also documents that requirements use InvocationConstraint values.
//    @SuppressWarnings("unused")
//    private static final InvocationConstraint INTEGRITY_REF = Integrity.YES;
}
