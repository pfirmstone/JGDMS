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

public class Attr11 extends Attr10 {
    public Integer i0_11;
    public Boolean b0_11;
    public String  s0_11;
    public Long    l0_11;

    public Integer i1_11;
    public Boolean b1_11;
    public String  s1_11;
    public Long    l1_11;

    public Integer i2_11;
    public Boolean b2_11;
    public String  s2_11;
    public Long    l2_11;

    public Attr11() {
        super();
    }

    public Attr11(Integer i0_11,Boolean b0_11,String s0_11,Long l0_11,
                  Integer i1_11,Boolean b1_11,String s1_11,Long l1_11,
                  Integer i2_11,Boolean b2_11,String s2_11,Long l2_11)
    {
        super(new Integer((i0_11.intValue())+4),
              new Boolean(true),
              "attribute string "+(new Integer((i0_11.intValue())+4+2)),

              new Integer((i1_11.intValue())+4),
              new Boolean(true),
             "attribute string "+(new Integer((i1_11.intValue())+4+2)) );

        this.i0_11 = i0_11;
        this.b0_11 = b0_11;
        this.s0_11 = s0_11;
        this.l0_11 = l0_11;

        this.i1_11 = i1_11;
        this.b1_11 = b1_11;
        this.s1_11 = s1_11;
        this.l1_11 = l1_11;

        this.i2_11 = i2_11;
        this.b2_11 = b2_11;
        this.s2_11 = s2_11;
        this.l2_11 = l2_11;
    }

    public void setDefaults() {
        super.setDefaults();
        i0_11 = new Integer(1100);
        b0_11 = new Boolean(false);
        s0_11 = "default string 1102";
        l0_11 = new Long(1103);

        i1_11 = new Integer(1110);
        b1_11 = new Boolean(false);
        s1_11 = "default string 1112";
        l1_11 = new Long(1113);

        i2_11 = new Integer(1120);
        b2_11 = new Boolean(false);
        s2_11 = "default string 1122";
        l2_11 = new Long(1123);
    }

    public boolean equals(Object obj) {
        try {
            if ( this == obj ) {
                return true;
            } else if ( (obj.getClass()).equals(Attr11.class) ) {
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
            } else if ( (Attr11.class).isAssignableFrom(obj.getClass()) ) {
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
            if (    (    (((Attr11)obj).i0_11 == i0_11)
                      || (((Attr11)obj).i0_11.equals(i0_11)) )
                 && (    (((Attr11)obj).b0_11 == b0_11)
                      || (((Attr11)obj).b0_11.equals(b0_11)) )
                 && (    (((Attr11)obj).s0_11 == s0_11)
                      || (((Attr11)obj).s0_11.equals(s0_11)) )
                 && (    (((Attr11)obj).l0_11 == l0_11)
                      || (((Attr11)obj).l0_11.equals(l0_11)) )

                 && (    (((Attr11)obj).i1_11 == i1_11)
                      || (((Attr11)obj).i1_11.equals(i1_11)) )
                 && (    (((Attr11)obj).b1_11 == b1_11)
                      || (((Attr11)obj).b1_11.equals(b1_11)) )
                 && (    (((Attr11)obj).s1_11 == s1_11)
                      || (((Attr11)obj).s1_11.equals(s1_11)) )
                 && (    (((Attr11)obj).l1_11 == l1_11)
                      || (((Attr11)obj).l1_11.equals(l1_11)) )

                 && (    (((Attr11)obj).i2_11 == i2_11)
                      || (((Attr11)obj).i2_11.equals(i2_11)) )
                 && (    (((Attr11)obj).b2_11 == b2_11)
                      || (((Attr11)obj).b2_11.equals(b2_11)) )
                 && (    (((Attr11)obj).s2_11 == s2_11)
                      || (((Attr11)obj).s2_11.equals(s2_11)) )
                 && (    (((Attr11)obj).l2_11 == l2_11)
                      || (((Attr11)obj).l2_11.equals(l2_11)) )

                 && (    (((Attr11)obj).i0_10 == i0_10)
                      || (((Attr11)obj).i0_10.equals(i0_10)) )
                 && (    (((Attr11)obj).b0_10 == b0_10)
                      || (((Attr11)obj).b0_10.equals(b0_10)) )
                 && (    (((Attr11)obj).s0_10 == s0_10)
                      || (((Attr11)obj).s0_10.equals(s0_10)) )

                 && (    (((Attr11)obj).i1_10 == i1_10)
                      || (((Attr11)obj).i1_10.equals(i1_10)) )
                 && (    (((Attr11)obj).b1_10 == b1_10)
                      || (((Attr11)obj).b1_10.equals(b1_10)) )
                 && (    (((Attr11)obj).s1_10 == s1_10)
                      || (((Attr11)obj).s1_10.equals(s1_10)) )

                 && (    (((Attr11)obj).i0_09 == i0_09)
                      || (((Attr11)obj).i0_09.equals(i0_09)) )
                 && (    (((Attr11)obj).b0_09 == b0_09)
                      || (((Attr11)obj).b0_09.equals(b0_09)) )
                 && (    (((Attr11)obj).s0_09 == s0_09)
                      || (((Attr11)obj).s0_09.equals(s0_09)) )
                 && (    (((Attr11)obj).l0_09 == l0_09)
                      || (((Attr11)obj).l0_09.equals(l0_09)) )
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
