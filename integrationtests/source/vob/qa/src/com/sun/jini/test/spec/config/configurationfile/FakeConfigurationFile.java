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

package com.sun.jini.test.spec.config.configurationfile;
import java.io.Reader;
import java.util.List;
import net.jini.config.ConfigurationFile;
import net.jini.config.ConfigurationException;


/**
 * This class opens protected methods of ConfigurationFile class for testing
 */
public class FakeConfigurationFile extends ConfigurationFile {
    
    private static boolean useDefaultException = true;
    
    public FakeConfigurationFile(String[] options)
            throws ConfigurationException {
        super(options);
    }

    public FakeConfigurationFile(String[] options, ClassLoader cl)
            throws ConfigurationException {
        super(options, cl);
    }

    public FakeConfigurationFile(Reader reader, String[] options)
            throws ConfigurationException {
        super(reader, options);
        System.out.println(useDefaultException);
    }

    public FakeConfigurationFile(Reader reader, String[] options,
            ClassLoader cl) throws ConfigurationException {
        super(reader, options, cl);
        System.out.println(useDefaultException);
    }

    public Object getEntryInternal(String component, String name, Class type,
            Object data) throws ConfigurationException {
        return super.getEntryInternal(component, name, type, data);
    }

    public Class getSpecialEntryType(String name)
            throws ConfigurationException {
        return super.getSpecialEntryType(name);
    }

    public Object getSpecialEntry(String name)
            throws ConfigurationException {
        return super.getSpecialEntry(name);
    }
  
    protected synchronized void throwConfigurationException(
        ConfigurationException d, List errors)  throws ConfigurationException 
    {
        if (!useDefaultException) {
            ErrorDescriptor error = (ErrorDescriptor) errors.get(0);
            d = new ConfigurationFileException(error.getDescription(),
                                               error.getCause(),
                                               error.getLocationName(),
                                               error.getLineNumber());
        }
        throw d;
    }
    
    static synchronized void useDefaultException(boolean useDefault) {
        useDefaultException = useDefault;
    }

}
