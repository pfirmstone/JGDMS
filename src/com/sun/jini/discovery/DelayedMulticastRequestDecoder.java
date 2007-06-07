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

package com.sun.jini.discovery;

import java.io.IOException;
import java.nio.ByteBuffer;
import net.jini.core.constraint.InvocationConstraints;

/**
 * Interface implemented by classes which decode multicast request data
 * and additionally support delayed constraint checking.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.1
 */
public interface DelayedMulticastRequestDecoder
    extends MulticastRequestDecoder 
{

    /**
     * Decodes the multicast request data contained in the given buffer in a
     * manner that satisfies the specified absolute constraints and client
     * subject checker (if any), returning a {@link MulticastRequest} instance
     * that contains the decoded data, with constraint checking optionally
     * delayed.
     * <code>null</code> constraints are
     * considered equivalent to empty constraints.  Constraint checking may be
     * delayed using the <code>delayConstraintCheck</code> flag.
     * <p> If the <code>delayConstraintCheck</code> flag is <code>true</code>,
     * the method behaves as follows:<ul>
     * <li> Some of the specified constraints may not be checked before this
     * method returns; the returned <code>MulticastRequest</code>'s
     * {@link MulticastRequest#checkConstraints checkConstraints}
     * method must be invoked to complete checking of all the constraints.
     * <li> Constraints which must be checked before accessor methods of the
     * returned <code>MulticastRequest</code> can be invoked are always
     * checked before this method returns.</ul>
     * If <code>delayConstraintCheck</code> is <code>false</code>, all the
     * specified constraints are checked before this method returns.
     *
     * @param buf a buffer containing the packet data to decode. The contents
     * of <code>buf</code> may be used on subsequent invocations of the returned
     * <code>MulticastRequest</code> instance's <code>checkConstraints</code>
     * method.  The caller must ensure that the contents of <code>buf</code> are
     * not modified before invocation of the <code>checkConstraints</code>
     * method.  Additionally, the multicast request data must begin at position
     * zero of <code>buf</code>.
     * @param constraints the constraints to apply when decoding the data, or
     * <code>null</code>
     * @param checker the object to use to check the client subject, or
     * <code>null</code>
     * @param delayConstraintCheck flag to control delayed constraint checking
     * @return the decoded multicast request data
     * @throws IOException if an error occurs in interpreting the data
     * @throws UnsupportedConstraintException if unable to satisfy the
     * specified constraints
     * @throws SecurityException if the given constraints cannot be satisfied
     * due to insufficient caller permissions, or if the client subject check
     * fails
     * @throws NullPointerException if <code>buf</code> is <code>null</code>
     */
    MulticastRequest decodeMulticastRequest(ByteBuffer buf,
					    InvocationConstraints constraints,
					    ClientSubjectChecker checker,
                                            boolean delayConstraintCheck)
	throws IOException;
}
