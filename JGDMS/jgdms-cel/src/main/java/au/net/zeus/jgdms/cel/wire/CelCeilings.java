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

package au.net.zeus.jgdms.cel.wire;

import au.net.zeus.jgdms.der.object.ObjectCodec;

/**
 * The pinned ceilings of JGDMS-STD-011 §10.3 / Appendix B §B.7, [PROPOSED]
 * values adopted verbatim by this decoder (ratifying them is Peter's/the
 * board's call, not this module's -- see the report's spec-contradiction
 * notes for the one place a numeral had to be chosen rather than merely
 * copied). All ceilings use the inclusive fencepost convention: a value
 * exactly equal to the ceiling is accepted; the ceiling plus one is rejected.
 */
public final class CelCeilings {

    private CelCeilings() {}

    /** AST node count across one decode: every literal, operator, call, selector step, and list element. */
    public static final int MAX_EXPR_NODES = 1024;

    /** AST depth = maximum evaluation stack depth. The root {@code ExprNode} counts as depth 1. */
    public static final int MAX_EXPR_DEPTH = 32;

    /** Maximum selector-chain length of a single {@code FIELD_REF} node. */
    public static final int MAX_SELECTOR_STEPS = 16;

    /**
     * The per-scalar (string UTF-8 / bytes) content-octet ceiling for
     * {@code LIT_STRING}/{@code LIT_BYTES} literals. Reuses STD-006 §4.5's
     * {@code maxCollection} numeral by the same deliberate choice Appendix B
     * §B.7 makes (their product is exactly 2^32, the number STD-011 §10.6's
     * checked-arithmetic-width rule is built around) -- sourced from {@link
     * ObjectCodec#MAX_COLLECTION} rather than restated, so the two ceilings
     * cannot drift apart silently.
     */
    public static final int MAX_SCALAR_BYTES = ObjectCodec.MAX_COLLECTION;

    /** STD-006 §4.5's collection-size ceiling, reused directly for {@code ListLitNode} element counts (Appendix B §B.3). */
    public static final int MAX_COLLECTION = ObjectCodec.MAX_COLLECTION;

    /** {@code SelectorStep.unqual} / {@code QualifiedSelector.fieldName} ceiling -- aligned with STD-006 §7.8's {@code AtomicSerialFieldDef.wireName}. */
    public static final int MAX_UNQUAL_NAME_BYTES = 255;

    /** {@code QualifiedSelector.className} ceiling -- aligned with STD-006 §7.8's {@code AtomicSerialFieldDef.wireType}. */
    public static final int MAX_CLASS_NAME_BYTES = 1024;

    /** Verification-time (T6) ceiling on the computed static cost {@code C(E)} -- see {@code au.net.zeus.jgdms.cel.cost.CostModel}. */
    public static final long MAX_EXPR_COST = 1_000_000L;
}
