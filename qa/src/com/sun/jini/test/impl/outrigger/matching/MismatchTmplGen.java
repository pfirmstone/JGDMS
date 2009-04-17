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
package com.sun.jini.test.impl.outrigger.matching;

// All imports
import net.jini.core.entry.Entry;
import java.lang.reflect.*;


/**
 * Template generator that generates a number of templates that don't match
 * the given entry.  The classes of the mismatches is taken from an array
 * of class objects passed into the constructor
 */
class MismatchTmplGen extends ReflectionTmplGenBase
        implements TemplateGenerator {
    final private Class[] menu;
    private int menuIndex = 0;

    /**
     * Create a <code>MismatchTmplGen</code>. All the classes in
     * <code>menu</code> should be non-abstract class that implement
     * Entry, if not will throw
     * <code>IllegalArgumentException</code>. Will also throw
     * <code>IllegalArgumentException</code> if any member of menu is
     * <code>AbstractEntry</code> (there is no way to create a
     * <code>AbstractEntry</code> template that does not match a
     * <code>UniqueEntry</code>
     */
    public MismatchTmplGen(Entry m, Class[] menu)
            throws java.lang.ClassNotFoundException {
        super(m);
        this.menu = menu;
        Class entryClass = Class.forName("net.jini.core.entry.Entry");

        for (int i = 0; i < this.menu.length; i++) {
            final Class target = this.menu[i];

            if (!entryClass.isAssignableFrom(target)) {
                throw new IllegalArgumentException("Not all of the Classes in "
                        + "the array implement Entry");
            }

            if (target.getName().equals("net.jini.entry.AbstractEntry")) {
                throw new IllegalArgumentException("None of the Classes can be "
                        + "AbstractEntry");
            }

            try {
                target.newInstance();
            } catch (InstantiationException e) {
                throw new IllegalArgumentException("Not all of the Classes in "
                        + "the array are valid Entry implementations");
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Not all of the Classes in "
                        + "the array are valid Entry implementations");
            }
        }
    }

    /*
     * Takes the passed object and tries to return an object of the
     * same class  that is diffrent.  If it can't it returns null
     */
    private Object makeDiffrent(Object o) {
        if (o == null) {
            return null;
        }

        if (o instanceof Integer) {
            return new Integer(((Integer) o).intValue() - 1);
        } else if (o instanceof Long) {
            return new Long(((Long) o).longValue() + 1);
        } else if (o instanceof String) {
            return new String(((String) o) + " minus one");
        } else if (o instanceof Boolean) {
            return new Boolean(((Boolean) o).booleanValue() ? false : true);
        } else if (o instanceof Byte) {
            return new Long(((Long) o).byteValue() + 1);
        } else if (o instanceof Character) {
            return new Character((char) (((Character) o).charValue() - 1));
        } else if (o instanceof Byte) {
            return new Byte((byte) (((Byte) o).byteValue() + 1));
        } else if (o instanceof Double) {
            return new Double(((Double) o).doubleValue() * 17.5);
        } else if (o instanceof Float) {
            return new Float(((Float) o).floatValue() / 17.5);
        } else if (o instanceof Short) {
            return new Short((short) (((Short) o).shortValue() - 1));
        } else {

            // Give up
            return null;
        }
    }

    synchronized public Entry next()
            throws InvocationTargetException, IllegalAccessException,
            InstantiationException {
        if (menuIndex >= menu.length) {
            return null;
        }

        // Not done
        final Class targetClass = menu[menuIndex++];
        final Entry target = createAndInit(targetClass);

        /*
         * If the master is assignable to targetClass then
         * createAndInit will acctually have generated a match, fix this
         */
        if (targetClass.isAssignableFrom(masterClass)) {
            final Field[] targetFields = targetClass.getFields();

            for (int i = 0; i < targetFields.length; i++) {
                final Field canadate = targetFields[i];

                if (isMatchField(canadate)) {
                    final Object newVal = makeDiffrent(canadate.get(target));

                    if (newVal != null) {
                        canadate.set(target, newVal);
                        return target;
                    }
                }
            }

            /*
             * If we are here we could not change target so it
             * would not match, give up on this class and move on to the next
             */
            return next();
        }
        return target;
    }

    public boolean isMatchingGenerator() {
        return false;
    }
}
