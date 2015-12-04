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

package org.apache.river.qa.harness;

import java.io.File;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;
import net.jini.config.NoSuchEntryException;

/** 
 * A test configuration object which will obtain entries from a
 * specified <code>ConfigurationFile</code> if the entry exists,
 * and will fall back to a default <code>ConfigurationFile</code>
 * if the entry does not exist. This mechanism eliminates the need
 * for most tests to provide test-specific configuration files.
 */
public class QAConfiguration implements Configuration {

    private Configuration testConfiguration;
    private Configuration defaultConfiguration;

    public QAConfiguration(String[] options, QAConfig config) 
	throws ConfigurationException
    {
	testConfiguration = new ConfigurationFile(options);
	options[0] = 
	    config.getStringConfigVal("org.apache.river.qa.harness.defaultTestConfig", 
				      null);
	defaultConfiguration = new ConfigurationFile(options);
    }

    public Object getEntry(String component, String name, Class type)
	throws ConfigurationException 
    {
	Object val;
	try {
	    val = testConfiguration.getEntry(component, name, type);
	    return val;
	} catch (NoSuchEntryException ignore) {
	}
	val = defaultConfiguration.getEntry(component, name, type);
	return val;
    }
	
    public Object getEntry(String component, 
			   String name,
			   Class type, 
			   Object defaultVal)
	throws ConfigurationException 
    {
	Object val;
	try {
	    val = testConfiguration.getEntry(component, name, type);
	    return val;
	} catch (NoSuchEntryException ignore) {
	}
	val = defaultConfiguration.getEntry(component, name, type, defaultVal);
	return val;
    }
	
    public Object getEntry(String component, 
			   String name,
			   Class type, 
			   Object defaultVal,
			   Object data)
	throws ConfigurationException 
    {
	Object val;
	try {
	    val = testConfiguration.getEntry(component,
					     name,
					     type,
					     Configuration.NO_DEFAULT,
					     data);
	    return val;
	} catch (NoSuchEntryException ignore) {
	}
	val = defaultConfiguration.getEntry(component,
					    name, 
					    type,
					    defaultVal,
					    data);
	return val;
    }
}
