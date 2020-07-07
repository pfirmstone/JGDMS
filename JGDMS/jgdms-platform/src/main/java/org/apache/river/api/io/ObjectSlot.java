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

import java.io.ObjectStreamField;

// A slot is a field plus its value

class ObjectSlot {
    // Field descriptor
    private ObjectStreamField field;
    // Actual value this emulated field holds
    private Object fieldValue;
    private boolean booleanValue;
    private byte byteValue;
    private char charValue;
    private short shortValue;
    private int intValue;
    private long longValue;
    private float floatValue;
    private double doubleValue;
    // If this field has a default value (true) or something has been
    // assigned (false)
    private boolean defaulted = true;
    private ClassNotFoundException exception = null;

    /**
     * @return the field
     */
    public synchronized ObjectStreamField getField() {
	return field;
    }

    /**
     * @param field the field to set
     */
    public synchronized void setField(ObjectStreamField field) {
	this.field = field;
    }

    /**
     * @return the fieldValue
     */
    public synchronized Object getFieldValue() throws ClassNotFoundException {
	if (exception != null) throw exception;
	return fieldValue;
    }

    /**
     * @param fieldValue the fieldValue to set
     */
    public synchronized void setFieldValue(Object fieldValue) {
	this.fieldValue = fieldValue;
    }
    
    public synchronized void setFieldException(ClassNotFoundException e){
	this.exception = e;
    }

    /**
     * @return the booleanValue
     */
    public synchronized boolean isBooleanValue() {
	return booleanValue;
    }

    /**
     * @param booleanValue the booleanValue to set
     */
    public synchronized void setBooleanValue(boolean booleanValue) {
	this.booleanValue = booleanValue;
    }

    /**
     * @return the byteValue
     */
    public synchronized byte getByteValue() {
	return byteValue;
    }

    /**
     * @param byteValue the byteValue to set
     */
    public synchronized void setByteValue(byte byteValue) {
	this.byteValue = byteValue;
    }

    /**
     * @return the charValue
     */
    public synchronized char getCharValue() {
	return charValue;
    }

    /**
     * @param charValue the charValue to set
     */
    public synchronized void setCharValue(char charValue) {
	this.charValue = charValue;
    }

    /**
     * @return the shortValue
     */
    public synchronized short getShortValue() {
	return shortValue;
    }

    /**
     * @param shortValue the shortValue to set
     */
    public synchronized void setShortValue(short shortValue) {
	this.shortValue = shortValue;
    }

    /**
     * @return the intValue
     */
    public synchronized int getIntValue() {
	return intValue;
    }

    /**
     * @param intValue the intValue to set
     */
    public synchronized void setIntValue(int intValue) {
	this.intValue = intValue;
    }

    /**
     * @return the longValue
     */
    public synchronized long getLongValue() {
	return longValue;
    }

    /**
     * @param longValue the longValue to set
     */
    public synchronized void setLongValue(long longValue) {
	this.longValue = longValue;
    }

    /**
     * @return the floatValue
     */
    public synchronized float getFloatValue() {
	return floatValue;
    }

    /**
     * @param floatValue the floatValue to set
     */
    public synchronized void setFloatValue(float floatValue) {
	this.floatValue = floatValue;
    }

    /**
     * @return the doubleValue
     */
    public synchronized double getDoubleValue() {
	return doubleValue;
    }

    /**
     * @param doubleValue the doubleValue to set
     */
    public synchronized void setDoubleValue(double doubleValue) {
	this.doubleValue = doubleValue;
    }

    /**
     * @return the defaulted
     */
    public synchronized boolean isDefaulted() {
	return defaulted;
    }

    /**
     * @param defaulted the defaulted to set
     */
    public synchronized void setDefaulted(boolean defaulted) {
	this.defaulted = defaulted;
    }
}
