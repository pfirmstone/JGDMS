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

import java.io.Serializable;
import java.util.Map;
import net.jini.id.Uuid;

/**
 * Base class for the objects Norm logs as delta for each state
 * changing operation.
 *
 * @author Sun Microsystems, Inc.
 */
abstract class LoggedOperation implements Serializable {
    private static final long serialVersionUID = 2;

    /** 
     * The <code>Uuid</code> of the set this operation was on
     * @serial
     */
    protected Uuid setID;

    /**
     * Simple constructor
     * @param setID The <code>Uuid</code> for the set this operation is on
     */
    protected LoggedOperation(Uuid setID) {
	this.setID = setID;
    }

    /**
     * Update state of the passed <code>Map</code> of
     * <code>LeaseSet</code>s to reflect the state of server after
     * this operation was performed.
     * @throws StoreException if there is a problem applying the update
     */
    abstract void apply(Map setTable) throws StoreException;
}
