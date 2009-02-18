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
package com.sun.jini.test.impl.start;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.start.LifeCycle;
import com.sun.jini.start.NonActivatableServiceDescriptor;
import net.jini.config.ConfigurationException;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;

public class NonActivatableServiceDescriptorTest2 extends StarterBase {

    private static String cb = "http://host:port/cb";
    private static String p = "policy";
    private static String cp = "classpath/bogus.jar";
    private static String impl = "implClass";
    private static String[] confArgs = null;
    private static LifeCycle lc = 
        new LifeCycle() { // default, no-op object
	    public boolean unregister(Object impl) { return false; }
	};
    private static ProxyPreparer pp = new BasicProxyPreparer();

    public void run() throws Exception {

        NonActivatableServiceDescriptor nasd = 
	    new NonActivatableServiceDescriptor(
		cb, p, cp, impl, confArgs, lc, pp);
   	logger.log(Level.INFO, "NASD w/ null serverConfigArgs: {0}", 
		    nasd);
    }
}
