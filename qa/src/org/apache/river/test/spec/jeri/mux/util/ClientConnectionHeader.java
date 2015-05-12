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
 * Utility class to represent a Jini ERI Multiplexing Protocol client header
 * message.
 */
public class ClientConnectionHeader implements Message {

    private static final byte[] reference =
        new byte[] {0x4a,0x6d,0x75,0x78,0x01,0x00,(byte)0x80,0x00};
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
        payload = new byte[] {received[5],received[6]};
        return received;
    }

    //inherit javadoc
    public Object getPayload() {
        return payload;
    }

    //inherit javadoc
    public String toString() {
        return (received!=null) ? Util.convert(received) :
            Util.convert(reference);
    }

    //inherit javadoc
    public byte[] getRawMessage() {
        return received;
    }

    /**
     * Sets the number of bytes that the client is willing to accept
     * from a mux server.
     *
     * @param ration The number of bytes the client is willing to accept
     * @return The object that was just operated on
     */
    public ClientConnectionHeader setRation(short ration) {
        reference[5] = (byte) ((ration >>> 8) & 0x00ff);
        reference[6] = (byte) (ration & 0x00ff);;
        return this;
    }

    /**
     * Checks that the <code>message</code> passed in conforms
     * to the format for a ClientConnectionHeader message
     *
     * @param The message to check
     */
    private void check(byte[] message) throws ProtocolException {
        //check the length of the message
        if (message.length!=reference.length) {
            throw new ProtocolException("Length of message does not match"
                + " length of ClientConnectionHeader");
        }
        //check the contents of the bytes
        for (int i=0; i<5;i++) {
            if (message[i]!=reference[i]) {
                throw new ProtocolException("Byte " + i + " of the message:"
                    + " " + Util.convert(message) + " does not match the same"
                    + " byte in the protocol: "
                    + Util.convert(reference));
            }
        }
        //check the reserved 7-bits on the last byte
        if ((message[message.length-1]|0x01)!=0x01) {
            throw new ProtocolException("Reserved bits on the last byte"
                + " have been used");
        }
    }
}
