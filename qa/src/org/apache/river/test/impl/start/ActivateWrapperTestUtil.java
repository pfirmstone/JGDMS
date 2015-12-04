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
package org.apache.river.test.impl.start;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.*;
import java.net.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.activation.ActivationGroupDesc.*;
import java.util.*;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.start.*;
import org.apache.river.start.ActivateWrapper.*;
import org.apache.river.qa.harness.TestException;

public class ActivateWrapperTestUtil {

    private static Logger logger = Logger.getLogger("org.apache.river.qa.harness.test");


    public static File getServiceConfigFile() throws Exception {
        File config = File.createTempFile("Mercury", ".config");
	config.deleteOnExit();
        String entries = 
	    "org.apache.river.mercury{ \n static persistenceDirectory = \"" 
	    + config.toString() + "_log" + "\";\n}\n";
        FileWriter fw = new FileWriter(config);
	fw.write(entries);
	fw.flush();
	fw.close();
        return config; 
    }

    public static ActivateDesc getServiceActivateDesc(
	String servicePrefix, QAConfig config) 
	throws Exception 
    {
	String implClassName = 
	    config.getStringConfigVal(servicePrefix + ".impl", null);
	logger.log(Level.FINE, "ActivateWrapperTestUtil:getServiceActivateDesc() "
	    + "implClassName=" + implClassName);

	String classpath =
	    config.getStringConfigVal(servicePrefix + ".classpath", null);
	logger.log(Level.FINE, "ActivateWrapperTestUtil:getServiceActivateDesc() "
	    + "classpath=" + classpath);
        URL[] cpURLs = ClassLoaderUtil.getClasspathURLs(classpath);


	String codebase = 
	    config.getStringConfigVal(servicePrefix + ".codebase", null);
	logger.log(Level.FINE, "ActivateWrapperTestUtil:getServiceActivateDesc() "
	    + "codebase=" + codebase);
        URL[] cbURLs = ClassLoaderUtil.getCodebaseURLs(codebase);

	String policy =
	    config.getStringConfigVal(servicePrefix + ".policyfile", null);
	logger.log(Level.FINE, "ActivateWrapperTestUtil:getServiceActivateDesc() "
	    + "policy=" + policy);

        File configFile = getServiceConfigFile();
	logger.log(Level.FINE, "ActivateWrapperTestUtil:getServiceActivateDesc() "
	    + "config=" + configFile);

        if (implClassName == null ||
	    cpURLs == null     ||
	    cbURLs == null      ||
	    policy == null        ||
	    configFile == null         ) {
            throw new IllegalArgumentException("Obtined null values for "
		+ servicePrefix + " properties.");
	}

        MarshalledObject params = 
	    new MarshalledObject(new String[] {configFile.toString()});
        ActivateWrapper.ActivateDesc adesc = 
	    new ActivateWrapper.ActivateDesc(
	        implClassName,
	        cpURLs,
	        cbURLs,
	        policy,
	        params);

	return adesc;
    }
}
