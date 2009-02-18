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
package com.sun.jini.test.spec.jeri.mux.util;

//java.io
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Utility class to represent a Jini ERI Multiplexing protocol
 * Abort message
 */
public class AbortMessage implements Message {

    private static final byte[] reference =
        new byte[] {0x20,0x00,0x00,0x00};
    private byte[] received = null;
    private byte[] payload = null;

    //inherit javadoc
    public void send(OutputStream out) throws IOException {
        out.write(reference);
        out.flush();
    }

    //inherit javadoc
    public Object receive(InputStream in, long timeout)
        throws IOException, ProtocolException {
        while (in.available()<reference.length) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IOException("Thread was interrupted while waiting"
                    + " for I/O");
            }
        }
        received = new byte[reference.length];
        in.read(received);
        check(received);
        payload = new byte[] {received[2],received[3]};
        return received;
    }

    //inherit javadoc
    public Object getPayload() {
        return payload;
    }

    //inherit javadoc
    public byte[] getRawMessage() {
        return received;
    }

    //inherit javadoc
    public String toString() {
        return (received!=null) ? Util.convert(received) :
            Util.convert(reference);
    }

    /**
     * Returns the size of this Abort message
     *
     * @return The size of the message
     */
    public int getSize() {
        return (received!=null) ? (received[2]<<8) + received[3]
            : (reference[2]<<8) + reference[3];
    }

    /**
     * Sets the size for this message.  It may be desireable to set a size other
     * than the actual size of the payload in order to test protocol violation
     * detection
     *
     * @param length The length that this message will report
     * @return The <code>AbortMessage</code> that was just operated on
     */
    public AbortMessage setSize(short length) {
        reference[2] = (byte) ((length >>> 8) & 0x00ff);
        reference[3] = (byte) (length & 0x00ff);;
        return this;
    }

    /**
     * Sets the payload for the message.  In the case of the Abort message
     * the payload is interpreted as the detail of the Abort operation.
     *
     * @param o An object that represents the payload of the message
     * @return The object that was operated on
     */
    public AbortMessage setPayload(Object o) {
        payload = (byte[]) o;
        return this;
    }

    /**
     * Checks that the <code>message</code> passed in conforms
     * to the format for an Abort message
     *
     * @param The message to check
     */
    private void check(byte[] message) throws ProtocolException {
        //check the type of the message
        if ((received[0]|reference[0])!=reference[0]) {
            throw new ProtocolException("The message received: "
                + Util.convert(received) + " is not of Abort message type");
        }
        //check that the reserved bits are not used
        if (((received[0]&0x01)!=0x00) ||
            ((received[1]&0x80)!=0x00)){
            throw new ProtocolException("The received Abort message makes"
                + " use of reservered bits");
        }


    }
}
