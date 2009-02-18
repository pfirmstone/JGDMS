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
package com.sun.jini.test.spec.security.util;

// java
import java.security.AccessController;
import java.security.Permission;


/**
 * Class implementing PrivilegedExceptionAction interface whose run method
 * always throws checked FakeException.
 */
public class PEAThrowingCheckedEx
        extends TestPrivilegedExceptionAction {

    /** Exception which was thrown by run method. */
    protected Exception ex = null;

    /**
     * Default constructor.
     */
    public PEAThrowingCheckedEx() {
        super();
    }

    /**
     * Sets Permission to check to parameter's value.
     *
     * @param perm permission to check
     */
    public PEAThrowingCheckedEx(Permission perm) {
        super(perm);
    }

    /**
     * Gets current DomainCombiner and stores it.
     *
     * @throws FakeException always
     */
    public Object run() throws FakeException {
       comb = AccessController.getContext().getDomainCombiner();

       if (perm != null) {
           try {
               System.getSecurityManager().checkPermission(perm);
               isGranted = true;
           } catch (SecurityException se) {
               isGranted = false;
           }
       }
       ex = new FakeException();
       throw (FakeException) ex;
    }

    /**
     * Returns Exception thrown by run method.
     *
     * @return Exception thrown by run method
     */
    public Exception getException() {
        return ex;
    }
}
