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

public class Attr07 implements Entry, Attr {
    public Integer i0_07;
    public Boolean b0_07;
    public String  s0_07;
    public Long    l0_07;

    public Integer i1_07;
    public Boolean b1_07;
    public String  s1_07;
    public Long    l1_07;

    public Attr07() {
        super();
    }

    public Attr07(Integer i0_07,Boolean b0_07,String s0_07,Long l0_07,
                  Integer i1_07,Boolean b1_07,String s1_07,Long l1_07) {
        super();

        this.i0_07 = i0_07;
        this.b0_07 = b0_07;
        this.s0_07 = s0_07;
        this.l0_07 = l0_07;

        this.i1_07 = i1_07;
        this.b1_07 = b1_07;
        this.s1_07 = s1_07;
        this.l1_07 = l1_07;
    }

    public void setDefaults() {
        i0_07 = new Integer(700);
        b0_07 = new Boolean(false);
        s0_07 = "default string 702";
        l0_07 = new Long(703);

        i1_07 = new Integer(710);
        b1_07 = new Boolean(false);
        s1_07 = "default string 712";
        l1_07 = new Long(713);
    }

    public boolean equals(Object obj) {
        try {
            if ( this == obj ) {
                return true;
            } else if ( (obj.getClass()).equals(Attr07.class) ) {
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
            } else if ( (Attr07.class).isAssignableFrom(obj.getClass()) ) {
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
            if (    (    (((Attr07)obj).i0_07 == i0_07)
                      || (((Attr07)obj).i0_07.equals(i0_07)) )
                 && (    (((Attr07)obj).b0_07 == b0_07)
                      || (((Attr07)obj).b0_07.equals(b0_07)) )
                 && (    (((Attr07)obj).s0_07 == s0_07)
                      || (((Attr07)obj).s0_07.equals(s0_07)) )
                 && (    (((Attr07)obj).l0_07 == l0_07)
                      || (((Attr07)obj).l0_07.equals(l0_07)) )

                 && (    (((Attr07)obj).i1_07 == i1_07)
                      || (((Attr07)obj).i1_07.equals(i1_07)) )
                 && (    (((Attr07)obj).b1_07 == b1_07)
                      || (((Attr07)obj).b1_07.equals(b1_07)) )
                 && (    (((Attr07)obj).s1_07 == s1_07)
                      || (((Attr07)obj).s1_07.equals(s1_07)) )
                 && (    (((Attr07)obj).l1_07 == l1_07)
                      || (((Attr07)obj).l1_07.equals(l1_07)) )
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
