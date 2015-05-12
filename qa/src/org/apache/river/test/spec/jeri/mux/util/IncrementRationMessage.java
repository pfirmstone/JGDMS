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
 * Utility class to represent a Jini ERI Multiplexing Protocol
 * IncrementRation message.
 */
public class IncrementRationMessage implements Message {

    private static final byte[] reference =
        new byte[] {0x10,0x00,0x00,0x00};
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
        long stopTime = System.currentTimeMillis() +  timeout;
        while (System.currentTimeMillis()<stopTime) {
            try {
                if (in.available()>=reference.length) {
                    break;
                }
                Thread.sleep(100);
            } catch (InterruptedException ignore) {
            }
        }
        if (in.available()<reference.length) {
            return new byte[0];
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
     * Sets the amount of bytes that the sender is willing to receive.
     *
     * @param shift Multiplier for the increment amount
     * @param increment Number of additional bytes the sender is willing to
     * receive
     * @return The object that was operated on
     */
    public IncrementRationMessage setIncrement(byte shift, short increment) {
        shift = (byte) (shift & 0x0e);
        reference[0] = (byte) (reference[0]|shift);
        reference[2] = (byte) ((increment >> 8) & 0x00ff);
        reference[3] = (byte) (increment & 0x00ff);
        return this;
    }

    /**
     * Sets the session ID for this message.
     *
     * @return The object that was operated on
     */
    public IncrementRationMessage setSessionID(byte sessionID) {
        reference[1] = sessionID;
        return this;
    }

    /**
     * Checks that the <code>message</code> passed in conforms
     * to the format for an IncrementRation message
     *
     * @param The message to check
     */
    private void check(byte[] message) throws ProtocolException {
        //check the type of the message
        if ((received[0]|reference[0])!=reference[0]) {
            throw new ProtocolException("The message received: "
                + Util.convert(received) + " is not of Ping message type");
        }
        //check that the reserved byte is not used
        if ((received[1]|0x00)!=0x00) {
            throw new ProtocolException("The received Ping message makes"
                + " use of reservered bits");
        }
    }
}
