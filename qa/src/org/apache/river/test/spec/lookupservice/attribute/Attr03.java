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

public class Attr03 extends Attr02 {
    public Integer i0_03;
    public Boolean b0_03;
    public String  s0_03;

    public Attr03() { 
        super();
    }

    public Attr03(Integer i0_03,Boolean b0_03,String s0_03) {
        super();
        this.i0_03 = i0_03;
        this.b0_03 = b0_03;
        this.s0_03 = s0_03;
    }

    public void setDefaults() { 
        i0_03 = new Integer(300);
        b0_03 = new Boolean(false);
        s0_03 = "default string 302";
    }

    public boolean equals(Object obj) {
        try {
            if ( this == obj ) {
                return true;
            } else if ( (obj.getClass()).equals(Attr03.class) ) {
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
            } else if ( (Attr03.class).isAssignableFrom(obj.getClass()) ) {
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
            if (    (    (((Attr03)obj).i0_03 == i0_03)
                      || (((Attr03)obj).i0_03.equals(i0_03)) )
                 && (    (((Attr03)obj).b0_03 == b0_03)
                      || (((Attr03)obj).b0_03.equals(b0_03)) )
                 && (    (((Attr03)obj).s0_03 == s0_03)
                      || (((Attr03)obj).s0_03.equals(s0_03)) )
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
