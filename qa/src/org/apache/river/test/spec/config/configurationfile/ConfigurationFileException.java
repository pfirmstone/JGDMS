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
package org.apache.river.test.spec.config.configurationfile;

import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;

/**
 * Subclass of <code>ConfigurationException</code> used in
 * <code>ConfigurationFile</code> to provide the location of the configuration 
 * source, if available, and the line number in the configuration source that
 * triggered the exception.  If this exception is thrown as the result of
 * another exception encountered while processing the configuration information, 
 * that original exception can be accessed by calling 
 * {@link #getCause getCause}.  Note that any instance of <code>Error</code> 
 * thrown while processing the configuration information is propagated to the 
 * caller; it is not wrapped in a <code>ConfigurationFileException</code>.
 *
 * @author Sun Microsystems, Inc.
 * @see ConfigurationException
 * @see ConfigurationFile
 * @since 2.1
 */
public class ConfigurationFileException extends ConfigurationException {

    /** Constant denoting the beginning of a configuration source */
    public static final int BOF = 0;

    private static final long serialVersionUID = 1L;
    /**
     * Line number at which this exception occurred.
     * 
     * @serial
     */
    private final int lineno;
    /**
     * Location of the configuration source that triggered this exception or
     * <code>null</code> if location information is not available.
     * 
     * @serial
     */
    private final String location;

    /**
     * Creates an instance with the specified detail message, location of the 
     * configuration source, and line number in the configuration source
     * that triggered the exception.  The location parameter may be null if the 
     * location of the configuration source is unavailable.
     *
     * @param s the detail message
     * @param location the location of the configuration source
     * @param lineno the line number in the configuration source that triggered
     * this exception
     */
    public ConfigurationFileException(String s, String location, int lineno) {
        super(s);
        this.location = location;
        this.lineno = lineno;
    }

    /**
     * Creates an instance with the specified detail message; causing
     * exception, which should not be an instance of <code>Error</code>;
     * location of the configuration source; and line number in the 
     * configuration source that triggered the exception.  The location 
     * parameter may be null if the location of the configuration source is 
     * unavailable.
     * 
     * @param s the detail message
     * @param t the causing exception
     * @param location the location of the configuration source
     * @param lineno the line number in the configuration source that triggered
     * this exception
     */
    public ConfigurationFileException(String s, 
                               Throwable t, 
                               String location,
                               int lineno) 
    {
        super(s, t);
        this.location = location;
        this.lineno = lineno;
    }

    /**
     * Returns the line number in the configuration source that triggered this
     * exception.
     * 
     * @return int the line number in the configuration source that triggered 
     * this exception
     */
    public int getLineNumber() {
        return lineno;
    }

    /**
     * Returns the location of the configuration source or <code>null</code> if 
     * location information is unavailable.
     * 
     * @return String the location of the configuration source or 
     * <code>null</code> if the location is unavailable.
     */
    public String getLocation() {
        return location;
    }    
}
