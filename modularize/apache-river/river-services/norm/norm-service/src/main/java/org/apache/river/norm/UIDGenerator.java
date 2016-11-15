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
package org.apache.river.norm;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for generating locally unique IDs.
 *
 * @author Sun Microsystems, Inc.
 */
class UIDGenerator {
    /**
     * Value of the next ID
     */
    private final AtomicLong nextID = new AtomicLong();

    /**
     * Generate ID, the returned ID is unique.
     */
    long newID() {
        return nextID.getAndIncrement();
    }

    /**
     * Used during log recovery to update the generator that a given ID
     * is in use.  This method performs no locking.
     * @param ID ID that is in use
     */
    void inUse(long ID) {
        long nID = nextID.get();
        while (ID >= nID){
            boolean success = nextID.compareAndSet(nID, ID + 1);
            if (success) break;
	    nID = nextID.get();
        }
    }
}
