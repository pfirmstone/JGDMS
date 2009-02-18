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
package com.sun.jini.test.impl.outrigger.matching;

// Imports
import java.net.InetAddress;


/**
 * Class that direcly implements Entry and has no subclasses.
 * <p>
 * This class is very similar to <code>UniqueEntry</code>, however, it
 * does not exstend <code>AbstractEntry</code> and has no subclasses
 *
 * @see UniqueEntry
 */
public final class LoneEntry implements net.jini.core.entry.Entry {

    /**
     * ourVMName is a <code>String</code> that should uniquel identify
     * this VM.
     */
    static private String ourVMName;
    static {

        /*
         * Generate what will probably be a unique name for this VM
         * Start with the host name
         */
        String hostName;

        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {

            /*
             * This probably means we can't find the network, so we are
             * not going to get any uniqueness this way (all the VM
             * are going to be on this machine), just leave it blank.
             */
            hostName = "";
        }

        /*
         * Use the host name and append the current time.  Time is
         * used to different between VM on the same machine and to
         * deal with persistent JavaSpaces
         */
        ourVMName = hostName + System.currentTimeMillis();
    }

    /**
     * Counter used to give unique values to the entryID field as necessary.
     */
    static private int nextID = 0;

    /**
     * VM originating this entry.
     */
    public String originatingHostVM = null;

    /**
     * Unique ID of this entry. In general client should only set
     * this field if they want to give this entry the same idenity as
     * another.
     */
    public Integer entryID = null;

    /**
     * Required public zero arg constructor Entry classes need
     */
    public LoneEntry() {}

    /**
     * Create a new <code>LoneEntry</code>. If
     * <code>makeUnique</code> is true then set
     * <code>originatingHostVM</code> to a value uniquely identifying
     * this VM and set <code>entryID</code> to a value that has not
     * been used for other match entries originating from this VM.
     *
     * A false value will set originatingHostVM to <code>null</code>
     * and entryID to 0
     */
    public LoneEntry(boolean makeUnique) {
        if (!makeUnique) {
            return;
        }
        originatingHostVM = ourVMName;

        // Grab nextID
        int id;
        synchronized (getClass()) {
            id = nextID++;
        }
        entryID = new Integer(id);
    }

    /**
     * Create a new <code>LoneEntry</code> that can service as a
     * template to retrieve the passed LoneEntry (or subclass) from
     * a JavaSpace
     */
    public LoneEntry(LoneEntry entry) {
        originatingHostVM = entry.originatingHostVM;
        entryID = entry.entryID;
    }
}
