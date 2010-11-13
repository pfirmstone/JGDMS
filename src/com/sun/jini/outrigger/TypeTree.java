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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Vector;

/**
 * A type tree for entries.  It maintains, for each class, a list of
 * known subclasses so that we can walk down the relevant subpart of
 * the subtype tree for a template, looking for matching entries.  This
 * list of subtypes is not current garbage collected -- if a subtype
 * was once written, it's subtype entry will never be removed from this
 * tree.  All operations are done via class name.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see OutriggerServerImpl
 */
class TypeTree {
    /** For each type, a vector of known subtypes */
    private Hashtable subclasses = new Hashtable();

    /**
     * A generator used to randomize the order of iterator returns
     */
    // @see #RandomizedIterator
    static final private Random numgen = new Random();

    /**
     * Name of the root bucket of the type tree
     */
    static final private String ROOT = 
	net.jini.core.entry.Entry.class.getName();

    /**
     * Return the vector of subclasses for the given class.
     */
    private Vector classVector(String whichClass) {
	return (Vector) subclasses.get(whichClass);
    }

    /**
     * An iterator that will walk through a list of known types.
     */
    // @see #RandomizedIterator
    private abstract class TypeTreeIterator implements Iterator {
	protected int cursor;		// the current position in the list
	protected Object[] typearray;	// the list of types as an array

	// inherit doc comment
        public boolean hasNext() {
            if (cursor < typearray.length)
                return true;
 
            return false;
        }
 
	// inherit doc comment
        public Object next() throws NoSuchElementException {
            Object val = null;
 
            if (cursor >= typearray.length)
                throw new NoSuchElementException("TypeTreeIterator: next");
 
            try {
                val = typearray[cursor];
                cursor++;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new NoSuchElementException("TypeTreeIterator: next" +
                                                        e.getMessage());
            }

            return val;
        }
 
	/**
	 * Unimplemented operations
	 * @throws UnsupportedOperationException Always
	 */
        public void remove() throws UnsupportedOperationException {
            throw new UnsupportedOperationException(
		"TypeTreeIterator: remove not supported");
        }
    }

    /**
     * This class implements a randomized iterator over the
     * <code>TypeTree</code>.  Given a <code>className</code>, it
     * maintains a randomized list of subtypes for the given
     * <code>className</code>, including the class itself.
     */
    class RandomizedIterator extends TypeTreeIterator {
	/**
	 * Create a new <code>RandomizedIterator</code> for the given
	 * class.
	 */
	RandomizedIterator(String className) {
	    super();
	    init(className);
	}


	/*
	 * Traverse the given type tree and add to the list all the
	 * subtypes encountered within.
	 */
	private void walkTree(Collection children, Collection list) {
	    if (children != null) {
		list.addAll(children);
	        Object[] kids = children.toArray();
		for (int i = 0; i< kids.length; i++) {
		    walkTree(classVector((String)kids[i]), list);
		}
	    }
	}

	/**
	 * Set up this iterator to walk over the subtypes of this class,
	 * including the class itself.  It then randomizes the list.
	 */
	private void init(String className) {
            Collection types = new ArrayList();

	    if (className.equals(EntryRep.matchAnyClassName())) {
		// handle "match any" specially" -- search from ROOT
		// Simplification suggested by 
		// Lutz Birkhahn <lutz.birkhahn@GMX.DE>
		className = ROOT;
	    } else {
		// add this class
		types.add(className);
	    }

	    // add all subclasses
	    walkTree(classVector(className), types);

	    // Convert it to an array and then randomize
	    typearray = types.toArray();
	    int randnum = 0;
	    Object tmpobj = null;

	    for (int i = 0; i < typearray.length; i++) {
		randnum = numgen.nextInt(typearray.length - i);
		tmpobj = typearray[i];
		typearray[i] = typearray[randnum];
		typearray[randnum] = tmpobj;
	    }
	}
    }

    /**
     * Return an iterator over the subtypes of the given class
     * (including the type itself).  This implementation always returns
     * an iterator that randomizes the order of the classes returned.
     * In other words, it returns the names of all classes that are
     * instances of the class that named, in a random ordering.
     */
    Iterator subTypes(String className) {
	return new RandomizedIterator(className);
    }

    /**
     * Update the type tree with the given bits.  This will traverse the
     * given EntryRep's list of superclasses, retrieve the subclass list
     * at each list item and update it with the given EntryRep's type.
     *
     *  SupClass List
     *   |
     *   V
     *   SupC1-->Sub1OfSupC1--Sub2OfSupC1...SubNOfSupC1--EntryRep
     *   |
     *   |
     *   SupC2-->Sub1OfSupC2--Sub2OfSupC2...SubNOfSupC2--EntryRep
     *   .
     *   .
     *   .
     *   SupCN-->Sub1OfSupCN--Sub2OfSupCN...SubNOfSupCN--EntryRep
     */
    void addTypes(EntryRep bits) {
	String classFor = bits.classFor();
	String[] superclasses = bits.superclasses();

	//The given EntryRep will add its className to the
	//subtype list of all its supertypes.

	String prevClass = classFor;
	for (int i = 0; i < superclasses.length; i++) {
	    if (!addKnown(superclasses[i], prevClass)) {
		return;
	    }
	    prevClass = superclasses[i];
	}

	// If we are here prevClass must have java.Object as its
	// direct superclass (we don't store "java.Object" in
	// EntryRep.superclasses since that would be redundant) and
	// prevClass is not already in the the tree.  Place it in the
	// "net.jini.core.entry.Entry" bucket so it does not get lost
	// if it does not have any sub-classes.
	//
	// Fix suggested by Lutz Birkhahn <lutz.birkhahn@GMX.DE>
	addKnown(ROOT, prevClass);
    }

    /**
     * Add the subclass to the list of known subclasses of this superclass.  
     */
    private boolean addKnown(String superclass, String subclass) {
	Vector v;

	synchronized (subclasses) {
	    v = classVector(superclass);
	    if (v == null) {
		v = new Vector();
		subclasses.put(superclass, v);
	    }
	}

	synchronized (v) {
	    if (v.contains(subclass))
		return false;
	    v.addElement(subclass);
	}
	return true;
    }
}
