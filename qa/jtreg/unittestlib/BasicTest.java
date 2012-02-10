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

/**
 * Provides a simple implementation of Test.  Implements name and check by
 * storing the name, prefixed by the class name, and storing a value to compare
 * with.
 */
public abstract class BasicTest extends UnitTestUtilities implements Test {

    /* -- Fields -- */

    private final String name;
    private Object compareTo;
    private boolean compareToSet;

    /* -- Methods -- */

    /** Constructor that specifies name and compareTo values */
    public BasicTest(String name, Object compareTo) {
	this.name = name;
	setCompareTo(compareTo);
    }

    /** Set the value to compare to. */
    protected void setCompareTo(Object compareTo) {
        synchronized (this){
            this.compareTo = compareTo;
            compareToSet = true;
        }
    }

    /** Get the value to compare to.  Throws an exception if not set. */
    protected Object getCompareTo() {
        synchronized (this){
            if (!compareToSet) {
                throw new FailedException("Test error: compareTo not set");
            }
            return compareTo;
        }
    }

    /**
     * Constructor that just specifies name.  Use setCompareTo() before check()
     * gets called.
     */
    public BasicTest(String name) {
	this.name = name;
    }

    public String name() {
	return name;
    }

    public void check(Object result) throws Exception {
        Object compareToObj = getCompareTo();
	if (!safeEquals(compareToObj, result)) {
	    throw new FailedException("Should be: " + compareToObj);
	}
    }
}
