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
package com.sun.jini.config;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;

/**
 * Provided for backward compatibility, migrate to new name space.
 */
@Deprecated
public class Config {
    /** This class cannot be instantiated. */
    private Config() {
	throw new AssertionError(
            "com.sun.jini.config.Config cannot be instantiated");
    }

    /**
     * Obtains a non-<code>null</code> object from the specified
     * {@link Configuration} using the specified arguments.
     * <p>
     * Calling this method is equivalent to calling <code> {@link
     * #getNonNullEntry(Configuration, String, String, Class, Object, 
     * Object) getNonNullEntry}(config, component, name, type,
     * {@link Configuration#NO_DEFAULT}, 
     * {@link Configuration#NO_DATA})</code>.
     *
     * @param <T> the type of the object to be returned.
     * @param config the <code>Configuration</code> being consulted.
     * @param component the component being configured
     * @param name the name of the entry for the component
     * @param type the type of the object to be returned
     * @return a non-<code>null</code> object obtained from
     *         calling <code>config.getEntry</code> using 
     *         the other passed arguments
     * @throws NoSuchEntryException if the underlying call to 
     *         <code>config.getEntry</code> does
     * @throws ConfigurationException if the underlying call to 
     *         <code>config.getEntry</code> does, or if the 
     *         returned entry is <code>null</code>.
     *         Any <code>Error</code> thrown while creating the object
     *         is propagated to the caller; it is not wrapped in a
     *         <code>ConfigurationException</code>
     * @throws IllegalArgumentException if the underlying call to 
     *         <code>config.getEntry</code> does
     * @throws NullPointerException if any of the arguments are 
     *         <code>null</code>
     * @see Configuration#getEntry(String, String, Class) 
     * @see #getNonNullEntry(Configuration, String, String, Class, Object,
     *      Object) 
     */
    public static <T> T getNonNullEntry(Configuration config,
	    String component, String name, Class<T> type)
	throws ConfigurationException
    {
	return getNonNullEntry(config, component, name, type,
	    Configuration.NO_DEFAULT, Configuration.NO_DATA);
    }

    /**
     * Obtains a non-<code>null</code> object from the specified
     * {@link Configuration} using the specified arguments.
     * <p>
     * Calling this method is equivalent to calling <code> {@link
     * #getNonNullEntry(Configuration, String, String, Class, Object, 
     * Object) getNonNullEntry}(config, component, name, type, defaultValue,
     * {@link Configuration#NO_DATA})</code>.
     *
     * @param <T> the type of the object to be returned.
     * @param config the <code>Configuration</code> being consulted.
     * @param component the component being configured
     * @param name the name of the entry for the component
     * @param type the type of the object to be returned
     * @param defaultValue the object to return if no matching entry is
     *        found
     * @return a non-<code>null</code> object obtained from
     *         calling <code>config.getEntry</code> using 
     *         the other passed arguments
     * @throws NoSuchEntryException if the underlying call to 
     *         <code>config.getEntry</code> does
     * @throws ConfigurationException if the underlying call to 
     *         <code>config.getEntry</code> does, or if the 
     *         returned entry is <code>null</code>.
     *         Any <code>Error</code> thrown while creating the object
     *         is propagated to the caller; it is not wrapped in a
     *         <code>ConfigurationException</code>
     * @throws IllegalArgumentException if the underlying call to 
     *         <code>config.getEntry</code> does
     * @throws NullPointerException if any of the arguments are 
     *         <code>null</code>
     * @see Configuration#getEntry(String, String, Class, Object, Object) 
     * @see #getNonNullEntry(Configuration, String, String, Class, Object,
     *      Object) 
     */
    public static <T> T getNonNullEntry(Configuration config,
	    String component, String name, Class<T> type, Object defaultValue)
	throws ConfigurationException
    {
	return getNonNullEntry(config, component, name, type, defaultValue,
			       Configuration.NO_DATA);
    }			       

    /**
     * Obtains a non-<code>null</code> object from the specified
     * {@link Configuration} using the specified arguments.
     * <p>
     * Calling this method is equivalent to calling
     * <code>config.{@link Configuration#getEntry(String, String,
     * Class, Object, Object) getEntry}(component, name, type,
     * defaultValue, data)</code> after ensuring that
     * <code>defaultValue</code> is non-<code>null</code> and throwing
     * <code>ConfigurationException</code> if the result is
     * <code>null</code>.
     * 
     * @param <T> the type of the object to be returned.
     * @param config the <code>Configuration</code> being consulted.
     * @param component the component being configured
     * @param name the name of the entry for the component
     * @param type the type of the object to be returned
     * @param defaultValue the object to return if no matching entry is
     *        found
     * @param data an object to use when computing the value of the
     *        entry, or <code>Configuration#NO_DATA</code> to specify
     *        no data
     * @return a non-<code>null</code> object obtained from
     *         calling <code>config.getEntry</code> using 
     *         the other passed arguments
     * @throws NoSuchEntryException if the underlying call to 
     *         <code>config.getEntry</code> does
     * @throws ConfigurationException if the underlying call to 
     *         <code>config.getEntry</code> does, or if the 
     *         returned entry is <code>null</code>.
     *         Any <code>Error</code> thrown while creating the object
     *         is propagated to the caller; it is not wrapped in a
     *         <code>ConfigurationException</code>
     * @throws IllegalArgumentException if the underlying call to 
     *         <code>config.getEntry</code> does
     * @throws NullPointerException if the <code>config</code>, 
     *         <code>component</code>, <code>name</code>, <code>type</code>,
     *         or <code>defaultValue</code> arguments are <code>null</code>
     * @see Configuration#getEntry(String, String, Class, Object, Object) 
     * @see #getNonNullEntry(Configuration, String, String, Class, Object,
     *      Object) 
     */
    public static <T> T getNonNullEntry(Configuration config,
	    String component, String name, Class<T> type, Object defaultValue,
	    Object data)
	throws ConfigurationException
    {
	return org.apache.river.config.Config.getNonNullEntry(
                config, component, name, type, defaultValue, data);
    }


    /**
     * Obtains a <code>long</code> that falls within the given inclusive 
     * range from the specified {@link Configuration} using the specified
     * component and entry names.
     * 
     * @param config the <code>Configuration</code> being consulted.
     * @param component the component being configured
     * @param name the name of the entry for the component
     * @param defaultValue the <code>long</code> to return if no matching
     *        entry is found
     * @param min the minimum value the entry should have
     * @param max the maximum value the entry should have
     * @return a long obtained from calling <code>config.getEntry</code>
     *         using <code>component</code>, <code>name</code>,
     *         and <code>defaultValue</code>.
     * @throws NoSuchEntryException if the underlying call to 
     *         <code>config.getEntry</code> does
     * @throws ConfigurationException if the underlying call to 
     *         <code>config.getEntry</code> does, or if the 
     *         returned entry is <code>null</code>, or if 
     *         returned value is not between <code>min</code> and
     *         <code>max</code>, inclusive.
     *         Any <code>Error</code> thrown while creating the object
     *         is propagated to the caller; it is not wrapped in a
     *         <code>ConfigurationException</code>
     * @throws IllegalArgumentException if the underlying call to 
     *         <code>config.getEntry</code> does, if
     *         <code>defaultValue</code> is not between
     *         <code>min</code> and <code>max</code> inclusive,
     *         or if <code>min</code> is larger than <code>max</code>
     * @throws NullPointerException if <code>config</code>, 
     *         <code>component</code> or <code>name</code> is 
     *         <code>null</code>.  
     *         
     * @see Configuration#getEntry(String, String, Class, Object) 
     */
    public static long getLongEntry(Configuration config,
	    String component, String name, long defaultValue,
	    long min, long max)
	throws ConfigurationException
    {
	return org.apache.river.config.Config.getLongEntry(
                config, component, name, defaultValue, min, max);
    }

    /**
     * Obtains an <code>int</code> that falls within the given inclusive
     * range from the specified {@link Configuration} using the specified
     * component and entry names.
     * 
     * @param config the <code>Configuration</code> being consulted.
     * @param component the component being configured
     * @param name the name of the entry for the component
     * @param defaultValue the <code>int</code> to return if no matching
     *        entry is found
     * @param min the minimum value the entry should have
     * @param max the maximum value the entry should have
     * @return a int obtained from calling <code>config.getEntry</code>
     *         using <code>component</code>, <code>name</code>,
     *         and <code>defaultValue</code>.
     * @throws NoSuchEntryException if the underlying call to 
     *         <code>config.getEntry</code> does
     * @throws ConfigurationException if the underlying call to 
     *         <code>config.getEntry</code> does, or if the 
     *         returned entry is <code>null</code>, or if 
     *         returned value is not between <code>min</code> and
     *         <code>max</code>, inclusive.
     *         Any <code>Error</code> thrown while creating the object
     *         is propagated to the caller; it is not wrapped in a
     *         <code>ConfigurationException</code>
     * @throws IllegalArgumentException if the underlying call to 
     *         <code>config.getEntry</code> does, if
     *         <code>defaultValue</code> is not between
     *         <code>min</code> and <code>max</code> inclusive,
     *         or if <code>min</code> is larger than <code>max</code>
     * @throws NullPointerException if <code>config</code>, 
     *         <code>component</code> or <code>name</code> is 
     *         <code>null</code>.  
     *         
     * @see Configuration#getEntry(String, String, Class, Object) 
     */
    public static int getIntEntry(Configuration config,
	    String component, String name, int defaultValue,
	    int min, int max)
	throws ConfigurationException
    {
	return org.apache.river.config.Config.getIntEntry(
                config, component, name, defaultValue, min, max);
    }

    /**
     * Obtains a <code>float</code> that falls within the given inclusive
     * range from the specified {@link Configuration} using the specified
     * component and entry names.
     * 
     * @param config the <code>Configuration</code> being consulted.
     * @param component the component being configured
     * @param name the name of the entry for the component
     * @param defaultValue the <code>float</code> to return if no matching
     *        entry is found
     * @param min the minimum value the entry should have
     * @param max the maximum value the entry should have
     * @return a float obtained from calling <code>config.getEntry</code>
     *         using <code>component</code>, <code>name</code>,
     *         and <code>defaultValue</code>.
     * @throws NoSuchEntryException if the underlying call to 
     *         <code>config.getEntry</code> does
     * @throws ConfigurationException if the underlying call to 
     *         <code>config.getEntry</code> does, or if the 
     *         returned entry is <code>null</code>, or if 
     *         returned value is not between <code>min</code> and
     *         <code>max</code>, inclusive.
     *         Any <code>Error</code> thrown while creating the object
     *         is propagated to the caller; it is not wrapped in a
     *         <code>ConfigurationException</code>
     * @throws IllegalArgumentException if the underlying call to 
     *         <code>config.getEntry</code> does, if
     *         <code>defaultValue</code> is not between
     *         <code>min</code> and <code>max</code> inclusive,
     *         or if <code>min</code> is larger than <code>max</code>
     * @throws NullPointerException if <code>config</code>, 
     *         <code>component</code> or <code>name</code> is 
     *         <code>null</code>.  
     *         
     * @see Configuration#getEntry(String, String, Class, Object) 
     */
    public static float getFloatEntry(Configuration config,
	    String component, String name, float defaultValue,
	    float min, float max)
	throws ConfigurationException
    {
	return org.apache.river.config.Config.getFloatEntry(
                config, component, name, defaultValue, min, max);
    }
}
