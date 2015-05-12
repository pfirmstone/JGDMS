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
package org.apache.river.test.spec.iiop.util;

import java.util.logging.Level;

// java.rmi
import java.rmi.Remote;
import java.rmi.RemoteException;


/**
 * Simple class implementing Remote interface for constructing remote objects.
 */
public class TestRemoteObject implements TestRemoteInterface {

    /** Name id. */
    public String name;

    /**
     * Default constructor.
     * name field is set to <code>null</code>.
     */
    public TestRemoteObject() {
        name = null;
    }

    /**
     * Creates a new TestRemoteObject with the given name.
     *
     * @param name desired name for new TestRemoteObject.
     */
    public TestRemoteObject(String name) {
        this.name = name;
    }

    /**
     * Wait for a specified time and then return.
     *
     * @param duration period of time for waiting in milliseconds
     */
    public void wait(Integer duration) throws RemoteException {
        try {
            Thread.sleep(duration.intValue());
        } catch (InterruptedException ie) {}
    }

    /**
     * Simple method for testing remote call functionality.
     *
     * @param val incoming integer value
     *
     * @return incoming val + 1
     */
    public int incr(int val) throws RemoteException {
        return val + 1;
    }

    /**
     * Creates the string representation of this TestRemoteObject.
     *
     * @return The string representation.
     */
    public String toString() {
        return "TestRemoteObject: [name = " + name + "]";
    }
}
