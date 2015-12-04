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
package org.apache.river.test.impl.outrigger.matching;


/**
 *
 * A Random User Defined Class
 *
 * @author John W. F. McClain
 */
class RandomUserDefinedClass implements java.io.Serializable {
    public int aInt;
    private float aFloat;
    private String aString;

    RandomUserDefinedClass(int i, float f, String s) {
        aInt = i;
        aFloat = f;
        aString = s;
    }

    String aMethod() {
        return aInt + ":" + aFloat + "--" + aString;
    }

    public boolean equals(Object rhs) {
        if (!rhs.getClass().equals(getClass())) {
            return false;
        }
        RandomUserDefinedClass other = (RandomUserDefinedClass) rhs;
        return ((aInt == other.aInt) && (aFloat == other.aFloat)
                && (aString.equals(other.aString)));
    }

    public int hashCode() {
        return aInt ^ (int) aFloat ^ aString.hashCode();
    }
}
