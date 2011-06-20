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

package com.sun.jini.jeri.internal.runtime;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import net.jini.id.Uuid;

/**
 * 
 * @since 2.2.0
 * @author Peter Firmstone.
 */
class Lease {

    private final Uuid clientID;
    private final Set<Target> notifySet = new HashSet<Target>(3);
    // guarded?
    private long expiration;
    // guarded by leaseTable lock
    /* Once all targets have been removed, Lease is locked for removal.
     */
    private volatile boolean lockForRemoval;

    Lease(Uuid clientID, long duration) {
        super();
        this.clientID = clientID;
        expiration = System.currentTimeMillis() + duration;
        lockForRemoval = false;
    }

    Uuid getClientID() {
        return clientID;
    }

    boolean renew(long duration) {
        synchronized (this) {
            if (lockForRemoval) {
                return false;
            }
            long newExpiration = System.currentTimeMillis() + duration;
            if (newExpiration > expiration) {
                expiration = newExpiration;
            }
            return true;
        }
    }

    boolean notifyIfExpired(long now) {
        boolean expired = false;
        synchronized (this) {
            expired = expiration < now;
            if (expired) {
                lockForRemoval = true;
                Iterator<Target> i = notifySet.iterator();
                while (i.hasNext()) {
                    Target t = i.next();
                    t.leaseExpired(clientID);
                    i.remove();
                }
            }
        }
        return expired;
    }

    void remove(Target target) {
        synchronized (this) {
            notifySet.remove(target);
        }
    }

    boolean add(Target target) {
        synchronized (this) {
            if (lockForRemoval) {
                return false;
            }
            notifySet.add(target);
            return true;
        }
    }
}
