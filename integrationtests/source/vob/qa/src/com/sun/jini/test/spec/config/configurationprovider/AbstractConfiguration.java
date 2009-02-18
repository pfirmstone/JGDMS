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

package com.sun.jini.test.spec.config.configurationprovider;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.ConfigurationException;


/**
 * Some Configuration that is abstract.
 */
public abstract class AbstractConfiguration implements Configuration {
    /**
     * Is some constructor was called.
     */
    public static boolean wasCalled = false;
    /**
     * Last obtained class loader argument.
     */
    public static ClassLoader obtainedCl = null;

    /**
     * {@inheritDoc}
     */
    public AbstractConfiguration(String[] options)
            throws ConfigurationException {
	this(options, null);
        wasCalled = true;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractConfiguration(String[] options, ClassLoader cl)
	throws ConfigurationException
    {
        wasCalled = true;
        obtainedCl = cl;
    }
}
