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

// imports
import java.io.IOException;
import net.jini.core.entry.Entry;
import net.jini.io.MarshalledInstance;
import java.lang.reflect.*;


/**
 * Class takes a JavaSpace template in its constructor and provides a
 * method that indicates whether or not a given Entry is a match or not
 */
public class Template {

    // JavaSpace entry under test
    final private Entry source;

    // Class of source
    final private Class sourceClass;

    /*
     * The MarshalledInstances that reprsent the values of the public
     * non-static fields of source
     */
    final private MarshalledInstance[] matchValues;

    // The public non-static fields of source;
    final private Field[] matchFields;

    /**
     * any field with one or more of these modifiers is not a field used
     * for matching
     */
    final private static int ignoreMods = (Modifier.TRANSIENT | Modifier.STATIC
            | Modifier.FINAL);

    /**
     * Returns true if this is a field used for matching by JavaSpaces
     */
    public static boolean isMatchField(Field f) {
        final int fM = f.getModifiers();

        // Is the field transient, static or final?
        if ((fM & ignoreMods) != 0) {
            return false;
        }

        // Is it a primitive?
        if (f.getType().isPrimitive()) {
            return false;
        }
        return true;
    }

    /**
     * Generate a <code>Template</code> object based on the passed
     * entry.
     */
    public Template(Entry s) throws IllegalAccessException, IOException {
        source = s;

        if (source == null) {

            /*
             * Matches everything, assign the rest of the fields to
             * make the compiler happy
             */
            matchFields = null;
            matchValues = null;
            sourceClass = null;
            return;
        }
        sourceClass = source.getClass();
        final Field[] allFields = sourceClass.getFields();
        int matchCount = 0;

        for (int i = 0; i < allFields.length; i++) {
            if (isMatchField(allFields[i])) {
                matchCount++;
            } else {
                allFields[i] = null;
            }
        }
        matchFields = new Field[matchCount];
        matchValues = new MarshalledInstance[matchCount];
        matchCount = 0;

        for (int i = 0; i < allFields.length; i++) {
            if (allFields[i] != null) {
                matchFields[matchCount] = allFields[i];
                final Object fieldValue = matchFields[matchCount].get(source);

                if (fieldValue != null) {
                    matchValues[matchCount] = new MarshalledInstance(fieldValue);
                } else {
                    matchValues[matchCount] = null;
                }
                matchCount++;
            }
        }
    }

    /**
     * Return <code>true</code> if the <code>Entry</code> that this
     * object was constructed with matches the passed
     * <code>Entry</code>
     */
    public boolean doesMatch(Entry target)
            throws IllegalAccessException, IOException {
        if (target == null) {

            // Can't write a null
            return false;
        }

        if (source == null) {

            // null template matches everything
            return true;
        }

        // Can we assign the target to the class of the source?
        final Class targetClass = target.getClass();

        if (!(sourceClass.isAssignableFrom(targetClass))) {
            return false;
        }

        // Ok need to check the fields
        final Field[] targetFields = targetClass.getFields();

        for (int i = 0; i < targetFields.length; i++) {
            final Field targetField = targetFields[i];

            if (isMatchField(targetField)) {
                for (int j = 0; j < matchFields.length; j++) {
                    if (matchFields[j].equals(targetField)) {
                        if (matchValues[j] != null) {
                            final Object fieldValue = targetField.get(target);
                            final MarshalledInstance marshaled = new
                                    MarshalledInstance(fieldValue);

                            if (!marshaled.equals(matchValues[j])) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Compares the match fields of two Templates returning true if
     * they are are all equal. While only return true if the
     * underlying source entries are of the same class.
     */
    public boolean matchFieldAreEqual(Template target) {
        if (!sourceClass.equals(target.sourceClass)) {
            return false;
        }

        for (int i = 0; i < matchValues.length; i++) {

            // This test covers them both being null
            if (matchValues[i] == target.matchValues[i]) {
                continue;
            }

            if (!matchValues[i].equals(target.matchValues[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a hash code based on the values of the soruce entries
     * match field's
     */
    public int sourceHashCode() {
        int rslt = 0;

        for (int i = 0; i < matchFields.length; i++) {
            rslt ^= matchFields[i].hashCode();
        }
        return rslt;
    }
}
