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

package net.jini.io.context;

import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.ObjectInputValidation;

/**
 * A context element (an element of the collection returned by
 * {@link net.jini.io.ObjectStreamContext#getObjectStreamContext()}) that lets an
 * object being deserialized register a callback to be run when the enclosing
 * <em>decode unit</em> has finished deserializing.
 *
 * <p>A decode unit is one self-contained unmarshalling scope &mdash; e.g. the
 * arguments of one remote call, or the result of one remote call &mdash; which may
 * comprise several top-level {@code readObject} reads. This is the neutral,
 * wire-format-independent analogue of {@link java.io.ObjectInputStream#registerValidation
 * ObjectInputStream.registerValidation}: it lets an {@code @AtomicSerial}
 * constructor schedule post-graph work without reaching into the underlying
 * {@code java.io.ObjectInputStream}, so the same code works on both the JOSS/atomic
 * and the DER wire paths.
 *
 * <p>Its primary use is client-side distributed garbage collection: a live
 * remote-reference proxy registers a batch-completion callback here so that all
 * references received in one decode unit are coalesced into a single {@code dirty}
 * call to the object's owner, issued when the decode unit completes (JGDMS-STD-008
 * &sect;6; SRC&nbsp;RR-116 &sect;2.1). The deserializer guarantees the callback runs
 * <em>before</em> the decode unit is acknowledged, preserving the RR-116
 * transmit-race invariant.
 *
 * <p>The element instance also serves as the per-decode-unit <em>identity</em>:
 * exactly one {@code DeserializationCompletion} exists per decode unit, so a
 * consumer that batches per unit may key on the element itself.
 *
 * @author peter
 * @see net.jini.io.ObjectStreamContext
 * @see IntegrityEnforcement
 */
public interface DeserializationCompletion {

    /**
     * Registers {@code action} to be run when the current decode unit's object
     * graph has been fully deserialized. Callbacks are run in order of decreasing
     * {@code priority} (highest first), mirroring
     * {@link java.io.ObjectInputStream#registerValidation
     * ObjectInputStream.registerValidation}; the deserializer runs them once,
     * before the decode unit is acknowledged.
     *
     * @param action   the callback to run on decode-unit completion
     * @param priority the run-order priority; higher priorities run first
     * @throws NotActiveException     if there is no decode unit currently in progress
     * @throws InvalidObjectException if {@code action} is {@code null}
     */
    void registerCompletion(ObjectInputValidation action, int priority)
            throws NotActiveException, InvalidObjectException;
}
