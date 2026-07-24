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

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;

import java.util.List;

/**
 * Showcase bridge to the library's nesting-depth fence. Lives in
 * {@code au.net.zeus.jgdms.der.object} so it can build a self-nested value BY BYTES and
 * hand it to the package-private canonical-value decoder ({@link AnyCodec#decode}) -- the
 * very code path a real message travels.
 *
 * <p>The construction is trustworthy because it is checked against the library itself:
 * {@link #handBuiltMatchesLibrary(int)} confirms that, for every legal depth, the bytes
 * this class builds are IDENTICAL to the bytes the library's own encoder produces for the
 * equivalent value. So a rejection at an over-legal depth is genuinely the depth fence
 * refusing a well-formed-but-too-deep message, not a decoder tripping over a malformed one.
 */
public final class ShowcaseDepthProbe {

    private ShowcaseDepthProbe() {}

    /** The deepest nesting the reader will follow before it refuses. */
    public static final int DEPTH_LIMIT = ObjectCodec.MAX_NESTING;

    /** Outcome of feeding the reader a message nested deeper than it allows. */
    public static final class Result {
        public final int depthLimit;
        public final int legalDepthDecoded;   // a just-legal depth that decoded cleanly
        public final int overDepthOffered;    // the too-deep depth we fed
        public final boolean refusedCleanly;  // a checked refusal, naming the depth limit
        public final boolean stackOverflowed; // MUST be false: the fence, not a crash
        public final boolean handBuiltIsCanonical; // our bytes == the library's own encoding
        public final String refusalDetail;

        Result(int depthLimit, int legalDepthDecoded, int overDepthOffered,
               boolean refusedCleanly, boolean stackOverflowed,
               boolean handBuiltIsCanonical, String refusalDetail) {
            this.depthLimit = depthLimit;
            this.legalDepthDecoded = legalDepthDecoded;
            this.overDepthOffered = overDepthOffered;
            this.refusedCleanly = refusedCleanly;
            this.stackOverflowed = stackOverflowed;
            this.handBuiltIsCanonical = handBuiltIsCanonical;
            this.refusalDetail = refusalDetail;
        }
    }

    /**
     * Decodes a just-legal depth (must succeed) and then a far-too-deep one (must be
     * refused, cleanly, with no stack-overflow crash even when the offered nesting is huge).
     *
     * @param legalDepth a depth within the limit (decodes cleanly)
     * @param overDepth  a depth past the limit (refused)
     */
    public static Result run(int legalDepth, int overDepth) throws Exception {
        boolean canonical = handBuiltMatchesLibrary(legalDepth);

        // Just-legal: must decode to a real nested value.
        int legalDecoded = -1;
        Object back = decode(nested(legalDepth));
        if (back != null) legalDecoded = legalDepth;

        // Too-deep: the fence must refuse it -- not hang, not crash.
        boolean refused = false;
        boolean stackOverflow = false;
        String detail = "";
        try {
            decode(nested(overDepth));
        } catch (DerException e) {
            refused = e.getMessage() != null && e.getMessage().contains("nesting depth");
            detail = trim(e.getMessage());
        } catch (StackOverflowError so) {
            stackOverflow = true;
            detail = "stack overflow (the fence did NOT hold)";
        }
        return new Result(DEPTH_LIMIT, legalDecoded, overDepth, refused,
                stackOverflow, canonical, detail);
    }

    /**
     * True when the bytes {@link #nested(int)} builds at {@code depth} are byte-for-byte the
     * same as the library's own encoding of the equivalent nested value -- the guarantee that
     * this probe feeds the reader a well-formed message, not a malformed one.
     */
    public static boolean handBuiltMatchesLibrary(int depth) throws DerException {
        byte[] hand = nested(depth);
        byte[] library = AnyCodec.encode(equivalentValue(depth), "showcase", 0);
        return java.util.Arrays.equals(hand, library);
    }

    // ---- construction -------------------------------------------------------------------

    /** One self-describing nested value, {@code depth} levels of "a list holding one thing". */
    private static byte[] nested(int depth) throws DerException {
        byte[] current = AnyCodec.encode(Integer.valueOf(0), "leaf", 0); // innermost: a plain number
        for (int i = 0; i < depth; i++) {
            current = listOfOne(current);
        }
        return current;
    }

    /** One ordered-collection level wrapping a single element (the canonical wire shape). */
    private static byte[] listOfOne(byte[] element) {
        return DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, 31),
                DerWriter.writeSequence(List.of(element)));
    }

    /** The equivalent Java value at a given depth: a list inside a list ... inside a number. */
    private static Object equivalentValue(int depth) {
        Object current = Integer.valueOf(0);
        for (int i = 0; i < depth; i++) {
            java.util.List<Object> list = new java.util.ArrayList<>(1);
            list.add(current);
            current = list;
        }
        return current;
    }

    private static Object decode(byte[] element) throws Exception {
        return AnyCodec.decode(new DerReader(element), 0, null, ResolutionContext.NONE);
    }

    private static String trim(String message) {
        if (message == null) return "(no detail)";
        return message.length() > 140 ? message.substring(0, 140) + "..." : message;
    }
}
