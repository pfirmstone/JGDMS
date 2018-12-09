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
package org.apache.river.test.impl.mercury;

import org.apache.river.qa.harness.AdminManager;
import org.apache.river.qa.harness.TestException;

import java.io.IOException;
import java.lang.ClassNotFoundException;
import net.jini.io.MarshalledInstance;
import java.rmi.RemoteException;
import java.rmi.Remote;
import net.jini.event.MailboxRegistration;
import net.jini.event.MailboxPullRegistration;

public class TestUtils {
    private static final boolean DEBUG = true;
    private static final String DEFAULT_MAILBOX_NAME = "EventMailbox";
    private static int generatorCount = 0;
    private static int listenerCount = 0;

    public TestUtils () { 
    }

    private static Object getProxy(Object o) 
	throws RemoteException, IOException, ClassNotFoundException
    {
	// Hack to keep reference to impl in object table
	// UnicastRemoteObject.toStub() doesn't work
        MarshalledInstance mo = new MarshalledInstance(o);
	return mo.get(false);
    }

    public static TestGenerator createGenerator(AdminManager manager) 
        throws RemoteException, IOException, ClassNotFoundException, TestException
    {
        TestGenerator[] gen = createGenerators(1, manager);
        return  gen[0];
    }

    public static TestGenerator[] createGenerators(int howMany, AdminManager manager)
        throws RemoteException, IOException, ClassNotFoundException, TestException
    {
        if (DEBUG)
            System.out.println("MailboxTestUtils: createGenerators: creating " +
                                howMany + " TestGenerator(s)");
        TestGenerator[] tmp = new TestGenerator[howMany];
        for (int i = 0; i < howMany; i++) {
	    generatorCount++;
            if (DEBUG)
                System.out.println("TxnTestUtils: createGenerators: " +
                    "creating TestGenerator(" + generatorCount + ")" );
	    tmp[i] = (TestGenerator)getProxy(manager.startService(
		"org.apache.river.test.impl.mercury.TestGenerator"));
	    //TODO - un/marshal result to force JRMP DGC reference creation
        }
        return tmp;
    }

    public static TestListener createListener(AdminManager manager) 
        throws RemoteException, IOException, ClassNotFoundException, TestException
    {
        TestListener[] list = createListeners(1, manager); 
        return list[0];
    }

    public static TestListener[] createListeners(int howMany, AdminManager manager) 
        throws RemoteException, IOException, ClassNotFoundException, TestException
    {
        if (DEBUG)
            System.out.println("MailboxTestUtils: createListeners: creating " +
                                howMany + "TestListener(s)");
        TestListener[] tmp = new TestListener[howMany];

        for (int i = 0; i < howMany; i++) {
            listenerCount++;
            if (DEBUG)
                System.out.println("TxnTestUtils: createListeners: " +
                    "creating TestListener(" + listenerCount + ")" );
	    tmp[i] = (TestListener)getProxy(manager.startService(
		"org.apache.river.test.impl.mercury.TestListener"));
        }
        System.out.println("MailboxTestUtils: createListeners: " +
			    "created " + tmp.length + " TestListener(s)");
        return tmp;
    }
    public static TestPullListener createPullListener(AdminManager manager) 
        throws RemoteException, IOException, ClassNotFoundException, TestException
    {
        TestPullListener[] list = createPullListeners(1, manager); 
        return list[0];
    }

    public static TestPullListener[] createPullListeners(
        int howMany, AdminManager manager) 
        throws RemoteException, IOException, ClassNotFoundException, TestException
    {
        if (DEBUG)
            System.out.println("MailboxTestUtils: createPullListeners: creating " +
                                howMany + "TestPullListener(s)");
        TestPullListener[] tmp = new TestPullListener[howMany];

        for (int i = 0; i < howMany; i++) {
            listenerCount++;
            if (DEBUG)
                System.out.println("TxnTestUtils: createPullListeners: " +
                    "creating TestListener(" + listenerCount + ")" );
	    tmp[i] = (TestPullListener)getProxy(manager.startService(
		"org.apache.river.test.impl.mercury.TestPullListener"));
        }
        System.out.println("MailboxTestUtils: createPullListeners: " +
			    "created " + tmp.length + " TestPullListener(s)");
        return tmp;
    }

    public static TestListener createNSOListener(AdminManager manager) 
        throws RemoteException, IOException, ClassNotFoundException, TestException
    {
        if (DEBUG)
            System.out.println("MailboxTestUtils: createNSOListener: "
		+ "creating an NSOListener");
	return (TestListener)getProxy(manager.startService(
		"org.apache.river.test.impl.mercury.NSOListener"));
    }

    public static TestListener createREListener(AdminManager manager) 
        throws RemoteException, IOException, ClassNotFoundException, TestException
    {
        if (DEBUG)
            System.out.println("MailboxTestUtils: createREListener: "
		+ "creating an REListener");
	return (TestListener)getProxy(manager.startService(
		"org.apache.river.test.impl.mercury.REListener"));
    }

    public static TestListener createUEListener(AdminManager manager) 
        throws RemoteException, IOException, ClassNotFoundException, TestException 
    {
        if (DEBUG)
            System.out.println("MailboxTestUtils: createUEListener: "
		+ "creating an UEListener");
	return (TestListener)getProxy(manager.startService(
		"org.apache.river.test.impl.mercury.UEListener"));
    }

    public static TestListener createDisableListener(
	AdminManager manager, MailboxRegistration mr) 
        throws RemoteException, IOException, ClassNotFoundException, TestException 
    {
        if (DEBUG)
            System.out.println("MailboxTestUtils: createDisableListener: "
		+ "creating an createDisableListener");
	TestListener l = 
	    (TestListener)getProxy(manager.startService(
		"org.apache.river.test.impl.mercury.DisableListener"));
	((DisableListener)l).setMailboxRegistration(mr);
	return l;
    }

    public static TestListener createDisableNSOListener(
	AdminManager manager, MailboxRegistration mr) 
        throws RemoteException, IOException, ClassNotFoundException, TestException 
    {
        if (DEBUG)
            System.out.println("MailboxTestUtils: createDisableNSOListener: "
		+ "creating an createDisableNSOListener");
	TestListener l = 
	    (TestListener)getProxy(manager.startService(
		"org.apache.river.test.impl.mercury.DisableNSOListener"));
	((DisableListener)l).setMailboxRegistration(mr);
	return l;
    }
}

