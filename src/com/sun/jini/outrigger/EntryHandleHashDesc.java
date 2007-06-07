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

/**
 * Used to hold the description of the parameters of the hash.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see EntryHandle
 * @see EntryHandle#hashFor(EntryRep,int,EntryHandleHashDesc)
 */
class EntryHandleHashDesc {
    /** Number of bits allocated in the hash for each field */
    int bitsPerField;

    /** How many fields are used in the hash? */
    int fieldsInHash;

    /** A mask with the lower <code>bitsPerField</code> bits set */
    long mask;

    public String toString() {
	return bitsPerField + " bits, " + fieldsInHash + " fields, " +
	       "mask = 0x" + Long.toHexString(mask);
    }
}
