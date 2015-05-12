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
package org.apache.river.test.spec.javaspace.conformance.snapshot;

// java.rmi
import java.rmi.RemoteException;

// net.jini
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.space.JavaSpace;

// org.apache.river.qa
import org.apache.river.test.spec.javaspace.conformance.NotifyCounter;


/**
 * This auxilary class listens for notify events and counts them.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotNotifyCounter extends NotifyCounter {

    /** Snapshot of template */
    private final Entry snapshot;

    /**
     * Constructor with no arguments, set template to null, and lease time to 0.
     *
     * @exception RemoteException
     *         If an error occured while trying to export this object.
     */
    public SnapshotNotifyCounter() throws RemoteException {
        this(null, 0, null);
    }

    /**
     * Constructor to init fields of the class and register class itself.
     *
     * @param template Template for which this class counts events.
     * @param leaseTime
     * @param space Space in which we will create snapshot
     *
     * @exception RemoteException
     *         If an error occured while trying to export this object.
     */
    public SnapshotNotifyCounter(Entry template, long leaseTime,
            JavaSpace space) throws RemoteException {
        super(template, leaseTime);

        if (space != null) {
            snapshot = space.snapshot(template);
        } else {
            snapshot = null;
        }
    }

    /**
     * Returns snapshot of template for which this class counts events.
     *
     * @return Snapshot.
     */
    public Entry getSnapshot() {
        return snapshot;
    }

    /**
     * Creates the string representation of this counter.
     *
     * @return The string representation.
     */
    public String toString() {
        String leaseStr;

        if (leaseTime == Lease.FOREVER) {
            leaseStr = "Lease.FOREVER";
        } else if (leaseTime == Lease.ANY) {
            leaseStr = "Lease.ANY";
        } else {
            leaseStr = "" + leaseTime;
        }
        return "SnapshotNotifyCounter: (snapshot for template " + template
                + ", lease time = " + leaseStr + ")";
    }
}
