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
 * Thrown if a configuration source location specified when creating a
 * <code>Configuration</code> is not found, including if <code>null</code> is
 * specified for provider options and the implementation does not provide
 * default options. If the problem results from an exception being thrown while
 * attempting to access a source location, that original exception can be
 * accessed by calling {@link #getCause getCause}. Note that any instance of
 * <code>Error</code> thrown while processing the configuration information is
 * propagated to the caller; it is not wrapped in a
 * <code>ConfigurationNotFoundException</code>.
 *
 * @author Sun Microsystems, Inc.
 * @see Configuration
 * @see ConfigurationProvider
 * @since 2.0
 */
public class ConfigurationNotFoundException extends ConfigurationException {

    private static final long serialVersionUID = -3084555497838803365L;

    /**
     * Creates an instance with the specified detail message.
     *
     * @param s the detail message
     */
    public ConfigurationNotFoundException(String s) {
	super(s);
    }

    /**
     * Creates an instance with the specified detail message and causing
     * exception, which should not be an instance of <code>Error</code>.
     *
     * @param s the detail message
     * @param t the causing exception
     */
    public ConfigurationNotFoundException(String s, Throwable t) {
	super(s, t);
    }
}
