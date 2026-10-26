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
package org.apache.river.outrigger;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * Operator-only JDK Flight Recorder event, committed once per
 * {@link FilterEval#matches} invocation that actually runs a client-supplied CEL
 * predicate against an <em>already entitlement-gated</em> candidate (design memo
 * B3 &sect;7 observability, alongside the {@link FilterAdmission} counters).
 *
 * <h3>Why an event in addition to the counters</h3>
 * The {@link FilterAdmission} counters are global-static aggregates; they answer
 * "how many candidates were evaluated / passed / excluded". A per-invocation JFR
 * event additionally gives a <b>timestamped stream</b> that an operator can use to
 * prove the confused-deputy invariant (INV-1) <em>as a mechanism, not an outcome</em>:
 * because {@link FilterEval#matches} is only ever called <b>after</b> the caller's
 * entitlement gate (the confirm window's {@code canPerform} on the capture path; the
 * {@code transition.getTxn()} gate on the fan-out path), a transition under a third
 * party's uncommitted transaction produces <b>no {@code FilterEvaluation} event at
 * all</b> — the predicate never ran against an entry the registrant is not entitled
 * to observe. That "zero events in the window" is the observable evidence the Board's
 * confused-deputy hunt asks for (memo &sect;8.3).
 *
 * <h3>Information-leak discipline (operator-only, memo &sect;7 / &sect;10 item&nbsp;4)</h3>
 * The event carries <b>only operator-safe metadata</b>: the terminal
 * {@link #outcome} tag and the candidate's stored {@code entrySchemaDigest} rendered
 * as {@link #schemaDigestHex}. It deliberately carries <b>no entry field values and
 * no predicate text</b>, so — exactly like the operator-only counters — it is never a
 * side-channel about data a client cannot itself read. An exclusion count/stream is
 * itself a mild info-channel, hence operator-only and never on the client result path.
 *
 * <h3>Zero production cost / zero attack surface</h3>
 * The event is {@link Enabled @Enabled(false)}: unless an operator explicitly turns it
 * on in a JFR recording, {@link Event#shouldCommit()} short-circuits and nothing is
 * built or recorded. It adds no remotely reachable method, no new input, and no
 * authorization decision — it is a passive local diagnostic, inert by default.
 *
 * @since JGDMS 4.0.0
 */
@Name("org.apache.river.outrigger.FilterEvaluation")
@Label("Outrigger CEL Filter Evaluation")
@Category({"JGDMS", "Outrigger", "CEL Filter"})
@Description("One server-side CEL predicate evaluation against an entitlement-gated "
        + "candidate. Operator-only metadata; carries no entry values or predicate text.")
@Enabled(false)
@StackTrace(false)
final class FilterEvaluationEvent extends Event {

    /** Terminal verdict tag; one of {@link FilterEval}'s outcome constants. */
    @Label("Outcome")
    String outcome;

    /** Hex of the candidate's stored {@code entrySchemaDigest} (the applicability key). */
    @Label("Schema Digest (hex)")
    String schemaDigestHex;
}
