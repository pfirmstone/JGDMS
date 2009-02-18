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
package com.sun.jini.test.spec.policyprovider.util;


/**
 *  Helper class to verify proper behavior of principal-based grants.
 *
 */
public class QAPrincipal implements java.security.Principal {
    // documentation inherited from java.security.Principal
    private String name;

    // documentation inherited from java.security.Principal
    public QAPrincipal(String name) {
        this.name = name;
    }

    // documentation inherited from java.security.Principal.getName
    public String getName() {
        return name;
    }

    // documentation inherited from java.security.Principal.hashCode
    public int hashCode() {
        return name.hashCode();
    }

    // documentation inherited from java.security.Principal.toString
    public String toString() {
        return "QAPrincipal[" + name + "]";
    }

    // documentation inherited from java.security.Principal.equals
    public boolean equals(Object obj) {
        return (obj instanceof QAPrincipal
                && name.equals(((QAPrincipal) obj).name));
    }
}
