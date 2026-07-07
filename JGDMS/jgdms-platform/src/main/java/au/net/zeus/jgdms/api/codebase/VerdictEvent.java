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
package au.net.zeus.jgdms.api.codebase;

import java.io.IOException;
import java.io.InvalidObjectException;
import net.jini.core.event.RemoteEvent;
import net.jini.io.MarshalledInstance;
import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Remote event delivered to a {@link net.jini.core.event.RemoteEventListener}
 * registered via
 * {@link au.net.zeus.jgdms.api.codebase.VerdictRegistry#registerVerdictListener}
 * when an authoritative {@link RegistryVerdict} is published.
 *
 * <p>The event carries the full {@link RegistryVerdict} so the listener does
 * not need to call {@code getVerdict()} in order to act on the notification.
 *
 * <p>The handback is stored as a {@link MarshalledInstance} (preferred) rather
 * than the deprecated {@code java.rmi.MarshalledObject}.
 *
 * @see au.net.zeus.jgdms.api.codebase.VerdictRegistry#registerVerdictListener
 * @since 3.1.1
 */
@AtomicSerial
public final class VerdictEvent extends RemoteEvent {

    private static final long serialVersionUID = 1L;

    private static final String VERDICT = "verdict";

    public static SerialForm[] serialForm() {
        return new SerialForm[]{
            new SerialForm(VERDICT, RegistryVerdict.class)
        };
    }

    public static void serialize(PutArg arg, VerdictEvent e) throws IOException {
        arg.put(VERDICT, e.verdict);
        arg.writeArgs();
    }

    /** The authoritative verdict that triggered this event. @serial */
    private final RegistryVerdict verdict;

    private static GetArg check(GetArg arg)
            throws IOException, ClassNotFoundException {
        new RemoteEvent(arg); // validate superclass invariants
        RegistryVerdict v = arg.get(VERDICT, null, RegistryVerdict.class);
        if (v == null) throw new InvalidObjectException("verdict must not be null");
        return arg;
    }

    /**
     * Deserialization constructor.
     *
     * @param arg deserialization state provided by {@link AtomicSerial}
     * @throws IOException            if deserialization fails
     * @throws ClassNotFoundException if a required class cannot be found
     */
    public VerdictEvent(GetArg arg) throws IOException, ClassNotFoundException {
        super(check(arg));
        this.verdict = arg.get(VERDICT, null, RegistryVerdict.class);
    }

    /**
     * Constructs a {@code VerdictEvent}.
     *
     * @param source    the event source (typically the exported server stub)
     * @param eventID   the event identifier from the {@link
     *                  net.jini.core.event.EventRegistration}
     * @param seqNum    the event sequence number (monotonically increasing
     *                  per registration)
     * @param handback  the opaque object supplied at registration time;
     *                  may be {@code null}
     * @param verdict   the authoritative verdict that triggered the event;
     *                  must be non-null
     * @throws NullPointerException if {@code verdict} is {@code null}
     */
    public VerdictEvent(Object source,
                        long eventID,
                        long seqNum,
                        MarshalledInstance handback,
                        RegistryVerdict verdict) {
        super(source, eventID, seqNum, handback);
        if (verdict == null) throw new NullPointerException("verdict");
        this.verdict = verdict;
    }

    /**
     * Returns the authoritative {@link RegistryVerdict} that caused this
     * event to be delivered.
     *
     * @return the verdict; never {@code null}
     */
    public RegistryVerdict getVerdict() {
        return verdict;
    }
}
