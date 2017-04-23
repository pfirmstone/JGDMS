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

/**
 * Provided for backward compatibility, migrate to new name space.
 */
@Deprecated
public class LookupAttributes {
    private LookupAttributes() {}

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
     * @param attrSets original Entry attributes
     * @param addAttrSets Entry attributes to added if not already contained in attrSets
     * @return new array of Entry attributes, containing no duplicates.
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
     * <code>MarshalledInstance.equals</code> on field values.  The 
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
     * @param attrSets original Entry attributes
     * @param addAttrSets Entry attributes to added if not already contained in attrSets
     * @param checkSC if true checks for any elements of addAttrSets are instances
     * of ServiceControlled.
     * @return new array of Entry attributes, containing no duplicates.
     */
    public static Entry[] add(Entry[] attrSets,
			      Entry[] addAttrSets,
			      boolean checkSC)
    {
	return org.apache.river.lookup.entry.LookupAttributes.add(attrSets, addAttrSets, checkSC);
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
     * @param attrSets Entry attributes
     * @param attrSetTmpls Entry attribute templates
     * @param modAttrSets Entry modified attributes
     * @return a new array of Entry attributes.
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
     * @param attrSets Entry attributes
     * @param attrSetTmpls Entry attribute templates
     * @param modAttrSets Entry modified attributes
     * @param checkSC if true checks for any elements of addAttrSets are instances
     * of ServiceControlled.
     * @return a new array of Entry attributes.
     */
    public static Entry[] modify(Entry[] attrSets,
				 Entry[] attrSetTmpls,
				 Entry[] modAttrSets,
				 boolean checkSC)
    {
	return org.apache.river.lookup.entry.LookupAttributes.modify(
                attrSets, attrSetTmpls, modAttrSets, checkSC);
    }

    /**
     * Test that two entries are the same type, with the same
     * public fields. Attribute equality is defined in terms of
     * <code>MarshalledObject.equals</code> on field values.
     * @param e1 an Entry
     * @param e2 A second Entry
     * @return true if Entries are the same type.
     */
    public static boolean equal(Entry e1, Entry e2) {
	return org.apache.river.lookup.entry.LookupAttributes.equal(e1, e2);
    }

    /** Tests that two <code>Entry[]</code> arrays are the same.
     * @param attrSet1 first Entry array.
     * @param attrSet2 second Entry array
     * @return true if both arrays contain the same number of elements with 
     * equal entries
     */
    public static boolean equal(Entry[] attrSet1, Entry[] attrSet2) {
	return org.apache.river.lookup.entry.LookupAttributes.equal(attrSet1, attrSet2);
    }

    /**
     * Test if the parameter <code>tmpl</code> is the same class as, or a
     * superclass of, the parameter <code>e</code>, and that every 
     * non-<code>null</code> public field of <code>tmpl</code> is the
     * same as the corresponding field of <code>e</code>. Attribute equality
     * is defined in terms of <code>MarshalledObject.equals</code> on
     * field values.
     * @param tmpl Entry template.
     * @param e Entry to be checked for template matching.
     * @return true if Entry matches template.
     */
    public static boolean matches(Entry tmpl, Entry e) {
	return org.apache.river.lookup.entry.LookupAttributes.matches(tmpl, e);
    }

    /**
     * Throws an <code>IllegalArgumentException</code> if any element of
     * the array is not an instance of a valid <code>Entry</code> class
     * (the class is not public, or does not have a no-arg constructor, or
     * has primitive public non-static non-final fields).  If
     * <code>nullOK</code> is <code>false</code>, and any element of the
     * array is <code>null</code>, a <code>NullPointerException</code>
     * is thrown.
     * @param attrs to be checked if valid Entry classes.
     * @param nullOK true if array can contain null elements.
     */
    public static void check(Entry[] attrs, boolean nullOK) {
	org.apache.river.lookup.entry.LookupAttributes.check(attrs, nullOK);
    }
}
