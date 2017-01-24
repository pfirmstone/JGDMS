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

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.start.destroy.DestroySharedGroup;
import org.apache.river.start.ServiceDescriptor;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.EmptyConfiguration;

import java.lang.reflect.Method;
import javax.security.auth.login.LoginContext;

public abstract class DestroySharedGroupCreateBaseTest extends StarterBase implements Test {
    private static final Class[] destroyArgs = 
	new Class[] { ServiceDescriptor[].class, Configuration.class };
    private static Method destroy;
    static {
	try {
	    destroy = 
		DestroySharedGroup.class.getDeclaredMethod(
		    "destroy", destroyArgs);
	    destroy.setAccessible(true);
	} catch (Exception e) {
	    throw new RuntimeException("Exception getting destroy method", e);
	}
    }
    private static final Class[] destroyWithLoginArgs = 
	new Class[] { ServiceDescriptor[].class, Configuration.class,
		      LoginContext.class};
    private static Method destroyWithLogin;
    static {
	try {
	    destroyWithLogin = 
		DestroySharedGroup.class.getDeclaredMethod(
		    "destroyWithLogin", destroyWithLoginArgs);
	    destroyWithLogin.setAccessible(true);
	} catch (Exception e) {
	    throw new RuntimeException("Exception getting destroyWithLogin method", e);
	}
    }
    protected DestroySharedGroupCreateBaseTest() { }

    static protected Method getDestroyMethod() { return destroy; }
    static protected Method getDestroyWithLoginMethod() { 
	return destroyWithLogin; 
    }
}

