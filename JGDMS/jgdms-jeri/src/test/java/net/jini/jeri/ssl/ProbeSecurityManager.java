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
package net.jini.jeri.ssl;

import java.security.AccessController;
import java.security.Permission;

/**
 * The security manager of {@code TimeoutSchedulerProbe}: checks the
 * permissions that the thread under test demands against its access control
 * context, and no others.  It must be defined in a protection domain that has
 * all permissions, like the security manager of the JDK, because its own frame
 * is on the stack of every check.
 */
@SuppressWarnings("removal")
public final class ProbeSecurityManager extends SecurityManager {

    /**
     * Set while a check is under way on this thread.  A plain SecurityManager
     * has no defence against a check that causes another, as loading the
     * policy does (it reads a security property), which then recurses until
     * the stack is exhausted; DirtyChai's CombinerSecurityManager has one, so
     * this has it too.  A check made while checking is not part of the
     * scenario, and is not made.
     */
    private static final ThreadLocal<Boolean> CHECKING = new ThreadLocal<>();

    @Override
    public void checkPermission(Permission p) {
        if (!TimeoutSchedulerProbe.ARMED.get() || CHECKING.get() != null) {
            return;
        }
        CHECKING.set(Boolean.TRUE);
        try {
            check(p);
        } finally {
            CHECKING.remove();
        }
    }

    private static void check(Permission p) {
        if (p.getClass().getSimpleName().equals("DefineClassPermission")) {
            /*
             * DirtyChai's check for the hidden class of a lambda is of the
             * defining class alone, and every class of the jar has it
             * already: not part of this scenario.
             */
            return;
        }
        if (p instanceof java.io.FilePermission && p.getName().endsWith(".class")) {
            /* The test's class loader reading the copies. */
            return;
        }
        TimeoutSchedulerProbe.demanded(p);
        AccessController.checkPermission(p);
    }

    @Override
    public void checkPermission(Permission p, Object context) {
        checkPermission(p);
    }
}
