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

package com.sun.jini.lookup.entry;

import net.jini.core.entry.Entry;
import net.jini.lookup.entry.ServiceControlled;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.rmi.MarshalledObject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Some simple utilities for manipulating lookup service attributes.
 * These are not high-performance operations; it is expected that
 * they are called relatively infrequently.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class LookupAttributes {

    private LookupAttributes() {}

    /** Comparator for sorting fields */
    private static final FieldComparator comparator = new FieldComparator();
    private static final Class[] noArg = new Class[0];

    /**
     * Returns a new array containing the elements of the 
     * <code>addAttrSets</code> parameter (that are not duplicates of
     * any of the elements already in the <code>attrSets</code> parameter)
     * added to the elements of <code>attrSets</code>. The parameter
     * arrays are not modified.
     * <p>
     * Note that attribute equality is defined in terms of 
     * <code>MarshalledObject.equals</code> on field values.  The 
     * parameter arrays are not modified.
     * <p>
     * Throws an <code>IllegalArgumentException</code> if any element of
     * <code>addAttrSets</code> is not an instance of a valid
     * <code>Entry</code> class (the class is not public, or does not have a
     * no-arg constructor, or has primitive public non-static non-final
     * fields).
     */
    public static Entry[] add(Entry[] attrSets, Entry[] addAttrSets) {
	return add(attrSets, addAttrSets, false);
    }

    /**
     * Returns a new array containing the elements of the 
     * <code>addAttrSets</code> parameter (that are not duplicates of
     * any of the elements already in the <code>attrSets</code> parameter)
     * added to the elements of <code>attrSets</code>. The parameter
     * arrays are not modified.
     * <p>
     * Note that attribute equality is defined in terms of 
     * <code>MarshalledObject.equals</code> on field values.  The 
     * parameter arrays are not modified.
     * <p>
     * If the <code>checkSC</code> parameter is <code>true</code>,
     * then a <code>SecurityException</code> is thrown if any elements
     * of the <code>addAttrSets</code> parameter are instanceof 
     * <code>ServiceControlled</code>.
     * <p>
     * Throws an <code>IllegalArgumentException</code> if any element of
     * <code>addAttrSets</code> is not an instance of a valid
     * <code>Entry</code> class (the class is not public, or does not have a
     * no-arg constructor, or has primitive public non-static non-final
     * fields).
     */
    public static Entry[] add(Entry[] attrSets,
			      Entry[] addAttrSets,
			      boolean checkSC)
    {
	check(addAttrSets, false);
	Entry[] newSets = concat(attrSets, addAttrSets);
	for (int i = newSets.length; --i >= attrSets.length; ) {
	    if (checkSC)
		check(newSets[i]);
	    if (isDup(newSets, i))
		newSets = delete(newSets, i);
	}
	return newSets;
    }

    /**
     * Returns a new array that contains copies of the attributes in the
     * <code>attrSets</code> parameter, modified according to the contents
     * of both the <code>attrSetTmpls</code> parameter and the 
     * <code>modAttrSets</code> parameter. The parameter arrays and
     * their <code>Entry</code> instances are not modified.
     * <p>
     * Throws an <code>IllegalArgumentException</code> if any element of
     * <code>attrSetTmpls</code> or <code>modAttrSets</code> is not an
     * instance of a valid <code>Entry</code> class (the class is not public,
     * or does not have a no-arg constructor, or has primitive public
     * non-static non-final fields).
     */
    public static Entry[] modify(Entry[] attrSets,
				 Entry[] attrSetTmpls,
				 Entry[] modAttrSets)
    {
	return modify(attrSets, attrSetTmpls, modAttrSets, false);
    }

    /**
     * Returns a new array that contains copies of the attributes in the
     * <code>attrSets</code> parameter, modified according to the contents
     * of both the <code>attrSetTmpls</code> parameter and the 
     * <code>modAttrSets</code> parameter. The parameter arrays and
     * their <code>Entry</code> instances are not modified.
     * <p>
     * If the <code>checkSC</code> parameter is <code>true</code>, then a
     * <code>SecurityException</code> is thrown if any elements of the
     * <code>attrSets</code> parameter that would be deleted or modified
     * are instanceof <code>ServiceControlled</code>.
     * <p>
     * Throws an <code>IllegalArgumentException</code> if any element of
     * <code>attrSetTmpls</code> or <code>modAttrSets</code> is not an
     * instance of a valid <code>Entry</code> class (the class is not public,
     * or does not have a no-arg constructor, or has primitive public
     * non-static non-final fields).
     */
    public static Entry[] modify(Entry[] attrSets,
				 Entry[] attrSetTmpls,
				 Entry[] modAttrSets,
				 boolean checkSC)
    {
	if (attrSetTmpls.length != modAttrSets.length)
	    throw new IllegalArgumentException(
				       "attribute set length mismatch");
	for (int i = modAttrSets.length; --i >= 0; ) {
	    if (modAttrSets[i] != null &&
		!isAssignableFrom(modAttrSets[i].getClass(),
				  attrSetTmpls[i].getClass()))
		throw new IllegalArgumentException(
					   "attribute set type mismatch");
	}
	check(attrSetTmpls, false);
	check(modAttrSets, true);
	attrSets = (Entry[])attrSets.clone();
	for (int i = attrSets.length; --i >= 0; ) {
	    Entry pre = attrSets[i];
	    for (int j = attrSetTmpls.length; --j >= 0; ) {
		if (matches(attrSetTmpls[j], pre)) {
		    if (checkSC)
			check(pre);
		    Entry mods = modAttrSets[j];
		    if (mods == null) {
			attrSets = delete(attrSets, i);
			break;
		    } else {
			attrSets[i] = update(attrSets[i], mods);
		    }
		}
	    }
	}
	for (int i = attrSets.length; --i >= 0; ) {
	    if (isDup(attrSets, i))
		attrSets = delete(attrSets, i);
	}
	return attrSets;
    }

    /**
     * Test that two entries are the same type, with the same
     * public fields. Attribute equality is defined in terms of
     * <code>MarshalledObject.equals</code> on field values.
     */
    public static boolean equal(Entry e1, Entry e2) {
	if (!equal(e1.getClass(), e2.getClass()))
	    return false;
	Field[] fields1 = getFields(e1);
	Field[] fields2 = getFields(e2, e1, fields1);
	try {
	    for (int i = fields1.length; --i >= 0; ) {
		if (!equal(fields1[i].get(e1), fields2[i].get(e2)))
		    return false;
	    }
	} catch (IllegalAccessException ex) {
	    throw new IllegalArgumentException(
				       "unexpected IllegalAccessException");
	}
	return true;
    }

    /** Tests that two <code>Entry[]</code> arrays are the same. */
    public static boolean equal(Entry[] attrSet1, Entry[] attrSet2) {
	return contains(attrSet1, attrSet2) && contains(attrSet2, attrSet1);
    }

    /**
     * Test if the parameter <code>tmpl</code> is the same class as, or a
     * superclass of, the parameter <code>e</code>, and that every 
     * non-<code>null</code> public field of <code>tmpl</code> is the
     * same as the corresponding field of <code>e</code>. Attribute equality
     * is defined in terms of <code>MarshalledObject.equals</code> on
     * field values.
     */
    public static boolean matches(Entry tmpl, Entry e) {
	if (!isAssignableFrom(tmpl.getClass(), e.getClass()))
	    return false;
	Field[] tfields = getFields(tmpl);
	Field[] efields = getFields(e, tmpl, tfields);
	try {
	    for (int i = tfields.length; --i >= 0; ) {
		Object val = tfields[i].get(tmpl);
		if (val != null && !equal(val, efields[i].get(e)))
		    return false;
	    }
	} catch (IllegalAccessException ex) {
	    throw new IllegalArgumentException
                                      ("unexpected IllegalAccessException");
	}
	return true;
    }

    /**
     * Throws an <code>IllegalArgumentException</code> if any element of
     * the array is not an instance of a valid <code>Entry</code> class
     * (the class is not public, or does not have a no-arg constructor, or
     * has primitive public non-static non-final fields).  If
     * <code>nullOK</code> is <code>false</code>, and any element of the
     * array is <code>null</code>, a <code>NullPointerException</code>
     * is thrown.
     */
    public static void check(Entry[] attrs, boolean nullOK) {
	for (int i = attrs.length; --i >= 0; ) {
	    Entry e = attrs[i];
	    if (e == null && nullOK)
		continue;
	    Class c = e.getClass();
	    if (!Modifier.isPublic(c.getModifiers()))
		throw new IllegalArgumentException("entry class " +
						   c.getName() +
						   " is not public");
	    try {
		c.getConstructor(noArg);
	    } catch (NoSuchMethodException ex) {
		throw new IllegalArgumentException("entry class " +
						   c.getName() +
			        " does not have a public no-arg constructor");
	    }
	    Field[] fields = c.getFields();
	    for (int j = fields.length; --j >= 0; ) {
		if ((fields[j].getModifiers() &
		     (Modifier.STATIC|Modifier.FINAL|Modifier.TRANSIENT)) == 0
		    &&
		    fields[j].getType().isPrimitive())
		    throw new IllegalArgumentException("entry class " +
						       c.getName() +
						   " has a primitive field");
	    }
	}
    }

    /** 
     * Throws a <code>SecurityException</code> if parameter <code>e</code>
     * is instanceof <code>ServiceControlled</code>.
     */
    private static void check(Entry e) {
	if (e instanceof ServiceControlled)
	    throw new SecurityException
               ("attempt to add or modify a ServiceControlled attribute set");
    }

    /**
     * Test if the set at the given <code>index</code> is equal to any
     * other set earlier in the <code>Entry[]</code> array parameter.
     */
    private static boolean isDup(Entry[] attrs, int index) {
	Entry set = attrs[index];
	for (int i = index; --i >= 0; ) {
	    if (equal(set, attrs[i]))
		return true;
	}
	return false;
    }

    /**
     * Return a new entry that, for each non-<code>null</code> field of
     * the parameter <code>mods</code>, has the same field value as
     * <code>mods</code>, else the same field value as the parameter 
     * <code>e</code>.
     */
    private static Entry update(Entry e, Entry mods) {
	try {
	    Entry ec = (Entry)e.getClass().newInstance();
	    Field[] mfields = getFields(mods);
	    Field[] efields = getFields(e, mods, mfields);
	    for (int i = efields.length; --i >= 0; ) {
		efields[i].set(ec, efields[i].get(e));
	    }
	    for (int i = mfields.length; --i >= 0; ) {
		Object val = mfields[i].get(mods);
		if (val != null)
		    efields[i].set(ec, val);
	    }
	    return ec;
	} catch (InstantiationException ex) {
	    throw new IllegalArgumentException(
				       "unexpected InstantiationException");
	} catch (IllegalAccessException ex) {
	    throw new IllegalArgumentException(
				       "unexpected IllegalAccessException");
	}
    }

    /** 
     * Returns <code>true</code> if the two input objects are the same in
     * <code>MarshalledObject</code> form, <code>false</code> otherwise.
     */
    private static boolean equal(Object o1, Object o2) {
	if (o1 == o2)
	    return true;
	if (o1 == null || o2 == null)
	    return false;
	Class c = o1.getClass();
	if (c == String.class ||
	    c == Integer.class ||
	    c == Boolean.class ||
	    c == Character.class ||
	    c == Long.class ||
	    c == Float.class ||
	    c == Double.class ||
	    c == Byte.class ||
	    c == Short.class)
	    return o1.equals(o2);
	try {
	    return new MarshalledObject(o1).equals(new MarshalledObject(o2));
	} catch (IOException ex) {
	    throw new IllegalArgumentException("unexpected IOException");
	}
    }

    /**
     * Tests if two classes are equal, using the class equivalence
     * semantics of the lookup service: same name.
     */
    private static boolean equal(Class c1, Class c2) {
	return c1.equals(c2) || c1.getName().equals(c2.getName());
    }

    /**
     * Tests if class <code>c1</code> is equal to, or a superclass of,
     * class <code>c2</code>, using the class equivalence semantics of
     * the lookup service: same name.
     */
    private static boolean isAssignableFrom(Class c1, Class c2) {
	if (c1.isAssignableFrom(c2))
	    return true;
	String n1 = c1.getName();
	for (Class sup = c2; sup != null; sup = sup.getSuperclass()) {
	    if (n1.equals(sup.getName()))
		return true;
	}
	return false;
    }

    /**
     * Returns public fields, in super to subclass order, sorted
     * alphabetically within a given class.
     */
    private static Field[] getFields(Entry e) {
	Field[] fields = e.getClass().getFields();
	Arrays.sort(fields, comparator);
	int len = 0;
	for (int i = 0; i < fields.length; i++) {
	    if ((fields[i].getModifiers() &
		 (Modifier.STATIC|Modifier.FINAL|Modifier.TRANSIENT)) == 0)
		fields[len++] = fields[i];
	}
	if (len < fields.length) {
	    Field[] nfields = new Field[len];
	    System.arraycopy(fields, 0, nfields, 0, len);
	    fields = nfields;
	}
	return fields;
    }

    /** Comparator for sorting fields. */
    private static class FieldComparator implements Comparator {
	public FieldComparator() {}

	/**
	 * Sorts superclass fields before subclass fields, and sorts
	 * fields alphabetically within a given class.
	 */
	public int compare(Object o1, Object o2) {
	    Field f1 = (Field)o1;
	    Field f2 = (Field)o2;
	    if (f1 == f2)
		return 0;
	    if (f1.getDeclaringClass() == f2.getDeclaringClass())
		return f1.getName().compareTo(f2.getName());
	    if (f1.getDeclaringClass().isAssignableFrom(
						     f2.getDeclaringClass()))
		return -1;
	    return 1;
	}
    }

    /**
     * Returns the public fields of the parameter <code>e</code>.  If 
     * <code>e</code> and parameter <code>oe</code> have the same class,
     * then returns parameter <code>ofields</code>, otherwise ensures that
     * <code>e</code> has at least as many fields as does parameter
     * <code>ofields</code>.
     */
    private static Field[] getFields(Entry e, Entry oe, Field[] ofields) {
	if (e.getClass().equals(oe.getClass()))
	    return ofields;
	Field[] fields = getFields(e);
	if (fields.length < ofields.length)
	    throw new IllegalArgumentException("type mismatch");
	return fields;
    }

    /** Return a concatenation of the two arrays. */
    private static Entry[] concat(Entry[] attrs1, Entry[] attrs2) {
	Entry[] nattrs = new Entry[attrs1.length + attrs2.length];
	System.arraycopy(attrs1, 0, nattrs, 0, attrs1.length);
	System.arraycopy(attrs2, 0, nattrs, attrs1.length, attrs2.length);
	return nattrs;
    }

    /** Return a new array containing all but the given element. */
    private static Entry[] delete(Entry[] attrs, int i) {
	int len = attrs.length - 1;
	Entry[] nattrs = new Entry[len];
	System.arraycopy(attrs, 0, nattrs, 0, i);
	System.arraycopy(attrs, i + 1, nattrs, i, len - i);
	return nattrs;
    }

    /** 
     * Returns <code>true</code> if the <code>Entry</code> parameter
     * <code>e</code> is an element of the <code>Entry[]</code> array
     * parameter <code>eSet</code>; returns <code>false</code> otherwise.
     */
    private static boolean contains(Entry[] eSet, Entry e) {
	for(int i=0; i<eSet.length; i++) {
	    if(equal(eSet[i], e))
	       return true;
	}
	return false;
    }

    /** 
     * Returns <code>true</code> if the <code>Entry[]</code> array parameter 
     * <code>eSet1</code> contains the <code>Entry[]</code> array parameter 
     * <code>eSet2</code>; returns <code>false</code> otherwise. That is, 
     * this method determines if <code>eSet2</code> is a subset of
     * <code>eSet1</code>.
     */
    private static boolean contains(Entry[] eSet1, Entry[] eSet2) {
	int len1=0, len2=0;
	if(eSet1 != null)
	    len1 = eSet1.length;
	if(eSet2 != null)
	    len2 = eSet2.length;

	if(len1 < len2)
	    return false;
	for(int i=0; i<len2; i++) {
	    if(!contains(eSet1, eSet2[i]))
	       return false;
	}
	return true;
    }
}
