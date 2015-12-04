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

public class Attr10 extends Attr09 {
    public Integer i0_10;
    public Boolean b0_10;
    public String  s0_10;

    public Integer i1_10;
    public Boolean b1_10;
    public String  s1_10;

    public Attr10() {
        super();
    }

    public Attr10(Integer i0_10,Boolean b0_10,String s0_10,
                  Integer i1_10,Boolean b1_10,String s1_10)
    {
        super(new Integer((i0_10.intValue())+4),
              new Boolean(true),
              "attribute string "+(new Integer((i0_10.intValue())+4+2)),
              new Long((i0_10.intValue())+4+3) );

        this.i0_10 = i0_10;
        this.b0_10 = b0_10;
        this.s0_10 = s0_10;

        this.i1_10 = i1_10;
        this.b1_10 = b1_10;
        this.s1_10 = s1_10;
    }

    public void setDefaults() {
        super.setDefaults();
        i0_10 = new Integer(1000);
        b0_10 = new Boolean(false);
        s0_10 = "default string 1002";

        i1_10 = new Integer(1010);
        b1_10 = new Boolean(false);
        s1_10 = "default string 1002";
    }

    public boolean equals(Object obj) {
        try {
            if ( this == obj ) {
                return true;
            } else if ( (obj.getClass()).equals(Attr10.class) ) {
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
            } else if ( (Attr10.class).isAssignableFrom(obj.getClass()) ) {
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
            if (    (    (((Attr10)obj).i0_10 == i0_10)
                      || (((Attr10)obj).i0_10.equals(i0_10)) )
                 && (    (((Attr10)obj).b0_10 == b0_10)
                      || (((Attr10)obj).b0_10.equals(b0_10)) )
                 && (    (((Attr10)obj).s0_10 == s0_10)
                      || (((Attr10)obj).s0_10.equals(s0_10)) )

                 && (    (((Attr10)obj).i1_10 == i1_10)
                      || (((Attr10)obj).i1_10.equals(i1_10)) )
                 && (    (((Attr10)obj).b1_10 == b1_10)
                      || (((Attr10)obj).b1_10.equals(b1_10)) )
                 && (    (((Attr10)obj).s1_10 == s1_10)
                      || (((Attr10)obj).s1_10.equals(s1_10)) )

                 && (    (((Attr10)obj).i0_09 == i0_09)
                      || (((Attr10)obj).i0_09.equals(i0_09)) )
                 && (    (((Attr10)obj).b0_09 == b0_09)
                      || (((Attr10)obj).b0_09.equals(b0_09)) )
                 && (    (((Attr10)obj).s0_09 == s0_09)
                      || (((Attr10)obj).s0_09.equals(s0_09)) )
                 && (    (((Attr10)obj).l0_09 == l0_09)
                      || (((Attr10)obj).l0_09.equals(l0_09)) )
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
