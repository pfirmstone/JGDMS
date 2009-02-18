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
/* @test
 * @bug 6304035
 * @summary The current thread's context class loader should not be
 * pinned (kept strongly reachable) merely as a result of performing a
 * PreferredClassProvider operation, such as because the codebase URL
 * path for the operation matches an ancestor of the current thread's
 * context loader (i.e. a boomerang match) that is unlikely to become
 * itself unreachable, or even simply because no further
 * PreferredClassProvider operations occur.
 *
 * @build PinContextLoader
 * @run main/othervm/policy=security.policy PinContextLoader
 */

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.rmi.server.RMIClassLoader;
import java.util.logging.LogManager;
import net.jini.loader.ClassAnnotation;

public class PinContextLoader {

    private static final String CODEBASE = "http://localhost:8080/classes.jar";
    private static final long TIMEOUT = 5000;	// 5 seconds

    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 6304035\n");

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	/*
	 * Because java.util.logging.LogManager's (singleton)
	 * construction registers a "shutdown hook", which is a kind
	 * of thread, that inherits and pins whatever happens to be
	 * the current thread's context class loader for the lifetime
	 * of the VM, ensure that that happens before we set our
	 * ephemeral loader as the context class loader.
	 */
	LogManager.getLogManager();

	Class thisClass = PinContextLoader.class;
	ClassLoader boomerangLoader =
	    new AnnotatedDummyLoader(CODEBASE, thisClass.getClassLoader());
	ClassLoader ephemeralLoader = new DummyLoader(boomerangLoader);
	Thread.currentThread().setContextClassLoader(ephemeralLoader);
	RMIClassLoader.loadClass(CODEBASE, thisClass.getName(), null);
	Thread.currentThread().setContextClassLoader(boomerangLoader);
	ReferenceQueue refQueue = new ReferenceQueue();
	Reference weakLoader = new WeakReference(ephemeralLoader, refQueue);
	ephemeralLoader = null;
	System.gc();
	Reference ref = refQueue.remove(TIMEOUT);
	if (ref == null) {
	    throw new RuntimeException(
		"TEST FAILED: loader not detected weakly reachable");
	} else if (ref != weakLoader) {
	    throw new AssertionError(ref);
	}
	System.err.println("TEST PASSED");
    }

    private static class DummyLoader extends ClassLoader {
	DummyLoader(ClassLoader parent) {
	    super(parent);
	}
    }

    private static class AnnotatedDummyLoader
	extends ClassLoader
	implements ClassAnnotation
    {
	private final String annotation;

	AnnotatedDummyLoader(String annotation, ClassLoader parent) {
	    super(parent);
	    this.annotation = annotation;
	}

	public String getClassAnnotation() {
	    return annotation;
	}
    }
}
