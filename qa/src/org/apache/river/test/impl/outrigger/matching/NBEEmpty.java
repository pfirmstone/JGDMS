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
package org.apache.river.test.impl.outrigger.matching;


/**
 *
 * Simple JavaSpace entry class for testing JavaSpace matching
 *
 * @author John W. F. McClain
 */
public class NBEEmpty extends NBEUniqueEntry {

    /**
     * Required public zero arg constructor Entry classes need
     */
    public NBEEmpty() {}

    /**
     * Create a new <code>NBEEmpty</code>. Passes
     * <code>makeUnique</code> to super(). A true value will generate
     * an entry with a unique (originatingHostVM, entryID) pair.
     *
     * @see NBEUniqueEntry#NBEUniqueEntry(boolean)
     */
    public NBEEmpty(boolean makeUnique) {
        super(makeUnique);
    }
}
