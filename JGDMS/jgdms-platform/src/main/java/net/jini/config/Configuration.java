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

package net.jini.config;

import net.jini.export.Exporter;
import net.jini.security.ProxyPreparer;

/**
 * Defines an interface for obtaining objects needed to configure applications,
 * such as {@link Exporter} or {@link ProxyPreparer} instances, or other
 * application-specific objects, from configuration files, databases, or other
 * sources. Configuration entries are identified by a <code>component</code>
 * and a <code>name</code>. Methods that retrieve entries can specify a default
 * value to return in case the entry is not found, and supply data to use when
 * computing the value of the entry. <p>
 *
 * Application developers are encouraged to use this interface, rather than
 * explicitly constructing instances of exporters and proxy preparers, so that
 * applications can be customized without requiring code
 * modifications. Applications should normally use {@link
 * ConfigurationProvider} to obtain <code>Configuration</code> instances,
 * rather than referencing implementation classes directly, so that the
 * interpretation of configuration options can be customized without requiring
 * code modifications.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface Configuration {
    /**
     * An object to pass for <code>defaultValue</code> in calls to
     * <code>getEntry</code> to specify no default value.
     *
     * @see #getEntry(String, String, Class, Object)
     * @see #getEntry(String, String, Class, Object, Object)
     */
    Object NO_DEFAULT = new Object() {
        @Override
	public String toString() { return "Configuration.NO_DEFAULT"; }
    };

    /**
     * An object to pass for <code>data</code> in calls to
     * <code>getEntry</code> to specify no data.
     *
     * @see #getEntry(String, String, Class, Object, Object)
     */
    Object NO_DATA = new Object() {
        @Override
	public String toString() { return "Configuration.NO_DATA"; }
    };

    /**
     * Returns an object of the specified type created using the information in
     * the entry matching the specified component and name, which must be
     * found, and supplying no data. If <code>type</code> is a primitive type,
     * then the result is returned as an instance of the associated wrapper
     * class. Repeated calls with the same arguments may or may not return the
     * identical object. <p>
     *
     * The <code>component</code> identifies the object whose behavior will be
     * configured using the object returned. The value of
     * <code>component</code> must be a <i>QualifiedIdentifier</i>, as defined
     * in the <i>Java(TM) Language Specification</i> (<i>JLS</i>), and is
     * typically the class or package name of the object being configured. The
     * <code>name</code> identifies which of possibly several entries are
     * available for the given component. The value of <code>name</code> must
     * be an <i>Identifier</i>, as defined in the JLS. <p>
     *
     * Calling this method is equivalent to calling <code>{@link
     * #getEntry(String, String, Class, Object, Object) getEntry}(component,
     * name, type, {@link #NO_DEFAULT}, {@link #NO_DATA})</code>.
     *
     * @param <T> Object returned.
     * @param component the component being configured
     * @param name the name of the entry for the component
     * @param type the type of the object to be returned
     * @return an object created using the information in the entry matching
     * <code>component</code> and <code>name</code>
     * @throws NoSuchEntryException if no matching entry is found
     * @throws ConfigurationException if a matching entry is found but a
     * problem occurs creating the object for the entry, or if
     * <code>type</code> is a reference type and the result for the matching
     * entry is not either <code>null</code> or an instance of
     * <code>type</code>, or if <code>type</code> is a primitive type and the
     * result is not an instance of the associated wrapper class. Any
     * <code>Error</code> thrown while creating the object is propagated to the
     * caller; it is not wrapped in a <code>ConfigurationException</code>.
     * @throws IllegalArgumentException if <code>component</code> is not
     * <code>null</code> and is not a valid <i>QualifiedIdentifier</i>, or if
     * <code>name</code> is not <code>null</code> and is not a valid
     * <i>Identifier</i>
     * @throws NullPointerException if any argument is <code>null</code>
     * @see #getEntry(String, String, Class, Object)
     */
    <T> T getEntry(String component, String name, Class<T> type)
	throws ConfigurationException;

    /**
     * Returns an object of the specified type created using the information in
     * the entry matching the specified component and name, and supplying no
     * data, returning the default value if no matching entry is found and the
     * default value is not {@link #NO_DEFAULT}. If <code>type</code> is a
     * primitive type, then the result is returned as an instance of the
     * associated wrapper class. Repeated calls with the same arguments may or
     * may not return the identical object. <p>
     *
     * The <code>component</code> identifies the object whose behavior will be
     * configured using the object returned. The value of
     * <code>component</code> must be a <i>QualifiedIdentifier</i>, as defined
     * in the <i>Java Language Specification</i> (<i>JLS</i>), and is typically
     * the class or package name of the object being configured. The
     * <code>name</code> identifies which of possibly several entries are
     * available for the given component. The value of <code>name</code> must
     * be an <i>Identifier</i>, as defined in the JLS. <p>
     *
     * Calling this method is equivalent to calling <code>{@link
     * #getEntry(String, String, Class, Object, Object) getEntry}(component,
     * name, type, defaultValue, {@link #NO_DATA})</code>.
     *
     * @param <T> the type of the object returned.
     * @param component the component being configured
     * @param name the name of the entry for the component
     * @param type the type of the object to be returned
     * @param defaultValue the object to return if no matching entry is found,
     * or <code>NO_DEFAULT</code> to specify no default
     * @return an object created using the information in the entry matching
     * <code>component</code> and <code>name</code>, or
     * <code>defaultValue</code> if no matching entry is found and
     * <code>defaultValue</code> is not <code>NO_DEFAULT</code>
     * @throws NoSuchEntryException if no matching entry is found and
     * <code>defaultValue</code> is <code>NO_DEFAULT</code>
     * @throws ConfigurationException if a matching entry is found but a
     * problem occurs creating the object for the entry, or if
     * <code>type</code> is a reference type and the result for the matching
     * entry is not either <code>null</code> or an instance of
     * <code>type</code>, or if <code>type</code> is a primitive type and the
     * result is not an instance of the associated wrapper class. Any
     * <code>Error</code> thrown while creating the object is propagated to the
     * caller; it is not wrapped in a <code>ConfigurationException</code>.
     * @throws IllegalArgumentException if <code>component</code> is not
     * <code>null</code> and is not a valid <i>QualifiedIdentifier</i>; or if
     * <code>name</code> is not <code>null</code> and is not a valid
     * <i>Identifier</i>; or if <code>type</code> is a reference type and
     * <code>defaultValue</code> is not <code>NO_DEFAULT</code>,
     * <code>null</code>, or an instance of <code>type</code>; or if
     * <code>type</code> is a primitive type and <code>defaultValue</code> is
     * not <code>NO_DEFAULT</code> or an instance of the associated wrapper
     * class
     * @throws NullPointerException if <code>component</code>,
     * <code>name</code>, or <code>type</code> is <code>null</code>
     * @see #getEntry(String, String, Class, Object, Object)
     */
    <T> T getEntry(String component,
		    String name,
		    Class<T> type,
		    Object defaultValue)
	throws ConfigurationException;

    /**
     * Returns an object of the specified type created using the information in
     * the entry matching the specified component and name, and using the
     * specified data (unless it is {@link #NO_DATA}), returning the default
     * value if no matching entry is found and the default value is not {@link
     * #NO_DEFAULT}. If <code>type</code> is a primitive type, then the result
     * is returned as an instance of the associated wrapper class. Repeated
     * calls with the same arguments may or may not return the identical
     * object. <p>
     *
     * The <code>component</code> identifies the object whose behavior will be
     * configured using the object returned. The value of
     * <code>component</code> must be a <i>QualifiedIdentifier</i>, as defined
     * in the <i>Java Language Specification</i> (<i>JLS</i>), and is typically
     * the class or package name of the object being configured. The
     * <code>name</code> identifies which of possibly several entries are
     * available for the given component. The value of <code>name</code> must
     * be an <i>Identifier</i>, as defined in the JLS.
     *
     * @param <T> the type of the object to be returned
     * @param component the component being configured
     * @param name the name of the entry for the component
     * @param type the type of the object to be returned
     * @param defaultValue the object to return if no matching entry is found,
     * or <code>NO_DEFAULT</code> to specify no default
     * @param data an object to use when computing the value of the entry, or
     * <code>NO_DATA</code> to specify no data
     * @return an object created using the information in the entry matching
     * <code>component</code> and <code>name</code>, and using the value of
     * <code>data</code> (unless it is <code>NO_DATA</code>), or
     * <code>defaultValue</code> if no matching entry is found and
     * <code>defaultValue</code> is not <code>NO_DEFAULT</code>
     * @throws NoSuchEntryException if no matching entry is found and
     * <code>defaultValue</code> is <code>NO_DEFAULT</code>
     * @throws ConfigurationException if a matching entry is found but a
     * problem occurs creating the object for the entry, or if
     * <code>type</code> is a reference type and the result for the matching
     * entry is not either <code>null</code> or an instance of
     * <code>type</code>, or if <code>type</code> is a primitive type and the
     * result is not an instance of the associated wrapper class. Any
     * <code>Error</code> thrown while creating the object is propagated to the
     * caller; it is not wrapped in a <code>ConfigurationException</code>.
     * @throws IllegalArgumentException if <code>component</code> is not
     * <code>null</code> and is not a valid <i>QualifiedIdentifier</i>; or if
     * <code>name</code> is not <code>null</code> and is not a valid
     * <i>Identifier</i>; or if <code>type</code> is a reference type and
     * <code>defaultValue</code> is not <code>NO_DEFAULT</code>,
     * <code>null</code>, or an instance of <code>type</code>; or if
     * <code>type</code> is a primitive type and <code>defaultValue</code> is
     * not <code>NO_DEFAULT</code> or an instance of the associated wrapper
     * class
     * @throws NullPointerException if <code>component</code>,
     * <code>name</code>, or <code>type</code> is <code>null</code>
     */
    <T> T getEntry(String component,
		    String name,
		    Class<T> type,
		    Object defaultValue,
		    Object data)
	throws ConfigurationException;
}
