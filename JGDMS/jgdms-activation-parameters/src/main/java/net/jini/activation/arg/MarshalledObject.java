/*
 * Copyright 2021 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.activation.arg;

import java.io.IOException;
import java.util.Collection;

/**
 * A <code>MarshalledObject</code> contains an object in serialized
 * form. The contained object can be deserialized on demand when
 * explicitly requested. This allows an object to be sent from one VM
 * to another in a way that allows the receiver to control when and if
 * the object is deserialized.
 * 
 * @since 4.0
 */
public interface MarshalledObject {
    
    /**
     * Returns a new copy of the contained marshalled object.
     * @return a new copy of the contained object.
     * @throws IOException if an 
     *         <code>IOException</code> occurs while deserializing the
     *         object from its internal representation
     * @throws ClassNotFoundException if any classes necessary
     *         for reconstructing the contained object can not
     *         be found or if <code>verifyCodebaseIntegrity</code>
     *         is <code>true</code> and the integrity of the
     *         contained object's codebase cannot be confirmed
     */
    public Object get() throws IOException, ClassNotFoundException;
    
    /**
     * Returns a new copy of the contained object.
     *
     * @param verifyCodebaseIntegrity if <code>true</code> then
     *        codebase integrity is verified, otherwise code base
     *        integrity is not verified
     * @return a new copy of the contained object
     * @throws IOException if an 
     *         <code>IOException</code> occurs while deserializing the
     *         object from its internal representation
     * @throws ClassNotFoundException if any classes necessary
     *         for reconstructing the contained object can not
     *         be found or if <code>verifyCodebaseIntegrity</code>
     *         is <code>true</code> and the integrity of the
     *         contained object's codebase cannot be confirmed
     */
    public Object get(final boolean verifyCodebaseIntegrity) 
	throws IOException, ClassNotFoundException;
    
    /**
     * Returns a new copy of the contained object.
     *
     * @param <T> Instance class type.
     * @param verifyCodebaseIntegrity if <code>true</code> then
     *        codebase integrity is verified, otherwise code base
     *        integrity is not verified
     * @param type - class of object to be read from bytes.
     * @return a new copy of the contained object
     * @throws IOException if an 
     *         <code>IOException</code> occurs while deserializing the
     *         object from its internal representation
     * @throws ClassNotFoundException if any classes necessary
     *         for reconstructing the contained object can not
     *         be found or if <code>verifyCodebaseIntegrity</code>
     *         is <code>true</code> and the integrity of the
     *         contained object's codebase cannot be confirmed
     */
    public <T> T get(final boolean verifyCodebaseIntegrity, Class<T> type) 
	throws IOException, ClassNotFoundException;
    
    /**
     * Returns a new copy of the contained object.
     * 
     * If <code>context</code> is not <code>null</code>
     * the input stream used to unmarshal the object implements 
     * <code>net.jini.io.ObjectStreamContext</code> and returns the given
     * collection from its <code>getObjectStreamContext</code> method.
     * <p>If <code>context</code> is <code>null</code>
     * the input stream used to unmarshal the object implements 
     * <code>net.jini.io.ObjectStreamContext</code> and returns a 
     * collection from its <code>getObjectStreamContext</code>
     * method which contains a single element of type 
     * <code>net.jini.io.context.IntegrityEnforcement</code>; 
     * the <code>integrityEnforced</code> method of this element returns the specified
     * <code>verifyCodebaseIntegrity</code> value.
     *
     * @param defaultLoader the class loader value (possibly
     *	      <code>null</code>) to pass as the <code>defaultLoader</code>
     *        argument to <code>net.jini.loader.ClassLoading</code> methods
     * @param verifyCodebaseIntegrity if <code>true</code> then
     *        codebase integrity is verified, otherwise code base
     *        integrity is not verified
     * @param verifierLoader the class loader value (possibly
     *        <code>null</code>) to pass to <code>
     *        net.jini.security.Security#verifyCodebaseIntegrity</code>, if
     *        <code>verifyCodebaseIntegrity</code> is <code>true</code>
     * @param context the collection of context information objects or
     *        <code>null</code>
     * @return a new copy of the contained object
     * @throws IOException if an 
     *         <code>IOException</code> occurs while deserializing the
     *         object from its internal representation
     * @throws ClassNotFoundException if any classes necessary
     *         for reconstructing the contained object can not
     *         be found or if <code>verifyCodebaseIntegrity</code>
     *         is <code>true</code> and the integrity of the
     *         contained object's codebase cannot be confirmed
     */
    public Object get(final ClassLoader defaultLoader,
		      final boolean verifyCodebaseIntegrity,
		      final ClassLoader verifierLoader,
		      final Collection context)
	throws IOException, ClassNotFoundException;
    
    /**
     * Returns a new copy of the contained object.If <code>context</code> 
     * is not <code>null</code>the input stream used to unmarshal the object
     * implements <code>net.jini.io.ObjectStreamContext</code> and returns 
     * the given collection from its <code>getObjectStreamContext</code> method.
     * <p>If <code>context</code> is <code>null</code>
     * the input stream used to unmarshal the object implements 
     * <code>net.jini.io.ObjectStreamContext</code> and returns a 
     * collection from its <code>getObjectStreamContext</code>
     * method which contains a single element of type 
     * <code>net.jini.io.context.IntegrityEnforcement</code>; 
     * the <code>integrityEnforced</code> method of this element returns the specified
     * <code>verifyCodebaseIntegrity</code> value.
     *
     * @param <T> Instance class type.
     * @param defaultLoader the class loader value (possibly
     *	      <code>null</code>) to pass as the <code>defaultLoader</code>
     *        argument to <code>RMIClassLoader</code> methods
     * @param verifyCodebaseIntegrity if <code>true</code> then
     *        codebase integrity is verified, otherwise code base
     *        integrity is not verified
     * @param verifierLoader the class loader value (possibly
     *        <code>null</code>) to pass to <code>
     *        net.jini.security.Security#verifyCodebaseIntegrity</code>, if
     *        <code>verifyCodebaseIntegrity</code> is <code>true</code>
     * @param context the collection of context information objects or
     *        <code>null</code>
     * @param type the class type of the object returned.
     * @return a new copy of the contained object
     * @throws IOException if an 
     *         <code>IOException</code> occurs while deserializing the
     *         object from its internal representation
     * @throws ClassNotFoundException if any classes necessary
     *         for reconstructing the contained object can not
     *         be found or if <code>verifyCodebaseIntegrity</code>
     *         is <code>true</code> and the integrity of the
     *         contained object's codebase cannot be confirmed
     */
    public <T> T get(final ClassLoader defaultLoader,
		      final boolean verifyCodebaseIntegrity,
		      final ClassLoader verifierLoader,
		      final Collection context,
                      final Class<T> type)
	throws IOException, ClassNotFoundException;
    
    
}
