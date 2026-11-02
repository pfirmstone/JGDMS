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
package au.zeus.jgdms.security.jwt;

import java.security.PrivilegedAction;

/**
 * Creates (and optionally starts) one daemon thread, for use inside
 * {@code AccessController.doPrivileged}.
 *
 * <p>This class is this module's own, deliberately not {@code
 * org.apache.river.thread.NewThreadAction}: a new thread inherits the access control
 * context of the stack that created it, and a class of another jar on that stack
 * puts that jar's protection domain into every thread's context, so that jar would
 * need the thread, HTTP and Subject permissions of this module's threads.  Nothing
 * outside this module and the JDK may be called from {@link #run()}.
 *
 * <p>Observable behaviour is that of {@code NewThreadAction}: the name carries the
 * JGDMS prefix ("(JSK)", Jini Starter Kit, historical), the thread is a daemon, and
 * its context class loader is the system class loader so that it cannot pin an
 * application loader.
 */
final class NewDaemonThread implements PrivilegedAction<Thread> {

    /** Name prefix shared with the other JGDMS threads. */
    static final String NAME_PREFIX = "(JSK) ";

    private final Runnable runnable;
    private final String name;
    private final boolean start;

    NewDaemonThread(Runnable runnable, String name, boolean start) {
        this.runnable = runnable;
        this.name = name;
        this.start = start;
    }

    @Override
    public Thread run() {
        Thread t = new Thread(runnable, NAME_PREFIX + name);
        t.setDaemon(true);
        t.setContextClassLoader(ClassLoader.getSystemClassLoader());
        if (start) t.start();
        return t;
    }
}
