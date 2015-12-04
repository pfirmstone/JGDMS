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

import org.apache.river.action.GetIntegerAction;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import org.apache.river.start.ServiceStarter;
import org.apache.river.start.SharedActivatableServiceDescriptor;
import net.jini.config.ConfigurationException;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

import java.lang.reflect.Constructor;
import java.rmi.activation.ActivationSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

public class SharedActivatableServiceDescriptorTest3 extends StarterBase implements Test {

    private static String cb = "http://host:port/cb";
    private static String pol = "policy";
    private static String cp = "file:/classpath/bogus.jar";
    private static String impl = "implClass";
    private static String logDir = "/tmp/logDir";
    private static String[] confArgs = null;
    private static boolean restart = true;
    private static String host = "host";
    private static Integer port = new Integer(1234);
    
    private static ProxyPreparer pp = new BasicProxyPreparer();

    public void run() throws Exception {

        SharedActivatableServiceDescriptor sasd = 
	    new SharedActivatableServiceDescriptor(
		cb, pol, cp, impl, logDir, confArgs, restart);

	logger.log(Level.INFO, "Sasd w/ null config args: {0}", sasd);
    }
}
