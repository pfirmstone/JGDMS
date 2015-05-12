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
package org.apache.river.test.spec.jeri.mux.util;

//java.io
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Interface to define the opertations that can be performed on a generic
 * Jini ERI Multiplexing Protcol message.
 */
public interface Message {

    /**
     * Send the Jini ERI Multiplexing Protocol message over an output stream.
     *
     * @param out The output stream over which to transmit the message.
     */
    public void send(OutputStream out) throws IOException;

    /**
     * Receive a Jini ERI Multiplexing Protocol message from am input stream.
     *
     * @param in The input stream from which to receive the message
     * @return The message received
     */
    public Object receive(InputStream in, long timeout)
        throws IOException, ProtocolException;

    /**
     * Extract the contents of the message.  This applies to most Jini ERI
     * Multiplexing Protocol messages, since they consist of a header and data.
     * The data part of the message is considered the payload.  Messages that
     * have no data associated with them return an empty array of bytes.
     *
     * @return The data content of the message
     */
    public Object getPayload();

    /**
     * Returns the byte representation of the message.
     *
     * @return An array of bytes representing the message
     */
    public byte[] getRawMessage();

}
