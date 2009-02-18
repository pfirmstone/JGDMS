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
 * Utility class that implements a Jini ERI Multiplexing Protocol Data
 * Message
 */
public class DataMessage implements Message {

    private byte[] reference = new byte[]{(byte)0x80,(byte)0x00,(byte)0x00,
        (byte)0x00};
    private byte[] received = null;
    private byte[] payload = new byte[0];
    private boolean supressFormatCheck = false;

    //inherit javadoc
    public void send(OutputStream out) throws IOException {
        out.write(construct());
        out.flush();
    }

    //inherit javadoc
    public synchronized Object receive(InputStream in, long timeout)
        throws IOException, ProtocolException {
        byte[] header = new byte[reference.length];
        Thread myThread = Thread.currentThread();
        long stopTime = System.currentTimeMillis() +  timeout;
        while (System.currentTimeMillis()<stopTime) {
            try {
                int available = in.available();
                if (available>=header.length) {
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
        int tmp1 = (header[2]<<8);
        int tmp2 = (header[3]);
        int size =  tmp1 + tmp2;
        stopTime = System.currentTimeMillis() +  timeout;
        while (System.currentTimeMillis()<stopTime) {
            try {
                if (in.available()>=size) {
                    break;
                }
                Thread.sleep(100);
            } catch (InterruptedException ignore) {
            }
        }
        if (size < 0 || in.available() < size) {
            return new byte[0];
        }
        payload = new byte[size];
        int bytesRead = in.read(payload);
        stopTime = System.currentTimeMillis() +  timeout;
        while ((bytesRead!=-1)&&
            (bytesRead<size)&&
            (System.currentTimeMillis()<stopTime)) {
            int read = in.read(payload,bytesRead,(size-bytesRead));
            if (read==-1) {
                bytesRead = -1;
            } else {
                bytesRead = bytesRead + read;
            }
        }
        received = new byte[header.length + payload.length];
        System.arraycopy(header,0,received,0,header.length);
        System.arraycopy(payload,0,received,header.length,payload.length);
        if (!supressFormatCheck) {
            check(received);
        }
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
        return (received!=null) ? Util.convert(
            new byte[]{received[0],received[1],received[2],received[3]}) :
            Util.convert(reference);
    }

    /**
     * Determines if this Data message has its <b>open</b> flag set
     *
     * @return true if the <b>open</b> flag is set, false otherwise
     */
    public boolean isOpen() {
        return (received!=null) ? (received[0] & 0x10) == 0x10
            : (reference[0] & 0x10) == 0x10;
    }

    /**
     * Sets the <b>open</b> flag on this message.
     *
     * @return The message that was operated on
     */
    public DataMessage setOpen() {
        reference[0] = (byte) (reference[0] | 0x10);
        return this;
    }

    /**
     * Determines if this Data message has its <b>close</b> flag set
     *
     * @return true if the <b>close</b> flag is set, false otherwise
     */
    public boolean isClose() {
        return (received!=null) ? (received[0] & 0x08) == 0x08
            : (reference[0] & 0x08) == 0x08;
    }

    /**
     * Sets the <b>close</b> flag on this message.
     *
     * @return The message that was operated on
     */
    public DataMessage setClose() {
        reference[0] = (byte) (reference[0] | 0x08);
        return this;
    }

    /**
     * Determines if this Data message has its <b>eof</b> flag set
     *
     * @return true if the <b>eof</b> flag is set, false otherwise
     */
    public boolean isEof() {
        return (received!=null) ? (received[0] & 0x04) == 0x04
            : (reference[0] & 0x04) == 0x04;
    }

    /**
     * Sets the <b>eof</b> flag on this message.
     *
     * @return The message that was operated on
     */
    public DataMessage setEof() {
        reference[0] = (byte) (reference[0] | 0x04);
        return this;
    }


    /**
     * Determines if this Data message has its <b>ackRequired</b> flag set
     *
     * @return true if the <b>ackRequired</b> flag is set, false otherwise
     */
    public boolean isAckRequired() {
        return (received!=null) ? (received[0] & 0x02) == 0x02
            : (reference[0] & 0x02) == 0x02;
    }

    /**
     * Sets the <b>ackRequired</b> flag on this message.
     *
     * @return The message that was operated on
     */
    public DataMessage setAckRequired() {
        reference[0] = (byte) (reference[0] | 0x02);
        return this;
    }

    /**
     * Returns the session ID associated with this
     * message
     *
     * @return The session ID for this message
     */
    public int getsessionId() {
        return (received!=null) ? received[1] : reference[1];
    }

    /**
     * Sets the session ID for this message.
     *
     * @return The object that was operated on
     */
    public DataMessage setSessionID(byte sessionID) {
        reference[1] = sessionID;
        return this;
    }

    /**
     * Sets the payload for the message.  In the case of the Data message
     * the payload is interpreted as data.
     *
     * @param o An object that represents the payload of the message
     * @return The object that was operated on
     */
    public DataMessage setPayload(Object o) {
        payload = (byte[]) o;
        return this;
    }

    /**
     * Sets the size for this message.  It may be desireable to set a size other
     * than the actual size of the payload in order to test protocol violation
     * detection
     *
     * @param length The length that this message will report
     * @return The object that was just operated on
     */
    public DataMessage setSize(short length) {
        reference[2] = (byte) ((length >>> 8) & 0x00ff);
        reference[3] = (byte) (length & 0x00ff);;
        return this;
    }

    /**
     * Returns the size of this message
     *
     * @return The size of the message
     */
    public int getSize() {
        return (received!=null) ? (received[2]<<8) + received[3]
            : (reference[2]<<8) + reference[3];
    }

    /**
     * Prevents a format check when the message is received.  This is
     * useful when receiving multiple messages that are fragments of a
     * larger message.  Suppressing format check allows the caller to not
     * have to anticipate which flags should be set on each message fragment.
     *
     * @return The object that was operated on
     */
    public DataMessage suppressFormatCheck() {
        supressFormatCheck = true;
        return this;
    }

    /**
     * Checks that the <code>message</code> passed in conforms
     * to the format for a Data message
     *
     * @param The message to check
     */
    private void check(byte[] message) throws ProtocolException {
        //check that the flags and message type are as expected
        if ((received[0]|reference[0])!=reference[0]) {
            byte[] tmp = new byte[reference.length];
            System.arraycopy(received,0,tmp,0,reference.length);
            throw new ProtocolException("Received DataMessage: "
                + Util.convert(received) + " does not"
                + " match expected DataMessage: " + Util.convert(reference));
        }

        //check that the reserved bit is not used
        if ((received[1]&(byte)0x80) != 0x00) {
            throw new ProtocolException("Received DataMessage makes use"
                + " of reserved bit");
        }

        //check that the size of the is correct
        if (getSize()!=payload.length) {
            throw new ProtocolException("The size of the data received: "
                + payload.length + " does not match the size reported in"
                + " the header: " + getSize());
        }

    }

    /**
     * Constructs the Data message to send over the output stream
     */
    private byte[] construct() {
        byte[] message = new byte[reference.length + payload.length];
        System.arraycopy(reference,0,message,0,reference.length);
        System.arraycopy(payload,0,message,reference.length,payload.length);
        return message;
    }
}
