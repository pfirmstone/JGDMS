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
package org.apache.river.test.spec.javaspace.conformance;

// net.jini
import net.jini.core.entry.Entry;


/**
 * Simple implementation of Entry interface.
 *
 * @author Mikhail A. Markov
 */
public class SimpleEntry implements Entry, Cloneable {

    /** Field #1: <code>String</code> */
    public String name;

    /** Field #2: <code>Integer</code> */
    public Integer id;

    /**
     * Default constructor.
     * All fields are set to <code>null</code>.
     */
    public SimpleEntry() {
        name = null;
        id = null;
    }

    /**
     * Creates a new SimpleEntry with the given name and id,
     * specified as <code>int</code>.
     */
    public SimpleEntry(String name, int id) {
        this.name = name;
        this.id = new Integer(id);
    }

    /**
     * Creates a new SimpleEntry with the given name and id,
     * specified as <code>Integer</code>.
     */
    public SimpleEntry(String name, Integer id) {
        this.name = name;
        this.id = id;
    }

    /**
     * Creates a SimpleEntry from another one.
     */
    public SimpleEntry(SimpleEntry se) {
        this.name = new String(se.name);
        this.id = new Integer(se.id.intValue());
    }

    /**
     * Verifies that a SimpleEntry matches a template(this SimpleEntry).
     * @param se SimpleEntry needed to be checked.
     * @return Result of comparing.
     */
    public boolean implies(SimpleEntry se) {
        if (se == null) {
            return false;
        }
        return ((name == null || name.equals(se.name))
                && (id == null || id.equals(se.id)));
    }

    /**
     * Compare this SimpleEntry with another one.
     * @param o SimpleEntry needed to be checked.
     * @return Result of comparing.
     */
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if(!(o instanceof SimpleEntry)) {
            return false;
        }
        SimpleEntry se = (SimpleEntry)o;

        if (name != null) {
            if (id != null) {
                return (name.equals(se.name) && id.equals(se.id));
            } else {
                return (name.equals(se.name) && se.id == null);
            }
        } else {
            if (id != null) {
                return (se.name == null && id.equals(se.id));
            } else {
                return (se.name == null && se.id == null);
            }
        }
    }

    /**
     * Provides hash code for the object
     * @return hash code
     */
    public int hashCode() {
        int idHashCode = id==null? 0 : id.hashCode();
        int nameHashCode = name==null? 0 : name.hashCode();
        return idHashCode ^ nameHashCode;
    }

    /**
     * Creates the string representation of this SimpleEntry.
     * @return The string representation.
     */
    public String toString() {
        return "SimpleEntry: [name = " + name + ", id = " + id + "]";
    }

    /**
     * Returns a clone of this entry.
     *
     * @exception CloneNotSupportedException
     *         Is thrown when an instance of a class cannot be cloned.
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
