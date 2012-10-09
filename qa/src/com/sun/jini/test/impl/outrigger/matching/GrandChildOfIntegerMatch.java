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
package com.sun.jini.test.impl.outrigger.matching;


/**
 *
 * Simple JavaSpace entry class for testing JavaSpace matching
 *
 * 
 */
public class GrandChildOfIntegerMatch extends ChildOfIntegerMatch {

    /**
     * Required public zero arg constructor Entry classes need
     */
    public GrandChildOfIntegerMatch() {}

    /**
     * Create a new <code>GrandChildOfIntegerMatch</code>. Passes
     * <code>makeUnique</code> to super(). A true value will generate
     * an entry with a unique (originatingHostVM, entryID) pair.
     */
    public GrandChildOfIntegerMatch(boolean makeUnique) {
        super(makeUnique);
    }

    /**
     * Create a new <code>GrandChildOfIntegerMatch</code> that can
     * service as a template to retrieve the passed
     * GrandChildOfIntegerMatch (or subclass) from a JavaSpace
     */
    public GrandChildOfIntegerMatch(ChildOfIntegerMatch entry) {

        /*
         * Note because ChildOfIntergerMatch does not add any fields
         * this is very boaring.
         */
        super(entry);
    }
}
