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

/**
 * A description of a template's parameters.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see EntryHandle#descFor
 * @see EntryHandle
 */
class EntryHandleTmplDesc {
    /** The hash value for the template itself, already masked */
    final long hash;

    /**
     * The mask for EntryHandle's hash codes -- if <code>handle.hash &
     * mask != tmplDesc.hash</code>, then the template doesn't match the
     * object held by the handle.
     */
    final long mask;
    
    EntryHandleTmplDesc (long hash, long mask){
        this.hash = hash;
        this.mask = mask;
    }
    
    public String toString() {
	return "0x" + Long.toHexString(hash) + " & 0x" + Long.toHexString(mask);
    }
}
