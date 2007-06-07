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

package com.sun.jini.thread;

import java.security.BasicPermission;

/**
 * Permission to use internal thread pools (see GetThreadPoolAction).
 *
 * A ThreadPoolPermission contains a name (also referred to as a
 * "target name") but no action list; you either have the named
 * permission or you don't.
 *
 * ThreadPoolPermission defines two target names,
 * "getSystemThreadPool" and "getUserThreadPool", for permission to
 * get the system thread group pool and permission to get the
 * non-system thread group pool.
 *
 * A ThreadPoolPermission implies the following:
 *
 * - permission to access the thread group designated by the target
 *   name; see SecurityManager.checkAccess(ThreadGroup)
 *
 * - permission to execute a NewThreadAction, which requires
 *   RuntimePermission("getClassLoader") and
 *   RuntimePermission("setContextClassLoader"); more practically,
 *   this is permission to have a Runnable executed with the system
 *   class loader as the context class loader
 *
 * - permission to execute a Runnable without the caller's
 *   protection domain in force
 *
 * @author Sun Microsystems, Inc.
 **/
public final class ThreadPoolPermission extends BasicPermission {

    private static final long serialVersionUID = -2515392803055387779L;

    /**
     * Creates a new ThreadPoolPermission with the
     * specified name.
     **/
    public ThreadPoolPermission(String name) {
	super(name);
    }
}
