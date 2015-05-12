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
import net.jini.core.entry.Entry;
import java.lang.reflect.*;


/**
 * Template generator base class that provides a number of utilities
 * using reflection.
 */
class ReflectionTmplGenBase {

    /**
     * Entry that all the templates are be generated relative too
     */
    final protected Entry master;

    /**
     * Class object of master;
     * @see ReflectionTmplGenBase#master
     */
    final protected Class masterClass;

    /**
     * <code>Field</code> objects that corspond to all of the fields
     * of <code>masterClass</code> that JavaSpaces uses to match on.
     * @see ReflectionTmplGenBase#masterClass
     */
    final protected Field[] matchFields;

    /**
     * Returns true if this is a field used for matching by JavaSpaces
     */
    protected boolean isMatchField(Field f) {
        final int fM = f.getModifiers();
        return !Modifier.isStatic(fM) && Modifier.isPublic(fM);
    }

    protected ReflectionTmplGenBase(Entry m) {
        master = m;
        masterClass = master.getClass();
        final Field[] allFields = masterClass.getFields();
        int matchCount = 0;

        for (int i = 0; i < allFields.length; i++) {
            if (isMatchField(allFields[i])) {
                matchCount++;
            } else {
                allFields[i] = null;
            }
        }
        matchFields = new Field[matchCount];
        matchCount = 0;

        for (int i = 0; i < allFields.length; i++) {
            if (allFields[i] != null) {
                matchFields[matchCount] = allFields[i];
                matchCount++;
            }
        }
    }

    /**
     * Creates an object of the specified class and fills in it fields
     * from the master.  Any public non-static fields which the master
     * does not have are set to <code>null</code>.
     *
     * @param targetClass Should be a type of <code>Entry</code>
     */
    protected Entry createAndInit(Class targetClass)
            throws InvocationTargetException, IllegalAccessException,
            InstantiationException {
        final Entry target = (Entry) targetClass.newInstance();
        final Field[] targetFields = targetClass.getFields();

        for (int i = 0; i < targetFields.length; i++) {
            final Field targetField = targetFields[i];

            if (isMatchField(targetField)) {
                targetField.set(target, null);

                for (int j = 0; j < matchFields.length; j++) {
                    final Field sourceField = matchFields[j];

                    if (sourceField.equals(targetField)) {
                        targetField.set(target, sourceField.get(master));
                    }
                }
            }
        }
        return target;
    }
}
