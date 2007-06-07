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
package com.sun.jini.fiddler;

import net.jini.security.AccessPermission;

/** 
 * Special purpose permission class, specific to the
 * Fiddler implementation of the lookup discovery service, that can be used
 * to express the access control policy for that service's backend server
 * when it is exported with a {@link net.jini.jeri.BasicJeriExporter}. This
 * permission class can be passed to an instance of
 * {@link net.jini.jeri.BasicInvocationDispatcher}, and then used in
 * security policy permission grants.
 * <p>
 * An instance of this class contains a name (also referred to as a
 * <i>target name</i>), but no actions list; you either have the named
 * permission or you don't. The convention is that the target name is the
 * <i>non</i>-fully qualified name of the remote method being invoked.
 * Wildcard matches are supported using the syntax specified by
 * {@link AccessPermission}.
 * <p>
 *
 * The target names that can be used with a Fiddler server are specified in
 * the package documentation for {@linkplain com.sun.jini.fiddler Fiddler}.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.0
 */
public class FiddlerPermission extends AccessPermission {

    private static final long serialVersionUID = 2L;

    /**
     * Creates an instance of this class having the specified target name.
     *
     * @throws NullPointerException if the given target name is
     *         <code>null</code>.
     * @throws IllegalArgumentException if the given target name does not
     *         match the required syntax, as specified by the class
     *         {@link AccessPermission}.
     */
    public FiddlerPermission(String name) {
	super(name);
    }
}
