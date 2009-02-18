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
import java.security.Permission;
import java.security.Principal;
import java.security.PermissionCollection;
import java.security.CodeSource;
import java.util.ArrayList;

/**
 * Test Policy provider implementing DynamicPolicy interface.
 */
public class TestDynamicPolicyProvider extends BaseDynamicPolicyProvider {

    /** Value returned by grantSupported method. */
    protected boolean isGrantSupported;

    /** Permissions to be returned by getGrants method. */
    protected Permission[] permissions;

    /**
     * Permissions to be excluded from TestPermissionCollection returned
     * by 'getPermissions' method.
     */
    protected Permission[] exPermissions;

    /** Parameters passed to 'getGrants' method. */
    protected Object[] getGrantsParams;

    /**
     * Default no-arg constructor.
     */
    public TestDynamicPolicyProvider() {
        permissions = null;
        exPermissions = null;
        isGrantSupported = false;
    }

    /**
     * Constructor setting permissions array to given one.
     *
     * @param permissions array of permissions to be stored
     */
    public TestDynamicPolicyProvider(Permission[] permissions) {
        isGrantSupported = false;
        this.permissions = permissions;
        exPermissions = null;
        getGrantsParams = null;
    }

    /**
     * Constructor setting permissions array to given one.
     * exPermissions array will be excluded from TestPermissionCollection
     * returned by 'getPermissions' method.
     *
     * @param permissions array of permissions to be stored
     * @param exPermissions array of permissions to be excluded from the final
     *        array
     */
    public TestDynamicPolicyProvider(Permission[] permissions,
            Permission[] exPermissions) {
        isGrantSupported = false;
        this.permissions = permissions;
        this.exPermissions = exPermissions;
        getGrantsParams = null;
    }

    /**
     * Returns false/true depending on value set by 'setGrantSupported()'
     * method.
     *
     * @return false/true depending on value set by 'setGrantSupported()' method
     */
    public boolean grantSupported() {
        ++grantSupNum;
        return isGrantSupported;
    }

    /**
     * Sets value returned by grantSupported method.
     *
     * @param val value to be set
     */
    public void setGrantSupported(boolean val) {
        isGrantSupported = val;
    }

    /**
     * Stores incoming parameters. Returns array of permissions specified
     * in constructor.
     *
     * @return array of permissions specified in constructor
     */
    public Permission[] getGrants(Class cl, Principal[] principals) {
        getGrantsParams = new Object[] { cl, principals };
        return permissions;
    }

    /**
     * Returns parameters passed to 'getGrants' method.
     *
     * @return parameters passed to 'getGrants' method
     */
    public Object[] getGetGrantsParams() {
        return getGrantsParams;
    }

    /**
     * Returns PermissionCollection containing all Permissions specified
     * in constructor.
     *
     * @return PermissionCollection containing all Permissions specified
     *         in constructor
     */
    public PermissionCollection getPermissions(CodeSource codesource) {
        if (exPermissions == null) {
            return new TestPermissionCollection(permissions);
        } else {
            ArrayList list = new ArrayList();

            for (int i = 0; i < permissions.length; ++i) {
                boolean isFound = false;

                for (int j = 0; j < exPermissions.length; ++j) {
                    if (permissions[i].equals(exPermissions[j])) {
                        isFound = true;
                        break;
                    }
                }

                if (!isFound) {
                    list.add(permissions[i]);
                }
            }
           return new TestPermissionCollection((Permission[]) list.toArray(
                   new Permission[list.size()]));
        }
    }

    /**
     * Returns string representation of the object.
     *
     * @return string representation of the object
     */
    public String toString() {
        if (permissions == null) {
            return "TestDynamicPolicyProvider[ null ]";
        }
        String str = "TestDynamicPolicyProvider{[ ";

        for (int i = 0; i < permissions.length; ++i) {
            str += permissions[i] + " ";
        }
        str += "]";

        if (exPermissions != null) {
            str += "; Not granted: [";

            for (int i = 0; i < exPermissions.length; ++i) {
                str += exPermissions[i] + " ";
            }
            str += "]";
        }
        return (str + "}");
    }
}
