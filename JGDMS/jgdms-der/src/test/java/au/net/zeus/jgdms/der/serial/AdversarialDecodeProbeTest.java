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
package au.net.zeus.jgdms.der.serial;

import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.apache.river.api.io.DerThrowableForm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Adversarial-decode negative suite for {@link DerThrowableForm} (review gate,
 * fix/der-throwable-marshalling; board guidance G3/G13: run the adversarial
 * input against the built classes, don't reason about the bound).
 *
 * <p>The carrier's positive tests all go through {@code capture()}, which
 * truncates at the ceilings -- so a conforming encoder can never produce the
 * over-ceiling input the {@code (GetArg)} constructor's fail-secure rejections
 * exist for. This suite builds NON-CONFORMING and HOSTILE carriers via
 * reflection (the only way to forge them from Java), encodes them onto the real
 * DER object stream, and asserts:
 * <ul>
 *   <li>the per-node ceiling rejections (maxStackFrames / maxSuppressed /
 *       className bounds / null-element screens) actually fire on DECODE;</li>
 *   <li>a carrier naming a non-{@code Throwable} class decodes as inert data
 *       and is rejected at the trusted-seam rebuild BEFORE any constructor of
 *       the named class runs;</li>
 *   <li>an unresolvable class name surfaces loudly as
 *       {@code ClassNotFoundException};</li>
 *   <li>hostile cause-chain depth past the capture ceiling is bounded by the
 *       codec {@code MAX_NESTING} fence, failing cleanly (never a
 *       {@code StackOverflowError}).</li>
 * </ul>
 */
class AdversarialDecodeProbeTest {

    // ---------------------------------------------------------------- helpers

    /** Reflectively invokes the private canonical ctor of DerThrowableForm. */
    private static DerThrowableForm form(String className, String message,
            Object stack, Object suppressed, DerThrowableForm cause) throws Exception {
        Class<?> stes = Class.forName("org.apache.river.api.io.StackTraceElementSerializer");
        Class<?> stesArr = Array.newInstance(stes, 0).getClass();
        Constructor<DerThrowableForm> c = DerThrowableForm.class.getDeclaredConstructor(
                String.class, String.class, stesArr,
                DerThrowableForm[].class, DerThrowableForm.class);
        c.setAccessible(true);
        return c.newInstance(className, message,
                stack != null ? stack : Array.newInstance(stes, 0),
                suppressed != null ? suppressed : new DerThrowableForm[0], cause);
    }

    /** Steals a real StackTraceElementSerializer from a captured fault. */
    private static Object oneFrame() throws Exception {
        DerThrowableForm f = DerThrowableForm.capture(new Exception("frame donor"));
        Field stackField = DerThrowableForm.class.getDeclaredField("stack");
        stackField.setAccessible(true);
        Object arr = stackField.get(f);
        assertTrue(Array.getLength(arr) > 0, "donor must have frames");
        return Array.get(arr, 0);
    }

    private static byte[] encode(Object o) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(baos)) {
            out.writeObject(o);
            out.flush();
        }
        return baos.toByteArray();
    }

    private static Object decode(byte[] bytes) throws Exception {
        try (DerMarshalInputStream in =
                new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    // ---------------------------------------------------------------- probes

    /** Over-ceiling stack frame count on the WIRE is rejected fail-secure by check(). */
    @Test
    void overCeilingStackFramesRejectedOnDecode() throws Exception {
        Object frame = oneFrame();
        Class<?> stes = frame.getClass();
        int n = DerThrowableForm.MAX_STACK_FRAMES + 1;
        Object big = Array.newInstance(stes, n);
        for (int i = 0; i < n; i++) Array.set(big, i, frame);
        byte[] wire = encode(form("java.lang.Exception", "hostile stack", big, null, null));
        Exception e = assertThrows(Exception.class, () -> decode(wire),
                "decode must reject " + n + " frames");
        assertTrue(rootChain(e).contains("maxStackFrames")
                        || rootChain(e).contains("stack frame count"),
                "expected the maxStackFrames rejection, got: " + rootChain(e));
    }

    /** Over-ceiling suppressed count on the WIRE is rejected fail-secure by check(). */
    @Test
    void overCeilingSuppressedRejectedOnDecode() throws Exception {
        int n = DerThrowableForm.MAX_SUPPRESSED + 1;
        DerThrowableForm[] sup = new DerThrowableForm[n];
        for (int i = 0; i < n; i++) {
            sup[i] = form("java.lang.Exception", "sup-" + i, null, null, null);
        }
        byte[] wire = encode(form("java.lang.Exception", "hostile suppressed", null, sup, null));
        Exception e = assertThrows(Exception.class, () -> decode(wire),
                "decode must reject " + n + " suppressed");
        assertTrue(rootChain(e).contains("maxSuppressed")
                        || rootChain(e).contains("suppressed count"),
                "expected the maxSuppressed rejection, got: " + rootChain(e));
    }

    /** Empty className on the WIRE is rejected fail-secure by check(). */
    @Test
    void emptyClassNameRejectedOnDecode() throws Exception {
        byte[] wire = encode(form("", "no name", null, null, null));
        Exception e = assertThrows(Exception.class, () -> decode(wire));
        assertTrue(rootChain(e).contains("className length out of bounds"),
                "expected the className bound rejection, got: " + rootChain(e));
    }

    /** Oversize className (>2048) on the WIRE is rejected fail-secure by check(). */
    @Test
    void oversizeClassNameRejectedOnDecode() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2049; i++) sb.append('a');
        byte[] wire = encode(form(sb.toString(), "long name", null, null, null));
        Exception e = assertThrows(Exception.class, () -> decode(wire));
        assertTrue(rootChain(e).contains("className length out of bounds"),
                "expected the className bound rejection, got: " + rootChain(e));
    }

    /**
     * A carrier naming a NON-Throwable class decodes (inert data) but the
     * trusted-seam rebuild rejects it BEFORE any constructor of the named class
     * can run -- and the class must not be initialized by the probe resolution.
     */
    @Test
    void nonThrowableClassNameFailsClosedAtRebuild() throws Exception {
        byte[] wire = encode(form("java.util.HashMap", "not a throwable", null, null, null));
        Object decoded = decode(wire);
        DerThrowableForm carrier = assertInstanceOf(DerThrowableForm.class, decoded,
                "the decoder must yield the inert carrier");
        InvalidObjectException e = assertThrows(InvalidObjectException.class,
                () -> carrier.toThrowable(getClass().getClassLoader()));
        assertTrue(e.getMessage().contains("not a Throwable"), e.getMessage());
    }

    /** Side-effecting non-Throwable ctor bait: rejection happens before instantiation. */
    public static class NotAThrowableBait {
        public static volatile boolean constructed = false;
        public NotAThrowableBait(String message, Throwable cause) { constructed = true; }
        public NotAThrowableBait(String message) { constructed = true; }
        public NotAThrowableBait() { constructed = true; }
    }

    @Test
    void nonThrowableCtorNeverRuns() throws Exception {
        byte[] wire = encode(form(NotAThrowableBait.class.getName(), "bait", null, null, null));
        DerThrowableForm carrier = (DerThrowableForm) decode(wire);
        assertThrows(InvalidObjectException.class,
                () -> carrier.toThrowable(getClass().getClassLoader()));
        assertTrue(!NotAThrowableBait.constructed,
                "a non-Throwable's constructor must NEVER run from a hostile carrier");
    }

    /** A missing class surfaces loudly as ClassNotFoundException at the seam. */
    @Test
    void unresolvableClassNameSurfacesLoudly() throws Exception {
        byte[] wire = encode(form("no.such.pkg.NoSuchFault", "gone", null, null, null));
        DerThrowableForm carrier = (DerThrowableForm) decode(wire);
        assertThrows(ClassNotFoundException.class,
                () -> carrier.toThrowable(getClass().getClassLoader()));
    }

    /**
     * DOCUMENTED ASYMMETRY: decode does not re-count cause depth (delegated to
     * the codec nesting fence). A hostile chain deeper than the capture ceiling
     * but within the codec bound is ACCEPTED; one past the codec bound must fail
     * CLEANLY (DerException-wrapped, never StackOverflowError).
     */
    @Test
    void hostileDeepChainBoundedByCodecFence() throws Exception {
        // Depth 13 (over MAX_CAUSE_DEPTH=12, within codec MAX_NESTING=16): accepted.
        DerThrowableForm chain = form("java.lang.Exception", "d13", null, null, null);
        for (int i = 12; i >= 0; i--) {
            chain = form("java.lang.Exception", "d" + i, null, null, chain);
        }
        Object decoded = decode(encode(chain));
        assertNotNull(decoded);
        assertInstanceOf(DerThrowableForm.class, decoded);

        // Depth far past the codec fence: the ENCODER's own nesting guard must
        // refuse cleanly (a conforming encoder can never emit it); decode-side
        // deep-nesting rejection is pinned by the codec's own suites.
        DerThrowableForm deep = form("java.lang.Exception", "leaf", null, null, null);
        for (int i = 0; i < 40; i++) {
            deep = form("java.lang.Exception", "n" + i, null, null, deep);
        }
        final DerThrowableForm f = deep;
        try {
            byte[] wire = encode(f);
            // If the encoder admitted it, decode must still fail cleanly.
            Exception e = assertThrows(Exception.class, () -> decode(wire));
            assertTrue(rootChain(e).contains("MAX_NESTING")
                    || rootChain(e).contains("nesting"), rootChain(e));
        } catch (IOException expected) {
            assertTrue(rootChain(expected).contains("MAX_NESTING")
                            || rootChain(expected).contains("nesting"),
                    "expected a clean nesting refusal, got: " + rootChain(expected));
        } catch (StackOverflowError soe) {
            fail("nesting guard missing: StackOverflowError on encode");
        }
    }

    /** null suppressed ELEMENT on the wire is rejected (fail-secure null screen). */
    @Test
    void nullSuppressedElementRejectedOnDecode() throws Exception {
        DerThrowableForm[] sup = new DerThrowableForm[]{null};
        byte[] wire = encode(form("java.lang.Exception", "null sup", null, sup, null));
        Exception e = assertThrows(Exception.class, () -> decode(wire));
        assertTrue(rootChain(e).contains("null suppressed element")
                        || rootChain(e).contains("null"),
                "expected null-element rejection, got: " + rootChain(e));
    }

    private static String rootChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getClass().getName()).append(": ").append(c.getMessage()).append(" | ");
        }
        return sb.toString();
    }
}
