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
package net.jini.lookup.ui.attribute;

/**
 * UI attribute that indicates a generated
 * UI implements the the <CODE>javax.accessibility.Accessible</CODE> interface
 * and that the designer of the UI did the necessary work to make sure the UI
 * would work well with assistive technologies that are aware of the Java Accessibility API.
 *
 * <P>
 * Only <CODE>UIDescriptor</CODE>s whose marshalled UI factory produces
 * a UI that supports the Accessibility API should include this attribute.
 * The presence of this attribute in an attribute set means the produced
 * UI will work well with assistive technologies that are aware of the Java
 * Accessibility API.
 *
 * @author Bill Venners
 */
public class AccessibleUI implements java.io.Serializable {

    private static final long serialVersionUID = 4565111059638534377L;

    /**
     * Constructs a new <code>AccessibleUI</code> attribute.
     */
    public AccessibleUI() {
    }

    /**
     * Compares the specified object (passed in <CODE>o</CODE>) with this
     * <CODE>AccessibleUI</CODE> object for equality. Returns <CODE>true</CODE> if
     * <CODE>o</CODE> is non-<code>null</code> and the fully qualified class name of the specified object (passed
     * in <CODE>o</CODE>) is the same as the fully qualified class name of this object.
     *
     * @param o the object to compare against
     * @return <code>true</code> if the objects are the same,
     *     <code>false</code> otherwise.
     */
    public boolean equals(Object o) {

        if (o == null) {
            return false;
        }

        String thisName = getClass().getName();
        String oName = o.getClass().getName();
        if (!oName.equals(thisName)) {
            return false;
        }

        return true;
    }

    /**
     * Returns the hash code value for this <CODE>AccessibleUI</CODE>. As
     * all <CODE>AccessibleUI</CODE> objects are conceptually equivalent, this
     * method returns the hash code value for this object's fully qualified
     * class name <code>String</code>.
     *
     * @return the hashcode for this object
     */
    public int hashCode() {
        return getClass().getName().hashCode();
    }
}


