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
package au.net.zeus.jgdms.policy.proxy;

import java.io.IOException;
import net.jini.core.event.RemoteEvent;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Remote event delivered to a {@link net.jini.core.event.RemoteEventListener}
 * registered via
 * {@link au.net.zeus.jgdms.api.policy.RemotePolicyService#registerForPolicyUpdates}
 * when the djinn-wide policy grants are replaced.
 *
 * <p>This event is a <em>notification only</em>: it carries no grant payload.
 * Clients should call
 * {@link au.net.zeus.jgdms.api.policy.RemotePolicyService#getCurrentGrants}
 * on receipt to obtain the authoritative new grant set.  This pull-on-notification
 * model ensures clients always see a consistent snapshot, even if multiple
 * {@code replace()} calls arrive in quick succession.
 *
 * @see au.net.zeus.jgdms.api.policy.RemotePolicyService#registerForPolicyUpdates
 * @since 3.1.1
 */
@AtomicSerial
public final class PolicyUpdateEvent extends RemoteEvent {

    private static final long serialVersionUID = 1L;

    /**
     * No extra serial fields beyond those inherited from {@link RemoteEvent}.
     */
    public static SerialForm[] serialForm() {
        return new SerialForm[0];
    }

    /**
     * Serializes this event (no extra fields to write).
     *
     * @param arg  serialization argument bag
     * @param e    the event to serialize
     * @throws IOException if serialization fails
     */
    public static void serialize(PutArg arg, PolicyUpdateEvent e) throws IOException {
        arg.writeArgs();
    }

    private static GetArg check(GetArg arg)
            throws IOException, ClassNotFoundException {
        new RemoteEvent(arg); // validate superclass invariants
        return arg;
    }

    /**
     * Deserialization constructor.
     *
     * @param arg deserialization state provided by {@link AtomicSerial}
     * @throws IOException            if deserialization fails
     * @throws ClassNotFoundException if a required class cannot be found
     */
    public PolicyUpdateEvent(GetArg arg)
            throws IOException, ClassNotFoundException {
        super(check(arg));
    }

    /**
     * Constructs a {@code PolicyUpdateEvent}.
     *
     * @param source   the event source (the exported server stub)
     * @param eventID  the event identifier from the {@link
     *                 net.jini.core.event.EventRegistration}
     * @param seqNum   the monotonically-increasing sequence number for this
     *                 registration
     * @param handback the opaque object supplied at registration time; may be
     *                 {@code null}
     */
    public PolicyUpdateEvent(Object source,
                             long eventID,
                             long seqNum,
                             MarshalledInstance handback) {
        super(source, eventID, seqNum, handback);
    }
}
