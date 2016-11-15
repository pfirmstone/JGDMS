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

import java.security.BasicPermission;

/**
 * {@link java.rmi.server.RMIClassLoaderSpi} instances that
 * implement {@link net.jini.loader.ClassLoading.SafeUnmarshalling} check
 * before loading class files from a downloaded codebase, that the codebase's
 * ProtectionDomain is permitted to load classes.
 * <p>
 * This functionality is limited to JERI and not supported by JRMP.
 * <p>
 * Note that a lack of this permission doesn't prevent loading interfaces.  
 * For that reason, static field initializers may be run, however interfaces
 * are not permitted to use static initializer blocks, so cannot throw an 
 * exception such as ThreadDeath directly, or call privileged methods
 * as a {@link java.security.ProtectionDomain} representing the untrusted 
 * interface is present in the current execution context
 * {@link java.security.AccessControlContext}.
 * <p>
 * It is recommended for this permission to be granted to code Signers, or
 * be granted after proxy preparation.
 * <p>
 * If this permission is to be granted after proxy preparation, a jar file 
 * can declare the permissions it needs granted during proxy preparation, see 
 * {@link org.apache.river.api.security.AdvisoryDynamicPermissions}.
 * <p>
 * Users who want to disable this security check should use a wildcard "*" grant 
 * statement in their security policy file:
 * <p>
 * <code>
 * grant{ <br>
 *	permission net.jini.loader.RemoteClassLoadingPermission "*";
 * };
 * </code>
 * <p>
 * Uses BasicPermission hierarchical naming conventions and wild card rules.
 * 
 * @author peter
 */
public class RemoteClassLoadingPermission extends BasicPermission {

    public RemoteClassLoadingPermission(String name) {
	super(name);
    }
    
}
