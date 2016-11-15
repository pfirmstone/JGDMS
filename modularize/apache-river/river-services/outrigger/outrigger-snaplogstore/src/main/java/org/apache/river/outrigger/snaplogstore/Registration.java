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
package org.apache.river.outrigger.snaplogstore;

import org.apache.river.outrigger.StorableObject;
import org.apache.river.outrigger.StorableResource;

/**
 * Wrapper for outrigger event registrations. An event registration
 * consists of a leased registration and the matching template.
 */
class Registration extends Resource {
    static final long serialVersionUID = 2L;

    private final BaseObject[] templates;

    private final String type;

    Registration(StorableResource chit, String type, StorableObject[] ts) {
	super(chit);
	this.type = type;	
	templates = new BaseObject[ts.length];
	for (int i=0; i<templates.length; i++) {
	    templates[i] = new BaseObject(ts[i]);
	}
    }

    BaseObject[] getTemplates() {
	return templates;
    }

    String getType() {
	return type;
    }
}
