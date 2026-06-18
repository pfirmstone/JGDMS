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

package au.net.zeus.jgdms.der.stream;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.ObjectInputValidation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.jini.io.context.DeserializationCompletion;

/**
 * Per-decode-unit completion sink for the DER object stream (JGDMS-STD-008 sec.6;
 * SRC&nbsp;RR-116 sec.2.1 transmit-race invariant).
 *
 * <p>The DER codec, unlike {@link java.io.ObjectInputStream}, has no automatic
 * "run validations when the outermost {@code readObject} returns" trigger and no
 * handle table; a decode unit (one remote call's arguments, or one reply's result)
 * may span several top-level {@code readObject} reads. This class is the neutral,
 * wire-format-independent stand-in for {@code ObjectInputStream.registerValidation}:
 * an {@code @AtomicSerial} object being decoded registers a completion callback here
 * (directly via {@link DerMarshalInputStream#registerValidation}, or through the
 * {@link DeserializationCompletion} context element exposed by the active
 * {@code DerGetArg}), and {@link DerMarshalInputStream#endDecodeUnit()} fires them
 * once, in decreasing-priority order, after the whole value-sequence has been read
 * and BEFORE the stream closes (close == the mux acknowledgement).
 *
 * <p>Its primary client is {@code net.jini.jeri.BasicObjectEndpoint}: a single
 * instance exists per decode unit, so the DGC client keys its per-call batch on this
 * element and issues one coalesced {@code dirty} call when the unit completes.
 *
 * <p>A {@code DerMarshalInputStream} is confined to one call on one thread, but the
 * registration list is guarded for safe publication; no {@code ThreadLocal} is used
 * (JGDMS targets virtual threads).
 */
final class DerDecodeUnit implements DeserializationCompletion {

    private final Object lock = new Object();
    private final List<Reg> registrations = new ArrayList<>();
    private boolean flushed = false;

    @Override
    public void registerCompletion(ObjectInputValidation action, int priority)
            throws NotActiveException, InvalidObjectException {
        if (action == null) {
            throw new InvalidObjectException("null completion callback");
        }
        synchronized (lock) {
            if (flushed) {
                throw new NotActiveException(
                        "DER decode unit already completed; no further registration");
            }
            registrations.add(new Reg(action, priority));
        }
    }

    /**
     * Runs every registered callback exactly once, in decreasing-priority order
     * (highest first, mirroring {@link java.io.ObjectInputStream#registerValidation}),
     * then marks the unit complete. Idempotent: a second call -- including the
     * defensive one from {@link DerMarshalInputStream#close()} -- is a no-op.
     *
     * @throws IOException if a callback's {@code validateObject()} fails
     */
    void flush() throws IOException {
        List<Reg> ordered;
        synchronized (lock) {
            if (flushed) {
                return;
            }
            flushed = true;
            ordered = new ArrayList<>(registrations);
        }
        // Stable sort: descending priority; insertion order preserved within a priority.
        ordered.sort(Comparator.comparingInt((Reg r) -> r.priority).reversed());
        for (Reg r : ordered) {
            try {
                r.callback.validateObject();
            } catch (InvalidObjectException e) {
                throw e;
            } catch (RuntimeException e) {
                InvalidObjectException ioe =
                        new InvalidObjectException("decode-unit completion callback failed");
                ioe.initCause(e);
                throw ioe;
            }
        }
    }

    private record Reg(ObjectInputValidation callback, int priority) {}
}
