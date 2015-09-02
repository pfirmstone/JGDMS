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

public class Attr05 extends Attr04 {
    public Integer i0_05;
    public Boolean b0_05;
    public String  s0_05;
    public Long    l0_05;

    public Attr05() {
        super();
    }

    public Attr05(Integer i0_05,Boolean b0_05,String s0_05,Long l0_05)
    {
        super
          (new Integer((i0_05.intValue())+4),
           new Boolean(true),
          "attribute string "+(new Integer((i0_05.intValue())+4+2)) );
        this.i0_05 = i0_05;
        this.b0_05 = b0_05;
        this.s0_05 = s0_05;
        this.l0_05 = l0_05;
    }

    public void setDefaults() {
        super.setDefaults();
        i0_05 = new Integer(500);
        b0_05 = new Boolean(false);
        s0_05 = "default string 502";
        l0_05 = new Long(503);
    }

    public boolean equals(Object obj) {
        try {
            if ( this == obj ) {
                return true;
            } else if ( (obj.getClass()).equals(Attr05.class) ) {
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
            } else if ( (Attr05.class).isAssignableFrom(obj.getClass()) ) {
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
            if (    (    (((Attr05)obj).i0_05 == i0_05)
                      || (((Attr05)obj).i0_05.equals(i0_05)) )
                 && (    (((Attr05)obj).b0_05 == b0_05)
                      || (((Attr05)obj).b0_05.equals(b0_05)) )
                 && (    (((Attr05)obj).s0_05 == s0_05)
                      || (((Attr05)obj).s0_05.equals(s0_05)) )
                 && (    (((Attr05)obj).l0_05 == l0_05)
                      || (((Attr05)obj).l0_05.equals(l0_05)) )

                 && (    (((Attr05)obj).i0_04 == i0_04)
                      || (((Attr05)obj).i0_04.equals(i0_04)) )
                 && (    (((Attr05)obj).b0_04 == b0_04)
                      || (((Attr05)obj).b0_04.equals(b0_04)) )
                 && (    (((Attr05)obj).s0_04 == s0_04)
                      || (((Attr05)obj).s0_04.equals(s0_04)) )
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
