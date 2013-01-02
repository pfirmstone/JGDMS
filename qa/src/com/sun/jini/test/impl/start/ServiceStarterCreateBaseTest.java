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

import com.sun.jini.qa.harness.Test;
import com.sun.jini.start.ServiceStarter;
import com.sun.jini.start.ServiceDescriptor;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.EmptyConfiguration;

import java.lang.reflect.Method;
import javax.security.auth.login.LoginContext;

public abstract class ServiceStarterCreateBaseTest extends StarterBase implements Test {
    private static final Class[] createArgs = 
	new Class[] { ServiceDescriptor[].class, Configuration.class };
    private static Method create;
    static {
	try {
	    create = 
		ServiceStarter.class.getDeclaredMethod("create", createArgs);
	    create.setAccessible(true);
	} catch (Exception e) {
	    throw new RuntimeException("Exception getting create method", e);
	}
    }
    private static final Class[] createWithLoginArgs = 
	new Class[] { ServiceDescriptor[].class, Configuration.class,
		      LoginContext.class};
    private static Method createWithLogin;
    static {
	try {
	    createWithLogin = 
		ServiceStarter.class.getDeclaredMethod(
		    "createWithLogin", createWithLoginArgs);
	    createWithLogin.setAccessible(true);
	} catch (Exception e) {
	    throw new RuntimeException("Exception getting createWithLogin method", e);
	}
    }
    protected ServiceStarterCreateBaseTest() { }

    static protected Method getCreateMethod() { return create; }
    static protected Method getCreateWithLoginMethod() { 
	return createWithLogin; 
    }
}

