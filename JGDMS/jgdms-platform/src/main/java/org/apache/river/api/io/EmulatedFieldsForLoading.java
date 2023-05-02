/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.io;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;

/**
 *
 * @author peter
 */
class EmulatedFieldsForLoading extends ObjectInputStream.GetField {
    // The class descriptor with the declared fields the receiver emulates
    private final ObjectStreamClass streamClass;
    // The actual representation, with a more powerful API (set&get)
    private final EmulatedFields emulatedFields;

    /**
     * Constructs a new instance of EmulatedFieldsForLoading.
     *
     * @param streamClass
     *            an ObjectStreamClass, defining the class for which to emulate
     *            fields.
     */
    EmulatedFieldsForLoading(ObjectStreamClass streamClass, ObjectStreamField [] fields) {
	super();
	this.streamClass = streamClass;
	emulatedFields = new EmulatedFields(fields); // Get Fields copies, consider not copying for efficiency?
    }

    /**
     * Return a boolean indicating if the field named <code>name</code> has
     * been assigned a value explicitly (false) or if it still holds a default
     * value for the type (true) because it hasn't been assigned to yet.
     *
     * @param name
     *            A String, the name of the field to test
     * @return <code>true</code> if the field holds it default value,
     *         <code>false</code> otherwise.
     *
     * @throws IOException
     *             If an IO error occurs
     * @throws IllegalArgumentException
     *             If the corresponding field can not be found.
     */
    @Override
    public boolean defaulted(String name) throws IOException, IllegalArgumentException {
	return emulatedFields.defaulted(name);
    }

    /**
     * Return the actual EmulatedFields instance used by the receiver. We have
     * the actual work in a separate class so that the code can be shared. The
     * receiver has to be of a subclass of GetField.
     *
     * @return array of ObjectSlot the receiver represents.
     */
    EmulatedFields emulatedFields() {
	return emulatedFields;
    }

    /**
     * Find and return the byte value of a given field named <code>name</code>
     * in the receiver. If the field has not been assigned any value yet, the
     * default value <code>defaultValue</code> is returned instead.
     *
     * @param name
     *            A String, the name of the field to find
     * @param defaultValue
     *            Return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, or the
     *         default value otherwise
     *
     * @throws IOException
     *             If an IO error occurs
     * @throws IllegalArgumentException
     *             If the corresponding field can not be found.
     */
    @Override
    public byte get(String name, byte defaultValue) throws IOException, IllegalArgumentException {
	return emulatedFields.get(name, defaultValue);
    }

    /**
     * Find and return the char value of a given field named <code>name</code>
     * in the receiver. If the field has not been assigned any value yet, the
     * default value <code>defaultValue</code> is returned instead.
     *
     * @param name
     *            A String, the name of the field to find
     * @param defaultValue
     *            Return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, or the
     *         default value otherwise
     *
     * @throws IOException
     *             If an IO error occurs
     * @throws IllegalArgumentException
     *             If the corresponding field can not be found.
     */
    @Override
    public char get(String name, char defaultValue) throws IOException, IllegalArgumentException {
	return emulatedFields.get(name, defaultValue);
    }

    /**
     * Find and return the double value of a given field named <code>name</code>
     * in the receiver. If the field has not been assigned any value yet, the
     * default value <code>defaultValue</code> is returned instead.
     *
     * @param name
     *            A String, the name of the field to find
     * @param defaultValue
     *            Return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, or the
     *         default value otherwise
     *
     * @throws IOException
     *             If an IO error occurs
     * @throws IllegalArgumentException
     *             If the corresponding field can not be found.
     */
    @Override
    public double get(String name, double defaultValue) throws IOException, IllegalArgumentException {
	return emulatedFields.get(name, defaultValue);
    }

    /**
     * Find and return the float value of a given field named <code>name</code>
     * in the receiver. If the field has not been assigned any value yet, the
     * default value <code>defaultValue</code> is returned instead.
     *
     * @param name
     *            A String, the name of the field to find
     * @param defaultValue
     *            Return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, or the
     *         default value otherwise
     *
     * @throws IOException
     *             If an IO error occurs
     * @throws IllegalArgumentException
     *             If the corresponding field can not be found.
     */
    @Override
    public float get(String name, float defaultValue) throws IOException, IllegalArgumentException {
	return emulatedFields.get(name, defaultValue);
    }

    /**
     * Find and return the int value of a given field named <code>name</code>
     * in the receiver. If the field has not been assigned any value yet, the
     * default value <code>defaultValue</code> is returned instead.
     *
     * @param name
     *            A String, the name of the field to find
     * @param defaultValue
     *            Return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, or the
     *         default value otherwise
     *
     * @throws IOException
     *             If an IO error occurs
     * @throws IllegalArgumentException
     *             If the corresponding field can not be found.
     */
    @Override
    public int get(String name, int defaultValue) throws IOException, IllegalArgumentException {
	return emulatedFields.get(name, defaultValue);
    }

    /**
     * Find and return the long value of a given field named <code>name</code>
     * in the receiver. If the field has not been assigned any value yet, the
     * default value <code>defaultValue</code> is returned instead.
     *
     * @param name
     *            A String, the name of the field to find
     * @param defaultValue
     *            Return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, or the
     *         default value otherwise
     *
     * @throws IOException
     *             If an IO error occurs
     * @throws IllegalArgumentException
     *             If the corresponding field can not be found.
     */
    @Override
    public long get(String name, long defaultValue) throws IOException, IllegalArgumentException {
	return emulatedFields.get(name, defaultValue);
    }

    /**
     * Find and return the Object value of a given field named <code>name</code>
     * in the receiver. If the field has not been assigned any value yet, the
     * default value <code>defaultValue</code> is returned instead.
     *
     * @param name
     *            A String, the name of the field to find
     * @param defaultValue
     *            Return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, or the
     *         default value otherwise
     *
     * @throws IOException
     *             If an IO error occurs
     * @throws IllegalArgumentException
     *             If the corresponding field can not be found.
     */
    @Override
    public Object get(String name, Object defaultValue) 
	    throws IOException, IllegalArgumentException {
	try {
	    return emulatedFields.get(name, defaultValue, null);
	} catch (ClassNotFoundException ex) {
	    // Need to throw something more specific, so upper calls
	    // can rethrow class not found exception.
	    throw new IOException("Unable to get field: " + name, ex);
	}
    }
    
    public <T> T get(String name, T defaultValue, Class<T> type)
	    throws IOException, IllegalArgumentException, ClassNotFoundException 
    {
	return emulatedFields.get(name, defaultValue, type);
    }

    /**
     * Find and return the short value of a given field named <code>name</code>
     * in the receiver. If the field has not been assigned any value yet, the
     * default value <code>defaultValue</code> is returned instead.
     *
     * @param name
     *            A String, the name of the field to find
     * @param defaultValue
     *            Return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, or the
     *         default value otherwise
     *
     * @throws IOException
     *             If an IO error occurs
     * @throws IllegalArgumentException
     *             If the corresponding field can not be found.
     */
    @Override
    public short get(String name, short defaultValue) throws IOException, IllegalArgumentException {
	return emulatedFields.get(name, defaultValue);
    }

    /**
     * Find and return the boolean value of a given field named
     * <code>name</code> in the receiver. If the field has not been assigned
     * any value yet, the default value <code>defaultValue</code> is returned
     * instead.
     *
     * @param name
     *            A String, the name of the field to find
     * @param defaultValue
     *            Return value in case the field has not been assigned to yet.
     * @return the value of the given field if it has been assigned, or the
     *         default value otherwise
     *
     * @throws IOException
     *             If an IO error occurs
     * @throws IllegalArgumentException
     *             If the corresponding field can not be found.
     */
    @Override
    public boolean get(String name, boolean defaultValue) throws IOException, IllegalArgumentException {
	return emulatedFields.get(name, defaultValue);
    }

    /**
     * Return the class descriptor for which the emulated fields are defined.
     *
     * @return ObjectStreamClass The class descriptor for which the emulated
     *         fields are defined.
     */
    @Override
    public ObjectStreamClass getObjectStreamClass() {
	return streamClass;
    }
    
}
