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
import java.security.Principal;


/**
 * Test class implementing Principal interface.
 */
public class FakePrincipal implements Principal {

    /** Principal's name */
    protected String name = null;

    /**
     * Default constructor.
     */
    public FakePrincipal() {
         name = "";
    }

    /**
     * Creates principal with name specified.
     *
     * @param name Principal's name
     */
    public FakePrincipal(String name) {
        this.name = name;
    }

    /**
     * Compares this principal with another one. Returns true if
     * principal to be compared is an instance of FakePrincipal and has the same
     * name.
     *
     * @return true if principal to be compared is an instance of FakePrincipal
     *         and has the same name
     */
    public boolean equals(Object another) {
        return (another instanceof FakePrincipal)
                && ((FakePrincipal) another).getName().equals(name);
    }

    /**
     * Return the name of this principal.
     *
     * @return the name of this principal
     */
    public String getName() {
        return name;
    }

    /**
     * Method from Principal interface. Does nothing.
     *
     * @return 0
     */
    public int hashCode() {
        return 0;
    }

    /**
     * Method from Principal interface. Does nothing.
     *
     * @return name of this class
     */
    public String toString() {
        return getName();
    }
}
