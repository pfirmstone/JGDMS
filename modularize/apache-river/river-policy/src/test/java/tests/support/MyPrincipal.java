/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
* @author Stepan M. Mishura
* @version $Revision$
*/

package tests.support;

import java.io.Serializable;
import java.security.Principal;

public class MyPrincipal implements Principal, Serializable {

    private String name;

    public MyPrincipal(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    // documentation inherited from java.security.Principal.hashCode
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    // documentation inherited from java.security.Principal.toString
    @Override
    public String toString() {
        return "MyPrincipal[" + name + "]";
    }

    // documentation inherited from java.security.Principal.equals
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof MyPrincipal
                && name.equals(((MyPrincipal) obj).name));
    }
}