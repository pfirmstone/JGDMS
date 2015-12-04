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

import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.start.DestroySharedGroup;
import org.apache.river.start.ServiceDescriptor;
import org.apache.river.start.SharedActivatableServiceDescriptor;
import org.apache.river.start.SharedGroup;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.EmptyConfiguration;
import org.apache.river.qa.harness.QAConfig;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.Arrays;


public class DestroySharedGroupCreateBadDesc extends DestroySharedGroupCreateBaseTest {

    protected static class MyBogusServiceDescriptor 
	implements ServiceDescriptor
    {
         public Object create(Configuration config) throws Exception {
	     return null;
	 }
    }

    protected static class MyBogusServiceDescriptor2 
	extends SharedActivatableServiceDescriptor 
    {

	 MyBogusServiceDescriptor2(
	    // Required Args
	    String codebase,
	    String policy, 
	    String classpath,
	    String implClassName,
	    String sharedGroupLog,
	    // Optional Args,
	    String[] serverConfigArgs,
	    boolean restart)  
	{ 
	    super(codebase, policy, classpath, implClassName, 
		  sharedGroupLog, serverConfigArgs, restart);
	}

         public Object create(Configuration config) throws Exception {
	     throw new Exception("MyBogusServiceDescriptor2:create()");
	 }
    }

    protected static class MyBogusServiceDescriptor3 
	extends SharedActivatableServiceDescriptor 
    {

	 MyBogusServiceDescriptor3(
	    // Required Args
	    String codebase,
	    String policy, 
	    String classpath,
	    String implClassName,
	    String sharedGroupLog,
	    // Optional Args,
	    String[] serverConfigArgs,
	    boolean restart)  
	{ 
	    super(codebase, policy, classpath, implClassName, 
		  sharedGroupLog, serverConfigArgs, restart);
	}

         public Object create(Configuration config) throws Exception {
	     return null;
	 }
    }

    protected static class MyBogusServiceDescriptor4 
	extends SharedActivatableServiceDescriptor 
    {

	 MyBogusServiceDescriptor4(
	    // Required Args
	    String codebase,
	    String policy, 
	    String classpath,
	    String implClassName,
	    String sharedGroupLog,
	    // Optional Args,
	    String[] serverConfigArgs,
	    boolean restart)  
	{ 
	    super(codebase, policy, classpath, implClassName, 
		  sharedGroupLog, serverConfigArgs, restart);
	}

         public Object create(Configuration config) throws Exception {
	     SharedGroup sg =  new SharedGroup() {
		 public void destroyVM() throws RemoteException {
		     throw new RemoteException("BogusSharedGroup");
		 }
	     };
	     return new SharedActivatableServiceDescriptor.Created(
		 null, null, sg);
	 }
    }

    public void run() throws Exception {
	Configuration config = EmptyConfiguration.INSTANCE;
	// Create bogus data
	ServiceDescriptor[] descs = new ServiceDescriptor[] {
	    new MyBogusServiceDescriptor(),
	    new MyBogusServiceDescriptor2("http://a/", "a", "a", "a", "a", 
		null, false),
	    new MyBogusServiceDescriptor3("http://a/", "a", "a", "a", "a", 
		null, false),
	    new MyBogusServiceDescriptor4("http://a/", "a", "a", "a", "a", 
		null, false),
	    null
	};
	String[] keys = new String[] {"destroy.unexpected.type", 
	                              "destroy.creation.exception", 
				      "destroy.unexpected.proxy",
				      "destroy.group.exception"};
	Object[] p = new Object[] { descs, config};
	getDestroyMethod().invoke(null, p);
        if (!checkReport(Arrays.asList(keys), handler.getKeys())) {
	    throw new TestException("Required keys not generated.");
	}
	
	//Negative check just for insurance
        if (checkReport(Arrays.asList(new String[] { "bogus.key" }), 
	    handler.getKeys())) 
	{
	    throw new TestException("Bogus keys generated.");
	}
    }
}

