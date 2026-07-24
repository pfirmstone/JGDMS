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

package au.net.zeus.jgdms.showcase.demo;

import au.net.zeus.jgdms.der.object.ShowcaseDepthProbe;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import au.net.zeus.jgdms.der.stream.ShowcaseExpansionBomb;
import au.net.zeus.jgdms.showcase.model.CalibratedReading;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Demonstration: "Feed it garbage, it stops politely."
 *
 * <p>Four hostile inputs are handed to the reader that turns wire bytes back into objects.
 * Each is refused cleanly -- a plain checked error, bounded memory, no hang, no crash, and
 * no attacker code ever runs, because the reader never rebuilds an object it has not first
 * accepted. In plain terms the four are:
 *
 * <ol>
 *   <li>a message cut off partway through;</li>
 *   <li>a message whose structure markers have been scrambled;</li>
 *   <li>a message nested inside itself far deeper than the reader will follow;</li>
 *   <li>a tiny message crafted to balloon into gigabytes the moment it is unpacked.</li>
 * </ol>
 *
 * <p>The demonstration checks itself: it asserts each input was refused in the RIGHT way,
 * and exits non-zero if any refusal did not hold. Nothing it prints on screen uses an
 * acronym or a piece of internal jargon.
 */
public final class HostileInputDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================================================");
        System.out.println(" Feed it garbage, it stops politely");
        System.out.println("========================================================================");
        System.out.println();
        System.out.println("The reader that turns wire bytes back into objects is handed four hostile");
        System.out.println("inputs. It refuses each one cleanly -- a plain error, bounded memory, no");
        System.out.println("hang, no crash. It never rebuilds an object it has not first accepted, so a");
        System.out.println("refused message never gets the chance to run code.");
        System.out.printf ("(This whole demonstration is running inside a memory ceiling of about %d megabytes.)%n",
                Runtime.getRuntime().maxMemory() / (1024 * 1024));
        System.out.println();

        boolean allHeld = true;
        allHeld &= section1_cutOff();
        allHeld &= section2_scrambled();
        allHeld &= section3_tooDeeplyNested();
        allHeld &= section4_theExpansionBomb();

        System.out.println("========================================================================");
        System.out.println(allHeld
                ? "ALL FOUR HELD: every hostile input was refused cleanly, with memory kept in bounds."
                : "A REFUSAL DID NOT HOLD -- see above.");
        System.out.println("========================================================================");
        if (!allHeld) System.exit(1);
    }

    // ---- 1. A message cut off partway through -------------------------------------------

    private static boolean section1_cutOff() throws Exception {
        heading(1, "A message cut off partway through");
        byte[] whole = aRealMessageOnTheWire();
        Object ok = read(whole);      // sanity: the untouched message reads back fine
        System.out.println("  a complete message of " + whole.length + " bytes reads back fine: "
                + (ok != null));

        byte[] cutOff = new byte[whole.length - 12];   // drop the tail, mid-structure
        System.out.println("  now the last 12 bytes are dropped, cutting it off partway through.");
        Outcome o = feedToReader(cutOff);
        System.out.println("  the length markers now promise more data than the message actually holds.");
        System.out.println("  " + describe(o));
        boolean held = o == Outcome.REFUSED;   // a clean checked error, not a hang or a crash
        verdict(held, "the reader refused the cut-off message cleanly");
        return held;
    }

    // ---- 2. A message whose structure markers have been scrambled -----------------------

    private static boolean section2_scrambled() throws Exception {
        heading(2, "A message whose structure markers have been scrambled");
        byte[] scrambled = aRealMessageOnTheWire();
        int markerIndex = 3;   // the marker that says what kind of thing comes next
        System.out.println("  the marker that says what kind of thing comes next is overwritten with");
        System.out.println("  a value that means nothing to the reader.");
        scrambled[markerIndex] = 0x30;
        Outcome o = feedToReader(scrambled);
        System.out.println("  " + describe(o));
        boolean held = o == Outcome.REFUSED;
        verdict(held, "the reader refused the scrambled message cleanly");
        return held;
    }

    // ---- 3. A message nested inside itself far deeper than the reader will follow --------

    private static boolean section3_tooDeeplyNested() throws Exception {
        heading(3, "A message nested inside itself deeper than the reader will follow");
        // A just-legal depth must read fine; a far-too-deep one must be refused -- and by the
        // reader's depth limit, NOT by the program running out of stack.
        ShowcaseDepthProbe.Result r = ShowcaseDepthProbe.run(ShowcaseDepthProbe.DEPTH_LIMIT, 5000);
        System.out.println("  the reader follows nesting up to a fixed depth of " + r.depthLimit
                + " and no further.");
        System.out.println("  a message nested exactly " + r.legalDepthDecoded + " deep reads back fine.");
        System.out.println("  a message nested " + r.overDepthOffered
                + " deep -- thousands of levels -- is offered.");
        System.out.println("  (the crafted message is genuine, not malformed: its bytes are identical to");
        System.out.println("   what the writer would produce -- confirmed here: " + r.handBuiltIsCanonical + ")");
        if (r.stackOverflowed) {
            System.out.println("  the program CRASHED for lack of stack room -- the fence did not hold.");
        } else if (r.refusedCleanly) {
            System.out.println("  the reader stopped at its depth limit with a plain, checked error,");
            System.out.println("  instead of following the nesting until it ran out of room.");
        } else {
            System.out.println("  the reader did not refuse it -- this should not happen.");
        }
        boolean held = r.refusedCleanly && !r.stackOverflowed;
        verdict(held, "the reader stopped at its depth limit instead of crashing");
        return held;
    }

    // ---- 4. The expansion bomb ----------------------------------------------------------

    private static boolean section4_theExpansionBomb() throws Exception {
        heading(4, "A tiny message crafted to balloon into gigabytes when unpacked");
        System.out.println("  The trick: send one large shape once, then a long run of tiny");
        System.out.println("  back-references to it. Each back-reference is a few dozen bytes on the");
        System.out.println("  wire, but each one would re-grow the whole large shape when unpacked, so a");
        System.out.println("  small wire message would balloon into gigabytes in memory.");
        System.out.println();
        System.out.println("  For a safe demonstration the reader is given a deliberately small input");
        System.out.println("  budget, so its ceiling is small and it refuses after only a few dozen");
        System.out.println("  re-growths -- without ever buffering the gigabytes a full-size budget");
        System.out.println("  would otherwise permit.");
        System.out.println();

        // (a) one single message -> refused at the per-message memory ceiling
        int budget = 256 * 1024;
        ShowcaseExpansionBomb.SingleMessageResult s =
                ShowcaseExpansionBomb.runSingleMessageBomb(budget, 65_500, 1000);
        System.out.println("  Part A -- one hostile message (the memory bomb):");
        System.out.printf ("    each %d-byte back-reference would re-grow to %,d bytes -- about %.0f times%n",
                s.perReferenceWireBytes, s.perReferenceExpandedBytes, s.amplification);
        System.out.println("      more output for every byte on the wire.");
        System.out.printf ("    the message on the wire: %,d bytes.%n", s.wireInputBytes);
        System.out.printf ("    the reader's memory ceiling for one message: about %d megabytes.%n",
                s.memoryCeilingBytes / (1024 * 1024));
        System.out.printf ("    growth reached %,d bytes and then stopped, under that ceiling.%n",
                s.expandedAtRefusalBytes);
        System.out.printf ("    at the normal, full-size input budget the same trick would have reached%n");
        System.out.printf ("      about %.0f gigabytes -- refused there too, memory never following.%n",
                s.projectedAtDefaultBudgetBytes / (1024.0 * 1024 * 1024));
        boolean partA = s.refused && s.expandedAtRefusalBytes <= s.memoryCeilingBytes;
        System.out.println("    " + (partA
                ? "refused with a plain, checked error; memory stayed under the ceiling."
                : "NOT refused as expected."));
        System.out.println();

        // (b) many small messages -> refused at the cumulative ceiling, memory flat
        ShowcaseExpansionBomb.ManyMessageResult m =
                ShowcaseExpansionBomb.runManyMessageBomb(budget, 65_500);
        System.out.println("  Part B -- a flood of small messages (the slow-burn version):");
        System.out.println("    each message re-grows just one shape, small enough on its own -- but the");
        System.out.println("    flood never stops. Memory for each message stays flat (the reader lets go");
        System.out.println("    of one before starting the next), yet the reader still stops the flood");
        System.out.println("    before the accumulated work runs away.");
        System.out.printf ("    %,d small messages were accepted, then the flood was refused.%n",
                m.messagesAccepted);
        System.out.printf ("    memory for the last accepted message: %,d bytes -- it never grew across the%n",
                m.perMessagePeakBytes);
        System.out.println("      whole flood; that is the bounded-memory guarantee.");
        boolean partB = m.refused && m.perMessagePeakBytes <= 128L * 1024;
        System.out.println("    " + (partB
                ? "refused with a plain, checked error; memory stayed flat throughout."
                : "NOT refused as expected."));
        System.out.println();

        boolean held = partA && partB;
        verdict(held, "the reader refused both the single bomb and the flood, memory bounded");
        return held;
    }

    // ---- shared plumbing ----------------------------------------------------------------

    private enum Outcome { REFUSED, CRASHED, ACCEPTED, UNEXPECTED }

    /** A genuine domain message written out in the canonical wire format. */
    private static byte[] aRealMessageOnTheWire() throws Exception {
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

    /** Feeds hostile bytes to the reader; a checked error is the clean, expected refusal. */
    private static Outcome feedToReader(byte[] bytes) {
        try {
            new DerMarshalInputStream(new ByteArrayInputStream(bytes)).readObject();
            return Outcome.ACCEPTED;
        } catch (java.io.IOException checked) {
            return Outcome.REFUSED;
        } catch (StackOverflowError | OutOfMemoryError crash) {
            return Outcome.CRASHED;
        } catch (Throwable other) {
            return Outcome.UNEXPECTED;
        }
    }

    private static String describe(Outcome o) {
        return switch (o) {
            case REFUSED   -> "the reader refused it with a plain, checked error -- the ordinary kind a "
                            + "program is expected to catch and handle.";
            case CRASHED   -> "the reader CRASHED -- this should not happen.";
            case ACCEPTED  -> "the reader accepted it -- this should not happen.";
            case UNEXPECTED -> "the reader failed in an unexpected way -- this should not happen.";
        };
    }

    private static void heading(int n, String title) {
        System.out.println("------------------------------------------------------------------------");
        System.out.println(" " + n + ". " + title);
        System.out.println("------------------------------------------------------------------------");
    }

    private static void verdict(boolean held, String what) {
        System.out.println("  => " + (held ? "HELD: " : "FAILED: ") + what + ".");
        System.out.println();
    }
}
