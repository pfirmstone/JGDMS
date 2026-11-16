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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.jini.core.constraint.MarshallingFormat;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit coverage for Reggie's persistence half of the born-immutable format
 * guards (JGDMS-STD-006 sec.3 item 5; SOW-Entry-ATOMIC-DER-Migration.md
 * sec.4 A3 guard (v)): the marker write/read round trip, the pre-A3
 * absent-marker-means-implicit-JOSS rule, and the fail-closed rejections --
 * unknown version, unknown marker token, and a config/store format
 * contradiction (the in-place format flip the guard exists to refuse).
 */
public class SnapshotFormatGuardTest {

    private static DataInputStream marker(String format) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        SnapshotFormatGuard.writeFormatMarker(new DataOutputStream(bout), format);
        return new DataInputStream(new ByteArrayInputStream(bout.toByteArray()));
    }

    private static DataInputStream empty() {
        return new DataInputStream(new ByteArrayInputStream(new byte[0]));
    }

    @Test
    public void currentVersionRoundTripsJossMarker() throws IOException {
        String written = MarshallingFormat.JOSS.getFormat();
        assertEquals(written, SnapshotFormatGuard.resolveRecoveredFormat(
            marker(written), SnapshotFormatGuard.LOG_VERSION));
    }

    @Test
    public void currentVersionRoundTripsDerMarker() throws IOException {
        String written = MarshallingFormat.ATOMIC_DER.getFormat();
        assertEquals(written, SnapshotFormatGuard.resolveRecoveredFormat(
            marker(written), SnapshotFormatGuard.LOG_VERSION));
    }

    /**
     * The pre-A3 shape (version 3, no marker ever written) resolves as
     * implicit legacy JOSS WITHOUT reading anything from the stream --
     * proven by handing it an empty stream, which would throw EOFException
     * if a read were attempted (STD-006 sec.11.8: an absent old-format
     * marker means implicit legacy JOSS, not a reject and not a read).
     */
    @Test
    public void preA3VersionResolvesImplicitJossWithoutReading() throws IOException {
        assertEquals(MarshallingFormat.JOSS.getFormat(),
            SnapshotFormatGuard.resolveRecoveredFormat(
                empty(), SnapshotFormatGuard.PRE_A3_LOG_VERSION));
    }

    @Test
    public void unknownVersionsAreHardRejects() {
        int[] bad = { 0, 1, 2, SnapshotFormatGuard.LOG_VERSION + 1, -1, Integer.MAX_VALUE };
        for (int version : bad) {
            try {
                SnapshotFormatGuard.resolveRecoveredFormat(empty(), version);
                fail("version " + version + " must be rejected");
            } catch (IOException expected) {
            }
        }
    }

    @Test
    public void matchingFormatsPassTheGuard() throws IOException {
        SnapshotFormatGuard.checkFormat(
            MarshallingFormat.JOSS.getFormat(), MarshallingFormat.JOSS);
        SnapshotFormatGuard.checkFormat(
            MarshallingFormat.ATOMIC_DER.getFormat(), MarshallingFormat.ATOMIC_DER);
    }

    /** Guard (ii): a JOSS-born snapshot at a DER-configured instance is refused. */
    @Test(expected = IOException.class)
    public void jossSnapshotAtDerInstanceIsRefused() throws IOException {
        SnapshotFormatGuard.checkFormat(
            MarshallingFormat.JOSS.getFormat(), MarshallingFormat.ATOMIC_DER);
    }

    /** And the mirror image: a DER-born snapshot at a JOSS-configured instance. */
    @Test(expected = IOException.class)
    public void derSnapshotAtJossInstanceIsRefused() throws IOException {
        SnapshotFormatGuard.checkFormat(
            MarshallingFormat.ATOMIC_DER.getFormat(), MarshallingFormat.JOSS);
    }

    /** Fail-closed on ambiguity: an unrecognized marker token is corruption. */
    @Test(expected = IOException.class)
    public void unrecognizedMarkerTokenIsRefused() throws IOException {
        SnapshotFormatGuard.checkFormat("SOME-FUTURE-FORMAT", MarshallingFormat.JOSS);
    }

    /** A pre-A3 (version 3) snapshot recovers cleanly at a JOSS instance... */
    @Test
    public void preA3SnapshotRecoversAtJossInstance() throws IOException {
        SnapshotFormatGuard.checkFormat(
            SnapshotFormatGuard.resolveRecoveredFormat(
                empty(), SnapshotFormatGuard.PRE_A3_LOG_VERSION),
            MarshallingFormat.JOSS);
    }

    /** ...and is refused, via the guard (not a blunt version check), at a DER one. */
    @Test(expected = IOException.class)
    public void preA3SnapshotRefusedAtDerInstance() throws IOException {
        SnapshotFormatGuard.checkFormat(
            SnapshotFormatGuard.resolveRecoveredFormat(
                empty(), SnapshotFormatGuard.PRE_A3_LOG_VERSION),
            MarshallingFormat.ATOMIC_DER);
    }

    /** An unconfigured (null) format must never be silently persisted. */
    @Test(expected = NullPointerException.class)
    public void nullFormatMarkerIsNeverWritten() throws IOException {
        SnapshotFormatGuard.writeFormatMarker(
            new DataOutputStream(new ByteArrayOutputStream()), null);
    }

    /** The full write-then-recover shape RegistrarImpl actually performs. */
    @Test
    public void writtenSnapshotHeaderRecoversAtSameConfig() throws IOException {
        for (MarshallingFormat fmt : new MarshallingFormat[]{
                MarshallingFormat.JOSS, MarshallingFormat.ATOMIC_DER }) {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bout);
            out.writeInt(SnapshotFormatGuard.LOG_VERSION);
            SnapshotFormatGuard.writeFormatMarker(out, fmt.getFormat());
            DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(bout.toByteArray()));
            int version = in.readInt();
            String recovered = SnapshotFormatGuard.resolveRecoveredFormat(in, version);
            SnapshotFormatGuard.checkFormat(recovered, fmt);
        }
    }
}
