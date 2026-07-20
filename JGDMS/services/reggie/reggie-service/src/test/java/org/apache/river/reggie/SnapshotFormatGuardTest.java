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
package org.apache.river.reggie;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import net.jini.core.constraint.MarshallingFormat;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Recovery-guard tests for {@link SnapshotFormatGuard} (SOW-Entry-ATOMIC-DER
 * -Migration.md sec.4 A3, item 5: the recovery guard mirroring A1's
 * Outrigger snaplogstore {@code BackEnd} guard). Reggie's version is
 * airtight rather than merely graceful-degradation-safe: DER is unreleased,
 * so an absent format marker unconditionally means implicit JOSS, and a
 * recovered format that contradicts the instance's own configuration is
 * always refused.
 *
 * <p>Exercises {@link SnapshotFormatGuard} directly rather than through a
 * full {@link RegistrarImpl} (which has no {@code Configuration}/export
 * harness suitable for unit tests) -- this is exactly why the guard logic
 * was extracted into its own small, standalone class.
 */
public class SnapshotFormatGuardTest {

    private static ByteArrayOutputStream writeVersionOnly(int version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeInt(version);
        out.flush();
        return bytes;
    }

    private static ByteArrayOutputStream writeCurrentVersionWithMarker(String format) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeInt(SnapshotFormatGuard.LOG_VERSION);
        SnapshotFormatGuard.writeFormatMarker(out, format);
        out.flush();
        return bytes;
    }

    private static ObjectInputStream reader(ByteArrayOutputStream written) throws IOException {
        return new ObjectInputStream(new ByteArrayInputStream(written.toByteArray()));
    }

    // ── version constants ────────────────────────────────────────────────

    @Test
    public void currentVersionIsOneMoreThanPreA3Version() {
        assertEquals(SnapshotFormatGuard.PRE_A3_LOG_VERSION + 1, SnapshotFormatGuard.LOG_VERSION);
    }

    // ── writeFormatMarker / resolveRecoveredFormat round trip ───────────

    @Test
    public void currentVersionMarkerRoundTripsForDer() throws Exception {
        ByteArrayOutputStream written = writeCurrentVersionWithMarker(
            MarshallingFormat.ATOMIC_DER.getFormat());
        ObjectInputStream in = reader(written);
        int version = in.readInt();
        assertEquals(SnapshotFormatGuard.LOG_VERSION, version);
        String recovered = SnapshotFormatGuard.resolveRecoveredFormat(in, version);
        assertEquals(MarshallingFormat.ATOMIC_DER.getFormat(), recovered);
    }

    @Test
    public void currentVersionMarkerRoundTripsForJoss() throws Exception {
        ByteArrayOutputStream written = writeCurrentVersionWithMarker(
            MarshallingFormat.JOSS.getFormat());
        ObjectInputStream in = reader(written);
        int version = in.readInt();
        String recovered = SnapshotFormatGuard.resolveRecoveredFormat(in, version);
        assertEquals(MarshallingFormat.JOSS.getFormat(), recovered);
    }

    /**
     * A pre-A3 snapshot never wrote a marker at all -- it predates the
     * born-immutable-format concept entirely -- and must resolve as
     * implicit legacy JOSS (STD-006 sec.11.8 graceful degradation),
     * airtight for Reggie specifically because DER is unreleased: no
     * deployed pre-A3 snapshot can be anything but JOSS.
     */
    @Test
    public void preA3VersionResolvesToImplicitJossWithoutReadingAnyMarker() throws Exception {
        ByteArrayOutputStream written = writeVersionOnly(SnapshotFormatGuard.PRE_A3_LOG_VERSION);
        ObjectInputStream in = reader(written);
        int version = in.readInt();
        assertEquals(SnapshotFormatGuard.PRE_A3_LOG_VERSION, version);
        String recovered = SnapshotFormatGuard.resolveRecoveredFormat(in, version);
        assertEquals(MarshallingFormat.JOSS.getFormat(), recovered);
    }

    /** Any version other than the two recognized ones is a hard reject. */
    @Test
    public void unrecognizedVersionIsHardReject() throws Exception {
        ByteArrayOutputStream written = writeVersionOnly(999);
        ObjectInputStream in = reader(written);
        int version = in.readInt();
        try {
            SnapshotFormatGuard.resolveRecoveredFormat(in, version);
            fail("an unrecognized log format version must be refused, not "
                + "silently interpreted");
        } catch (IOException expected) {
            // expected
        }
    }

    // ── checkFormat guard ────────────────────────────────────────────────

    @Test
    public void checkFormatPassesWhenRecoveredMatchesConfigured() throws Exception {
        SnapshotFormatGuard.checkFormat(
            MarshallingFormat.JOSS.getFormat(), MarshallingFormat.JOSS);
        SnapshotFormatGuard.checkFormat(
            MarshallingFormat.ATOMIC_DER.getFormat(), MarshallingFormat.ATOMIC_DER);
        // no exception: both are legal births
    }

    @Test
    public void checkFormatRefusesJossRecoveredUnderDerConfig() {
        try {
            SnapshotFormatGuard.checkFormat(
                MarshallingFormat.JOSS.getFormat(), MarshallingFormat.ATOMIC_DER);
            fail("a JOSS-born store must be refused by a DER-configured instance");
        } catch (IOException expected) {
            // expected
        }
    }

    @Test
    public void checkFormatRefusesDerRecoveredUnderJossConfig() {
        try {
            SnapshotFormatGuard.checkFormat(
                MarshallingFormat.ATOMIC_DER.getFormat(), MarshallingFormat.JOSS);
            fail("a DER-born store must be refused by a JOSS-configured instance");
        } catch (IOException expected) {
            // expected
        }
    }

    // ── the two acceptance-criterion scenarios from the SOW's A3 row ────

    /**
     * "Absent marker => implicit JOSS" recovery pair, part 1: a legacy
     * pre-A3 snapshot (no marker at all) recovers cleanly as JOSS under a
     * JOSS-configured instance -- no data loss on in-place upgrade to A3
     * code with the pre-existing (JOSS) config.
     */
    @Test
    public void legacyPreA3SnapshotRecoversAsJossUnderJossConfig() throws Exception {
        ByteArrayOutputStream written = writeVersionOnly(SnapshotFormatGuard.PRE_A3_LOG_VERSION);
        ObjectInputStream in = reader(written);
        int version = in.readInt();
        String recovered = SnapshotFormatGuard.resolveRecoveredFormat(in, version);
        // Must not throw: JOSS-configured instance accepts its own format.
        SnapshotFormatGuard.checkFormat(recovered, MarshallingFormat.JOSS);
    }

    /**
     * "Absent marker => implicit JOSS" recovery pair, part 2: the same
     * legacy pre-A3 snapshot is refused by a DER-configured instance --
     * this IS the airtight case the SOW calls out (DER unreleased means no
     * deployed store can legitimately be pre-A3-and-DER, so any such
     * mismatch is refused, never silently accepted).
     */
    @Test
    public void legacyPreA3SnapshotRefusedUnderDerConfig() throws Exception {
        ByteArrayOutputStream written = writeVersionOnly(SnapshotFormatGuard.PRE_A3_LOG_VERSION);
        ObjectInputStream in = reader(written);
        int version = in.readInt();
        String recovered = SnapshotFormatGuard.resolveRecoveredFormat(in, version);
        try {
            SnapshotFormatGuard.checkFormat(recovered, MarshallingFormat.ATOMIC_DER);
            fail("a legacy pre-A3 (implicit JOSS) snapshot must be refused "
                + "by a DER-configured instance");
        } catch (IOException expected) {
            // expected
        }
    }

    /**
     * Format-contradicting current-shape snapshot refused: a snapshot
     * explicitly born DER is refused if the instance is reconfigured to
     * JOSS (an operator attempting to flip useDerForEntries on a populated
     * store -- not a supported migration, per JGDMS-STD-006 sec.3 item 5).
     */
    @Test
    public void formatContradictingSnapshotRefused() throws Exception {
        ByteArrayOutputStream written = writeCurrentVersionWithMarker(
            MarshallingFormat.ATOMIC_DER.getFormat());
        ObjectInputStream in = reader(written);
        int version = in.readInt();
        String recovered = SnapshotFormatGuard.resolveRecoveredFormat(in, version);
        try {
            SnapshotFormatGuard.checkFormat(recovered, MarshallingFormat.JOSS);
            fail("a DER-born snapshot must be refused by a reconfigured "
                + "JOSS instance -- format flips on a populated store are "
                + "not a supported migration");
        } catch (IOException expected) {
            // expected
        }
    }
}
