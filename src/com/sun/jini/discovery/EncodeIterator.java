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
import java.net.DatagramPacket;

/**
 * Iterator for performing multicast encode operations on (potentially)
 * multiple discovery format providers.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface EncodeIterator {

    /**
     * Performs a single multicast encode operation using the next encoder
     * provider of a {@link Discovery} instance, returning the resulting
     * datagram packets or throwing the resulting exception.
     *
     * @return datagram packets resulting from an encode operation
     * @throws IOException if the encode operation failed
     * @throws net.jini.io.UnsupportedConstraintException if the encode
     * operation is unable to satisfy its constraints
     * @throws java.util.NoSuchElementException if there are no more encoders
     * for this iterator
     */
    DatagramPacket[] next() throws IOException;

    /**
     * Returns <code>true</code> if this iterator has additional encoders, or
     * <code>false</code> otherwise.
     *
     * @return <code>true</code> if this iterator has additional encoders, or
     * <code>false</code> otherwise
     */
    boolean hasNext();
}
