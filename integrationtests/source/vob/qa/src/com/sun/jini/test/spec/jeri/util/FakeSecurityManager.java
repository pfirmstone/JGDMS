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
package com.sun.jini.test.spec.jeri.util;

import java.security.Permission;
import java.util.logging.Logger;

/**
 * A SecurityManager subclass.  The constructor takes lists of 
 * permissions to explicitly allow and prohibit.
 * The overloaded checkPermission method throws
 * SecurityException if the permission to check is in the
 * prohibit list and returns quietly if the permission to check
 * is in the allow list.
 */
public class FakeSecurityManager extends SecurityManager {

    Logger logger;
    private Permission[] allow;
    private Permission[] prohibit;
    private SecurityManager delegate;

    /**
     * Constructs a FakeSecurityManager.  See <code>checkPermission</code>
     * method for how these parameters interact.
     *
     * @param allow list of permissions to allow, or <code>null</code>
     * @param prohibit list of permissions to prohibit, or <code>null</code>
     * @param delegate if not <code>null</code>, the SecurityManager
     *        to delegate permission checks to
     */
    public FakeSecurityManager(Permission[] allow, Permission[] prohibit,
        SecurityManager delegate)
    {
        super();
        logger = Logger.getLogger("com.sun.jini.qa.harness.test");
        logger.entering(getClass().getName(),"constructor");
        this.allow = (allow != null ? allow : new Permission[] {});
        this.prohibit = (prohibit != null ? prohibit : new Permission[] {});
        this.delegate = delegate;
    }

    /**
     * Checks <code>perm</code> in the following order:
     * <ul>
     *   <li>throws SecurityException if <code>perm</code> is in prohibit list
     *   <li>returns quietly if <code>perm</code> is in allow list
     *   <li>if delegate is not null, calls delegate.checkPermission(perm)
     *   <li>if delegate is null, calls super.checkPermission(perm)
     * </ul>
     *
     * @param perm the permission to check
     */
    public void checkPermission(Permission perm) {
        logger.entering(getClass().getName(),"checkPermission",perm);
        if (setImplies(prohibit,perm)) {
            throw new SecurityException();
        } else if (setImplies(allow,perm)) {
            return;
        } else if (delegate != null) {
            delegate.checkPermission(perm);
        } else {
            super.checkPermission(perm);
        }
    }

    private boolean setImplies(Permission[] set, Permission perm) {
        for (int i = 0; i < set.length; i++) {
            if (set[i].implies(perm)) {
                return true;
            }
        }
        return false;
    }

}
