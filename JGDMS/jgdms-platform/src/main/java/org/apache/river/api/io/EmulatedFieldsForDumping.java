/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.api.io;

import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.io.ObjectStreamClass;

/**
 * An EmulatedFieldsForDumping is an object that represents a set of emulated
 * fields for an object being dumped. It is a concrete implementation for
 * ObjectOutputStream.PutField
 * 
 * 
 * @see ObjectOutputStream.PutField
 * @see EmulatedFieldsForLoading
 */
class EmulatedFieldsForDumping extends PutField {

    // The actual representation, with a more powerful API (set&get)
    private final EmulatedFields emulatedFields;

    // Record the ObjectOutputStream that created this PutField for checking in the write method
    private final ObjectOutputStream oos;

    /**
     * Constructs a new instance of EmulatedFieldsForDumping.
     * 
     * @param streamClass
     *            a ObjectStreamClass, which describe the fields to be emulated
     *            (names, types, etc).
     */
    EmulatedFieldsForDumping(ObjectOutputStream oos, ObjectStreamClass streamClass) {
        super();
        emulatedFields = new EmulatedFields(streamClass.getFields());
        this.oos = oos;
    }

    EmulatedFieldsForDumping(ObjectStreamClass streamClass) {
        super();
        emulatedFields = new EmulatedFields(streamClass.getFields());
        this.oos = null;
    }

    /**
     * Return the actual EmulatedFields instance used by the receiver. We have
     * the actual work in a separate class so that the code can be shared. The
     * receiver has to be of a subclass of PutField.
     * 
     * @return array of ObjectSlot the receiver represents.
     */
    EmulatedFields emulatedFields() {
        return emulatedFields;
    }

    /**
     * Find and set the byte value of a given field named <code>name</code> in
     * the receiver.
     * 
     * @param name
     *            A String, the name of the field to set
     * @param value
     *            New value for the field.
     */
    @Override
    public void put(String name, byte value) {
        emulatedFields.put(name, value);
    }

    /**
     * Find and set the char value of a given field named <code>name</code> in
     * the receiver.
     * 
     * @param name
     *            A String, the name of the field to set
     * @param value
     *            New value for the field.
     */
    @Override
    public void put(String name, char value) {
        emulatedFields.put(name, value);
    }

    /**
     * Find and set the double value of a given field named <code>name</code>
     * in the receiver.
     * 
     * @param name
     *            A String, the name of the field to set
     * @param value
     *            New value for the field.
     */
    @Override
    public void put(String name, double value) {
        emulatedFields.put(name, value);
    }

    /**
     * Find and set the float value of a given field named <code>name</code>
     * in the receiver.
     * 
     * @param name
     *            A String, the name of the field to set
     * @param value
     *            New value for the field.
     */
    @Override
    public void put(String name, float value) {
        emulatedFields.put(name, value);
    }

    /**
     * Find and set the int value of a given field named <code>name</code> in
     * the receiver.
     * 
     * @param name
     *            A String, the name of the field to set
     * @param value
     *            New value for the field.
     */
    @Override
    public void put(String name, int value) {
        emulatedFields.put(name, value);
    }

    /**
     * Find and set the long value of a given field named <code>name</code> in
     * the receiver.
     * 
     * @param name
     *            A String, the name of the field to set
     * @param value
     *            New value for the field.
     */
    @Override
    public void put(String name, long value) {
        emulatedFields.put(name, value);
    }

    /**
     * Find and set the Object value of a given field named <code>name</code>
     * in the receiver.
     * 
     * @param name
     *            A String, the name of the field to set
     * @param value
     *            New value for the field.
     */
    @Override
    public void put(String name, Object value) {
        emulatedFields.put(name, value);
    }

    /**
     * Find and set the short value of a given field named <code>name</code>
     * in the receiver.
     * 
     * @param name
     *            A String, the name of the field to set
     * @param value
     *            New value for the field.
     */
    @Override
    public void put(String name, short value) {
        emulatedFields.put(name, value);
    }

    /**
     * Find and set the boolean value of a given field named <code>name</code>
     * in the receiver.
     * 
     * @param name
     *            A String, the name of the field to set
     * @param value
     *            New value for the field.
     */
    @Override
    public void put(String name, boolean value) {
        emulatedFields.put(name, value);
    }

    @Override
    public void write(ObjectOutput out) throws IOException {
        throw new UnsupportedOperationException("Not supported.");
    }

   
}
