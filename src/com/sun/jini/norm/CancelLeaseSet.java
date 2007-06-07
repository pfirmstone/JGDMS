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
 * Class that logs the removal of a set from the service
 *
 * @author Sun Microsystems, Inc.
 */
class CancelLeaseSet extends LoggedOperation {
    private static final long serialVersionUID = 1L;

    /**
     * Simple constructor
     * @param setID The <code>Uuid</code> for the set this operation is on
     */
    protected CancelLeaseSet(Uuid setID) {
	super(setID);
    }

    // Inherit java doc from super type
    void apply(Map setTable) {
	setTable.remove(setID);
    }
}
