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

package org.apache.river.test.spec.config.configurationprovider;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationNotFoundException;


/**
 * Some Configuration that can be instantiated but can not be
 * really used. This configuration provider doesn't have default
 * for options
 */
public class ValidConfigurationWithoutDefaults implements Configuration {
    public static boolean wasCalled = false;

    public ValidConfigurationWithoutDefaults(String[] options)
            throws ConfigurationException {
        this(options, null);
        wasCalled = true;
    }

    public ValidConfigurationWithoutDefaults(String[] options, ClassLoader cl)
            throws ConfigurationException {
        wasCalled = true;

        if (options == null) {
            throw new ConfigurationNotFoundException(
                    "default options are not supplied");
        }
    }

    public Object getEntry(String component, String name, Class type)
            throws ConfigurationException {
        throw new AssertionError();
    };

    public Object getEntry(String component, String name, Class type,
            Object defaultValue) throws ConfigurationException {
        throw new AssertionError();
    };

    public Object getEntry(String component, String name, Class type,
            Object defaultValue, Object data) throws ConfigurationException {
        throw new AssertionError();
    };
}
