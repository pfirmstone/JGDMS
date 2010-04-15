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
package net.jini.loader;

import java.io.IOException;
import java.net.URL;
import java.security.AccessControlContext;

/**
 *
 * @author Gregg Wonderly
 */
public interface CodebaseClassAccess {
	public Class loadClass(String codebase,
				  String name ) throws IOException, ClassNotFoundException;
	public Class loadClass(String codebase,
				  String name,
				  ClassLoader defaultLoader) throws IOException,ClassNotFoundException;
    public Class loadProxyClass(String codebase,
				       String[] interfaceNames,
				       ClassLoader defaultLoader ) throws IOException,ClassNotFoundException;
	public String getClassAnnotation( Class cls );
	public ClassLoader getClassLoader(String codebase) throws IOException;
	public ClassLoader createClassLoader( URL[] urls,
					    ClassLoader parent,
					    boolean requireDlPerm,
						AccessControlContext ctx );
	/**
	 * This should return the class loader that represents the system
	 * environment.  This might often be the same as {@link #getSystemContextClassLoader()}
	 * but may not be in certain circumstances where container mechanisms isolate certain
	 * parts of the classpath between various contexts.
	 * @return
	 */
    public ClassLoader getParentContextClassLoader();
	/**
	 * This should return the class loader that represents the local system
	 * environment that is associated with never-preferred classes
	 * @return
	 */
    public ClassLoader getSystemContextClassLoader( ClassLoader defaultLoader );
}
