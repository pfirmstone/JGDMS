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

package org.apache.river.proxy;

import java.net.MalformedURLException;
import java.rmi.server.RMIClassLoader;
import net.jini.loader.ClassLoading;

/**
 * Provided only for proxy binary backward compatibility with River versions
 * before 3.0
 * @author peter
 * @since 3.0
 */
public final class CodebaseProvider {
    private CodebaseProvider(){
	throw new AssertionError();
    }
    
    public static String getClassAnnotation(Class clas){
	try {
	    return ClassLoading.getClassAnnotation(clas);
	} catch (NoSuchMethodError e){
	    // Ignore, earlier version of River.
	}
	return RMIClassLoader.getClassAnnotation(clas);
    }
    
    public static Class loadClass(String codebase,
				  String name,
				  ClassLoader defaultLoader,
				  boolean verifyCodebaseIntegrity,
				  ClassLoader verifierLoader)
	throws MalformedURLException, ClassNotFoundException 
    {
	try {
	    return ClassLoading.loadClass(codebase, name, defaultLoader,
		    verifyCodebaseIntegrity, verifierLoader);
	} catch (NoSuchMethodError e){
	    // Ignore, earlier version of River.
	}
	return RMIClassLoader.loadClass(codebase, name, defaultLoader);
    }
    
    public static Class loadProxyClass(String codebase,
				       String[] interfaceNames,
				       ClassLoader defaultLoader,
				       boolean verifyCodebaseIntegrity,
				       ClassLoader verifierLoader)
	throws MalformedURLException, ClassNotFoundException
    {
	try {
	    return ClassLoading.loadProxyClass(codebase, interfaceNames, defaultLoader,
		    verifyCodebaseIntegrity, verifierLoader);
	} catch (NoSuchMethodError e){
	    // Ignore, earlier version of River.
	}
	return RMIClassLoader.loadProxyClass(codebase, interfaceNames, defaultLoader);
    }
}
