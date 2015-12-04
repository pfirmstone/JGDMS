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
public class GrandChildOfStringMatch extends ChildOfStringMatch {
    public short[] arrayOfShorts = null;

    /**
     * Required public zero arg constructor Entry classes need
     */
    public GrandChildOfStringMatch() {}

    /**
     * Create a new <code>GrandChildOfStringMatch</code>.  Passes
     * <code>makeUnique</code> to super().  A true value will generate
     * an entry with a unique (originatingHostVM, entryID) pair.
     *
     * @see UniqueEntry#UniqueEntry(boolean)
     */
    public GrandChildOfStringMatch(boolean makeUnique) {
        super(makeUnique);
    }

    /**
     * Create a new <code>ChildOfStringMatch</code> that can service as a
     * template to retrieve the passed ChildOfStringMatch (or subclass) from
     * a JavaSpace
     */
    public GrandChildOfStringMatch(GrandChildOfStringMatch entry) {
        super(entry);
        arrayOfShorts = entry.arrayOfShorts;
    }

    /** fields to test edge cases in net.jini.core.entry.Entry spec */
    static final int ENUMFIELD = 4;
    final float ANOTHERFIELD = 4.6f;
    static boolean bool;

    protected boolean arraysEqual(short[] other) {

        // Covers one or both being null :-)
        if (other == arrayOfShorts) {
            return true;
        }

        if (other.length != arrayOfShorts.length) {
            return false;
        }

        for (int i = 0; i < arrayOfShorts.length; i++) {
            if (other[i] != arrayOfShorts[i]) {
                return false;
            }
        }
        return true;
    }

    public boolean equals(Object rhs) {
        if (!rhs.getClass().equals(getClass())) {
            return false;
        }
        GrandChildOfStringMatch other = (GrandChildOfStringMatch) rhs;

        if (aFloat == null && other.aFloat != null) {
            return false;
        }

        if (aString == null && other.aString != null) {
            return false;
        }

        if (originatingHostVM == null && other.originatingHostVM != null) {
            return false;
        }

        if (entryID == null && other.entryID != null) {
            return false;
        }
        return (aFloat.equals(other.aFloat) && aString.equals(other.aString)
                && originatingHostVM.equals(other.originatingHostVM)
                && entryID.equals(other.entryID)
                && arraysEqual(other.arrayOfShorts));
    }

    public int hashCode() {
        return aFloat.hashCode() ^ aString.hashCode() ^
                originatingHostVM.hashCode() ^ entryID.hashCode() ^
                arrayOfShorts.length;
    }
}
