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
import java.security.Permission;
import java.security.Principal;

// net.jini
import net.jini.security.policy.DynamicPolicy;


/**
 * Base policy provider for test DynamicPolicy provider classes
 */
public abstract class BaseDynamicPolicyProvider extends BasePolicyProvider
        implements DynamicPolicy {

    /** Number of 'grantSupported' method calls. */
    protected int grantSupNum = 0;

    /** Parameters passed to 'grant' method. */
    protected Object[] grantParams = null;

    /**
     * Method from DynamicPolicy interface. Does nothing.
     *
     * @return false
     */
    public boolean grantSupported() {
        return false;
    }

    /**
     * Method from DynamicPolicy interface. Does nothing. Just stores incoming
     * parameters.
     */
    public void grant(Class cl, Principal[] principals,
            Permission[] permissions) {
        grantParams = new Object[] { cl, principals, permissions };
    }

    /**
     * Returns number of 'grantSupported' method calls.
     *
     * @return number of 'grantSupported' method calls
     */
    public int getGrantSupNum() {
        return grantSupNum;
    }

    /**
     * Returns parameters passed to 'grant' method.
     *
     * @return parameters passed to 'grant' method
     */
    public Object[] getGrantParams() {
        return grantParams;
    }

    /**
     * Method from DynamicPolicy interface. Does nothing.
     *
     * @return null
     */
    public Permission[] getGrants(Class cl, Principal[] principals) {
        return null;
    }
}
