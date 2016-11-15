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
package org.apache.river.outrigger;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

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
    /** For each type, a set of known subtypes.
     * 
     * Note: This was originally Hashtable<String,Vector<String>>, although
     * a synchronized Map<String<Set<String>> could have been used in place
     * of the concurrent collections, on this occasion, the concurrent
     * implementations were easier to implement and understand atomically.
     * 
     * The decision to use concurrent collections was not made for performance
     * reasons, but rather simplicity of implementation, see addKnown method.
     */
    private final ConcurrentMap<String,Set<String>> subclasses = new ConcurrentHashMap<String,Set<String>>();

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
     * Return the set of subclasses for the given class.
     */
    private Set<String> classSet(String whichClass) {
        return subclasses.get(whichClass);
    }

    /**
     * An iterator that will walk through a list of known types.
     */
    // @see #RandomizedIterator
    private static abstract class TypeTreeIterator<T> implements Iterator<T> {
	protected int cursor;		// the current position in the list
	protected final T [] typearray;	// the list of types as an array
        
        protected TypeTreeIterator (T [] types){
            cursor = 0;
            typearray = types;
        }

	// inherit doc comment
        public boolean hasNext() {
            if (cursor < typearray.length) return true;
            return false;
        }
 
	// inherit doc comment
        public T next() throws NoSuchElementException {
            T val = null;
 
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
    private static class RandomizedIterator extends TypeTreeIterator<String> {
	/**
	 * Create a new <code>RandomizedIterator</code> for the given
	 * class.
	 */
	RandomizedIterator(String className, TypeTree tree) {
	    super(init(className, tree));
	}


	/*
	 * Traverse the given type tree and add to the list all the
	 * subtypes encountered within.
	 */
	private static void walkTree(Collection<String> children, Collection<String> list, TypeTree tree) {
	    if (children != null) {
		list.addAll(children);
	        String[] kids = children.toArray(new String[children.size()]);
		for (int i = 0; i< kids.length; i++) {
		    walkTree(tree.classSet(kids[i]), list, tree);
		}
	    }
	}

	/**
	 * Set up this iterator to walk over the subtypes of this class,
	 * including the class itself.  It then randomizes the list.
	 */
	private static String [] init(String className, TypeTree tree) {
            // Use a Linked to avoid resizing.
            Collection<String> types = new LinkedList<String>();
            String [] typearray;
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
	    walkTree(tree.classSet(className), types, tree);

	    // Convert it to an array and then randomize
	    typearray = types.toArray(new String[types.size()]);
	    int randnum = 0;
	    String tmpobj = null;
            int length = typearray.length;
	    for (int i = 0; i < length; i++) {
		randnum = numgen.nextInt(length - i);
		tmpobj = typearray[i];
		typearray[i] = typearray[randnum];
		typearray[randnum] = tmpobj;
	    }
            return typearray;
	}
    }

    /**
     * Return an iterator over the subtypes of the given class
     * (including the type itself).  This implementation always returns
     * an iterator that randomizes the order of the classes returned.
     * In other words, it returns the names of all classes that are
     * instances of the class that named, in a random ordering.
     */
    Iterator<String> subTypes(String className) {
	return new RandomizedIterator(className, this);
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
	Set<String> v;
        v = classSet(superclass);
        if (v == null) {
            v = new ConcurrentSkipListSet<String>();
            Set<String> existed = subclasses.putIfAbsent(superclass, v);
            if (existed != null) v = existed; // discard new set.
        }
	return v.add(subclass);
    }
}
