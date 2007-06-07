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
package com.sun.jini.norm;

import java.util.Map;
import net.jini.id.Uuid;

/**
 * Base class for logged operations that do not create or destroy a set.
 *
 * @author Sun Microsystems, Inc.
 */
abstract class LeaseSetOperation extends LoggedOperation {
    private static final long serialVersionUID = 1L;

    /**
     * Simple constructor
     * @param setID The <code>Uuid</code> for the set this operation is on
     */
    protected LeaseSetOperation(Uuid setID) {
	super(setID);
    }

    // Inherit java doc from super type
    void apply(Map setTable) throws StoreException {
	final LeaseSet set = (LeaseSet) setTable.get(setID);
	if (set != null) {
	    apply(set);
	} else {
	    throw new CorruptedStoreException("Asked to update set " +
	        setID + " but we have no record of that set");
	}
    }

    /**
     * Update the state of the passed <code>LeaseSet</code> to reflect the
     * state of the server after this operation was performed.
     * @throws StoreException if there is a problem applying the update
     */
    abstract void apply(LeaseSet set) throws StoreException;
}
