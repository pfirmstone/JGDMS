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
package com.sun.jini.mercury;

import java.lang.reflect.Method;

/**
 * Convenience class that contains a package protected static utility method
 * used by the proxy classes of this package in proxy trust verification
 * process.
 *
 * Note that this class cannot be instantiated.
 *
 * @author Sun Microsystems, Inc.
 */
class ProxyUtil {

    /** This class cannot be instantiated. */
    private ProxyUtil() {
	throw new AssertionError("class cannot be instantiated");
    }//end constructor

    /**
     * Returns the public method for the specified <code>Class</code> type,
     * method name, and array of parameter types.
     * <p>
     * This method is typically used in place of {@link Class#getMethod
     * Class.getMethod} to get a method that should definitely be defined;
     * thus, this method throws an error instead of an exception if the
     * given method is missing.
     * <p>
     * This method is convenient for the initialization of a static
     * variable for use as the <code>mappings</code> argument to 
     * {@link com.sun.jini.proxy.ConstrainableProxyUtil#translateConstraints 
     * ConstrainableProxyUtil.translateConstraints}.
     *
     * @param type           the <code>Class</code> type that defines the
     *                       method of interest
     * @param name           <code>String</code> containing the name of the
     *                       method of interest
     * @param parameterTypes the <code>Class</code> types of the parameters
     *                       to the method of interest
     *
     * @return a <code>Method</code> object that provides information about,
     *         and access to, the method of interest
     *
     * @throws <code>NoSuchMethodError</code> if the method of interest cannot
     *         be found
     * @throws <code>NullPointerException</code> if <code>type</code> or
     *         <code>name</code> is <code>null</code> 
     */
    static Method getMethod(Class type,
				   String name,
				   Class[] parameterTypes)
    {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw (Error)(new NoSuchMethodError(e.getMessage()).initCause(e));
        }
    }//end getMethod
}//end class ProxyUtil
