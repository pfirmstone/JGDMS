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
package com.sun.jini.test.spec.io.util;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.InvocationConstraint;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.io.Serializable;

/**
 * A fake MethodConstraints object with configurable method return values.
 * <p>
 * Used by:
 * <ul>
 *   <li>com.sun.jini.test.spec.io.marshalinputstream.Resolve_LoadClassTest
 *   <li>com.sun.jini.test.spec.io.marshalinputstream.Resolve_VerifyCodebaseIntegrityTest
 * </ul>
 */
public class FakeMethodConstraints implements MethodConstraints, Serializable {

    private InvocationConstraint[] required_ic;

    /**
     * Constructs a FakeMethodConstraints.  
     *
     * @param req the required constraints, or null to create (in effect)
     *        MethodConstraints.EMPTY
     */
    public FakeMethodConstraints(InvocationConstraint[] req) {
        required_ic = req;
    }

    /**
     * Implementation of interface method.
     */
    public InvocationConstraints getConstraints(Method method) {
        return new InvocationConstraints(required_ic,null);
    }

    /**
     * Implementation of interface method.
     */
    public Iterator possibleConstraints() {
        return new Iterator() {
	    private int i = (required_ic == null ? 0 : required_ic.length);

	    public boolean hasNext() {
		return i > 0;
	    }
	    public Object next() {
		if (i > 0) {
		    return new InvocationConstraints(
                        new InvocationConstraint[] { required_ic[--i] },
                        null);
		} else if (i == 0) {
                    --i;
		    return InvocationConstraints.EMPTY;
                } else {
		    throw new NoSuchElementException("no more elements");
		}
	    }
	    public void remove() {
		throw new UnsupportedOperationException("immutable object");
	    }
	};
    }

}
