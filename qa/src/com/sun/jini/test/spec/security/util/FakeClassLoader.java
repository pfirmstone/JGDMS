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
package com.sun.jini.test.spec.security.util;

// java
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;


/**
 * TestClassLoader
 */
public class FakeClassLoader extends URLClassLoader {

    /** Classes which was loaded through this class loader. */
    protected ArrayList classes;

    /**
     * Constructs a new ClassLoader for the given URL.
     *
     * @param url the URL from which to load classes and resources
     */
    public FakeClassLoader(URL url) {
        super(new URL[] { url });
        classes = new ArrayList();
    }

    /**
     * Calls super.loadClass method and add loaded class to the array of
     * classes.
     *
     * @param name the name of the class
     * @return the resulting Class object
     * @throws ClassNotFoundException if the class was not found
     */
    public Class loadClass(String name) throws ClassNotFoundException {
        Class cl = super.loadClass(name);
        classes.add(cl);
        return cl;
    }

    /**
     * Returns an array of classes which was loaded through this class loader.
     *
     * @return an array of classes which was loaded through this class loader
     */
    public Class[] getClasses() {
        return (Class []) classes.toArray(new Class[classes.size()]);
    }
}
