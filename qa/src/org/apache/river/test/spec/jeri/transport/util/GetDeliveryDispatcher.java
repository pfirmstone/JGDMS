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
package org.apache.river.test.spec.jeri.transport.util;

//jeri imports
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;

//java.io
import java.io.IOException;
import java.io.ObjectInputStream;

//java.util
import java.util.logging.Logger;
import org.apache.river.qa.harness.TestException;

public class GetDeliveryDispatcher implements RequestDispatcher {

    private boolean accept = true;
    private boolean callDone = false;
    private int dispatchCalled = 0;
    private Lock lock = new Lock();

    public void reject() {
        synchronized(lock){
            callDone = false;
            accept = false;
        }
    }

    public void accept() {
        synchronized(lock){
            callDone = false;
            accept = true;
        }
    }

    public void dispatch(InboundRequest req) {
        Logger log = AbstractEndpointTest.getLogger();
        synchronized(lock) {
            if (accept) {
                log.finest("Accepting the remote call");
                try {
                   ObjectInputStream ois = new ObjectInputStream(
                   req.getRequestInputStream());
                   dispatchCalled = ois.readInt();
                   callDone = true;
                   log.finest("Read from the input stream :" + dispatchCalled);
                   lock.signal();
                } catch (Exception e) {
                    callDone = true;
                    e.printStackTrace();
                    lock.signal();
                }
            } else {
                log.finest("Rejecting the remote call");
                dispatchCalled = -1;
                callDone = true;
                lock.signal();
                throw new RuntimeException("Rejecting the remote call");
            }
        }
    }

    public int dispatchCalled() throws TestException{
	long start = System.currentTimeMillis();
	long finish = start + 900000L;
        synchronized(lock) {
            while (!callDone) {
                try {
                    lock.waitForSignal(2000L);
		    if (System.currentTimeMillis() > finish) throw new TestException("waiting too long for dispatch call");
                } catch (InterruptedException e) {
                   e.printStackTrace();
                }
            }
            callDone = false;
            int temp = dispatchCalled;
            dispatchCalled = 0;
            return temp;
        }
    }

    private class Lock {
        public synchronized void waitForSignal(long timeout) throws InterruptedException {
            AbstractEndpointTest.getLogger().finest("Waiting on the lock."
                + " Call done is " + callDone);
		wait(timeout);
	    }

        public synchronized void signal() {
           AbstractEndpointTest.getLogger().finest("Releasing the lock."
               + " Call done is " + callDone);
           notify();
        }
    }

}
