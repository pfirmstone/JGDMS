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
package org.apache.river.test.impl.norm;

import java.io.ObjectInputStream;
import java.io.IOException;

/**
 * Subclass of LocalLease that after a preset number of deserilizations
 * sets the expiration to 0
 */
class DestructingLocalLease extends LocalLease {
    /**
     * How many deseriailzations to allow setting expiration to 0
     */
    private long untilDestruction;

    /**
     * Create a destructing local lease with the specified initial
     * expiration time
     * @param initExp    Initial expiration time
     * @param renewLimit Limit on long each renewal request can be for
     * @param bundle     Two <code>LocalLeases</code> with the same bundle
     * @param id         Uniuque ID for this lease
     *                   value can be batched together
     * @param count      Number of deserilizations to allow before 
     *                   setting expiration to zero
     */
    DestructingLocalLease(long initExp, long renewLimit, long bundle, long id,
			  long count) 
    {
	super(initExp, renewLimit, bundle, id);
	untilDestruction = count;
    }
	    
    /**
     * If larger than zero Decrement untilDestruction and if equals
     * zero afterward, zero expiration time
     */
    private synchronized void readObject(ObjectInputStream stream)
	throws IOException, ClassNotFoundException
    {
	stream.defaultReadObject();
	if (untilDestruction > 0) {
	    untilDestruction--;

	    if (untilDestruction == 0) {
		setExpiration(0);
		System.err.println("Lease zeroed");
	    }
	}
    }
}
