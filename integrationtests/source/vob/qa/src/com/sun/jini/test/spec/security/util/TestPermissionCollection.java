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
import java.security.PermissionCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.NoSuchElementException;

// net.jini
import net.jini.security.GrantPermission;


/**
 * PermissionCollection class whose 'implies' method returns false
 * if given permission is an instance of GrantPermission and is in set of
 * permissions specified in constructor.
 */
public class TestPermissionCollection extends PermissionCollection {

    /** List of permissions. */
    protected ArrayList permissions;

    /**
     * Constructor storing set of permissions.
     */
    public TestPermissionCollection(Permission[] perms) {
        permissions = new ArrayList();

        if (perms != null) {
            permissions.addAll(Arrays.asList(perms));
        }
    }

    /**
     * Returns false if given permission is an instance of GrantPermission and
     * is in the list and true otherwise.
     *
     * @param permission tested Permission
     * @return false if given permission is an instance of GrantPermission and
     *         is in the list and true otherwise
     */
    public boolean implies(Permission permission) {
        if (permission instanceof GrantPermission) {
            return permissions.contains(permission);
        } else {
            return true;
        }
    }

    /**
     * Added given permission to the list.
     *
     * @param permission Permission to be added
     */
    public void add(Permission permission) {
        permissions.add(permission);
    }

    /**
     * Returns enumeration of current list of permissions.
     *
     * @return enumeration of current list of permissions
     */
    public Enumeration elements() {
        return new TestEnumeration(permissions.toArray());
    }


    /**
     * Class implementing Enumeration interface.
     */
    class TestEnumeration implements Enumeration {

        /** Array of objects. */
        private Object[] objs;

        /** Index to next available record of array. */
        private int curIdx;

        /**
         * Constructs enumeration from given array.
         *
         * @param objs array of objects
         * @throws NullPointerException if objs is null
         */
        public TestEnumeration(Object[] objs) {
            if (objs == null) {
                throw new NullPointerException(
                        "Object's array can not be null.");
            }
            this.objs = objs;
            curIdx = 0;
        }

        /**
         * Returns true if the enumeration has more elements, and false
         * otherwise.
         *
         * @return true if the enumeration has more elements, and false
         *         otherwise
         */
        public boolean hasMoreElements() {
            return (curIdx < objs.length);
        }

        /**
         * Returns the next element in the enumeration.
         *
         * @return the next element in the enumeration
         * @throws NoSuchElementException if the enumeration has
         *         no more elements
         */
        public Object nextElement() {
            if (curIdx >= objs.length) {
                throw new NoSuchElementException();
            }
            return objs[curIdx++];
        }
    }
}
