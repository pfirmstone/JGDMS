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

package org.apache.river.mercury;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import net.jini.id.Uuid;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;

/**
 * Simple struct to hold the <code>Uuid</code> for a new
 * <code>RemoteEventIterator</code> instance and the first batch of
 * data. 
 */
@AtomicSerial
class RemoteEventIteratorData implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * <code>Uuid</code> for iteration.
     */
    final Uuid uuid;

    /** Initial set of entries */
    final Collection<RemoteEventData> events;

    /**
     * Creates a new RemoteEventIteratorData instance.
     * @param uuid value of <code>uuid</code> field.
     * @param events value of <code>events</code> field.
     */
    RemoteEventIteratorData(Uuid uuid, Collection<RemoteEventData> events) {
	this.uuid = uuid;
	this.events = events;
    }
    
    RemoteEventIteratorData(GetArg arg) throws IOException{
	this(arg.get("uuid", null, Uuid.class),
	     Valid.copyCol(
		 arg.get("events", null, Collection.class),
		 new ArrayList<RemoteEventData>(),
		 RemoteEventData.class
	     )
	);
    }
}
