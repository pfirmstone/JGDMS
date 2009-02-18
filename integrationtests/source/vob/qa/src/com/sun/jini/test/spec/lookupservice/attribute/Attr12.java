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

public class Attr12 extends Attr11 {
    public Integer i0_12;
    public Boolean b0_12;
    public String  s0_12;
    public Long    l0_12;

    public Integer i1_12;
    public Boolean b1_12;
    public String  s1_12;
    public Long    l1_12;

    public Integer i2_12;
    public Boolean b2_12;
    public String  s2_12;
    public Long    l2_12;

    public Integer i3_12;
    public Boolean b3_12;
    public String  s3_12;
    public Long    l3_12;

    public Attr12() {
        super();
    }

    public Attr12(Integer i0_12,Boolean b0_12,String s0_12,Long l0_12,
                  Integer i1_12,Boolean b1_12,String s1_12,Long l1_12,
                  Integer i2_12,Boolean b2_12,String s2_12,Long l2_12,
                  Integer i3_12,Boolean b3_12,String s3_12,Long l3_12)
    {
        super(new Integer((i0_12.intValue())+4),
              new Boolean(true),
              "attribute string "+(new Integer((i0_12.intValue())+4+2)),
              new Long((i0_12.intValue())+4+3),

              new Integer((i1_12.intValue())+4),
              new Boolean(true),
             "attribute string "+(new Integer((i1_12.intValue())+4+2)),
              new Long((i1_12.intValue())+4+3),

              new Integer((i2_12.intValue())+4),
              new Boolean(true),
             "attribute string "+(new Integer((i2_12.intValue())+4+2)),
              new Long((i2_12.intValue())+4+3)  );

        this.i0_12 = i0_12;
        this.b0_12 = b0_12;
        this.s0_12 = s0_12;
        this.l0_12 = l0_12;

        this.i1_12 = i1_12;
        this.b1_12 = b1_12;
        this.s1_12 = s1_12;
        this.l1_12 = l1_12;

        this.i2_12 = i2_12;
        this.b2_12 = b2_12;
        this.s2_12 = s2_12;
        this.l2_12 = l2_12;

        this.i3_12 = i3_12;
        this.b3_12 = b3_12;
        this.s3_12 = s3_12;
        this.l3_12 = l3_12;
    }

    public void setDefaults() {
        super.setDefaults();
        i0_12 = new Integer(1200);
        b0_12 = new Boolean(false);
        s0_12 = "default string 1202";
        l0_12 = new Long(1203);

        i1_12 = new Integer(1210);
        b1_12 = new Boolean(false);
        s1_12 = "default string 1212";
        l1_12 = new Long(1213);

        i2_12 = new Integer(1220);
        b2_12 = new Boolean(false);
        s2_12 = "default string 1222";
        l2_12 = new Long(1223);

        i3_12 = new Integer(1230);
        b3_12 = new Boolean(false);
        s3_12 = "default string 1232";
        l3_12 = new Long(1233);
    }

    public boolean equals(Object obj) {
        try {
            if ( this == obj ) {
                return true;
            } else if ( (obj.getClass()).equals(Attr12.class) ) {
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
            } else if ( (Attr12.class).isAssignableFrom(obj.getClass()) ) {
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
            if (    (    (((Attr12)obj).i0_12 == i0_12)
                      || (((Attr12)obj).i0_12.equals(i0_12)) )
                 && (    (((Attr12)obj).b0_12 == b0_12)
                      || (((Attr12)obj).b0_12.equals(b0_12)) )
                 && (    (((Attr12)obj).s0_12 == s0_12)
                      || (((Attr12)obj).s0_12.equals(s0_12)) )
                 && (    (((Attr12)obj).l0_12 == l0_12)
                      || (((Attr12)obj).l0_12.equals(l0_12)) )

                 && (    (((Attr12)obj).i1_12 == i1_12)
                      || (((Attr12)obj).i1_12.equals(i1_12)) )
                 && (    (((Attr12)obj).b1_12 == b1_12)
                      || (((Attr12)obj).b1_12.equals(b1_12)) )
                 && (    (((Attr12)obj).s1_12 == s1_12)
                      || (((Attr12)obj).s1_12.equals(s1_12)) )
                 && (    (((Attr12)obj).l1_12 == l1_12)
                      || (((Attr12)obj).l1_12.equals(l1_12)) )

                 && (    (((Attr12)obj).i2_12 == i2_12)
                      || (((Attr12)obj).i2_12.equals(i2_12)) )
                 && (    (((Attr12)obj).b2_12 == b2_12)
                      || (((Attr12)obj).b2_12.equals(b2_12)) )
                 && (    (((Attr12)obj).s2_12 == s2_12)
                      || (((Attr12)obj).s2_12.equals(s2_12)) )
                 && (    (((Attr12)obj).l2_12 == l2_12)
                      || (((Attr12)obj).l2_12.equals(l2_12)) )

                 && (    (((Attr12)obj).i3_12 == i3_12)
                      || (((Attr12)obj).i3_12.equals(i3_12)) )
                 && (    (((Attr12)obj).b3_12 == b3_12)
                      || (((Attr12)obj).b3_12.equals(b3_12)) )
                 && (    (((Attr12)obj).s3_12 == s3_12)
                      || (((Attr12)obj).s3_12.equals(s3_12)) )
                 && (    (((Attr12)obj).l3_12 == l3_12)
                      || (((Attr12)obj).l3_12.equals(l3_12)) )

                 && (    (((Attr12)obj).i0_11 == i0_11)
                      || (((Attr12)obj).i0_11.equals(i0_11)) )
                 && (    (((Attr12)obj).b0_11 == b0_11)
                      || (((Attr12)obj).b0_11.equals(b0_11)) )
                 && (    (((Attr12)obj).s0_11 == s0_11)
                      || (((Attr12)obj).s0_11.equals(s0_11)) )
                 && (    (((Attr12)obj).l0_11 == l0_11)
                      || (((Attr12)obj).l0_11.equals(l0_11)) )

                 && (    (((Attr12)obj).i1_11 == i1_11)
                      || (((Attr12)obj).i1_11.equals(i1_11)) )
                 && (    (((Attr12)obj).b1_11 == b1_11)
                      || (((Attr12)obj).b1_11.equals(b1_11)) )
                 && (    (((Attr12)obj).s1_11 == s1_11)
                      || (((Attr12)obj).s1_11.equals(s1_11)) )
                 && (    (((Attr12)obj).l1_11 == l1_11)
                      || (((Attr12)obj).l1_11.equals(l1_11)) )

                 && (    (((Attr12)obj).i2_11 == i2_11)
                      || (((Attr12)obj).i2_11.equals(i2_11)) )
                 && (    (((Attr12)obj).b2_11 == b2_11)
                      || (((Attr12)obj).b2_11.equals(b2_11)) )
                 && (    (((Attr12)obj).s2_11 == s2_11)
                      || (((Attr12)obj).s2_11.equals(s2_11)) )
                 && (    (((Attr12)obj).l2_11 == l2_11)
                      || (((Attr12)obj).l2_11.equals(l2_11)) )

                 && (    (((Attr12)obj).i0_10 == i0_10)
                      || (((Attr12)obj).i0_10.equals(i0_10)) )
                 && (    (((Attr12)obj).b0_10 == b0_10)
                      || (((Attr12)obj).b0_10.equals(b0_10)) )
                 && (    (((Attr12)obj).s0_10 == s0_10)
                      || (((Attr12)obj).s0_10.equals(s0_10)) )

                 && (    (((Attr12)obj).i1_10 == i1_10)
                      || (((Attr12)obj).i1_10.equals(i1_10)) )
                 && (    (((Attr12)obj).b1_10 == b1_10)
                      || (((Attr12)obj).b1_10.equals(b1_10)) )
                 && (    (((Attr12)obj).s1_10 == s1_10)
                      || (((Attr12)obj).s1_10.equals(s1_10)) )

                 && (    (((Attr12)obj).i0_09 == i0_09)
                      || (((Attr12)obj).i0_09.equals(i0_09)) )
                 && (    (((Attr12)obj).b0_09 == b0_09)
                      || (((Attr12)obj).b0_09.equals(b0_09)) )
                 && (    (((Attr12)obj).s0_09 == s0_09)
                      || (((Attr12)obj).s0_09.equals(s0_09)) )
                 && (    (((Attr12)obj).l0_09 == l0_09)
                      || (((Attr12)obj).l0_09.equals(l0_09)) )
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
