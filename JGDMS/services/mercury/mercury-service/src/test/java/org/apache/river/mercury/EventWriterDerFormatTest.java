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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import net.jini.core.event.RemoteEvent;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicMarshalledInstance;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * Tests for the {@link EventWriter#write} DER write site (commit 221fe251c,
 * JGDMS task #23 coverage).
 *
 * <h2>REGRESSION FOUND: {@code EventWriter.write} cannot succeed for any
 * {@code RemoteEvent} -- commit 221fe251c broke mercury's persistent event
 * log</h2>
 * <p>{@link #writeFailsBecauseRemoteEventSourceFieldIsUnsupportedByDer}
 * documents (and pins) a real, currently-live bug: commit 221fe251c changed
 * {@code EventWriter.write} to wrap the whole {@link RemoteEvent} argument in
 * a {@code MarshalledInstance} tagged {@code MarshallingFormat.ATOMIC_DER}.
 * {@code RemoteEvent.serialForm()} (in {@code jgdms-platform}, outside this
 * task's {@code JGDMS/services/mercury/} scope) declares its {@code source}
 * field as raw {@code java.lang.Object}:
 * <pre>    new SerialForm("source", Object.class)</pre>
 * The DER schema generator ({@code au.net.zeus.jgdms.der.schema
 * .SchemaGenerator#javaTypeToWireType}) explicitly rejects a field declared
 * as literal {@code Object.class} -- <em>every</em> other type it accepts
 * (primitive, {@code String}, {@code byte[]}, a declared interface/abstract
 * type as a bounded polymorphic slot, a registered {@code DerReplacer}, or a
 * concrete {@code @AtomicSerial} type) can still generate a static wire
 * schema; a field genuinely typed {@code Object} cannot. So <em>every</em>
 * call to {@code EventWriter.write(RemoteEvent, LogOutputStream)} throws
 * {@code IOException} ("DER encoding failed: SchemaGenerator: unsupported
 * serial field type java.lang.Object in class
 * net.jini.core.event.RemoteEvent"), regardless of the event's actual
 * concrete class or its source object's actual runtime type -- this is a
 * static, unconditional schema-generation failure, not a data-dependent one.
 *
 * <p>{@code PersistentEventLog.add(RemoteEvent)} (this module,
 * {@code PersistentEventLog.java}) calls {@code eventWriter.write(event,
 * out)} directly and propagates the {@code IOException}: this is mercury's
 * core mailbox event-persistence path. Commit 221fe251c's own message notes
 * its round-trip probes covered "StorableReference/StorableObject/
 * JoinState" (outrigger/mahalo/norm) -- it does not claim to have exercised
 * {@code EventWriter.write} against real production code, and this test
 * shows why: it cannot have passed.
 *
 * <p><b>This is intentionally left failing-if-fixed-incorrectly, not
 * papered over</b>: {@link #writeFailsBecauseRemoteEventSourceFieldIsUnsupportedByDer}
 * asserts today's actual (broken) behavior so the suite stays green while
 * faithfully recording the regression for a follow-up fix -- most likely
 * either (a) giving {@code RemoteEvent.source} a narrower declared type
 * DER's schema generator can handle (e.g. an interface bound, mirroring how
 * {@code EventID.source} is written out as a nested
 * {@code MarshalledInstance.class}-typed field rather than raw
 * {@code Object.class}), or (b) reverting {@code EventWriter.write} to keep
 * using {@link AtomicMarshalledInstance} (JOSS) for the outer envelope,
 * matching what this test's other method demonstrates still works and still
 * decodes correctly through the unchanged, dual-read-capable
 * {@link EventReader#read}. Fixing {@code RemoteEvent.java} itself is out of
 * this task's {@code JGDMS/services/mercury/} scope (it lives in
 * {@code jgdms-platform}); fixing {@code EventWriter.java} would be a
 * production code change beyond "add test coverage" and is left for the
 * codebase owner's call.
 *
 * <p>{@link #legacyJossEncodedEventStillDecodesViaEventReader} independently
 * confirms {@link EventReader#read}'s dual-read contract still holds for
 * pre-migration (JOSS-encoded) log entries, using exactly the encoding shape
 * {@code EventWriter.write} used before commit 221fe251c -- i.e. even though
 * new writes are currently broken, old persisted data already on disk would
 * still be readable.
 *
 * <p><b>Merge-prep note, re-verified 2026-07-21 against trunk tip
 * {@code a9c0a6f6c} (post-rebase of this branch):</b> trunk commit
 * {@code eae74ce08} ("Route RemoteEvent.source ... through the STD-006 Any
 * form") landed a real fix for exactly this regression -- it added an
 * explicit {@code raw == Object.class -> return ANY;} intercept inside
 * {@code SchemaGenerator.deriveFieldWireType}'s non-collection branch, plus
 * matching decode-side support and a mercury
 * {@code EventWriterReaderDerRoundTripTest}. Trunk commit {@code 474a323d2}
 * ("docs: reconcile SOW A3 row ...", an unrelated Reggie/A3 commit) then
 * reverted that entire fix as accidental collateral -- diffing
 * {@code 474a323d2} shows it removing exactly the 18 lines {@code eae74ce08}
 * added to {@code SchemaGenerator.java}, plus deleting
 * {@code RemoteEventAnySourceFieldTest.java},
 * {@code ObjectTypedSerialFieldReachTest.java}, mercury's
 * {@code EventWriterReaderDerRoundTripTest.java}, and
 * {@code docs/SOW-RemoteEvent-Source-DER-Encoding.md}.
 *
 * <p>A later merge-prep pass on this branch (2026-07-20) hypothesized that
 * this revert was a stale finding -- since superseded by trunk commits
 * {@code 483a0d448} ("DER: capture proxy raw-wire-form ..."),
 * {@code a87fe4259} ("Update JoinManagerImpl.java"), and {@code 6f40d96e2}
 * ("Remote event no longer serializes MarshalledObject") -- and that the
 * Any-form fix was genuinely back. <b>That hypothesis was checked fresh for
 * this rebase and does not hold.</b> {@code 6f40d96e2} only drops the
 * deprecated {@code handback}/{@code MarshalledObject} entry from
 * {@code RemoteEvent.serialForm()} (a real, separate improvement -- confirmed
 * present, {@code serialForm()} is down to 4 entries); it does not touch
 * {@code source}'s {@code Object.class} declaration or
 * {@code SchemaGenerator} at all. {@code 483a0d448} and {@code a87fe4259}
 * touch invocation-handler wire-form capture and {@code JoinManagerImpl}
 * respectively -- neither file is {@code SchemaGenerator.java} or
 * {@code RemoteEvent.java}. {@code git log 61a43c8bf..a9c0a6f6c -- .../
 * schema/SchemaGenerator.java .../event/RemoteEvent.java} returns empty:
 * <b>no commit between the revert and the current trunk tip touches either
 * file</b>. {@code git show trunk:.../SchemaGenerator.java} still has no
 * {@code raw == Object.class} branch in {@code deriveFieldWireType}, so a
 * bare {@code Object.class} field still falls through to
 * {@code toWireType(Class,...)}'s final {@code throw new DerException(
 * "unsupported serial field type " + ...)}. Confirmed by actually running
 * this test class on the DirtyChai SM-capable JDK against the rebased tip:
 * {@code writeFailsBecauseRemoteEventSourceFieldIsUnsupportedByDer} still
 * passes (i.e. {@code EventWriter.write} still throws), not by static
 * reading alone.
 *
 * <p>So {@code writeFailsBecauseRemoteEventSourceFieldIsUnsupportedByDer}
 * below is <b>still accurate against trunk's actual current state</b> and
 * must <b>not</b> be "fixed" to expect success without first re-confirming
 * whether the Any-form fix is back on trunk. Re-run, against the current
 * trunk tip: {@code git show trunk:JGDMS/jgdms-der/src/main/java/au/net/
 * zeus/jgdms/der/schema/SchemaGenerator.java} and look for
 * {@code deriveFieldWireType}'s non-collection branch -- it must contain an
 * explicit {@code if (raw == Object.class) return ANY;} (or equivalent)
 * BEFORE the fall-through to {@code toWireType(raw, declaring)} for the fix
 * to actually be in effect. A commit merely touching
 * {@code RemoteEvent.java} or citing {@code eae74ce08}/{@code abf863fdf} in
 * its message is not sufficient evidence on its own -- confirm the specific
 * {@code SchemaGenerator.java} hunk survived, and re-run this test class to
 * see the pinned assertion actually flip before rewriting it.
 *
 * <p>Both tests require {@code jgdms-der} on the test runtime classpath
 * (declared test-scope in this module's pom.xml). The dual-read test
 * additionally requires a SecurityManager-capable JDK (e.g. DirtyChai) to
 * run: {@link AtomicMarshalledInstance}'s JOSS input stream performs a
 * {@code SerializablePermission} subclass check via
 * {@code AccessController.doPrivileged}/{@code Permission.checkGuard}, which
 * throws {@code SecurityException: checking permissions is not supported}
 * on a vanilla (SecurityManager-removed, JEP 486) JDK 24+ such as the
 * system default {@code java-25-openjdk}. Run with e.g.
 * {@code JAVA_HOME=.../DirtyChai/build/*&#47;images/jdk} and
 * {@code -Djava.security.manager=allow}.
 */
public class EventWriterDerFormatTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ── the regression: EventWriter.write always fails post-221fe251c ───────

    @Test
    public void writeFailsBecauseRemoteEventSourceFieldIsUnsupportedByDer() throws Exception {
        DerFixtures.Payload source = new DerFixtures.Payload("event-source");
        MarshalledInstance handback = new MarshalledInstance(new DerFixtures.Payload("handback"));
        RemoteEvent event = new RemoteEvent(source, 1L, 100L, handback);

        File logFile = tmp.newFile("mercury-event-writer-der-regression.log");
        StreamKey key = new StreamKey(logFile, StreamType.OUTPUT);

        EventWriter writer = new EventWriter();
        try (LogOutputStream out = new LogOutputStream(logFile, key, false)) {
            writer.write(event, out);
            fail("EventWriter.write must currently fail: RemoteEvent.serialForm() "
                    + "declares 'source' as raw Object.class, which the DER schema "
                    + "generator rejects unconditionally. If this assertion starts "
                    + "failing, RemoteEvent/EventWriter has been fixed -- update "
                    + "this test to a genuine round-trip assertion instead.");
        } catch (IOException expected) {
            assertTrue("expected the DER schema-generation failure, got: " + expected,
                    expected.getMessage() != null
                    && expected.getMessage().contains("unsupported serial field type")
                    && expected.getMessage().contains("java.lang.Object"));
        }
    }

    // ── dual-read: EventReader still decodes pre-migration JOSS data ────────

    @Test
    public void legacyJossEncodedEventStillDecodesViaEventReader() throws Exception {
        DerFixtures.Payload source = new DerFixtures.Payload("legacy-event-source");
        MarshalledInstance handback = new MarshalledInstance(new DerFixtures.Payload("legacy-handback"));
        RemoteEvent original = new RemoteEvent(source, 2L, 200L, handback);

        // Exactly the pre-221fe251c EventWriter.write shape: wrap the whole
        // RemoteEvent in the legacy JOSS AtomicMarshalledInstance, not a
        // DER-tagged MarshalledInstance.
        AtomicMarshalledInstance mi = new AtomicMarshalledInstance(original);

        File logFile = tmp.newFile("mercury-event-reader-legacy-joss.log");
        StreamKey key = new StreamKey(logFile, StreamType.OUTPUT);
        try (LogOutputStream out = new LogOutputStream(logFile, key, false)) {
            // Mirror EventWriter's own EventOutputStream shape closely enough
            // to be read back by the real EventReader: a bare ObjectOutputStream
            // writing a single MarshalledInstance, no stream header (EventReader
            // relies on EventOutputStream/EventInputStream suppressing headers so
            // consecutive log records concatenate without object-stream framing
            // overhead; a single-record file works with a plain ObjectOutputStream
            // too since EventReader's EventInputStream reads exactly one object).
            try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(out) {
                @Override
                protected void writeStreamHeader() throws IOException {
                    // no-op, matching EventWriter.EventOutputStream
                }
            }) {
                oos.writeObject(mi);
                oos.flush();
            }
            // Not calling out.sync(): fsync-to-disk durability is orthogonal
            // to what this test proves (the bytes are readable once flushed
            // and the stream is closed), and fsync can fail in sandboxed/
            // container filesystems unrelated to the JOSS/DER logic under
            // test.
        }

        EventReader reader = new EventReader();
        RemoteEvent decoded;
        try (FileInputStream in = new FileInputStream(logFile)) {
            decoded = reader.read(in);
        }

        assertNotNull("EventReader must still decode a legacy JOSS-encoded RemoteEvent",
                decoded);
        assertEquals(source, decoded.getSource());
        assertEquals(2L, decoded.getID());
        assertEquals(200L, decoded.getSequenceNumber());
    }
}
