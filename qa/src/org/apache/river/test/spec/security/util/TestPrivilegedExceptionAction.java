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
package org.apache.river.test.spec.security.util;

// java
import java.security.AccessController;
import java.security.DomainCombiner;
import java.security.PrivilegedExceptionAction;
import java.security.Permission;


/**
 * Class implementing PrivilegedExceptionAction interface.
 */
public class TestPrivilegedExceptionAction
        implements PrivilegedExceptionAction {

    /** Object which was returned by run method. */
    protected Object obj = null;

    /** DomainCombiner got in run method. */
    protected DomainCombiner comb = null;

    /** Permission to check. */
    protected Permission perm;

    /** Result of 'SecurityManager.checkPermission' method call. */
    protected boolean isGranted;

    /**
     * Default constructor.
     */
    public TestPrivilegedExceptionAction() {
        perm = null;
    }

    /**
     * Sets Permission to check to parameter's value.
     *
     * @param perm permission to check
     */
    public TestPrivilegedExceptionAction(Permission perm) {
        this.perm = perm;
    }

    /**
     * Gets current DomainCombiner and stores it.
     *
     * @return test object
     */
    public Object run() throws Exception {
       comb = AccessController.getContext().getDomainCombiner();

       if (perm != null) {
           try {
               System.getSecurityManager().checkPermission(perm);
               isGranted = true;
           } catch (SecurityException se) {
               isGranted = false;
           }
       }
       obj = new TestObject();
       return obj;
    }

    /**
     * Returns result of 'SecurityManager.checkPermission' method call.
     *
     * @return result of 'SecurityManager.checkPermission' method call
     */
    public boolean isGrantedPerm() {
        return isGranted;
    }

    /**
     * Returns stored domain combiner.
     *
     * @return stored domain combiner
     */
    public DomainCombiner getCombiner() {
        return comb;
    }

    /**
     * Returns object returned by run method.
     *
     * @return object returned by run method
     */
    public Object getObject() {
        return obj;
    }
}
