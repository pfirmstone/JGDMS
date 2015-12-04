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

package net.jini.config;

/**
 * Thrown if a problem occurs when obtaining configuration information. If the
 * problem results from an exception being thrown while processing the
 * configuration information, that original exception can be accessed by
 * calling {@link #getCause getCause}. Note that any instance of
 * <code>Error</code> thrown while processing the configuration information is
 * propagated to the caller; it is not wrapped in a
 * <code>ConfigurationException</code>.
 *
 * @author Sun Microsystems, Inc.
 * @see Configuration
 * @see ConfigurationProvider
 * @since 2.0
 */
public class ConfigurationException extends Exception {

    private static final long serialVersionUID = -6556992318636509514L;

    /**
     * Creates an instance with the specified detail message.
     *
     * @param s the detail message
     */
    public ConfigurationException(String s) {
	super(s);
    }

    /**
     * Creates an instance with the specified detail message and causing
     * exception, which should not be an instance of <code>Error</code>.
     *
     * @param s the detail message
     * @param t the causing exception
     */
    public ConfigurationException(String s, Throwable t) {
	super(s, t);
    }

    /**
     * Returns a short description of this exception. The result includes the
     * name of the actual class of this object; the result of calling the
     * {@link #getMessage getMessage} method for this object, if the result is
     * not <code>null</code>; and the result of calling <code>toString</code>
     * on the causing exception, if that exception is not <code>null</code>.
     */
    public String toString() {
	String s = super.toString();
	Throwable cause = getCause();
	return (cause == null) ? s : (s + "; caused by:\n\t" + cause);
    }
}
