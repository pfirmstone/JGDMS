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
package net.jini.entry;

import java.lang.reflect.*;

import java.util.ArrayList;
import java.util.WeakHashMap;
import net.jini.core.entry.CloneableEntry;
import net.jini.core.entry.Entry;

/**
 * An abstract implementation of {@link Entry} that provides useful
 * implementations of <code>equals</code>, <code>hashCode</code>, and
 * <code>toString</code>. Implementations of the <code>Entry</code>
 * interface may, but are not required to, extend this class. <p>
 *
 * The methods of this class consult the <em>entry fields</em> of the
 * entries they process. The entry fields of an <code>Entry</code> are
 * its public, non-primitive, non-static, non-transient, non-final
 * fields.<p>
 *
 * @author Sun Microsystems, Inc.
 * @see Entry 
 */
public abstract class AbstractEntry implements CloneableEntry {
    static final long serialVersionUID = 5071868345060424804L;

    /**
     * Creates an instance of this class.
     */
    protected AbstractEntry() { }
    
    /**
     * Clone has been implemented to allow utilities such as
     * {@link net.jini.lookup.ServiceDiscoveryManager} to avoid sharing 
     * internally stored instances with client code.
     * 
     * Entry's that have mutable fields, for example arrays or collections,
     * should override this method, call super.clone(), then safely copy
     * any mutable fields before returning.
     * 
     * @return a clone of the original Entry
     * @since 3.0.0
     */
    public Entry clone() 
    {
        try {
            Entry clone = (Entry) super.clone();
            return clone;
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError();
        }
    }
    
    /**
     * Compares this <code>AbstractEntry</code> to the specified
     * object. If <code>other</code> is <code>null</code> or not an
     * instance of {@link Entry} returns <code>false</code>,
     * otherwise returns the result of calling {@link #equals(Entry,Entry)
     * AbstractEntry.equals(this, (Entry) other)}.
     * @param other the object to compare this <code>AbstractEntry</code>
     *              against
     * @return <code>true</code> if this object is equivalent
     *         to <code>other</code> and <code>false</code> otherwise
     */
    public boolean equals(Object other) {
	if (!(other instanceof Entry))
	    return false;
	return equals(this, (Entry) other);
    }

    /**
     * Returns <code>true</code> if the two arguments are of the same
     * class and for each entry field <em>F</em>, the arguments'
     * values for <em>F</em> are either both <code>null</code> or the
     * invocation of <code>equals</code> on one argument's value for
     * <em>F</em> with the other argument's value for <em>F</em> as
     * its parameter returns <code>true</code>. Will also return
     * <code>true</code> if both arguments are <code>null</code>. In
     * all other cases an invocation of this method will return
     * <code>false</code>.<p>
     *
     * @param e1  an entry object to compare to e2
     * @param e2  an entry object to compare to e1
     * @return <code>true</code> if the two arguments are equivalent 
     */
    public static boolean equals(Entry e1, Entry e2) {
	if (e1 == e2)
	    return true;

	// Note, if both e1 and e2 are null the previous test would
	// have returned true.
	if (e1 == null || e2 == null)
	    return false;

	if (e1.getClass() != e2.getClass())
	    return false;

	Field[] fields = fieldInfo(e1);
	try {
	    // compare each field
	    for (int i = 0; i < fields.length; i++) {

		// f works for other since other is the same type as this
		Field f = fields[i];
		Object ov = f.get(e1);
		Object tv = f.get(e2);

		if (tv == ov)			// same obj or both null is OK
		    continue;
		if (tv == null || ov == null)	// if only one is null, not OK
		    return false;
		if (!tv.equals(ov))		// not equals is not OK
		    return false;
	    }
	    return true;
	} catch (IllegalAccessException e) {
	    // should never happen, all entry fields are public
	    throw new AssertionError(e);
	}
    }

    /**
     * Returns the result of calling {@link #hashCode(Entry)
     * AbstractEntry.hashCode(this)}.
     * @return the result of calling {@link #hashCode(Entry)
     * AbstractEntry.hashCode(this)}
     */
    public int hashCode() {
	return hashCode(this);
    }

    /**
     * Returns zero XORed with the result of invoking
     * <code>hashCode</code> on each of the argument's
     * non-<code>null</code> entry fields. Returns <code>0</code> if
     * the argument is <code>null</code>.
     *
     * @param entry the <code>Entry</code> for which to generate a
     *              hash code
     * @return a hash code formed by XORing the hash codes of
     *         <code>entry</code>'s non-<code>null</code> entry field,
     *         or <code>0</code> if <code>entry</code> is
     *         <code>null</code> 
     */
    public static int hashCode(Entry entry) {
	if (entry == null)
	    return 0;
	
	int hash = 0;
	Field[] fields = fieldInfo(entry);
	try {
	    for (int i = 0; i < fields.length; i++) {
		Object tv = fields[i].get(entry);
		if (tv != null)
		    hash ^= tv.hashCode();
	    }
	    return hash;
	} catch (IllegalAccessException e) {
	    // should never happen, all entry fields are public
	    throw new AssertionError(e);
	}
    }

    /**
     * Returns the result of calling {@link #toString(Entry)
     * AbstractEntry.toString(this)}.
     * @return the result of calling {@link #toString(Entry)
     * AbstractEntry.toString(this)}
     */
    public String toString() {
	return toString(this);
    }

    /**
     * Returns a <code>String</code> representation of its argument
     * that will contain the name of the argument's class and a
     * representation of each of the argument's entry fields. The
     * representation of each entry field will include the field's
     * name and a representation of its value. If passed
     * <code>null</code> will return the string <code>"null"</code>. <p>
     *
     * @param entry  an entry to represent as a String
     * @return a <code>String</code> representation of
     *         <code>entry</code> that contains the name of the
     *         <code>entry</code>'s class and a representation of each
     *         of <code>entry</code>'s entry fields
     */
    public static String toString(Entry entry) {
	if (entry == null)
	    return "null";

	Field[] fields = fieldInfo(entry);
	StringBuffer str = new StringBuffer(entry.getClass().getName());
	str.append('(');	// later matched with a ')'
	for (int i = 0; i < fields.length; i++) {
	    try {
		Field f = fields[i];
		if (i > 0)
		    str.append(',');
		str.append(f.getName());
		str.append('=');
		str.append(f.get(entry));
	    } catch (IllegalAccessException e) {
		// should never happen, all entry fields are public
		throw new AssertionError(e);
	    }
	}
	// now add the ending '('
	str.append(')');
	return str.toString();
    }

    private static WeakHashMap fieldArrays;

    /**
     * Calculate the list of usable fields for this type
     */
    private static Field[] fieldInfo(Entry entry) {
	Field[] fields = null;

	synchronized (AbstractEntry.class) {
	    if (fieldArrays == null)
		fieldArrays = new WeakHashMap();
	    else {
		fields = (Field[]) fieldArrays.get(entry.getClass());
		if (fields != null)
		    return fields;
	    }
	}

	/*
	 * Scan the array to see if we can use it or if we must build up
	 * a smaller array because we must skip some fields.  If so, we
	 * create an ArrayList and add the unskippable fields to it, and
	 * then fetch the array back out of it.
	 */
	final int SKIP_MODIFIERS =
	    (Modifier.STATIC | Modifier.TRANSIENT | Modifier.FINAL);
	fields = entry.getClass().getFields();
	ArrayList usable = null;
	for (int i = 0; i < fields.length; i++) {
	    // exclude this one?
	    if ((fields[i].getModifiers() & SKIP_MODIFIERS) != 0 ||
		(fields[i].getType().isPrimitive())) 
	    {
		if (usable == null) {		// first excluded: set up for it
		    usable = new ArrayList();	// allocate the list of usable
		    for (int j = 0; j < i; j++)	// earlier fields are usable
			usable.add(fields[j]);
		}
	    } else {				// not excluded
		if (usable != null)		// tracking usable fields?
		    usable.add(fields[i]);
	    }
	}
	if (usable != null)
	    fields = (Field[]) usable.toArray(new Field[usable.size()]);

	synchronized (AbstractEntry.class) {
	    // We could check to make sure someone else 
	    // has not already stuck a value for entry.getClass() in,
	    // fieldArrays but there should be no harm in an overwrite
	    // and if anything likely to be less efficient
	    fieldArrays.put(entry.getClass(), fields);
	}

	return fields;
    }
}
