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

package com.sun.jini.proxy;

import java.io.InvalidObjectException;
import java.lang.reflect.Method;
import net.jini.constraint.BasicMethodConstraints.MethodDesc;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;

/**
 * A collection of utility methods for use in implementing constrainable
 * proxies.  This class cannot be instantiated.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class ConstrainableProxyUtil {

    /** This class cannot be instantiated. */
    private ConstrainableProxyUtil() {
	throw new AssertionError();
    }

    /**
     * Creates a {@link MethodConstraints} using the constraints in
     * <code>methodConstraints</code>, but with the methods remapped according
     * to <code>mappings</code>, where the first element of each pair of
     * elements is mapped to the second.  For example, if
     * <code>methodConstraints</code> returns constraints <code>C1</code> for
     * method <code>M1</code>, and the elements of mappings are methods
     * <code>M1</code> and <code>M2</code>, then the resulting method
     * constraints return <code>C1</code> for method <code>M2</code>.
     *
     * @param methodConstraints the method constraints whose methods should be
     *	      translated, or <code>null</code> for empty constraints
     * @param mappings the method mappings
     * @return the translated method constraints
     * @throws NullPointerException if <code>mappings</code> is
     *	       <code>null</code> or contains <code>null</code> elements
     * @throws IllegalArgumentException if <code>mappings</code> contains an
     *	       odd number of elements
     */
    public static MethodConstraints translateConstraints(
	MethodConstraints methodConstraints,
	Method[] mappings)
    {
	if (mappings.length % 2 != 0) {
	    throw new IllegalArgumentException("mappings has odd length");
	} else if (methodConstraints == null) {
	    return null;
	}
	int count = mappings.length / 2;
	MethodDesc[] descs = new MethodDesc[count];
	for (int i = mappings.length - 1; i >= 0; i-= 2) {
	    Method from = mappings[i - 1];
	    Method to = mappings[i];
	    descs[--count] = new MethodDesc(
		to.getName(), to.getParameterTypes(),
		methodConstraints.getConstraints(from));
	}
	return new BasicMethodConstraints(descs);
    }

    /**
     * Test to see if two {@link MethodConstraints} instances are
     * equivalent given a method-to-method mapping. Only the
     * constraints for methods that appear in the mapping are
     * compared. The mapping is represented by an array, <code>
     * mappings</code>, of 2n {@link Method} objects.  For all values
     * p less than n the constraints associated with
     * <code>mappings[2p]</code> in <code>methodConstraints1</code>
     * are compared to the constraints associated with
     * <code>mappings[2p+1]</code> in
     * <code>methodConstraints2</code>. If null is passed in for both
     * instances they are considered equivalent.
     *
     * @param methodConstraints1 the first <code>MethodConstraints</code>
     *        object to compare.
     * @param methodConstraints2 the second <code>MethodConstraints</code>
     *        object to compare.
     * @param mappings the method-to-method mapping.
     * @return <code>true</code> if the <code>MethodConstraints</code> 
     *         instances represent equivalent constraints, returns
     *         <code>false</code> otherwise.
     * @throws NullPointerException if <code>mapping</code> is
     *         <code>null</code> contains <code>null</code> elements.
     * @throws IllegalArgumentException if <code>mapping</code> contains an
     *         odd number of elements 
     */
    public static boolean equivalentConstraints(
        MethodConstraints methodConstraints1, 
	MethodConstraints methodConstraints2,
	Method[] mappings) 
    {
	if (mappings.length % 2 != 0) {
	    throw new IllegalArgumentException("mappings has odd length");
	}

	// both null means they are equivalent.
	if (methodConstraints1 == null && methodConstraints2 == null) {
	    return true;
	}

	// If only one is null they are not equivalent
	if (methodConstraints1 == null || methodConstraints2 == null) {
	    return false;
	}

	// Both non-null need to run though map check
	final int count = mappings.length / 2;
	for (int i = 0; i < mappings.length; i+=2) {	    
	    final InvocationConstraints c1 = 
		methodConstraints1.getConstraints(mappings[i]);
	    final InvocationConstraints c2 = 
		methodConstraints2.getConstraints(mappings[i+1]);
	    if (!c1.equals(c2)) {
		return false;
	    }
	}

	return true;
    }

    /**
     * Verify that an object, <code>proxy</code>, is an instance of
     * {@link RemoteMethodControl} its {@link MethodConstraints} are
     * equivalent to another <code>MethodConstraints</code> instance,
     * <code>methodConstraints</code> once a mapping has been applied.
     * If <code>proxy</code> does not implement
     * <code>RemoteMethodControl</code> or the associated constraints are
     * not equivalent throw an {@link InvalidObjectException}. The
     * mapping is represented by an array, <code>mappings</code>, of
     * 2n {@link Method} objects. For all values p less than n the
     * constraints associated with <code>mappings[2p]</code> in
     * <code>methodConstraints</code> are compared to the constraints
     * associated with <code>mappings[2p+1]</code> in the
     * <code>MethodConstraints</code> returned by
     * <code>proxy.getConstraints</code>. Will also return normally if
     * both <code>methodConstraints</code> and the value returned by
     * <code>proxy.getConstraints</code> are <code>null</code>.
     *
     * @param methodConstraints the method constraints 
     *        <code>proxy</code> should have.
     * @param proxy the proxy to test, must implement 
     *        <code>RemoteMethodControl</code>.
     * @param mappings the method to method mapping     
     * @throws NullPointerException if <code>mappings</code> or
     *         <code>proxy</code> is <code>null</code> or if
     *         <code>mapping</code> contains <code>null</code> elements
     * @throws IllegalArgumentException if <code>mappings</code> contains an
     *	       odd number of elements
     * @throws InvalidObjectException if <code>proxy</code> does
     *         not implement <code>RemoteMethodControl</code>, or if
     *         the constraints on <code>proxy</code> are not
     *         equivalent to <code>methodConstraints</code>.  
     */
    public static void verifyConsistentConstraints(
	MethodConstraints methodConstraints, Object proxy, Method[] mappings)
	throws InvalidObjectException
    {
	if (!(proxy instanceof RemoteMethodControl))
	    throw new InvalidObjectException(
	        "Proxy does not implement RemoteMethodControl");

	final MethodConstraints proxyMethodConstraints =
	    ((RemoteMethodControl) proxy).getConstraints();

	if (!equivalentConstraints(methodConstraints, proxyMethodConstraints,
				   mappings))
        {
	    // Not equivalent, complain.
	    throw new InvalidObjectException(
		"Inconsistent constraints on proxy");
	}

	// else everything is ok, normal return
    }
}
