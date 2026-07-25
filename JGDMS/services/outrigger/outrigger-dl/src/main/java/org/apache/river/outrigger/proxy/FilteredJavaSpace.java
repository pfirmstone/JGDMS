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
package org.apache.river.outrigger.proxy;

import java.rmi.RemoteException;
import java.util.Collection;
import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.io.MarshalledInstance;

/**
 * Client-facing extension of the JavaSpaces API that attaches a CEL predicate
 * filter to Outrigger operations (JGDMS-STD-011 / SOW Part&nbsp;B). The
 * {@link SpaceProxy2} proxy implements this interface in addition to
 * {@code net.jini.space.TupleSpace}; a client that wants filtered semantics
 * casts its space proxy to {@code FilteredJavaSpace}.
 *
 * <h3>The filter is an explicit operation parameter</h3>
 * Every method here takes a {@code byte[] filter} — a canonical
 * {@link FilterEnvelope} produced by the client authoring adapter
 * ({@code EntryFilter.compile}). The filter is a genuine operation parameter,
 * <b>never</b> a field smuggled onto the template {@code EntryRep}. This makes
 * version skew a <em>loud break</em>: a proxy from an older server that does not
 * implement these methods simply cannot be cast to {@code FilteredJavaSpace},
 * and the corresponding {@link OutriggerServer} overloads are absent, so a
 * filtered call fails outright rather than silently running unfiltered and
 * over-returning.
 *
 * <h3>Semantics</h3>
 * The filter <em>narrows</em> the result of the ordinary template match: an
 * entry is returned/notified only if it matches the template <b>and</b> the CEL
 * predicate accepts it. The predicate is evaluated server-side over the
 * candidate's schema-projected DER field values; no entry class is loaded on
 * the server. Field names in the predicate are resolved against the candidate's
 * own v2 schema (applicability key: {@code entrySchemaDigest}); a candidate
 * whose schema lacks a referenced field, or whose bytes fail canonical decode,
 * is treated as a non-match (fail-closed).
 *
 * <p>A {@code null} filter is not valid on these methods — a caller that wants
 * an unfiltered query uses the ordinary {@code net.jini.space.JavaSpace} /
 * {@code TupleSpace} methods.
 *
 * <h3>Rejection is loud</h3>
 * If the filter cannot be admitted — a malformed envelope, a CEL record that
 * fails decode/cost/type verification, a {@code Transform} where a
 * {@code Predicate} is required, or (until unit&nbsp;B3 lands) an operation
 * whose match chokepoint does not yet evaluate filters — the operation fails
 * with {@link FilterRejectedException}. A refused filter is never downgraded to
 * an unfiltered query.
 *
 * @since JGDMS 4.0.0
 */
public interface FilteredJavaSpace {

    /**
     * Filtered variant of {@code JavaSpace.read}.
     *
     * @param tmpl    the query template (may be {@code null} for match-any)
     * @param txn     the transaction, or {@code null}
     * @param timeout the maximum wait, in milliseconds
     * @param filter  a canonical {@link FilterEnvelope} (must not be {@code null})
     * @return a matching entry that also satisfies the filter, or {@code null}
     *         if none became available before the timeout
     * @throws FilterRejectedException if the filter is refused (loud; never an
     *         unfiltered fallback)
     */
    Entry read(Entry tmpl, Transaction txn, long timeout, byte[] filter)
            throws UnusableEntryException, TransactionException,
                   InterruptedException, RemoteException, FilterRejectedException;

    /**
     * Filtered variant of {@code JavaSpace.readIfExists}.
     * @see #read(Entry, Transaction, long, byte[])
     */
    Entry readIfExists(Entry tmpl, Transaction txn, long timeout, byte[] filter)
            throws UnusableEntryException, TransactionException,
                   InterruptedException, RemoteException, FilterRejectedException;

    /**
     * Filtered variant of {@code JavaSpace.take}.
     * @see #read(Entry, Transaction, long, byte[])
     */
    Entry take(Entry tmpl, Transaction txn, long timeout, byte[] filter)
            throws UnusableEntryException, TransactionException,
                   InterruptedException, RemoteException, FilterRejectedException;

    /**
     * Filtered variant of {@code JavaSpace.takeIfExists}.
     * @see #read(Entry, Transaction, long, byte[])
     */
    Entry takeIfExists(Entry tmpl, Transaction txn, long timeout, byte[] filter)
            throws UnusableEntryException, TransactionException,
                   InterruptedException, RemoteException, FilterRejectedException;

    /**
     * Filtered variant of {@code TupleSpace.notify}: the listener is notified
     * only for writes that match {@code tmpl} <b>and</b> satisfy the filter.
     *
     * @param tmpl     the registration template (may be {@code null})
     * @param txn      the transaction, or {@code null}
     * @param listener the remote event listener
     * @param lease    the requested registration lease, in milliseconds
     * @param handback an event handback, or {@code null}
     * @param filter   a canonical {@link FilterEnvelope} (must not be {@code null})
     * @return the event registration
     * @throws FilterRejectedException if the filter is refused
     */
    EventRegistration notify(Entry tmpl, Transaction txn,
                             RemoteEventListener listener, long lease,
                             MarshalledInstance handback, byte[] filter)
            throws TransactionException, RemoteException, FilterRejectedException;

    /**
     * Filtered variant of {@code TupleSpace.registerForAvailabilityEvent}: an
     * availability event fires only for entries that match one of {@code tmpls}
     * <b>and</b> satisfy the filter. The one filter is admitted against every
     * template's schema; if it fails to type-check against any of them, the
     * registration is refused.
     *
     * @param tmpls          the registration templates
     * @param txn            the transaction, or {@code null}
     * @param visibilityOnly {@code true} for visibility-only events
     * @param listener       the remote event listener
     * @param leaseDuration  the requested registration lease, in milliseconds
     * @param handback       an event handback, or {@code null}
     * @param filter         a canonical {@link FilterEnvelope} (must not be {@code null})
     * @return the event registration
     * @throws FilterRejectedException if the filter is refused
     */
    EventRegistration registerForAvailabilityEvent(
            Collection tmpls, Transaction txn, boolean visibilityOnly,
            RemoteEventListener listener, long leaseDuration,
            MarshalledInstance handback, byte[] filter)
            throws TransactionException, RemoteException, FilterRejectedException;
}
