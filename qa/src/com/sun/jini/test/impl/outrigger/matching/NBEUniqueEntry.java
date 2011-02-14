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

// java.net
import java.net.InetAddress;


/**
 * Base class to be used for JavaSpace entries that require identity
 * and are not naturally unique.  The nature of JavaSpaces (deals in
 * copies of entries, all of an entries fields are public, etc.)
 * requires some invariants to be maintained by the client to
 * guarantee the uniqueness of NBEUniqueEntry's.
 *
 * <UL>
 * <LI> Clients should only write <code>NBEUniqueEntry</code>s that they
 * have :
 *      <UL>
 *      <LI> obtained from <code>NBEUniqueEntry(true)</code>, or the
 *           constructor of a subclass that called super(true).
 *      <LI> <em>taken</em> from the JavaSpace.
 *      </UL>
 * <LI> Clients should not write the same <code>NBEUniqueEntry</code> (or
 *      copy of) to a JavaSpace more than once without an intervening
 *      take.
 * </UL>
 *
 * Another way to look at this class is that it allows clients to fake
 * mutable entries.  By giving each entry a unique identity one can
 * match up solely on the NBEUniqueEntry portion of the entry, this allows
 * a client to look-up the correct entry even if it has changed.  The
 * <code>NBEUniqueEntry(UniqueEntry)</code> constructor is provide to
 * produce template for exactly this purpose.
 *
 * This class is very similar to <code>UniqueEntry</code>, however, it
 * does not exstend <code>AbstractEntry</code>
 * @see UniqueEntry
 * @see net.jini.entry.AbstractEntry
 *
 * @author John W. F. McClain
 */
public class NBEUniqueEntry implements net.jini.core.entry.Entry {

    /**
     * ourVMName is a <code>String</code> that should uniquel identify
     * this VM.
     */
    static private String ourVMName;
    static {

        // Generate what will probably be a unique name for this VM

        // Start with the host name
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
     * Unique ID of this entry.  In general client should only set
     * this field if they want to give this entry the same idenity as
     * another.
     */
    public Integer entryID = null;

    /**
     * Required public zero arg constructor Entry classes need
     */
    public NBEUniqueEntry() {}

    /**
     * Create a new <code>UniqueEntry</code>. If
     * <code>makeUnique</code> is true then set
     * <code>originatingHostVM</code> to a value uniquely identifying
     * this VM and set <code>entryID</code> to a value that has not
     * been used for other match entries originating from this VM.
     *
     * A false value will set originatingHostVM to <code>null</code>
     * and entryID to 0
     */
    public NBEUniqueEntry(boolean makeUnique) {
        if (!makeUnique) {
            return;
        }
        originatingHostVM = ourVMName;

        // Grab nextID
        int id = getID();
        entryID = new Integer(id);
    }

    private static synchronized int getID() {
        return nextID++;
    }

    /**
     * Create a new <code>UniqueEntry</code> that can service as a
     * template to retrieve the passed UniqueEntry (or subclass) from
     * a JavaSpace
     */
    public NBEUniqueEntry(UniqueEntry entry) {
        originatingHostVM = entry.originatingHostVM;
        entryID = entry.entryID;
    }

    @Override
    public String toString() {
        return "NBEUniqueEntry [entryID=" + entryID + ", originatingHostVM="
                + originatingHostVM + ", toString()=" + super.toString() + "]";
    }
    
 
}
