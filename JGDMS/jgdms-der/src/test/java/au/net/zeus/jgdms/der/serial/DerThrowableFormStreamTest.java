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
package au.net.zeus.jgdms.der.serial;

import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.rmi.RemoteException;
import java.rmi.ServerError;
import java.rmi.ServerException;
import net.jini.io.UnsupportedConstraintException;
import org.apache.river.api.io.DerThrowableForm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DerThrowableForm} (the STD-006 sec.7.6 {@code ThrowableRecord} safe
 * subset, the pure-DER invocation-fault carrier) over the pure DER object
 * stream: capture -&gt; encode -&gt; decode -&gt; trusted-seam typed rebuild.
 *
 * <p>Covers the U1b finding-8a fault shapes (ServerException/ServerError
 * wrappers via constructor matching, suppressed lists, the JDK cause self
 * sentinel), the sec.4.5 ceiling fenceposts with truncation-with-marker
 * semantics, and the structural-fit consistency pin between
 * {@link DerThrowableForm#MAX_CAUSE_DEPTH} and {@link ObjectCodec#MAX_NESTING}.
 */
class DerThrowableFormStreamTest {

    /** Round-trips a captured fault through the pure DER object stream and rebuilds. */
    private static Throwable roundTrip(Throwable fault) throws Exception {
        DerThrowableForm form = DerThrowableForm.capture(fault);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(baos)) {
            out.writeObject(form);
            out.flush();
        }
        Object decoded;
        try (DerMarshalInputStream in =
                new DerMarshalInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            decoded = in.readObject();
        }
        assertInstanceOf(DerThrowableForm.class, decoded,
                "the DER decoder must yield the inert carrier, never a rebuilt Throwable");
        assertEquals(form, decoded, "carrier must round-trip structurally intact");
        return ((DerThrowableForm) decoded)
                .toThrowable(DerThrowableFormStreamTest.class.getClassLoader());
    }

    // ---------------------------------------------------------------- fault shapes

    /**
     * The dispatcher's standard wrap: RemoteException in server thread -&gt;
     * ServerException("...", cause). ServerException(String, Exception) must be
     * matched by the ctor-matching reconstruction (the (String, Exception)
     * shape), with the RemoteException cause rebuilt beneath it.
     */
    @Test
    void serverExceptionWrapRebuildsTyped() throws Exception {
        RemoteException cause = new RemoteException("remote boom");
        ServerException fault = new ServerException("RemoteException in server thread", cause);

        Throwable rebuilt = roundTrip(fault);

        ServerException se = assertInstanceOf(ServerException.class, rebuilt);
        // RemoteException.getMessage() decorates with "; nested exception is:" from
        // the detail field; the rebuilt wrapper reproduces the ORIGINAL's decorated
        // message exactly (capture strips the decoration, the ctor-matched rebuild
        // re-applies it) -- fidelity is original-vs-rebuilt equality.
        assertEquals(fault.getMessage(), se.getMessage());
        assertTrue(se.getMessage().startsWith("RemoteException in server thread"));
        RemoteException rc = assertInstanceOf(RemoteException.class, se.getCause());
        assertEquals("remote boom", rc.getMessage());
        // ServerException extends RemoteException: the (String, Exception) ctor
        // stores the cause in the detail field, which getCause() reports.
        assertEquals(cause.getMessage(), ((RemoteException) rebuilt).detail.getMessage());
    }

    /** Error in server thread -&gt; ServerError(String, Error): the (String, Error) ctor shape. */
    @Test
    void serverErrorWrapRebuildsTyped() throws Exception {
        ServerError fault = new ServerError("Error in server thread",
                new InternalError("server internal"));

        Throwable rebuilt = roundTrip(fault);

        ServerError se = assertInstanceOf(ServerError.class, rebuilt);
        // ServerError extends RemoteException: decorated getMessage(), as above.
        assertEquals(fault.getMessage(), se.getMessage());
        assertTrue(se.getMessage().startsWith("Error in server thread"));
        assertInstanceOf(InternalError.class, se.getCause());
        assertEquals("server internal", se.getCause().getMessage());
    }

    /** Plain unchecked faults (NPE with and without message, IAE). */
    @Test
    void uncheckedFaultsRebuildTyped() throws Exception {
        Throwable npe = roundTrip(new NullPointerException());
        assertInstanceOf(NullPointerException.class, npe);
        assertNull(npe.getMessage());

        Throwable npe2 = roundTrip(new NullPointerException("was null"));
        assertEquals("was null", npe2.getMessage());

        Throwable iae = roundTrip(new IllegalArgumentException("bad arg",
                new IllegalStateException("state")));
        assertInstanceOf(IllegalArgumentException.class, iae);
        assertEquals("bad arg", iae.getMessage());
        assertInstanceOf(IllegalStateException.class, iae.getCause());
    }

    /**
     * The fail-loud posture type: UnsupportedConstraintException must survive
     * the carrier with its exact type and message (it has the (String) ctor
     * shape).
     */
    @Test
    void unsupportedConstraintExceptionRebuildsTyped() throws Exception {
        Throwable rebuilt = roundTrip(new UnsupportedConstraintException(
                "cannot satisfy unfulfilled constraint: Integrity.YES"));
        UnsupportedConstraintException uce =
                assertInstanceOf(UnsupportedConstraintException.class, rebuilt);
        assertEquals("cannot satisfy unfulfilled constraint: Integrity.YES",
                uce.getMessage());
    }

    /**
     * Stack trace frames survive capture/rebuild in their four canonical
     * components (declaringClass, methodName, fileName, lineNumber -- the
     * STD-006 sec.7.6 {@code StackTraceElement} shape; the JDK-9+
     * classLoaderName/moduleName decorations do not travel, matching the atomic
     * layer's {@code StackTraceElementSerializer}).
     */
    @Test
    void stackTraceSurvives() throws Exception {
        Exception fault = new Exception("with stack");
        StackTraceElement[] original = fault.getStackTrace();
        assertTrue(original.length > 0);

        Throwable rebuilt = roundTrip(fault);

        StackTraceElement[] frames = rebuilt.getStackTrace();
        assertEquals(original.length, frames.length);
        for (int i = 0; i < frames.length; i++) {
            assertEquals(original[i].getClassName(), frames[i].getClassName());
            assertEquals(original[i].getMethodName(), frames[i].getMethodName());
            assertEquals(original[i].getFileName(), frames[i].getFileName());
            assertEquals(original[i].getLineNumber(), frames[i].getLineNumber());
        }
    }

    /** Suppressed exceptions survive, recursively. */
    @Test
    void suppressedSurvive() throws Exception {
        Exception fault = new Exception("primary");
        fault.addSuppressed(new IllegalStateException("sup-1"));
        fault.addSuppressed(new RuntimeException("sup-2", new Exception("sup-2-cause")));

        Throwable rebuilt = roundTrip(fault);

        Throwable[] sup = rebuilt.getSuppressed();
        assertEquals(2, sup.length);
        assertInstanceOf(IllegalStateException.class, sup[0]);
        assertEquals("sup-1", sup[0].getMessage());
        assertInstanceOf(RuntimeException.class, sup[1]);
        assertEquals("sup-2-cause", sup[1].getCause().getMessage());
    }

    /**
     * The JDK cause == this self-sentinel (an exception whose cause was never
     * initialised): getCause() maps it to null, so the sentinel never enters the
     * form and the rebuilt throwable's cause is unset.
     */
    @Test
    void selfSentinelCauseStaysNull() throws Exception {
        Exception fault = new Exception("no cause set"); // cause field == this (sentinel)
        assertNull(fault.getCause());

        Throwable rebuilt = roundTrip(fault);

        assertNull(rebuilt.getCause(),
                "the self-sentinel must never travel; rebuilt cause stays null");
    }

    // ---------------------------------------------------------------- ceilings

    private static Exception chainOfDepth(int depth) {
        Exception t = new Exception("depth-" + depth);
        for (int i = depth - 1; i >= 0; i--) {
            t = new Exception("depth-" + i, t);
        }
        return t; // t is depth 0; deepest cause is depth `depth`
    }

    private static int chainDepth(Throwable t) {
        int d = 0;
        for (Throwable c = t.getCause(); c != null; c = c.getCause()) d++;
        return d;
    }

    /**
     * Fencepost pair: a cause chain whose deepest node sits exactly AT the
     * ceiling round-trips intact; one deeper is truncated with an explicit
     * marker (never silently, never by abort).
     */
    @Test
    void causeDepthCeilingFencepost() throws Exception {
        // AT the ceiling: nodes at depth 0..MAX_CAUSE_DEPTH-1 are captured in
        // full; the node AT depth MAX_CAUSE_DEPTH is the marker boundary, so the
        // deepest fully-preserved chain has MAX_CAUSE_DEPTH-1 edges... verify the
        // exact boundary semantics both sides.
        Exception atCeiling = chainOfDepth(DerThrowableForm.MAX_CAUSE_DEPTH - 1);
        Throwable rebuiltAt = roundTrip(atCeiling);
        assertEquals(DerThrowableForm.MAX_CAUSE_DEPTH - 1, chainDepth(rebuiltAt));
        // walk to the deepest node: it must be pristine (no marker)
        Throwable deepest = rebuiltAt;
        while (deepest.getCause() != null) deepest = deepest.getCause();
        assertEquals("depth-" + (DerThrowableForm.MAX_CAUSE_DEPTH - 1),
                deepest.getMessage(), "at-ceiling chain must be untruncated");

        // ONE OVER: the node at depth MAX_CAUSE_DEPTH becomes the marker node --
        // class name preserved, message marker-prefixed, chain ends there.
        Exception oneOver = chainOfDepth(DerThrowableForm.MAX_CAUSE_DEPTH);
        Throwable rebuiltOver = roundTrip(oneOver);
        assertEquals(DerThrowableForm.MAX_CAUSE_DEPTH, chainDepth(rebuiltOver),
                "truncation replaces the boundary node with a marker, retaining its position");
        Throwable boundary = rebuiltOver;
        while (boundary.getCause() != null) boundary = boundary.getCause();
        assertInstanceOf(Exception.class, boundary,
                "the marker preserves the boundary throwable's class");
        assertNotNull(boundary.getMessage());
        assertTrue(boundary.getMessage().contains("truncated at depth "
                        + DerThrowableForm.MAX_CAUSE_DEPTH),
                "loss must be explicit -- marker missing: " + boundary.getMessage());
        assertTrue(boundary.getMessage().contains("depth-" + DerThrowableForm.MAX_CAUSE_DEPTH),
                "the boundary node's original message must be preserved in the marker");
    }

    /** Over-ceiling suppressed lists keep the first entries plus an explicit marker. */
    @Test
    void suppressedCeilingTruncatesWithMarker() throws Exception {
        Exception fault = new Exception("many suppressed");
        for (int i = 0; i < DerThrowableForm.MAX_SUPPRESSED + 5; i++) {
            fault.addSuppressed(new RuntimeException("sup-" + i));
        }

        Throwable rebuilt = roundTrip(fault);

        Throwable[] sup = rebuilt.getSuppressed();
        assertEquals(DerThrowableForm.MAX_SUPPRESSED, sup.length,
                "kept list is capped at MAX_SUPPRESSED including the marker");
        assertEquals("sup-0", sup[0].getMessage());
        Throwable last = sup[sup.length - 1];
        assertTrue(last.getMessage().contains("suppressed exception(s) dropped"),
                "loss must be explicit -- marker missing: " + last.getMessage());
    }

    /** Over-ceiling stacks keep the first frames plus an explicit marker frame. */
    @Test
    void stackFrameCeilingTruncatesWithMarker() throws Exception {
        Exception fault = new Exception("deep stack");
        StackTraceElement[] big = new StackTraceElement[DerThrowableForm.MAX_STACK_FRAMES + 10];
        for (int i = 0; i < big.length; i++) {
            big[i] = new StackTraceElement("C" + i, "m" + i, "F.java", i);
        }
        fault.setStackTrace(big);

        Throwable rebuilt = roundTrip(fault);

        StackTraceElement[] frames = rebuilt.getStackTrace();
        assertEquals(DerThrowableForm.MAX_STACK_FRAMES, frames.length);
        assertEquals(big[0], frames[0]);
        StackTraceElement markerFrame = frames[frames.length - 1];
        assertEquals("[JGDMS-DER-truncated]", markerFrame.getClassName());
        assertTrue(markerFrame.getMethodName().contains("dropped_11_frames"),
                "marker frame must record the dropped count: " + markerFrame);
    }

    /** A cyclic cause graph (initCause abuse) is truncated with a marker, not looped. */
    @Test
    void cyclicCauseChainTruncatesWithMarker() throws Exception {
        Exception a = new Exception("a");
        Exception b = new Exception("b", a);
        a.initCause(b); // a -> b -> a cycle

        Throwable rebuilt = roundTrip(b);

        assertEquals("b", rebuilt.getMessage());
        assertEquals("a", rebuilt.getCause().getMessage());
        Throwable marker = rebuilt.getCause().getCause();
        assertNotNull(marker, "cycle boundary marker expected");
        assertTrue(marker.getMessage().contains("cyclic cause/suppressed chain truncated"),
                "loss must be explicit -- marker missing: " + marker.getMessage());
        assertNull(marker.getCause(), "the cycle must be cut");
    }

    // ---------------------------------------------------------------- structural fit

    /**
     * CONSISTENCY PIN: the carrier's capture-side depth ceiling must fit inside
     * the DER codec's structural nesting bound with margin for the leaf node's
     * stack-frame elements (each tree level consumes one codec nesting level;
     * the leaf's stack frames consume one more). If MAX_NESTING ever shrinks or
     * MAX_CAUSE_DEPTH grows past it, faults at the ceiling would fail to encode
     * -- re-introducing the abort this carrier exists to remove.
     */
    @Test
    void causeDepthCeilingFitsCodecNestingBound() {
        assertTrue(DerThrowableForm.MAX_CAUSE_DEPTH + 2 <= ObjectCodec.MAX_NESTING,
                "MAX_CAUSE_DEPTH (" + DerThrowableForm.MAX_CAUSE_DEPTH
                + ") + stack-frame level + margin must fit ObjectCodec.MAX_NESTING ("
                + ObjectCodec.MAX_NESTING + ")");
    }

    /**
     * The worst legal shape ENCODES: a fault at the depth ceiling with a full
     * suppressed load at the leaf still fits the codec nesting bound (proof the
     * ceilings actually protect the fault path from the encoder's own bound).
     */
    @Test
    void worstCaseAtCeilingEncodes() throws Exception {
        Exception deepest = new Exception("leaf");
        deepest.addSuppressed(new RuntimeException("leaf-suppressed"));
        Exception t = deepest;
        for (int i = DerThrowableForm.MAX_CAUSE_DEPTH - 2; i >= 0; i--) {
            t = new Exception("d" + i, t);
        }
        Throwable rebuilt = roundTrip(t);
        assertEquals(DerThrowableForm.MAX_CAUSE_DEPTH - 1, chainDepth(rebuilt));
    }
}
