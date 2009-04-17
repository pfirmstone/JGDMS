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
package com.sun.jini.test.spec.lookupservice.attribute;
import net.jini.core.entry.Entry;

public class Attr06 implements Entry, Attr {
    public Integer i0_06;
    public Boolean b0_06;
    public String  s0_06;

    public Attr06() {
        super();
    }

    public Attr06(Integer i0_06,Boolean b0_06,String s0_06) {
        super();
        this.i0_06 = i0_06;
        this.b0_06 = b0_06;
        this.s0_06 = s0_06;
    }

    public void setDefaults() {
        i0_06 = new Integer(600);
        b0_06 = new Boolean(false);
        s0_06 = "default string 602";
    }

    public boolean equals(Object obj) {
        try {
            if ( this == obj ) {
                return true;
            } else if ( (obj.getClass()).equals(Attr06.class) ) {
                return fieldsMatch(obj);
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
            } else if ( (Attr06.class).isAssignableFrom(obj.getClass()) ) {
                return fieldsMatch(obj);
	    } else {
                return false;
            }
        } catch (NullPointerException e) {
            return false;
	}
    }

    private boolean fieldsMatch(Object obj) {
        try {
            if (    (    (((Attr06)obj).i0_06 == i0_06)
                      || (((Attr06)obj).i0_06.equals(i0_06)) )
                 && (    (((Attr06)obj).b0_06 == b0_06)
                      || (((Attr06)obj).b0_06.equals(b0_06)) )
                 && (    (((Attr06)obj).s0_06 == s0_06)
                      || (((Attr06)obj).s0_06.equals(s0_06)) )
               ) {
                return true;
            } else {
                return false;
	    }
        } catch (NullPointerException e) {
            return false;
	}
    }
}
