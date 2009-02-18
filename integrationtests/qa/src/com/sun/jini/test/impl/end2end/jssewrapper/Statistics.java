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

package com.sun.jini.test.impl.end2end.jssewrapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.lang.StringBuffer;

/**
 * A class which maintains call statistics for wrapper methods
 */
public class Statistics {

    /**
     * A list containing a set of StatisticsItem objects.
     */
    private static ArrayList list = new ArrayList();

    /**
     * A class representing an entry for a method in a statistics table
     */
    public static class StatisticsItem implements Comparable {

	/** the class name of the method */
	String className;

	/** the method name to count */
	String methodName;

	/** counter for the number of calls made on the method */
	int counter;

	/**
	 * Construct the StatisticsItem
	 *
	 * @param className the name of the class
	 * @param methodName the name of the method
	 */
	StatisticsItem(String className, String methodName) {
	    this.className = className;
	    this.methodName = methodName;
	}

	/**
	 * Implements the Comparable interface
	 *
	 * @param obj the object to compare
	 */
	public int compareTo(Object obj) {
	    StatisticsItem item = (StatisticsItem) obj;
	    int comp = className.compareTo(item.className);
	    if (comp != 0) return comp;
	    return methodName.compareTo(item.methodName);
	}
    }

    /**
     * Increment the call counter for the given class and method
     *
     * @param className the name of the class
     * @param methodName the name of the method
     */
    public static void increment(String className, String methodName) {
	getItem(className, methodName).counter++;
    }

    /**
     * Retrieve a StatisticsItem from the list, creating a new one with a
     * count of zero if the item doesn't exist.
     *
     * @param className class name of item to find
     * @param methodName method name of item to find
     *
     * @return a StatisticsItem matching the given parameters, or a new
     *         StatisticsItem representing the given parameters
     */
    private static StatisticsItem getItem(String className, String methodName) {
	StatisticsItem item = null;
	synchronized (list) {
	    for (Iterator it = list.iterator(); it.hasNext(); ) {
		item = (StatisticsItem)it.next();
		if (item.className.equals(className)
		        && item.methodName.equals(methodName))
		{
		    return item;
		}
	    }
	    item = new StatisticsItem(className, methodName);
	    list.add(item);
	}
        return item;
    }

    /**
     * Reset the state of the statistics list to empty
     */
    public static void reset() {
	synchronized (list) {
	    list.clear();
	}
    }

    /**
     * Return a sorted copy of the statistics table
     *
     * @return a copy of the Collection containing the sorted StatisticsItems
     */
    public static Collection getStatistics() {
	ArrayList c;
	synchronized (list) {
	    c = (ArrayList) list.clone();
	}
	Collections.sort(c);
	return c;
    }

    /**
     * Return the call count for the given class and method names
     *
     * @param className the name of the class to access
     * @param methodName the name of the method to access
     * @return the number of times the given method has been called
     */
    public static int getCount(String className, String methodName) {
	return getItem(className, methodName).counter;
    }

    /**
     * Return a formatted String containing the call statistics for
     * all of the StatisticsItems in the table
     *
     * @return the string of call statistics data
     */
    public static String dumpStats() {
	StringBuffer buf = new StringBuffer();
	Collection c = getStatistics();
	Iterator iter = c.iterator();
	while (iter.hasNext()) {
	    StatisticsItem item = (StatisticsItem)iter.next();
	    buf.append(twoColumns(item.className + "." + item.methodName + ":",
				  60,
				  Integer.toString(item.counter)));
	    buf.append("\n");
	}
	return buf.toString();
    }

    /**
     * A helper for columnating two strings. The first string begins in
     * column 0, the second string begins at the given column number (columns
     * are numbered from 1).
     *
     * @param col1 the first column String
     * @param colNum the columns to start the second string at
     * @param col2 the second column String
     */
    private static String twoColumns(String col1, int colNum, String col2) {
	StringBuffer buf = new StringBuffer(col1);
	int delta = colNum - col1.length() - 1; // number from 1
	if (delta < 1) delta = 1;
	while (--delta >= 0) buf.append(" ");
	buf.append(col2);
	return new String(buf);
    }
}
