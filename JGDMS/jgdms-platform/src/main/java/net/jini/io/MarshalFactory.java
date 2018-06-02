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

package net.jini.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

/**
 * Allows for alternative serialization implementations of MarshalledInstance.
 * 
 * @author peter
 */
public interface MarshalFactory {
    
    /**
     * 
     * @param objIn InputStream to read serialized objects from.
     * @param locIn InputStream to read codebase annotations from, optional.
     *@param defaultLoader the class loader value (possibly
     *	      <code>null</code>) to pass as the <code>defaultLoader</code>
     *        argument to <code>RMIClassLoader</code> methods
     * @param verifyCodebaseIntegrity if <code>true</code> then
     *        codebase integrity is verified, otherwise code base
     *        integrity is not verified
     * @param verifierLoader the class loader value (possibly
     *        <code>null</code>) to pass to {@link
     *        net.jini.security.Security#verifyCodebaseIntegrity
     *        Security.verifyCodebaseIntegrity}, if
     *        <code>verifyCodebaseIntegrity</code> is <code>true</code>
     * @param context the collection of context information objects or
     *        <code>null</code>
     * @return a new MarshalInstanceInput instance.
     * @throws IOException if an 
     *         <code>IOException</code> occurs while creating the
     *         MarshalInstanceInput.
     */
    MarshalInstanceInput createMarshalInput(InputStream objIn, 
	    InputStream locIn,
	    ClassLoader defaultLoader,
	    boolean verifyCodebaseIntegrity,
	    ClassLoader verifierLoader,
	    Collection context) throws IOException;
    
    /**
     * 
     * @param objOut the output stream to write objects to.
     * @param locOut the output stream to write annotations to.
     * @param context the collection of context information objects or
     *        <code>null</code>
     * @return an instance of MarshalInstanceOutput
     * @throws java.io.IOException if an 
     *         <code>IOException</code> occurs while creating the
     *         MarshalInstanceOutput.
     */
    MarshalInstanceOutput createMarshalOutput(OutputStream objOut, OutputStream locOut, Collection context) throws IOException;
    
}
