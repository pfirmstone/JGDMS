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

import java.io.IOException;
import java.util.Map;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Class that logs the creation of a set by the service
 *
 * @author Sun Microsystems, Inc.
 */
@AtomicSerial
class CreateLeaseSet extends LoggedOperation {
    private static final long serialVersionUID = 1L;

    /**
     * A copy of the set that was created
     * @serial
     */
    private LeaseSet set;

    /**
     * Simple constructor
     * @param set The set just created
     */
    protected CreateLeaseSet(LeaseSet set) {
	super(set.getUuid());
	this.set = set;
    }
    
    /**
     * Constructor for @AtomicSerial
     * @param arg serial arguments.
     * @throws IOException 
     */
    protected CreateLeaseSet(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg.get("set", null, LeaseSet.class));
    }

    // Inherit java doc from super type
    void apply(Map setTable) {
	setTable.put(setID, set);
    }
}
