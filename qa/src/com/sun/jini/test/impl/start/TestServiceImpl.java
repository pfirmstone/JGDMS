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

import java.rmi.RemoteException;
import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationException;
import java.rmi.MarshalledObject;

import com.sun.jini.config.Config;
import com.sun.jini.test.impl.start.TestService;
import com.sun.jini.start.LifeCycle;
import org.apache.river.api.util.Startable;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.tcp.TcpServerEndpoint;

import java.io.File;
import java.io.IOException;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureClassLoader;
import java.util.*;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

/**
 * Activatable implementation of the TestService interface.
 */
public class TestServiceImpl implements TestService, ProxyAccessor, Startable {

    private static volatile int staticInt = 0;
    
    private Uuid uuid;

    private TestService serverStub = null;

    private static final String TEST_SERVICE = "com.sun.jini.testservice";
    private boolean started;
    private Exporter exporter;
    private AccessControlContext context;

    public synchronized Object getProxy() { return serverStub; }

    // Activation constructor
    public TestServiceImpl(ActivationID activationID, MarshalledObject data)
        throws Exception
    {
	init((String[])data.get());
    }

    // 
    public TestServiceImpl(String[] configArgs, LifeCycle lc)
        throws Exception
    {
	init(configArgs);
    
    }
    
    private void init(String[] configArgs) throws Exception {
        final Configuration config =
            ConfigurationProvider.getInstance(configArgs);
        LoginContext loginContext = (LoginContext) config.getEntry(
            TEST_SERVICE, "loginContext", LoginContext.class, null);
        if (loginContext != null) {
            doInitWithLogin(config, loginContext);
        } else {
            doInit(config);
        }
    }

    private void doInitWithLogin(final Configuration config,
        LoginContext loginContext) throws Exception
    {
        loginContext.login();
        try {
            Subject.doAsPrivileged(
                loginContext.getSubject(),
                new PrivilegedExceptionAction() {
                    public Object run() throws Exception {
                        doInit(config);
                        return null;
                    }
                },
                null);
        } catch (PrivilegedActionException e) {
           try {
                loginContext.logout();
            } catch (LoginException le) {
                System.out.println("Trouble logging out" + le);
            }
            throw e.getException();
        }
    }

    /** Initialization common to both activatable and transient instances. */
    private void doInit(Configuration config) throws Exception {
        uuid = net.jini.id.UuidFactory.generate();

        exporter = (Exporter) Config.getNonNullEntry(
            config, TEST_SERVICE, "exporter", Exporter.class,
            new BasicJeriExporter(
                TcpServerEndpoint.getInstance(0), new BasicILFactory(), false, true));
        System.out.println("service exporter is: "
            +  exporter);
        context = AccessController.getContext();
        
        // Store class loader ref in shared map
        TestServiceSharedMap.storeClassLoader(uuid, this.getClass().getClassLoader());
    }

    // inherit javadoc
    // used by SecurityTest
    public byte[] loadFile(File file)
        throws RemoteException, FileNotFoundException, IOException
    {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int nextByte;
	try {
            while ((nextByte = fis.read()) != -1) {
                bos.write(nextByte);
            }
	} catch (EOFException eofe) {
	    eofe.printStackTrace(System.err);
	}
	fis.close();
	bos.flush();
        return bos.toByteArray();
    }

    // inherit javadoc
    // used by CodebaseTest & ClasspathTest
    public Object loadClass(String className)
        throws RemoteException, ClassNotFoundException,
            IllegalAccessException, InstantiationException
    {
        Class newClass = Class.forName(className);
        return newClass.newInstance();
    }

    // inherit javadoc
    // used by ClasspathTest
    public void setCommonStaticVariable(int newValue) throws RemoteException {
	System.out.println("TestServiceImpl.setCommonStaticVariable: "
	    + "class path is " + System.getProperty("java.class.path"));
        TestServiceDummyClass0.commonStaticInt = newValue;
    }

    // inherit javadoc
    // used by ClasspathTest
    public int getCommonStaticVariable() throws RemoteException {
	System.out.println("TestServiceImpl.getCommonStaticVariable: "
	    + "class path is " + System.getProperty("java.class.path"));
        return TestServiceDummyClass0.commonStaticInt;
    }

    // inherit javadoc
    // used by ClasspathTest
    public void setLocalStaticVariable(int newValue) throws RemoteException {
        TestServiceImpl.staticInt = newValue;
    }

    // inherit javadoc
    // used by ClasspathTest
    public int getLocalStaticVariable() throws RemoteException {
        return TestServiceImpl.staticInt;
    }

    // inherit javadoc
    // used by ClassLoaderTest
    public boolean compareSiblingClassLoaderHierarchy(Uuid other) 
        throws RemoteException 
    {
        boolean result = false;
	try {
            ClassLoader thisClassLoader;
            synchronized (this){
                thisClassLoader = 
                    (ClassLoader) TestServiceSharedMap.getClassLoader(uuid);
            }
            ClassLoader otherClassLoader = 
                (ClassLoader) TestServiceSharedMap.getClassLoader(other);
            ArrayList thisLoaders = new ArrayList();
            ArrayList otherLoaders = new ArrayList();
            getClassLoaderTree(thisClassLoader, thisLoaders);
            System.out.println("This service's class loaders: " + thisLoaders);
            getClassLoaderTree(otherClassLoader, otherLoaders);
            System.out.println("Other service's class loaders: " + otherLoaders);  
            List thisCommon = thisLoaders.subList(0, thisLoaders.size()-2);
            List otherCommon = otherLoaders.subList(0, otherLoaders.size()-2);
            if (thisClassLoader != null && 
                otherClassLoader != null &&
                thisLoaders.size() == otherLoaders.size() &&
                thisCommon.containsAll(otherCommon) &&
                !thisClassLoader.equals(otherClassLoader)) 
            {
                result = true;
            }
        } catch (Exception e) {
	    System.out.println("TestServiceImpl.getClassLoaderHierarchy: "
		+ "unexpected exception");
	    e.printStackTrace(System.out);
	}
        return result;
    }

    // used by compareToClassLoaderHierarchy()
    private void getClassLoaderTree(ClassLoader c, List l) {
        if (c != null) {
            getClassLoaderTree(c.getParent(), l);
        }
        l.add(c);
    }
    
    public synchronized Uuid getUuid() throws RemoteException {
        return uuid;
    }

    @Override
    public final synchronized void start() throws Exception {
        if (started) return;
        started = true;
        AccessController.doPrivileged(new PrivilegedExceptionAction<Object>(){

            @Override
            public Object run() throws Exception {
                // Export server instance and get its reference
                serverStub =  (TestService) exporter.export(TestServiceImpl.this);
                System.out.println("Service stub is: " + serverStub);
                return null;
            }
            
        }, context);
    }
}
