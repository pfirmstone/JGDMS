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
 * This class represents a Jini ERI Multiplexing Protocol Error message.
 */
public class ErrorMessage implements Message {

    private static final byte[] reference =
        new byte[] {0x08,0x00,0x00,0x00};
    private byte[] received = null;
    private byte[] payload = null;

    //inherit javadoc
    public void send(OutputStream out) throws IOException {
        out.write(reference);
        out.flush();
    }

    //inherit javadoc
    public Object receive(InputStream in,  long timeout)
        throws IOException, ProtocolException {
        byte[] header = new byte[reference.length];
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
        in.read(header);
        int tmp1 = header[2]<<8;
        int tmp2 = header[3];
        int size =  tmp1 + tmp2;
        payload =  new byte[size];
        while (in.available()<payload.length) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IOException("Thread was interrupted while waiting"
                    + " for I/O");
            }
        }
        in.read(payload);
        received = new byte[header.length + payload.length];
        System.arraycopy(header,0,received,0,header.length);
        System.arraycopy(payload,0,received,header.length,payload.length);
        check(received);
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
     * Returns the session ID associated with this
     * message
     *
     * @return The session ID for this message
     */
    public int getSessionID() {
        return (received!=null) ? received[1] : reference[1];
    }

    /**
     * Sets the session ID for this message.
     *
     * @return The object that was operated on
     */
    public ErrorMessage setSessionID(byte sessionID) {
        reference[1] = sessionID;
        return this;
    }

    /**
     * Checks that the <code>message</code> passed in conforms
     * to the format for an Error message
     *
     * @param The message to check
     */
    private void check(byte[] message) throws ProtocolException {
        //check that the message identifier matches
        if (message[0]!=reference[0]) {
            throw new ProtocolException("The message identifier does not"
                + " match Error message");
        }

        //check the length of the message
        if (message.length!=reference.length) {
            throw new ProtocolException("Length of message does not match"
                + " length of Error message");
        }
        //check the reserved 2-bytes on the last byte
        if ((message[1]|0x00)!=0x00) {
            throw new ProtocolException("Reserved byte on"
                + " Error message have been used");
        }
    }
}
