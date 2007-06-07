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
import net.jini.core.constraint.InvocationConstraints;

/**
 * Interface implemented by classes which encode multicast announcement data
 * according to discovery protocol formats.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface MulticastAnnouncementEncoder extends DiscoveryFormatProvider {

    /**
     * Encodes the given multicast announcement data into byte buffers obtained
     * from the provided datagram buffer factory, in a manner that satisfies
     * the specified absolute constraints.  <code>null</code> constraints are
     * considered equivalent to empty constraints.  Multicast announcement data
     * that is too large to fit in a single datagram buffer is split across
     * multiple buffers, with the constraints applied to each; this method is
     * responsible for determining if and when to split the data based on the
     * available space in the obtained buffers.
     *
     * @param announcement the announcement data to encode
     * @param bufs the factory for producing buffers in which to write encoded
     * data
     * @param constraints the constraints to apply when encoding the data, or
     * <code>null</code>
     * @throws IOException if an error occurs in encoding the data to send
     * @throws net.jini.io.UnsupportedConstraintException if unable to satisfy
     * the specified constraints
     * @throws SecurityException if the given constraints cannot be satisfied
     * due to insufficient caller permissions
     * @throws NullPointerException if <code>announcement</code> or
     * <code>bufs</code> is <code>null</code>
     */
    void encodeMulticastAnnouncement(MulticastAnnouncement announcement,
				     DatagramBufferFactory bufs,
				     InvocationConstraints constraints)
	throws IOException;
}
