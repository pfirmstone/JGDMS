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
package com.sun.jini.outrigger;

import net.jini.id.Uuid;

/**
 * Simple struct to hold the <code>Uuid</code> for a new
 * <code>MatchSet</code> instance and the first batch of
 * data. Also holds initial lease time.
 */
class MatchSetData implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * <code>Uuid</code> for iteration and associated lease. 
     * <code>null</code> if the entire iteration is in 
     * <code>reps</code>.
     */
    final Uuid uuid;

    /** Intial set of entries */
    final EntryRep[] reps;

    /** 
     * Initial lease time. Negative if the entire iteration is in
     * <code>reps</code>.
     */
    final long intialLeaseDuration;

    /**
     * Creates a new MatchSetData instance.
     * @param uuid value of <code>uuid</code> field.
     * @param reps value of <code>reps</code> field.
     * @param intialLeaseDuration value of <code>intialLeaseDuration</code> 
     *        field.
     */
    MatchSetData(Uuid uuid, EntryRep[] reps, long intialLeaseDuration) {
	this.uuid = uuid;
	this.reps = reps;
	this.intialLeaseDuration = intialLeaseDuration;
    }
}
