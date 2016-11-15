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
package net.jini.lookup.ui.attribute;

import java.util.Set;
import java.util.Collection;
import java.util.Iterator;
import java.util.Collections;
import org.apache.river.lookup.util.ConsistentSet;

/**
 * UI attribute that lists UI factory interfaces of which a UI factory
 * is an instance.
 *
 * @author Bill Venners
 */
public class UIFactoryTypes implements java.io.Serializable {

    private static final long serialVersionUID = 5400327511520984900L;

    /**
     * @serial A <code>Set</code> of <code>String</code>s,
     *     each of which represents one fully qualified type name.
     *     Each <code>String</code> type name should indicate one
     *     UI factory interface implemented by the UI factory contained
     *     in marshalled form in the same <code>UIDescriptor</code> in
     *     which this <code>UIFactoryTypes</code> appears.
     */
    private Set typeNames;

    /**
     * Constructs a <CODE>UIFactoryTypes</CODE> attribute using the
     * passed <CODE>Set</CODE>. The <CODE>Set</CODE> can
     * be mutable or immutable, and must contain only
     * <CODE>String</CODE>s. Each <CODE>String</CODE> should
     * represent a fully qualified Java type name of a UI factory
     * interface. This constructor copies
     * the contents of the passed <code>Set</code> into a
     * serializable read-only <code>Set</code> that has a
     * consistent serialized form across all VMs.
     *
     * <P>
     * The <code>isAssignableTo</code> method of this class will only
     * return <code>true</code> for types whose names are passed
     * explicitly to this constructor via the <CODE>typeNames</CODE> parameter.
     * This constructor does not inspect the inheritance hierarchies of the
     * types whose names are passed via the <code>typeNames</code>
     * parameter. It is the client's responsibility to include the name of
     * every UI factory interface of which the relevant UI factory (the UI factory being described
     * by this class) is an instance.
     *
     * @param typeNames A <CODE>Set</CODE> of <CODE>String</CODE>
     * objects. Each element must be non-null and an instance
     * of <CODE>java.lang.String</CODE>.
     *
     * @throws NullPointerException if <CODE>typeNames</CODE>
     * is <CODE>null</CODE> or any element of <CODE>typeNames</CODE>
     * set is <CODE>null</CODE>.
     *
     * @throws IllegalArgumentException if any non-null element of
     * <CODE>typeNames</CODE> set is not an instance of
     * <CODE>java.lang.String</CODE>.
     */
    public UIFactoryTypes(Set typeNames) {

        if (typeNames == null) {
            throw new NullPointerException();
        }

        Iterator it = typeNames.iterator();
        while (it.hasNext()) {
            Object o = it.next();
            if (o == null) {
                throw new NullPointerException();
            }
            if (!(o instanceof String)) {
                throw new IllegalArgumentException();
            }
        }

        this.typeNames = new ConsistentSet(typeNames);
    }

    /**
     * Returns <CODE>true</CODE> if the UI factory (contained in
     * marshalled form in the same <CODE>UIDescriptor</CODE>) is
     * an instance of the UI factory interface
     * type passed in parameter <CODE>classObj</CODE>.
     * Else, returns <CODE>false</CODE>.
     *
     * <P>
     * This method compares the fully qualified name
     * of the type represented by the passed <CODE>Class</CODE> with
     * the fully qualified names of UI factory interfaces implemented
     * by the UI factory's class. If
     * an exact string match is found, this method returns <CODE>true</CODE>.
     * If the UI factory is ultimately loaded with a class loader whose
     * parent-delegation chain doesn't include the class loader
     * that defined the passed class, a type with the same name
     * would be loaded into a different namespace of
     * the virtual machine. If so, the two types would
     * be considered different by the virtual machine, even though
     * they shared the same fully qualified name.
     *
     * @param classObj the type to check
     *
     * @return <code>true</code> if the UI factory object (once
     * unmarshalled) can be cast to the type represented by the
     * passed <code>Class</code>
     *
     * @throws NullPointerException if <CODE>classObj</CODE>
     * is <CODE>null</CODE>
     */
    public boolean isAssignableTo(Class classObj) {

        if (classObj == null) {
            throw new NullPointerException();
        }

        return typeNames.contains(classObj.getName());
    }

    /**
     * Returns an iterator over the set of types of which
     * a UI factory object is an instance in no particular order.
     * The returned <CODE>Iterator</CODE> does not support
     * <CODE>remove()</CODE>.
     *
     * @return an iterator over the set of factory types
     */
    public Iterator iterator() {

        return typeNames.iterator();
    }

    /**
     * Returns an unmodifiable Set of fully qualified type name
     * <CODE>String</CODE>s of which
     * a UI factory is an instance in no particular order.
     *
     * @return an unmodifiable set of UI factory type names
     */
    public Set getTypeNames() {

        return typeNames;
    }

    /**
     * Compares the specified object (the <CODE>Object</CODE> passed
     * in <CODE>o</CODE>) with this <CODE>UIFactoryTypes</CODE>
     * object for equality. Returns true if the specified object
     * is not null, if the specified object's class is
     * <CODE>UIFactoryTypes</CODE>, if the two sets of
     * factory types are the same size, and if every factory type mentioned in the
     * specified <CODE>UIFactoryTypes</CODE> object (passed in <CODE>o</CODE>) is also mentioned
     * in this <CODE>UIFactoryTypes</CODE> object.
     *
     * @param o the object to compare against
     *
     * @return <code>true</code> if the objects are the same,
     * <code>false</code> otherwise.
     */
    public boolean equals(Object o) {

        if (o == null) {
            return false;
        }

        if (o.getClass() != UIFactoryTypes.class) {
            return false;
        }

        UIFactoryTypes types = (UIFactoryTypes) o;

        if (!typeNames.equals(types.typeNames)) {
            return false;
        }

        return true;
    }

    /**
     * Returns the hash code value for this <CODE>UIFactoryTypes</CODE> object.
     *
     * @return the hashcode for this object
     */
    public int hashCode() {
        return typeNames.hashCode();
    }
}

