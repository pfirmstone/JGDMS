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

public class Attr04 implements Entry, Attr {
    public Integer i0_04;
    public Boolean b0_04;
    public String  s0_04;

    public Attr04() {
        super();
    }

    public Attr04(Integer i0_04,Boolean b0_04,String s0_04) {
        super();
        this.i0_04 = i0_04;
        this.b0_04 = b0_04;
        this.s0_04 = s0_04;
    }

    public void setDefaults() {
        i0_04 = new Integer(400);
        b0_04 = new Boolean(false);
        s0_04 = "default string 402";
    }

    public boolean equals(Object obj) {
        try {
            if ( this == obj ) {
                return true;
            } else if ( (obj.getClass()).equals(Attr04.class) ) {
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
            } else if ( (Attr04.class).isAssignableFrom(obj.getClass()) ) {
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
            if (    (    (((Attr04)obj).i0_04 == i0_04)
                      || (((Attr04)obj).i0_04.equals(i0_04)) )
                 && (    (((Attr04)obj).b0_04 == b0_04)
                      || (((Attr04)obj).b0_04.equals(b0_04)) )
                 && (    (((Attr04)obj).s0_04 == s0_04)
                      || (((Attr04)obj).s0_04.equals(s0_04)) )
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
