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

package au.net.zeus.jgdms.showcase;

import au.net.zeus.jgdms.der.object.ShowcaseDepthProbe;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import au.net.zeus.jgdms.der.stream.ShowcaseExpansionBomb;
import au.net.zeus.jgdms.showcase.demo.HostileInputDemo;
import au.net.zeus.jgdms.showcase.model.CalibratedReading;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated checks for the "feed it garbage, it stops politely" claim. Each hostile input is
 * asserted to be refused in the RIGHT way -- a checked error, no crash -- and, for the
 * expansion bomb, with memory provably bounded. These assertions never rely on the demo's
 * on-screen wording; they check the real behaviour of the reader and the library ceilings.
 */
class HostileInputStopsPolitelyTest {

    /** A genuine domain message written out in the canonical wire format. */
    private static byte[] realMessage() throws Exception {
        CalibratedReading reading = new CalibratedReading(
                1_000_042L, "Station-North", "temperature", "degreesCelsius",
                21.5, "calibration-certificate-2026-0042");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DerMarshalOutputStream out = new DerMarshalOutputStream(buffer);
        out.writeObject(reading);
        out.flush();
        return buffer.toByteArray();
    }

    private static Object read(byte[] bytes) throws Exception {
        return new DerMarshalInputStream(new ByteArrayInputStream(bytes)).readObject();
    }

    @Test
    void theUntouchedMessageReadsBackFine() throws Exception {
        assertNotNull(read(realMessage()), "the intact message must read back to an object");
    }

    @Test
    void truncatedInputIsRefusedWithACheckedError() throws Exception {
        byte[] whole = realMessage();
        byte[] cutOff = new byte[whole.length - 12];
        System.arraycopy(whole, 0, cutOff, 0, cutOff.length);
        // A checked IOException is the clean refusal; a hang or a crash is not.
        assertThrows(IOException.class, () -> read(cutOff),
                "a message cut off partway through must be refused with a checked error");
    }

    @Test
    void malformedStructureIsRefusedWithACheckedError() throws Exception {
        byte[] scrambled = realMessage();
        scrambled[3] = 0x30;   // overwrite the marker that says what kind of value comes next
        assertThrows(IOException.class, () -> read(scrambled),
                "a scrambled structure marker must be refused with a checked error");
    }

    @Test
    void overDeepNestingIsRefusedByTheDepthFence_notAStackOverflow() throws Exception {
        // Just-legal reads fine; far-too-deep is refused -- by the depth limit, not a crash.
        ShowcaseDepthProbe.Result r = ShowcaseDepthProbe.run(ShowcaseDepthProbe.DEPTH_LIMIT, 5000);
        assertTrue(r.handBuiltIsCanonical,
                "the crafted nesting must be byte-identical to the library's own encoding, so the "
                + "refusal is the depth fence and not a malformed-input rejection");
        assertEquals(ShowcaseDepthProbe.DEPTH_LIMIT, r.legalDepthDecoded,
                "a message nested to the limit must decode cleanly");
        assertTrue(r.refusedCleanly, "over-deep nesting must be refused cleanly (naming the depth limit)");
        assertFalse(r.stackOverflowed, "the depth fence must fire BEFORE the stack is exhausted");
    }

    @Test
    void depthFenceFollowsExactlyToTheLimitAndNoFurther() throws Exception {
        // The bytes we build are the canonical encoding at every legal depth (the guarantee the
        // over-deep refusal rests on), and refused one step past the limit.
        for (int depth = 1; depth <= ShowcaseDepthProbe.DEPTH_LIMIT; depth++) {
            assertTrue(ShowcaseDepthProbe.handBuiltMatchesLibrary(depth),
                    "hand-built nesting must equal the library encoding at depth " + depth);
        }
        ShowcaseDepthProbe.Result r = ShowcaseDepthProbe.run(
                ShowcaseDepthProbe.DEPTH_LIMIT, ShowcaseDepthProbe.DEPTH_LIMIT + 1);
        assertTrue(r.refusedCleanly && !r.stackOverflowed,
                "one level past the limit must be refused cleanly");
    }

    @Test
    void expansionBomb_singleMessage_refusedAtTheMemoryCeiling_memoryBounded() throws Exception {
        int budget = 256 * 1024;   // small budget -> small ceiling -> bounded reproduction
        ShowcaseExpansionBomb.SingleMessageResult s =
                ShowcaseExpansionBomb.runSingleMessageBomb(budget, 65_500, 1000);

        assertTrue(s.refused, "the single expansion bomb must be refused at the per-message ceiling");
        assertTrue(s.refusalDetail.contains("maxReconstitutedBytes"),
                "the refusal must name the per-message ceiling: " + s.refusalDetail);
        // Memory bounded: growth stopped at or below the reader's per-message ceiling.
        assertTrue(s.expandedAtRefusalBytes <= s.memoryCeilingBytes,
                "growth (" + s.expandedAtRefusalBytes + ") must stop under the ceiling ("
                + s.memoryCeilingBytes + ")");
        // The amplification is real and large: a tiny wire reference re-grows a whole shape.
        assertTrue(s.amplification > 100,
                "each reference must amplify far above 1x (got " + s.amplification + ")");
        // The wire message is small relative to what it would become unmetered.
        assertTrue(s.projectedAtDefaultBudgetBytes > 1_000_000_000L,
                "at the full-size budget the same trick would reach into the gigabytes (got "
                + s.projectedAtDefaultBudgetBytes + ")");
    }

    @Test
    void expansionBomb_flood_refusedAtTheCumulativeCeiling_perMessageMemoryFlat() throws Exception {
        int budget = 256 * 1024;
        ShowcaseExpansionBomb.ManyMessageResult m =
                ShowcaseExpansionBomb.runManyMessageBomb(budget, 65_500);

        assertTrue(m.refused, "the flood of small messages must be refused at the cumulative ceiling");
        assertTrue(m.refusalDetail.contains("maxStreamReconstitutedBytes"),
                "the refusal must name the cumulative ceiling: " + m.refusalDetail);
        // Many messages went through first -- proving the per-message ceiling did NOT catch this;
        // the separate cumulative ceiling did.
        assertTrue(m.messagesAccepted > 100,
                "many small messages must be accepted before the flood is stopped (got "
                + m.messagesAccepted + ")");
        // Memory bounded: each message re-grows only one shape; the peak never accumulates.
        assertTrue(m.perMessagePeakBytes <= 128L * 1024,
                "per-message memory must stay flat (one shape at a time), got " + m.perMessagePeakBytes);
    }

    @Test
    void demoMainRunsGreen() throws Exception {
        // The demonstration self-checks and exits non-zero on any failure; running its main here
        // (it does not call System.exit on success) confirms all four refusals hold end-to-end.
        HostileInputDemo.main(new String[0]);
    }
}
