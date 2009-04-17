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

public class Attr09 implements Entry, Attr {
    public Integer i0_09;
    public Boolean b0_09;
    public String  s0_09;
    public Long    l0_09;

    public Attr09() {
        super();
    }

    public Attr09(Integer i0_09,Boolean b0_09,String s0_09,Long l0_09) {
        super();

        this.i0_09 = i0_09;
        this.b0_09 = b0_09;
        this.s0_09 = s0_09;
        this.l0_09 = l0_09;
    }

    public void setDefaults() {
        i0_09 = new Integer(900);
        b0_09 = new Boolean(false);
        s0_09 = "default string 902";
        l0_09 = new Long(903);
    }

    public boolean equals(Object obj) {
        try {
            if ( this == obj ) {
                return true;
            } else if ( (obj.getClass()).equals(Attr09.class) ) {
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
            } else if ( (Attr09.class).isAssignableFrom(obj.getClass()) ) {
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
            if (    (    (((Attr09)obj).i0_09 == i0_09)
                      || (((Attr09)obj).i0_09.equals(i0_09)) )
                 && (    (((Attr09)obj).b0_09 == b0_09)
                      || (((Attr09)obj).b0_09.equals(b0_09)) )
                 && (    (((Attr09)obj).s0_09 == s0_09)
                      || (((Attr09)obj).s0_09.equals(s0_09)) )
                 && (    (((Attr09)obj).l0_09 == l0_09)
                      || (((Attr09)obj).l0_09.equals(l0_09)) )
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
