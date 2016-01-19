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
 * Represents a constraint on the confidentiality of message contents.
 * <p>
 * Serialization for this class is guaranteed to produce instances that are
 * comparable with <code>==</code>.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@AtomicSerial
public final class Confidentiality
				implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 6173438948668674131L;

    /**
     * Transmit message contents so that they cannot easily be interpreted by
     * third parties (typically by using encryption). The mechanisms used
     * to maintain confidentiality are not specified by this constraint.
     */
    public static final Confidentiality YES = new Confidentiality(true);
    /**
     * Transmit message contents in the clear (no use of encryption).
     * <p>
     * Normally this constraint should not be used unless there is an
     * organizational policy that data must be transmitted in the clear.
     */
    public static final Confidentiality NO = new Confidentiality(false);

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
    private Confidentiality(boolean val) {
	this.val = val;
    }
    
    /**
     * AtomicSerial constructor.
     * @param arg
     * @throws IOException
     */
    public Confidentiality(GetArg arg) throws IOException{
	this(arg.get("val", true));
    }
    
    /**
     * Returns a string representation of this object.
     */
    @Override
    public String toString() {
	return val ? "Confidentiality.YES" : "Confidentiality.NO";
    }

    /**
     * Canonicalize so that <code>==</code> can be used.
     * @return true for YES, false for NO.
     */
    public Object readResolve() {
	return val ? YES : NO;
    }
}
