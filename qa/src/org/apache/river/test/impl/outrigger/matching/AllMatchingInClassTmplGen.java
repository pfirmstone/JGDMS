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

// all imports
import net.jini.core.entry.Entry;
import java.lang.reflect.*;


/**
 * Template generators that generate all the templates that match the
 * Entry passed to the constructor that are of the same class
 */
class AllMatchingInClassTmplGen extends ReflectionTmplGenBase
        implements TemplateGenerator {
    final private long limit;
    private long selector = 0;

    AllMatchingInClassTmplGen(Entry m) {
        super(m);
        limit = (0x1l << (matchFields.length));
    }

    @Override
    synchronized public Entry next()
            throws InvocationTargetException, IllegalAccessException,
            InstantiationException {
        if (limit == selector) {
            return null;
        }

        // Not done
        Entry rslt;
        try {
            rslt = (Entry) masterClass.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException ex) {
            throw new InvocationTargetException(ex);
        } 
        long indicator = selector++;

        for (int i = 0; i < matchFields.length; i++) {
            Field current = matchFields[i];

            if ((0x1l & indicator) == 0x1) {

                // copy field
                current.set(rslt, current.get(master));
            } else {
                current.set(rslt, null);
            }
            indicator >>>= 1;
        }
        return rslt;
    }

    public boolean isMatchingGenerator() {
        return true;
    }
}
