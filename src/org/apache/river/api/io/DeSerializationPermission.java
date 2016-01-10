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

package org.apache.river.api.io;

import java.security.BasicPermission;

/**
 * Permission that when granted, allows de-serialization of Serializable 
 * object classes outside the trusted set defined in MarshalInputStream.
 * <p>
 * During de-serialization of
 * Serializable objects, no domains representing the remote caller are 
 * present in the execution context, increasing the likelihood of privilege
 * escalation, for that reason, this security check is only performed
 * from unprivileged context, before de-serialization of an object occurs.
 * <p>
 * Granting this permission means we have audited and trust the class
 * source code to perform de-serialization safely.
 * <p>
 * Only system wide grants have any effect, this permission cannot be granted
 * to a codebase, Signer or Subject Principal.
 * <p>
 * Implementations of {@link org.apache.river.api.io.Portable} that do NOT
 * implement Serializable are exempted from this security check as they are
 * created with unprivileged execution context.  Portable objects are sill 
 * subjected to a {@link net.jini.loader.RemoteClassLoadingPermission} security
 * check.  Objects with downloaded
 * code that is also Serializable will have to pass both security checks.
 * <p>
 * Users who want to disable this security check should use a wildcard "*" grant 
 * statement in their security policy file:
 * <p>
 * <code>
 * grant{ <br>
 *	permission net.jini.io.DeSerializationPermission "*";
 * };
 * </code>
 * <p>
 * <p>
 * Uses BasicPermission hierarchical naming conventions and wild card rules.
 * 
 * @author peter
 * @since 3.0.0
 */
public class DeSerializationPermission extends BasicPermission {

    public DeSerializationPermission(String fullyQualifiedClassName) {
	super(fullyQualifiedClassName);
    }

}
