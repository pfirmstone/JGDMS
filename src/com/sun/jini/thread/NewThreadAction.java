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

import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;

/**
 * A PrivilegedAction for creating a new thread conveniently with an
 * AccessController.doPrivileged or Security.doPrivileged.
 *
 * All constructors allow the choice of the Runnable for the new
 * thread to execute, the name of the new thread (which will be
 * prefixed with the constant NAME_PREFIX), and whether or not it will
 * be a daemon thread.
 *
 * The new thread may be created in the system thread group (the root
 * of the thread group tree) or an internally created non-system
 * thread group, as specified at construction of this class.
 *
 * The new thread will have the system class loader as its initial
 * context class loader (that is, its context class loader will NOT be
 * inherited from the current thread).
 *
 * @author	Sun Microsystems, Inc.
 **/
public final class NewThreadAction implements PrivilegedAction {

    static final String NAME_PREFIX = "(JSK) ";

    /** cached reference to the system (root) thread group */
    static final ThreadGroup systemThreadGroup = (ThreadGroup)
	AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		ThreadGroup group = Thread.currentThread().getThreadGroup();
		ThreadGroup parent;
		while ((parent = group.getParent()) != null) {
		    group = parent;
		}
		return group;
	    }
	});

    /**
     * special child of the system thread group for running tasks that
     * may execute user code, so that the security policy for threads in
     * the system thread group will not apply
     */
    static final ThreadGroup userThreadGroup = (ThreadGroup)
	AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		return new ThreadGroup(systemThreadGroup,
				       NAME_PREFIX + "Runtime");
	    }
	});

    private static final Permission getClassLoaderPermission =
	new RuntimePermission("getClassLoader");

    private final ThreadGroup group;
    private final Runnable runnable;
    private final String name;
    private final boolean daemon;

    NewThreadAction(ThreadGroup group, Runnable runnable,
		    String name, boolean daemon)
    {
	this.group = group;
	this.runnable = runnable;
	this.name = name;
	this.daemon = daemon;
    }

    /**
     * Creates an action that will create a new thread in the
     * system thread group.
     *
     * @param	runnable the Runnable for the new thread to execute
     *
     * @param	name the name of the new thread
     *
     * @param	daemon if true, new thread will be a daemon thread;
     * if false, new thread will not be a daemon thread
     */
    public NewThreadAction(Runnable runnable, String name, boolean daemon) {
	this(systemThreadGroup, runnable, name, daemon);
    }

    /**
     * Creates an action that will create a new thread.
     *
     * @param	runnable the Runnable for the new thread to execute
     *
     * @param	name the name of the new thread
     *
     * @param	daemon if true, new thread will be a daemon thread;
     * if false, new thread will not be a daemon thread
     *
     * @param	user if true, thread will be created in a non-system
     * thread group; if false, thread will be created in the system
     * thread group
     */
    public NewThreadAction(Runnable runnable, String name, boolean daemon,
			   boolean user)
    {
	this(user ? userThreadGroup : systemThreadGroup,
	     runnable, name, daemon);
    }

    public Object run() {
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkPermission(getClassLoaderPermission);
	}
	Thread t = new Thread(group, runnable, NAME_PREFIX + name);
	t.setContextClassLoader(ClassLoader.getSystemClassLoader());
	t.setDaemon(daemon);
	return t;
    }
}
