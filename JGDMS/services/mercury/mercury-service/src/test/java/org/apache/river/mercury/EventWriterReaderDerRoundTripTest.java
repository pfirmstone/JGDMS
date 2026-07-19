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
package org.apache.river.mercury;

import net.jini.core.event.RemoteEvent;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

import static org.junit.Assert.*;

/**
 * End-to-end {@code EventWriter.write()}/{@code EventReader.read()} DER round-trip test -- SOW
 * {@code docs/SOW-RemoteEvent-Source-DER-Encoding.md} test-plan items 2 and 6, the originally
 * broken path this SOW exists to fix. Lives in {@code org.apache.river.mercury} (same package as
 * the package-private {@code EventWriter}/{@code EventReader}/{@code LogOutputStream}/
 * {@code LogInputStream}/{@code StreamKey}/{@code StreamType}) so it can drive them directly,
 * bypassing {@code PersistentEventLog}'s log-rotation/control-file bookkeeping (irrelevant to
 * this SOW) while still exercising the REAL wire path: {@code EventWriter.write()} wraps the
 * event in a {@code MarshalledInstance(ev, ..., ATOMIC_DER)} exactly as {@code
 * PersistentEventLog.add()} does, and {@code EventReader.read()} dual-reads it back via
 * {@code MarshalledInstance.get(false)} exactly as {@code PersistentEventLog.next()} does.
 *
 * <h2>History</h2>
 * <p>Before this SOW, EVERY {@code EventWriter.write()} call failed unconditionally
 * ({@code RemoteEvent.source: Object.class} was schema-generation-rejected). Immediately after
 * the {@code source} fix, a SEPARATE, independent, pre-existing blocker was found: {@code
 * RemoteEvent} also declared {@code handback: java.rmi.MarshalledObject.class} (never supported
 * by {@code SchemaGenerator}), so {@code EventWriter.write()} was STILL fully broken -- masked
 * because {@code source} threw first. {@code handback} has since been removed from {@code
 * RemoteEvent.serialForm()}/{@code serialize()} (already {@code @Deprecated}, superseded by
 * {@code miHandback}), which resolves that second blocker too. This test is the first one to
 * actually exercise mercury's real {@code EventWriter}/{@code EventReader} API end-to-end.
 */
public class EventWriterReaderDerRoundTripTest {

    private File tempDir;
    private File logFile;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("mercury-der-roundtrip-test").toFile();
        logFile = new File(tempDir, "0.log");
    }

    @After
    public void tearDown() {
        if (logFile != null) logFile.delete();
        if (tempDir != null) tempDir.delete();
    }

    // -------------------------------------------------------------------------
    // Helpers: write ONE event to a fresh log file via the real EventWriter, then read it back
    // via the real EventReader, exactly mirroring PersistentEventLog.add()/next()'s own calls.
    // -------------------------------------------------------------------------

    private void writeOneEvent(RemoteEvent ev) throws IOException {
        StreamKey key = new StreamKey(logFile.getAbsoluteFile(), StreamType.OUTPUT);
        LogOutputStream out = new LogOutputStream(logFile, key, false);
        EventWriter writer = new EventWriter();
        try {
            writer.write(ev, out);
        } finally {
            out.close();
        }
    }

    private RemoteEvent readOneEvent() throws IOException, ClassNotFoundException {
        StreamKey key = new StreamKey(logFile.getAbsoluteFile(), StreamType.INPUT);
        LogInputStream in = new LogInputStream(logFile, key);
        EventReader reader = new EventReader();
        try {
            return reader.read(in);
        } finally {
            in.close();
        }
    }

    // =========================================================================
    // Item 2: EventWriter.write() now succeeds end-to-end for the Integer-source case.
    // Item 6 (scalar leg): EventReader replay of the persisted event succeeds, fully
    // -dl-independent (no class resolution needed for a scalar Any source).
    // =========================================================================

    @Test
    public void integerSource_writeThenRead_succeeds() throws Exception {
        RemoteEvent in = new RemoteEvent(Integer.valueOf(42), 1L, 2L, (MarshalledInstance) null);
        writeOneEvent(in); // this is EXACTLY the call that unconditionally failed before this SOW
        RemoteEvent out = readOneEvent();
        assertEquals(Integer.valueOf(42), out.getSource());
        assertEquals(1L, out.getID());
        assertEquals(2L, out.getSequenceNumber());
    }

    @Test
    public void stringSource_writeThenRead_succeeds() throws Exception {
        RemoteEvent in = new RemoteEvent("mercury-source", 10L, 20L, (MarshalledInstance) null);
        writeOneEvent(in);
        RemoteEvent out = readOneEvent();
        assertEquals("mercury-source", out.getSource());
    }

    // =========================================================================
    // Item 6 (resolvable-proxy leg): a locally-resolvable @AtomicSerial source must succeed.
    // =========================================================================

    /** Minimal local @AtomicSerial fixture -- stands in for a service's own proxy as event source. */
    @AtomicSerial
    public static final class LocalFixture {
        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm("id", int.class) };
        }
        public static void serialize(PutArg arg, LocalFixture f) throws IOException {
            arg.put("id", f.id);
            arg.writeArgs();
        }
        private final int id;
        public LocalFixture(int id) { this.id = id; }
        public LocalFixture(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get("id", 0));
        }
        @Override
        public boolean equals(Object o) {
            return o instanceof LocalFixture && ((LocalFixture) o).id == id;
        }
        @Override
        public int hashCode() { return id; }
    }

    @Test
    public void locallyResolvableAtomicSerialSource_writeThenRead_succeeds() throws Exception {
        LocalFixture source = new LocalFixture(7);
        RemoteEvent in = new RemoteEvent(source, 100L, 200L, (MarshalledInstance) null);
        writeOneEvent(in);
        RemoteEvent out = readOneEvent();
        assertEquals(source, out.getSource());
    }

    // =========================================================================
    // Item 6 (unresolvable leg): a deliberately-unresolvable @AtomicSerial-shaped source (the
    // -dl-gap stand-in) must fail loudly and cleanly -- a ClassNotFoundException-rooted
    // IOException, not log corruption and not a masked "source cannot be null".
    //
    // Technique: persist a REAL, validly-encoded LocalFixture source, then overwrite the
    // embedded class-name UTF8 bytes in place with a same-length name of a class that does not
    // exist anywhere on the classpath. This produces a genuine, correctly-structured DER record
    // naming an unresolvable class -- exactly what mercury's dependency-gap scenario looks like
    // on the wire (a real record, a name the receiver's classpath doesn't have) -- without
      // needing a second JVM/module-boundary to simulate the -dl gap itself (out of this SOW's
    // scope per its own "Explicitly out of scope" section).
    // =========================================================================

    @Test
    public void unresolvableAtomicSerialSource_readFailsLoudlyNotSilently() throws Exception {
        LocalFixture source = new LocalFixture(9);
        RemoteEvent in = new RemoteEvent(source, 1L, 1L, (MarshalledInstance) null);
        writeOneEvent(in);

        String realName = LocalFixture.class.getName();
        // A same-byte-length name of a class that does not exist anywhere on the classpath.
        String bogusName = sameLengthNonExistentClassName(realName);
        assertEquals("test precondition: names must be byte-length-identical for an in-place "
                + "UTF8 substitution to preserve DER TLV lengths",
                realName.getBytes(StandardCharsets.UTF_8).length,
                bogusName.getBytes(StandardCharsets.UTF_8).length);

        corruptClassNameInPlace(logFile, realName, bogusName);

        try {
            readOneEvent();
            fail("expected a ClassNotFoundException-rooted failure reading an unresolvable "
                    + "@AtomicSerial source, got none");
        } catch (ClassNotFoundException expected) {
            // EventReader.read() propagates ClassNotFoundException directly -- this is the
            // loud, clean failure mode the test plan requires.
            assertTrue("must name the bogus (unresolvable) class, not silently succeed",
                    expected.getMessage() == null || !expected.getMessage().contains("cannot be null"));
        } catch (IOException ioe) {
            // Also acceptable per the test plan ("a ClassNotFoundException-rooted IOException"):
            // some decode paths wrap CNFE in an IOException/InvalidObjectException. Must NOT be
            // masked as a generic null-source invariant violation.
            assertFalse("the decode failure must not be masked as a null-source invariant "
                    + "violation; got: " + ioe,
                    ioe.getMessage() != null && ioe.getMessage().contains("source cannot be null"));
            Throwable cause = ioe;
            boolean foundCnfe = false;
            while (cause != null) {
                if (cause instanceof ClassNotFoundException) { foundCnfe = true; break; }
                cause = cause.getCause() == cause ? null : cause.getCause();
            }
            assertTrue("expected a ClassNotFoundException somewhere in the cause chain of: " + ioe,
                    foundCnfe);
        }
    }

    private static String sameLengthNonExistentClassName(String realName) {
        // Same package prefix + same total length, simple class name replaced with a distinct,
        // definitely-nonexistent identifier padded/truncated to match length exactly.
        int lastDot = realName.lastIndexOf('.');
        String pkg = realName.substring(0, lastDot + 1);
        int simpleLen = realName.length() - pkg.length();
        StringBuilder bogus = new StringBuilder("NoSuchClassXYZ0000000000000000000000000000");
        while (bogus.length() < simpleLen) bogus.append('Q');
        String bogusSimple = bogus.substring(0, simpleLen);
        // Ensure it doesn't accidentally collide with the real name.
        assertNotEquals(realName.substring(lastDot + 1), bogusSimple);
        return pkg + bogusSimple;
    }

    /** In-place byte substitution of a UTF8-encoded class name within the persisted log file. */
    private static void corruptClassNameInPlace(File file, String from, String to) throws IOException {
        byte[] content = Files.readAllBytes(file.toPath());
        byte[] fromBytes = from.getBytes(StandardCharsets.UTF_8);
        byte[] toBytes = to.getBytes(StandardCharsets.UTF_8);
        assertEquals(fromBytes.length, toBytes.length);
        int idx = indexOf(content, fromBytes);
        assertTrue("embedded class name '" + from + "' not found in persisted log bytes "
                + "(schema-embedding format may have changed)", idx >= 0);
        System.arraycopy(toBytes, 0, content, idx, toBytes.length);
        Files.write(file.toPath(), content);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
