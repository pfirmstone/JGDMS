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
public class CodebaseAccessClassLoader {
	private static CodebaseClassAccess provider = new RMIClassLoaderCodebaseAccess();

	/**
	 * Sets the provider that will resolve classes for remote codebases.
	 * The initial provider is {@link RMIClassLoaderCodebaseAccess} which uses the
	 * {@link java.rmi.server.RMIClassLoaderSpi} defined mechanisms.
	 * Requires CodebaseAccessOverridePermission() for the current provider's class name
	 */
	public static void setCodebaseAccessClassLoader( CodebaseClassAccess access ) {
		SecurityManager sm = System.getSecurityManager();
		if( sm != null ) {
			sm.checkPermission(
				new CodebaseAccessOverridePermission( provider.getClass().getName() ) );
		}
		provider = access;
	}
	public static Class loadClass( String codebase, String name, ClassLoader defaultLoader ) throws IOException,ClassNotFoundException {
		return provider.loadClass( codebase, name, defaultLoader != null ? defaultLoader : getParentContextClassLoader() );
	}

	public static Class loadProxyClass( String codebase, String[] interfaceNames, ClassLoader defaultLoader ) throws IOException,ClassNotFoundException {
		return provider.loadProxyClass( codebase, interfaceNames, defaultLoader);
	}

	public static String getClassAnnotation( Class cls ) {
		return provider.getClassAnnotation( cls );
	}

	public static ClassLoader getClassLoader( String codebase ) throws IOException {
		return provider.getClassLoader( codebase );
	}

	public static Class loadClass(String name, String location) throws IOException, ClassNotFoundException {
		return provider.loadClass( name, location);
	}

	public static ClassLoader createClassLoader( final URL[] urls, final ClassLoader parent,
			final boolean requireDlPerm, final AccessControlContext ctx ) {
		return provider.createClassLoader( urls, parent, requireDlPerm, ctx );
	}
	public static ClassLoader getParentContextClassLoader() {
		return provider.getParentContextClassLoader();
	}
	public static ClassLoader getSystemContextClassLoader( ClassLoader defaultLoader ) {
		return provider.getSystemContextClassLoader( defaultLoader );
	}
}
