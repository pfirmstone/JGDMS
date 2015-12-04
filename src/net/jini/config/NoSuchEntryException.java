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
 * Thrown when an attempt to obtain an object from a {@link Configuration} does
 * not find a matching entry.
 *
 * @author Sun Microsystems, Inc.
 * @see Configuration#getEntry(String, String, Class) Configuration.getEntry
 * @since 2.0
 */
public class NoSuchEntryException extends ConfigurationException {

    private static final long serialVersionUID = 943820838185621405L;

    /**
     * Creates an instance with the specified detail message.
     *
     * @param s the detail message
     */
    public NoSuchEntryException(String s) {
	super(s);
    }

    /**
     * Creates an instance with the specified detail message and causing
     * exception, which should not be an instance of <code>Error</code>.
     *
     * @param s the detail message
     * @param t the causing exception
     */
    public NoSuchEntryException(String s, Throwable t) {
	super(s, t);
    }
}
