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

// imports
import net.jini.core.entry.Entry;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.io.PrintWriter;


public class RandomEntryFactory {

    // Entry field template objects for intrinsic types.

    // Boolean types
    private static final Boolean booleanTemplate = new Boolean(true);
    private static final Boolean[] booleanArrayTemplate = new Boolean[] {
        new Boolean(true) };
    private static final boolean[] primitiveBooleanArrayTemplate =
            new boolean[] {
                true };

    // Byte types
    private static final Byte byteTemplate = new Byte((byte) 2);
    private static final Byte[] byteArrayTemplate = new Byte[] {
        new Byte((byte) 2) };
    private static final byte[] primitiveByteArrayTemplate = new byte[] {
        (byte) 2 };

    // Character types
    private static final Character characterTemplate = new Character((char)
            'A');
    private static final Character[] characterArrayTemplate = new Character[] {
        new Character((char) 'A') };
    private static final char[] primitiveCharacterArrayTemplate = new char[] {
        (char) 'A' };

    // Double types
    private static final Double doubleTemplate = new Double((double) 3.0);
    private static final Double[] doubleArrayTemplate = new Double[] {
        new Double((double) 3.0) };
    private static final double[] primitiveDoubleArrayTemplate = new double[] {
        (double) 3.0 };

    // Float types
    private static final Float floatTemplate = new Float((float) 4.0);
    private static final Float[] floatArrayTemplate = new Float[] {
        new Float((float) 4.0) };
    private static final float[] primitiveFloatArrayTemplate = new float[] {
        (float) 4.0 };

    // Integer types
    private static final Integer integerTemplate = new Integer((int) 5);
    private static final Integer[] integerArrayTemplate = new Integer[] {
        new Integer((int) 5) };
    private static final int[] primitiveIntegerArrayTemplate = new int[] {
        (int) 5 };

    // Long types
    private static final Long longTemplate = new Long((long) 6);
    private static final Long[] longArrayTemplate = new Long[] {
        new Long((long) 6) };
    private static final long[] primitiveLongArrayTemplate = new long[] {
        (long) 6 };

    // Short types
    private static final Short shortTemplate = new Short((short) 7);
    private static final Short[] shortArrayTemplate = new Short[] {
        new Short((short) 7) };
    private static final short[] primitiveShortArrayTemplate = new short[] {
        (short) 7 };

    // String types
    private static final String stringTemplate = new String("JavaBabe");
    private static final String[] stringArrayTemplate = new String[] {
        new String("JavaBabe") };

    /**
     * Array of Class objects to used in generating Entry objects.
     */
    private static final Class[] classList = {
        IntegerMatch.class, ChildOfIntegerMatch.class,
        GrandChildOfIntegerMatch.class, NBEComplex.class, NBEEmpty.class,
        NBEUniqueEntry.class, RemoteMatch.class, StringMatch.class,
        ChildOfStringMatch.class, GrandChildOfStringMatch.class,
        UserDefMatch.class };

    /**
     * If set, turns on verbose output messages.
     */
    private static PrintWriter out = null;

    /**
     * Returns a non-unique, random Entry object.
     */
    public static Entry getEntry() {
        Class c = getRandomEntryClass();
        Entry e = null;

        try {
            e = (Entry) c.newInstance();
            entryInit(e, false);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        return e;
    }

    /**
     * Returns a unique, random Entry object.
     */
    public static Entry getUniqueEntry() {
        Class c = getRandomEntryClass();
        Entry ue = null;

        try {

            // Get the constructor that takes a boolean argument.
            Constructor ctor = c.getConstructor(new Class[] {
                boolean.class});

            // Create a new unique object and initialize it.
            ue = (Entry) ctor.newInstance(new Object[] {
                new Boolean(true)});
            entryInit(ue, true);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        return ue;
    }

    /**
     * Returns a random Entry class object from the <code>classList</code>.
     */
    private static Class getRandomEntryClass() {
        int index = RandomList.getRandomIndex(classList.length);
        factoryMessage("Creating a " + classList[index].getName());

        // Return random class object.
        return classList[index];
    }

    /**
     * Initializes the given Entry object with default values.  If the
     * uniqueEntry flag is set, then don't overwrite the fields inherited
     * from the UniqueEntry class.
     */
    private static Entry entryInit(Entry e, boolean uniqueEntry) {

        // Retreive entry's class object
        final Class c = e.getClass();

        // Retreive object's public fields
        final Field[] allFields = c.getFields();

        /*
         * Loop over all fields and try to initialize them
         * with default values. Skip the "originatingHostVM" and "entryID"
         * fields for unique entries, though.
         */
        Field field = null;

        for (int i = 0; i < allFields.length; i++) {
            field = allFields[i];

            if (Template.isMatchField(field)) {
                if (uniqueEntry && (field.getName().equals("originatingHostVM")
                        || field.getName().equals("entryID"))) {

                    // Skip inherited UniqueEntry fields.
                    continue;
                } else {

                    // Initialize field to default value.
                    fieldInit(field, e);
                }
            }
        }
        return e;
    }

    /**
     * Initializes the given Field object with default values.
     * Note that this method assumes fields consist only of "intrinsic"
     * types.
     */
    private static void fieldInit(Field f, Object o) {
        try {
            if (f.getType() == Boolean.class) {
                f.set(o, booleanTemplate);
            } else if (f.getType() == Boolean[].class) {
                f.set(o, booleanArrayTemplate);
            } else if (f.getType() == boolean[].class) {
                f.set(o, primitiveBooleanArrayTemplate);
            } else if (f.getType() == Byte.class) {
                f.set(o, byteTemplate);
            } else if (f.getType() == Byte[].class) {
                f.set(o, byteArrayTemplate);
            } else if (f.getType() == byte[].class) {
                f.set(o, primitiveByteArrayTemplate);
            } else if (f.getType() == Character.class) {
                f.set(o, characterTemplate);
            } else if (f.getType() == Character[].class) {
                f.set(o, characterArrayTemplate);
            } else if (f.getType() == char[].class) {
                f.set(o, primitiveCharacterArrayTemplate);
            } else if (f.getType() == Double.class) {
                f.set(o, doubleTemplate);
            } else if (f.getType() == Double[].class) {
                f.set(o, doubleArrayTemplate);
            } else if (f.getType() == double[].class) {
                f.set(o, primitiveDoubleArrayTemplate);
            } else if (f.getType() == Float.class) {
                f.set(o, floatTemplate);
            } else if (f.getType() == Float[].class) {
                f.set(o, floatArrayTemplate);
            } else if (f.getType() == float[].class) {
                f.set(o, primitiveFloatArrayTemplate);
            } else if (f.getType() == Integer.class) {
                f.set(o, integerTemplate);
            } else if (f.getType() == Integer[].class) {
                f.set(o, integerArrayTemplate);
            } else if (f.getType() == int[].class) {
                f.set(o, primitiveIntegerArrayTemplate);
            } else if (f.getType() == Long.class) {
                f.set(o, longTemplate);
            } else if (f.getType() == Long[].class) {
                f.set(o, longArrayTemplate);
            } else if (f.getType() == long[].class) {
                f.set(o, primitiveLongArrayTemplate);
            } else if (f.getType() == Short.class) {
                f.set(o, shortTemplate);
            } else if (f.getType() == Short[].class) {
                f.set(o, shortArrayTemplate);
            } else if (f.getType() == short[].class) {
                f.set(o, primitiveShortArrayTemplate);
            } else if (f.getType() == String.class) {
                f.set(o, stringTemplate);
            } else if (f.getType() == String[].class) {
                f.set(o, stringArrayTemplate);
            } else {
                factoryMessage("Unsupported type received ... skipping.");
            }
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Sets the stream for output messages from this class.
     * Setting the stream implicitly turns on debugging messages.
     */
    public static void setOutputStream(PrintWriter ps) {
        out = ps;
    }

    /**
     * Utility method that prepends the class name to output messages.
     */
    private static void factoryMessage(String msg) {
        if (out != null) {
            out.println("RandomEntryFactory: " + msg);
        }
    }

    /**
     * Driver routine for unit testing this class
     */
    public static void main(String[] args) {
        Entry entry = null;

        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                entry = RandomEntryFactory.getUniqueEntry();
            } else {
                entry = RandomEntryFactory.getEntry();
            }
            System.out.println("Entry " + i + ": " + entry);
        }
    }
}
