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

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;

import net.jini.id.Uuid;

/**
 * Fake service used by ClassLoaderTest, ClasspathTest,
 * CodebaseTest and SecurityTest.
 */
public interface TestService extends Remote {

    /**
     * Load the specified file and return it's contents as
     * an array of bytes.  An exception should be thrown if
     * the test service doesn't have permission to read the file.
     *
     * Used by: SecurityTest
     */
    public byte[] loadFile(File file)
        throws RemoteException, FileNotFoundException, IOException;

    /**
     * Load the class represented by the specified classname
     * and return a new instance of the class.
     *
     * Used by: CodebaseTest, ClasspathTest
     */
    public Object loadClass(String className)
        throws RemoteException, ClassNotFoundException,
            IllegalAccessException, InstantiationException;

    /**
     * Set the value of a static variable 
     * in TestServiceDummyClass0 (a class with a
     * defining class loader that is common to 
     * all application class loaders).
     *
     * Used by: ClasspathTest
     */
    public void setCommonStaticVariable(int newValue) throws RemoteException;

    /**
     * Get the current value of a static variable 
     * in TestServiceDummyClass0 (a class with a
     * defining class loader that is common to 
     * all application class loaders).
     *
     * Used by: ClasspathTest
     */
    public int getCommonStaticVariable() throws RemoteException;

    /**
     * Set the value of a static variable 
     * in the class implementing this interface
     *
     * Used by: ClasspathTest
     */
    public void setLocalStaticVariable(int newValue) throws RemoteException;

    /**
     * Get the current value of a static variable 
     * in the class implementing this interface
     *
     * Used by: ClasspathTest
     */
    public int getLocalStaticVariable() throws RemoteException;

    /**
     * Returns true if the class loader hierarchy of "this" service object
     * has the same ancestors but different "leaf" loaders. 
     *
     * Used by: ClassLoaderTest
     */
    public boolean compareSiblingClassLoaderHierarchy(Uuid id) throws RemoteException;

    /**
     * Returns Uuid for this service instance.
     *
     * Used by: ClassLoaderTest
     */
    public Uuid getUuid() throws RemoteException;
}
