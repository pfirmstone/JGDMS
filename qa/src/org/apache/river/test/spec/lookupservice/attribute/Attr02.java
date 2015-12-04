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
package org.apache.river.test.spec.lookupservice.attribute;
import net.jini.core.entry.Entry;

public class Attr02 implements Entry, Attr {
    public Attr02() { }

    public boolean equals(Object obj) {
        try {
            if ( this == obj ) {
                return true;
            } else if ( (obj.getClass()).equals(Attr02.class) ) {
                return true;
            } else {
                return false;
            }
        } catch (NullPointerException e) {
            return false;
	}
    }

    public boolean matches(Object obj) {
        try {
            if ( this.equals(obj) ) {
                return true;
            } else if ( (Attr02.class).isAssignableFrom(obj.getClass()) ) {
                return true;
	    } else {
                return false;
            }
        } catch (NullPointerException e) {
            return false;
	}
    }
}
