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

/**
 * The common surface, shared by the two standing-event-registration watcher
 * hierarchies ({@link EventRegistrationWatcher} and
 * {@link AvailabilityRegistrationWatcher}), for their server-side CEL filtering
 * (SOW Part&nbsp;B, unit&nbsp;B3). A registration carries two related pieces of
 * filter state:
 *
 * <ul>
 *   <li>the compiled, verified {@link FilterSet} that {@code process()} consults
 *       to gate each delivery (site&nbsp;E) — the runtime predicate; and</li>
 *   <li>the raw, opaque {@code byte[]} filter <em>envelope</em> that the
 *       registration was admitted from — the <b>durable seed</b> persisted in the
 *       log so that recovery can rebuild the {@link FilterSet} by re-admitting it
 *       (re-verifying against each recovered template), fail-closed.</li>
 * </ul>
 *
 * <p>Recovery ({@code OutriggerServerImpl.recoverRegister}) works against this
 * interface so it can install a rebuilt {@link FilterSet} without knowing which
 * concrete watcher hierarchy it holds. Both parents already carry an identical
 * {@code filters}/{@code setFilters} pair; this interface adds the durable
 * envelope accessor/mutator and unifies the two under one type.
 *
 * @since JGDMS 4.0.0
 */
interface FilteredWatcher {

    /**
     * @return the compiled CEL {@link FilterSet} gating this registration
     *         ({@link FilterSet#EMPTY} if unfiltered)
     */
    FilterSet filters();

    /**
     * Install the compiled CEL {@link FilterSet} gating this registration's
     * deliveries. {@code null}/EMPTY leaves it unfiltered.
     *
     * @param filters the compiled filter set (or {@code null}/EMPTY for unfiltered)
     */
    void setFilters(FilterSet filters);

    /**
     * The raw, opaque canonical {@code FilterEnvelope} bytes this registration was
     * admitted from, persisted so recovery can rebuild the {@link FilterSet} by
     * re-admission. {@code null} for an unfiltered registration.
     *
     * @return a copy of the durable filter envelope, or {@code null} if unfiltered
     */
    byte[] filterEnvelope();

    /**
     * Record the durable filter envelope (a defensive copy is retained).
     * {@code null} leaves the registration unfiltered (no envelope persisted).
     *
     * @param filterEnvelope the opaque canonical envelope bytes, or {@code null}
     */
    void setFilterEnvelope(byte[] filterEnvelope);
}
