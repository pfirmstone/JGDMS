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
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class to receive a message within a given period of time.
 */
public class TimedReceiveTask {

    private Message message = null;
    private InputStream in = null;
    Object received = null;

    public TimedReceiveTask(Message message, InputStream in, long timeout)
        throws IOException, ProtocolException, InterruptedException {
        this.message = message;
        this.in = in;
        Receiver receiver = new Receiver(timeout);
        Thread t = new Thread(receiver);
        long stopTime = System.currentTimeMillis() + timeout;
        t.start();
        while (!receiver.isDone()&&System.currentTimeMillis() < stopTime) {
            Thread.sleep(100);
        }
        Exception e = receiver.getException();
        if (e!=null) {
            if (e instanceof IOException) {
                throw (IOException) e;
            } else if (e instanceof ProtocolException) {
                throw (ProtocolException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
        if (!receiver.isDone()) {
            t.interrupt();
            throw new InterruptedException("Receive operation on " + message
                + " did not complete in " + timeout + " ms");
        }

    }

    //Return the message that was received
    public synchronized Object getMessage() {
        return received;
    }

    //Set the message received
    private synchronized void setMessage(Object message) {
        received = message;
    }

    private class Receiver implements Runnable {

        private Exception e = null;
        private boolean done = false;
        private Object lock = new Object();
        private long timeout;

        public Receiver(long timeout) {
            this.timeout = timeout;
        }

        //Main receive loop
        public void run() {
            try {
                Object bytes = message.receive(in, timeout);
                setMessage(bytes);
                synchronized(lock) {
                    done = true;
                }
            } catch (ProtocolException e) {
                synchronized(lock) {
                    this.e = e;
                }
            } catch (IOException e) {
                synchronized(lock) {
                    this.e = e;
                }
            }
        }

        //Return an exception or null if no exception occurred
        private Exception getException() {
            synchronized(lock) {
                return e;
            }
        }

        //Was the message received successfully?
        private boolean isDone() {
            synchronized(lock) {
                return done;
        }
        }
    }

}
