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

package net.jini.core.constraint;

import java.io.IOException;
import java.io.Serializable;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Represents an invariant constraint on {@link ObjectInput} and 
 * {@link ObjectOutput}, covering data transmitted in band as part
 * of the remote call itself. 
 * If an invariant violation on in-band data is detected during a remote
 * call, a {@link java.io.InvalidObjectException} will be thrown, construction of the Object
 * in question will fail atomically
 * (in the client or in the server, depending on which side detected the violation).
 * Invariant validation is fine grained, which means every Object in the 
 * graph must have its invariants satisfied.
 * <p>
 * Java's standard {@link java.io.ObjectOutputStream} and {@link java.io.ObjectInputStream}
 * check invariants after construction, circular links and finalizers allow
 * an attacker to obtain references to partially constructed objects before
 * invariants have been checked.
 * <p>
 * Serialization for this class is guaranteed to produce instances that are
 * comparable with <code>==</code>.
 *
 * @since 3.0
 */
@AtomicSerial
public final class AtomicValidation implements InvocationConstraint, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Detect when message contents (both requests and replies) have been
     * altered by third parties, and if detected, refuse to process the
     * message and throw an exception. The mechanisms used to maintain
     * integrity are not specified by this constraint.
     */
    public static final AtomicValidation YES = new AtomicValidation(true);
    /**
     * Do not detect when message contents have been altered by third parties.
     * Normally this constraint should not be used, as many secure
     * communication mechanisms have integrity mechanisms that cannot be
     * disabled.
     */
    public static final AtomicValidation NO = new AtomicValidation(false);

    /**
     * <code>true</code> for <code>YES</code>, <code>false</code> for
     * <code>NO</code>
     *
     * @serial
     */
    private final boolean val;

    /**
     * Simple constructor.
     *
     * @param val <code>true</code> for <code>YES</code>, <code>false</code>
     * for <code>NO</code>
     */
    private AtomicValidation(boolean val) {
	this.val = val;
    }
    
    AtomicValidation(GetArg arg) throws IOException {
	this(arg.get("val", true));
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return val ? "Integrity.YES" : "Integrity.NO";
    }

    /**
     * Canonicalize so that <code>==</code> can be used.
     */
    private Object readResolve() {
	return val ? YES : NO;
    }
}
