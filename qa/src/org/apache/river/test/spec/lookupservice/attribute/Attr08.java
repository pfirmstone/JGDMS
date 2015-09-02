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

public class Attr08 implements Entry, Attr {
    public Integer i0_08;
    public Boolean b0_08;
    public String  s0_08;
    public Long    l0_08;

    public Integer i1_08;
    public Boolean b1_08;
    public String  s1_08;
    public Long    l1_08;

    public Integer i2_08;
    public Boolean b2_08;
    public String  s2_08;
    public Long    l2_08;

    public Attr08() {
        super();
    }

    public Attr08(Integer i0_08,Boolean b0_08,String s0_08,Long l0_08,
                  Integer i1_08,Boolean b1_08,String s1_08,Long l1_08,
                  Integer i2_08,Boolean b2_08,String s2_08,Long l2_08) {
        super();

        this.i0_08 = i0_08;
        this.b0_08 = b0_08;
        this.s0_08 = s0_08;
        this.l0_08 = l0_08;

        this.i1_08 = i1_08;
        this.b1_08 = b1_08;
        this.s1_08 = s1_08;
        this.l1_08 = l1_08;

        this.i2_08 = i2_08;
        this.b2_08 = b2_08;
        this.s2_08 = s2_08;
        this.l2_08 = l2_08;
    }

    public void setDefaults() {
        i0_08 = new Integer(800);
        b0_08 = new Boolean(false);
        s0_08 = "default string 802";
        l0_08 = new Long(803);

        i1_08 = new Integer(810);
        b1_08 = new Boolean(false);
        s1_08 = "default string 812";
        l1_08 = new Long(813);

        i2_08 = new Integer(820);
        b2_08 = new Boolean(false);
        s2_08 = "default string 822";
        l2_08 = new Long(823);
    }

    public boolean equals(Object obj) {
        try {
            if ( this == obj ) {
                return true;
            } else if ( (obj.getClass()).equals(Attr08.class) ) {
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
            } else if ( (Attr08.class).isAssignableFrom(obj.getClass()) ) {
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
            if (    (    (((Attr08)obj).i0_08 == i0_08)
                      || (((Attr08)obj).i0_08.equals(i0_08)) )
                 && (    (((Attr08)obj).b0_08 == b0_08)
                      || (((Attr08)obj).b0_08.equals(b0_08)) )
                 && (    (((Attr08)obj).s0_08 == s0_08)
                      || (((Attr08)obj).s0_08.equals(s0_08)) )
                 && (    (((Attr08)obj).l0_08 == l0_08)
                      || (((Attr08)obj).l0_08.equals(l0_08)) )

                 && (    (((Attr08)obj).i1_08 == i1_08)
                      || (((Attr08)obj).i1_08.equals(i1_08)) )
                 && (    (((Attr08)obj).b1_08 == b1_08)
                      || (((Attr08)obj).b1_08.equals(b1_08)) )
                 && (    (((Attr08)obj).s1_08 == s1_08)
                      || (((Attr08)obj).s1_08.equals(s1_08)) )
                 && (    (((Attr08)obj).l1_08 == l1_08)
                      || (((Attr08)obj).l1_08.equals(l1_08)) )

                 && (    (((Attr08)obj).i2_08 == i2_08)
                      || (((Attr08)obj).i2_08.equals(i2_08)) )
                 && (    (((Attr08)obj).b2_08 == b2_08)
                      || (((Attr08)obj).b2_08.equals(b2_08)) )
                 && (    (((Attr08)obj).s2_08 == s2_08)
                      || (((Attr08)obj).s2_08.equals(s2_08)) )
                 && (    (((Attr08)obj).l2_08 == l2_08)
                      || (((Attr08)obj).l2_08.equals(l2_08)) )
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
