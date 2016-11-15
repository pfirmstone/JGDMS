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
package org.apache.river.outrigger;

import java.util.Queue;

/**
 * Base class for handles to Entries and templates.
 *
 * @author Sun Microsystems, Inc.
 *
 */
abstract class BaseHandle  {
    private final EntryRep rep;		// the rep this handle manages
    private final Queue<? extends BaseHandle> content;

    /**
     * Create a new handle
     * 
     * @param content thread safe Queue from which this BaseHandle will be removed
     * atomically, BaseHandle is not added to content during construction,
     * as it would allow this to escape.
     * @param rep EntryRep managed by this BaseHandle.
     */
    protected BaseHandle(EntryRep rep, Queue<? extends BaseHandle> content) {
	this.rep = rep;
        this.content = content;
    }

    /**
     * Return the handle's <code>EntryRep</code> object.
     */
    EntryRep rep() {
	return rep;
    }

    // inherit doc comment
    public String classFor() {
	return rep.classFor();
    }
    
    public abstract boolean removed();
    
    /**
     * Overridden and called from subclass.
     * 
     * @return true if removed.
     */
    public boolean remove(){
        return content.remove(this);
    }
}



