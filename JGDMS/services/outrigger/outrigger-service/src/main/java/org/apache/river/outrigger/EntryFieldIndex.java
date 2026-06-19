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
/*
 * The candidate-selection algorithm implemented here (a per-field hash-bucket
 * index, "most-selective field" query heuristic, and empty-bucket early-abort)
 * is derived from the entry-matching design of Blitz JavaSpaces by Dan Creswell:
 *   org.dancres.blitz.entry.ci.{CacheIndexerImpl, SimpleCacheLines, CacheLine}
 * (C) Copyright 2003, 2006 Dan Creswell, licensed under the Blitz BSD License v1.0.
 * Origin: https://github.com/dancres/blitzjavaspaces
 * Re-expressed here over Outrigger's EntryHandle / EntryRep, where the per-field
 * hash is net.jini.io.MarshalledInstance.hashCode() (0 for a null field) and a
 * null template field is a wildcard.
 */
package org.apache.river.outrigger;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.river.outrigger.proxy.EntryRep;

/**
 * A per-field hash-bucket index over the {@link EntryHandle}s held by a single
 * {@link EntryHolder} (i.e. a single exact entry class).  It exists to narrow the
 * set of candidates a template query must examine, replacing the linear scan of
 * the holder's contents with a lookup of the most-selective constrained field.
 * <p>
 * <b>How it relates to matching.</b> This index is purely a <i>candidate filter</i>;
 * it never decides a match.  Every handle it returns is still subjected to the full
 * field-by-field {@link EntryRep#matches} test (and the existing transaction /
 * availability checks) by the caller.  The index only guarantees the converse:
 * <i>every entry that truly matches a constrained template is returned</i>.  That
 * guarantee rests on three properties of Outrigger's representation, all verified
 * against current source:
 * <ul>
 *   <li><b>Stable field offsets.</b> {@code EntryRep} orders fields canonically with
 *       superclass fields before subclass fields (see {@code EntryRep.FieldComparator}:
 *       "super before subclass, alphabetical within a class").  Therefore a template
 *       of a <i>superclass</i>, which has fewer fields, has its constrained fields at
 *       the same offsets {@code 0..M-1} as in any subclass entry held here.  The query
 *       iterates only the template's field count, so shorter templates are handled
 *       naturally.</li>
 *   <li><b>Hash consistent with equality.</b> The per-field hash is
 *       {@link EntryHandle#hashForField}, i.e. {@code MarshalledInstance.hashCode()}
 *       (or 0 for a null field).  Two field values that are {@code equals} (the test
 *       {@code EntryRep.matches} uses) necessarily hash equal, so a matching entry is
 *       always in the bucket the query looks up.  Hash <i>collisions</i> merely admit
 *       extra candidates, which {@code matches} then rejects &mdash; they cannot cause
 *       a missed match.</li>
 *   <li><b>Null fields.</b> A field that was {@code null} when the entry was written
 *       hashes to 0, so it is bucketed under 0 and is only ever a candidate for a
 *       template constraining that field to a value that also hashes to 0.</li>
 * </ul>
 * <p>
 * <b>Concurrency.</b> The index is lock-free: field-lines and buckets are
 * {@link ConcurrentHashMap}-based and the buckets are concurrent sets, so
 * {@link #insert}, {@link #remove} and {@link #candidates} never block each other
 * and impose no new lock on the holder's hot path.  {@link EntryHolder} calls
 * {@code insert}/{@code remove} from inside its existing per-handle synchronization
 * as it mutates {@code content}, so the index stays in step with the holder.
 * <p>
 * Empty buckets are deliberately <i>not</i> pruned: lock-free pruning would race a
 * concurrent insert into the same bucket and could drop a live entry (a missed
 * match).  A stale empty bucket is harmless &mdash; {@link #candidates} treats an
 * empty bucket exactly like an absent one (early-abort) &mdash; it only costs a
 * little memory, refunded as soon as the value recurs.
 *
 * @see EntryHolder
 * @see EntryHandle#hashForField
 */
final class EntryFieldIndex {

    /**
     * Field offset -&gt; (field hash -&gt; set of handles whose field at that offset
     * has that hash).  Lines and buckets are created on demand; all entries in a
     * holder share the same field count, so the offsets converge to {@code 0..N-1}.
     * (Creswell: {@code CacheLines} / {@code SimpleCacheLines} / {@code CacheLine}.)
     */
    private final ConcurrentMap<Integer, ConcurrentMap<Long, Set<EntryHandle>>> lines =
        new ConcurrentHashMap<Integer, ConcurrentMap<Long, Set<EntryHandle>>>();

    private static Set<EntryHandle> newBucket() {
        return Collections.newSetFromMap(new ConcurrentHashMap<EntryHandle, Boolean>());
    }

    /**
     * Index a handle under the hash of each of its fields.  Called once per handle
     * as it is added to the holder.  Adding the same handle twice is a no-op (the
     * buckets are sets).
     */
    void insert(EntryHandle handle) {
        final EntryRep rep = handle.rep();
        final int n = rep.numFields();
        for (int i = 0; i < n; i++) {
            final Long key = EntryHandle.hashForField(rep, i);
            ConcurrentMap<Long, Set<EntryHandle>> line = lines.get(i);
            if (line == null) {
                line = new ConcurrentHashMap<Long, Set<EntryHandle>>();
                final ConcurrentMap<Long, Set<EntryHandle>> race = lines.putIfAbsent(i, line);
                if (race != null) line = race;
            }
            Set<EntryHandle> bucket = line.get(key);
            if (bucket == null) {
                bucket = newBucket();
                final Set<EntryHandle> race = line.putIfAbsent(key, bucket);
                if (race != null) bucket = race;
            }
            bucket.add(handle);
        }
    }

    /**
     * Remove a handle from every bucket it occupies.  The handle's rep is immutable,
     * so its field hashes are recomputed here rather than cached.  Empty buckets are
     * left in place (see class comment).
     */
    void remove(EntryHandle handle) {
        final EntryRep rep = handle.rep();
        final int n = rep.numFields();
        for (int i = 0; i < n; i++) {
            final ConcurrentMap<Long, Set<EntryHandle>> line = lines.get(i);
            if (line == null) continue;
            final Set<EntryHandle> bucket = line.get(EntryHandle.hashForField(rep, i));
            if (bucket != null) bucket.remove(handle);
        }
    }

    /** The outcome of a candidate lookup for a template. */
    static final class CandidateSet {
        /**
         * {@code true} when a constrained template field hashed to an empty bucket:
         * no entry can match, and the caller may return without scanning anything.
         */
        final boolean earlyAbort;
        /**
         * A private snapshot of the candidate handles to confirm with a full match,
         * or {@code null} when the template constrained no indexable field (a pure
         * wildcard) &mdash; in which case the caller must fall back to the full scan
         * of the holder's contents.  Empty (non-null) iff {@code earlyAbort}.
         */
        final Set<EntryHandle> candidates;
        /** The field offset whose bucket was returned, or -1 if none. */
        final int chosenField;

        private CandidateSet(boolean earlyAbort, Set<EntryHandle> candidates, int chosenField) {
            this.earlyAbort = earlyAbort;
            this.candidates = candidates;
            this.chosenField = chosenField;
        }
    }

    private static final CandidateSet ABORT =
        new CandidateSet(true, Collections.<EntryHandle>emptySet(), -1);
    private static final CandidateSet FULL_SCAN =
        new CandidateSet(false, null, -1);

    /**
     * Compute the candidate handles for {@code tmpl}.  Mirrors Creswell's
     * {@code findImpl}: skip wildcard (null) fields; if any constrained field's bucket
     * is empty, early-abort; otherwise return a snapshot of the smallest (most
     * selective) constrained bucket.  If the template constrains no indexable field,
     * returns {@link #FULL_SCAN} (candidates == null) signalling the caller to scan.
     *
     * @param tmpl the template rep; {@code tmpl.value(i) == null} is a wildcard for
     *             field {@code i}.  May have fewer fields than the indexed entries
     *             (a superclass template); only its own fields are considered.
     */
    CandidateSet candidates(EntryRep tmpl) {
        if (lines.isEmpty()) {
            return ABORT;   // nothing indexed yet -> no entry can match
        }
        final int tmplFields = tmpl.numFields();

        int bestOffset = -1, bestSize = Integer.MAX_VALUE;
        Set<EntryHandle> bestBucket = null;
        for (int i = 0; i < tmplFields; i++) {
            if (tmpl.value(i) == null) {
                continue;                                   // wildcard field
            }
            final ConcurrentMap<Long, Set<EntryHandle>> line = lines.get(i);
            final Set<EntryHandle> bucket =
                (line == null) ? null : line.get(EntryHandle.hashForField(tmpl, i));
            final int size = (bucket == null) ? 0 : bucket.size();
            if (size == 0) {
                return ABORT;                               // empty-bucket early-abort
            }
            if (size < bestSize) {                          // "find the smallest index available"
                bestSize = size;
                bestOffset = i;
                bestBucket = bucket;
            }
        }

        if (bestOffset == -1) {
            return FULL_SCAN;                               // pure wildcard -> caller scans
        }
        // Snapshot so the caller can iterate without racing concurrent index mutation.
        return new CandidateSet(false, new LinkedHashSet<EntryHandle>(bestBucket), bestOffset);
    }
}
