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

import java.nio.ByteBuffer;

/**
 * Factory that produces byte buffers, each corresponding to a separate
 * datagram packet to be sent.  <code>DatagramBufferFactory</code> instances
 * are passed to the encoding methods of {@link MulticastRequestEncoder} and
 * {@link MulticastAnnouncementEncoder}, which can encode data to multiple
 * datagram packets (if needed) by writing to multiple buffers obtained from
 * the factory.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface DatagramBufferFactory {

    /**
     * Returns a byte buffer into which to write encoded multicast packet data.
     * The buffer encompasses all of the data for the packet to be sent: buffer
     * offset <code>0</code> corresponds to the start of packet data, and the
     * capacity of the buffer indicates the maximum packet size.  Encoding
     * methods should start writing data at the initial (non-zero) position of
     * the buffer; the final position of the buffer after the encoding method
     * has returned is used to mark the end of encoded data, which translates
     * into the actual length of the sent packet.
     *
     * @return a buffer into which to write encoded multicast packet data
     */
    ByteBuffer newBuffer();
}
