/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.io;

import java.io.ObjectStreamField;

/**
 *
 * @author peter
 */
class EmulatedFields {
    // The collection of slots the receiver represents
    private final ObjectSlot[] slotsToSerialize;
    private final ObjectStreamField[] declaredFields;

    /**
     * Constructs a new instance of EmulatedFields.
     *
     * @param fields
     *            an array of ObjectStreamFields, which describe the fields to
     *            be emulated (names, types, etc).
     * @param declared
     *            an array of ObjectStreamFields, which describe the declared
     *            fields.
     */
    public EmulatedFields(ObjectStreamField[] declared) {
	super();
	// We assume the slots are already sorted in the right shape for dumping
	slotsToSerialize = buildSlots(declared);
	declaredFields = declared;
    }

    /**
     * Build emulated slots that correspond to emulated fields. A slot is a
     * field descriptor (ObjectStreamField) plus the actual value it holds.
     *
     * @param fields
     *            an array of ObjectStreamField, which describe the fields to be
     *            emulated (names, types, etc).
     */
    private static ObjectSlot[] buildSlots(ObjectStreamField[] fields) {
	ObjectSlot[] slotsToSerialize = new ObjectSlot[fields.length];
	for (int i = 0; i < fields.length; i++) {
	    ObjectSlot s = new ObjectSlot();
	    slotsToSerialize[i] = s;
	    s.setField(fields[i]);
	}
	return slotsToSerialize;
	// We assume the slots are already sorted in the right shape for dumping
    }

    /**
     * Returns {@code true} indicating the field called {@code name} has not had
     * a value explicitly assigned and that it still holds a default value for
     * its type, or {@code false} indicating that the field named has been
     * assigned a value explicitly.
     *
     * @param name
     *            the name of the field to test.
     * @return {@code true} if {@code name} still holds its default value,
     *         {@code false} otherwise
     *
     * @throws IllegalArgumentException
     *             if {@code name} is {@code null}
     */
    public synchronized boolean defaulted(String name) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, null);
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	return slot.isDefaulted();
    }

    /**
     * Finds and returns an ObjectSlot that corresponds to a field named {@code
     * fieldName} and type {@code fieldType}. If the field type {@code
     * fieldType} corresponds to a primitive type, the field type has to match
     * exactly or {@code null} is returned. If the field type {@code fieldType}
     * corresponds to an object type, the field type has to be compatible in
     * terms of assignment, or null is returned. If {@code fieldType} is {@code
     * null}, no such compatibility checking is performed and the slot is
     * returned.
     *
     * @param fieldName
     *            the name of the field to find
     * @param fieldType
     *            the type of the field. This will be used to test
     *            compatibility. If {@code null}, no testing is done, the
     *            corresponding slot is returned.
     * @return the object slot, or {@code null} if there is no field with that
     *         name, or no compatible field (relative to {@code fieldType})
     */
    private synchronized ObjectSlot findSlot(String fieldName, Class<?> fieldType) {
	boolean isPrimitive = fieldType != null && fieldType.isPrimitive();
	for (int i = 0; i < slotsToSerialize.length; i++) {
	    ObjectSlot slot = slotsToSerialize[i];
	    if (slot.getField().getName().equals(fieldName)) {
		if (isPrimitive) {
		    // Looking for a primitive type field. Types must match
		    // *exactly*
		    if (slot.getField().getType() == fieldType) {
			return slot;
		    }
		} else {
		    // Looking for a non-primitive type field.
		    if (fieldType == null) {
			return slot; // Null means we take anything
		    }
		    // Types must be compatible (assignment)
		    if (slot.getField().getType().isAssignableFrom(fieldType)) {
			return slot;
		    }
		}
	    }
	}
	if (declaredFields != null) {
	    for (int i = 0; i < declaredFields.length; i++) {
		ObjectStreamField field = declaredFields[i];
		if (field.getName().equals(fieldName)) {
		    if (isPrimitive ? field.getType() == fieldType : fieldType == null || field.getType().isAssignableFrom(fieldType)) {
			ObjectSlot slot = new ObjectSlot();
			slot.setField(field);
			slot.setDefaulted(true);
			return slot;
		    }
		}
	    }
	}
	return null;
    }

    /**
     * Finds and returns the byte value of a given field named {@code name}
     * in the receiver. If the field has not been assigned any value yet, the
     * default value {@code defaultValue} is returned instead.
     *
     * @param name
     *            the name of the field to find.
     * @param defaultValue
     *            return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, the default
     *         value otherwise.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized byte get(String name, byte defaultValue) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Byte.TYPE);
	// if not initialized yet, we give the default value
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	return slot.isDefaulted() ? defaultValue : slot.getByteValue();
    }

    /**
     * Finds and returns the char value of a given field named {@code name} in the
     * receiver. If the field has not been assigned any value yet, the default
     * value {@code defaultValue} is returned instead.
     *
     * @param name
     *            the name of the field to find.
     * @param defaultValue
     *            return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, the default
     *         value otherwise.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized char get(String name, char defaultValue) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Character.TYPE);
	// if not initialized yet, we give the default value
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	return slot.isDefaulted() ? defaultValue : slot.getCharValue();
    }

    /**
     * Finds and returns the double value of a given field named {@code name}
     * in the receiver. If the field has not been assigned any value yet, the
     * default value {@code defaultValue} is returned instead.
     *
     * @param name
     *            the name of the field to find.
     * @param defaultValue
     *            return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, the default
     *         value otherwise.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized double get(String name, double defaultValue) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Double.TYPE);
	// if not initialized yet, we give the default value
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	return slot.isDefaulted() ? defaultValue : slot.getDoubleValue();
    }

    /**
     * Finds and returns the float value of a given field named {@code name} in
     * the receiver. If the field has not been assigned any value yet, the
     * default value {@code defaultValue} is returned instead.
     *
     * @param name
     *            the name of the field to find.
     * @param defaultValue
     *            return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, the default
     *         value otherwise.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized float get(String name, float defaultValue) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Float.TYPE);
	// if not initialized yet, we give the default value
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	return slot.isDefaulted() ? defaultValue : slot.getFloatValue();
    }

    /**
     * Finds and returns the int value of a given field named {@code name} in the
     * receiver. If the field has not been assigned any value yet, the default
     * value {@code defaultValue} is returned instead.
     *
     * @param name
     *            the name of the field to find.
     * @param defaultValue
     *            return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, the default
     *         value otherwise.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized int get(String name, int defaultValue) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Integer.TYPE);
	// if not initialized yet, we give the default value
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	return slot.isDefaulted() ? defaultValue : slot.getIntValue();
    }

    /**
     * Finds and returns the long value of a given field named {@code name} in the
     * receiver. If the field has not been assigned any value yet, the default
     * value {@code defaultValue} is returned instead.
     *
     * @param name
     *            the name of the field to find.
     * @param defaultValue
     *            return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, the default
     *         value otherwise.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized long get(String name, long defaultValue) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Long.TYPE);
	// if not initialized yet, we give the default value
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	return slot.isDefaulted() ? defaultValue : slot.getLongValue();
    }

    /**
     * Finds and returns the Object value of a given field named {@code name} in
     * the receiver. If the field has not been assigned any value yet, the
     * default value {@code defaultValue} is returned instead.
     *
     * @param name
     *            the name of the field to find.
     * @param defaultValue
     *            return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, the default
     *         value otherwise.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized Object get(String name, Object defaultValue) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, null);
	// if not initialized yet, we give the default value
	if (slot == null || slot.getField().getType().isPrimitive()) {
	    throw new IllegalArgumentException();
	}
	return slot.isDefaulted() ? defaultValue : slot.getFieldValue();
    }

    /**
     * Finds and returns the short value of a given field named {@code name} in
     * the receiver. If the field has not been assigned any value yet, the
     * default value {@code defaultValue} is returned instead.
     *
     * @param name
     *            the name of the field to find.
     * @param defaultValue
     *            return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, the default
     *         value otherwise.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized short get(String name, short defaultValue) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Short.TYPE);
	// if not initialized yet, we give the default value
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	return slot.isDefaulted() ? defaultValue : slot.getShortValue();
    }

    /**
     * Finds and returns the boolean value of a given field named {@code name} in
     * the receiver. If the field has not been assigned any value yet, the
     * default value {@code defaultValue} is returned instead.
     *
     * @param name
     *            the name of the field to find.
     * @param defaultValue
     *            return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, the default
     *         value otherwise.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized boolean get(String name, boolean defaultValue) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Boolean.TYPE);
	// if not initialized yet, we give the default value
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	return slot.isDefaulted() ? defaultValue : slot.isBooleanValue();
    }

    /**
     * Find and set the byte value of a given field named {@code name} in the
     * receiver.
     *
     * @param name
     *            the name of the field to set.
     * @param value
     *            new value for the field.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized void put(String name, byte value) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Byte.TYPE);
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	slot.setByteValue(value);
	slot.setDefaulted(false); // No longer default value
    }

    /**
     * Find and set the char value of a given field named {@code name} in the
     * receiver.
     *
     * @param name
     *            the name of the field to set.
     * @param value
     *            new value for the field.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized void put(String name, char value) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Character.TYPE);
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	slot.setCharValue(value);
	slot.setDefaulted(false); // No longer default value
    }

    /**
     * Find and set the double value of a given field named {@code name} in the
     * receiver.
     *
     * @param name
     *            the name of the field to set.
     * @param value
     *            new value for the field.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized void put(String name, double value) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Double.TYPE);
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	slot.setDoubleValue(value);
	slot.setDefaulted(false); // No longer default value
    }

    /**
     * Find and set the float value of a given field named {@code name} in the
     * receiver.
     *
     * @param name
     *            the name of the field to set.
     * @param value
     *            new value for the field.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized void put(String name, float value) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Float.TYPE);
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	slot.setFloatValue(value);
	slot.setDefaulted(false); // No longer default value
    }

    /**
     * Find and set the int value of a given field named {@code name} in the
     * receiver.
     *
     * @param name
     *            the name of the field to set.
     * @param value
     *            new value for the field.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized void put(String name, int value) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Integer.TYPE);
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	slot.setIntValue(value);
	slot.setDefaulted(false); // No longer default value
    }

    /**
     * Find and set the long value of a given field named {@code name} in the
     * receiver.
     *
     * @param name
     *            the name of the field to set.
     * @param value
     *            new value for the field.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized void put(String name, long value) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Long.TYPE);
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	slot.setLongValue(value);
	slot.setDefaulted(false); // No longer default value
    }

    /**
     * Find and set the Object value of a given field named {@code name} in the
     * receiver.
     *
     * @param name
     *            the name of the field to set.
     * @param value
     *            new value for the field.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized void put(String name, Object value) throws IllegalArgumentException {
	Class<?> valueClass = null;
	if (value != null) {
	    valueClass = value.getClass();
	}
	ObjectSlot slot = findSlot(name, valueClass);
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	slot.setFieldValue(value);
	slot.setDefaulted(false); // No longer default value
    }

    /**
     * Find and set the short value of a given field named {@code name} in the
     * receiver.
     *
     * @param name
     *            the name of the field to set.
     * @param value
     *            new value for the field.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized void put(String name, short value) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Short.TYPE);
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	slot.setShortValue(value);
	slot.setDefaulted(false); // No longer default value
    }

    /**
     * Find and set the boolean value of a given field named {@code name} in the
     * receiver.
     *
     * @param name
     *            the name of the field to set.
     * @param value
     *            new value for the field.
     *
     * @throws IllegalArgumentException
     *             if the corresponding field can not be found.
     */
    public synchronized void put(String name, boolean value) throws IllegalArgumentException {
	ObjectSlot slot = findSlot(name, Boolean.TYPE);
	if (slot == null) {
	    throw new IllegalArgumentException();
	}
	slot.setBooleanValue(value);
	slot.setDefaulted(false); // No longer default value
    }

    /**
     * Return the array of ObjectSlot the receiver represents.
     *
     * @return array of ObjectSlot the receiver represents.
     */
    public synchronized ObjectSlot[] slots() {
	return slotsToSerialize;
    }
    
}
