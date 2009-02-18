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

package com.sun.jini.test.spec.config.configurationprovider;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.File;
import java.io.FilePermission;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandlerFactory;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.SecureClassLoader;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import sun.misc.Resource;
import sun.misc.URLClassPath;
import sun.net.www.ParseUtil;

/**
 * That is fake class loader that is equal to URLClassLoader
 * but doesn't really shows specified parent ClassLoader as parent
 * but have internal deligation to it
 */
public class FakeURLClassLoader extends URLClassLoader {
    URLClassLoader parent = null;
    
    public FakeURLClassLoader(URL[] urls, ClassLoader parent) {
	super(urls, null);
        this.parent = new URLClassLoader(urls, parent);
    }

    public FakeURLClassLoader(URL[] urls) {
	super(urls, null);
        this.parent = new URLClassLoader(urls,
                Thread.currentThread().getContextClassLoader());
    }

    public FakeURLClassLoader(URL[] urls, ClassLoader parent,
			  URLStreamHandlerFactory factory) {
	super(urls, null, factory);
        this.parent = new URLClassLoader(urls, parent, factory);
    }

    public Class loadClass(String name) throws ClassNotFoundException {
	return parent.loadClass(name);
    }

    public URL findResource(final String name) {
        return parent.findResource(name);
    }

    public Enumeration findResources(final String name) throws IOException {
        return parent.findResources(name);
    }
}

