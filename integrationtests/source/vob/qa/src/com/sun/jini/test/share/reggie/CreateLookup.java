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
package com.sun.jini.test.share.reggie;

import com.sun.jini.start.ServiceStarter;

import net.jini.core.lookup.ServiceRegistrar;

import java.io.IOException;
import java.rmi.activation.ActivationException;
import java.rmi.Remote;

import java.util.Properties;

/**
 * This class contains the public methods and command line interface for
 * creating an activatable instance of the reggie implementation of the
 * lookup service. This class employs the utility class 
 * <code>com.sun.jini.start.ServiceStarter</code> to start the desired
 * service. That utility class requires that this class define a
 * <code>public static create</code> method which performs all functions
 * that are specific to the desired service, and which can be invoked by the
 * the utility class.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see com.sun.jini.start.ServiceStarter
 */
public class CreateLookup {
    /** The fully-qualified path.classname of the class that defines the
     *  implementation of the backend server of the service that is to be
     *  started
     */
    private static String implClass = RegistrarImpl.class.getName();
    /** The name used to identify the particular resource bundle from which
     *  usage/error message information may be retrieved for display
     *  (helpful with internationalization)
     */
    private static String resourcename = "lookup";
    /**
     * Command line interface for starting an instance of the service
     * referenced in the <code>implClass</code> field
     * <p>
     *
     * @param args <code>String</code> array containing the command line
     *             arguments
     *
     * @see com.sun.jini.start.ServiceStarter
     */
    public static void main(String[] args) {
	//BDJ
//          ServiceStarter.create(args, starterClass, implClass, resourcename);
    }//end main

    /**
     * This method defines how the service referenced in the
     * <code>implClass</code> field of this class is to be started.
     * This method will be invoked through reflection by the utility
     * class <code>com.sun.jini.start.ServiceStarter</code>. This method
     * performs all functions that require knowledge of, or access to,
     * the service-specific information; information that the utility
     * class cannot easily obtain or exploit while attempting to start
     * the desired service.
     * <p>
     * This method returns a proxy that provides client-side access to
     * the backend server of the started service.
     *
     * @param serverStub a <code>Remote</code> reference to the backend server
     *                   of the started service
     * 
     * @return an instance of the public interface to the desired service
     *         referenced in the <code>implClass</code> field of this class;
     *         that is, a proxy that provides client-side access to the
     *         backend server of the started service
     * 
     * @throws java.rmi.activation.ActivationException this is a general
     *         exception thrown by the activation system to indicate a
     *         a number of exceptional conditions that prevent the
     *         activation system from fulfilling a particular request.
     * 
     * @throws java.io.IOException 
     *
     * @see com.sun.jini.start.ServiceStarter
     */
    public static ServiceRegistrar create(Remote serverStub)
                                      throws ActivationException, IOException
    {
	return new RegistrarProxy((Registrar)serverStub,
                                  ((Registrar)serverStub).getServiceID());
    }//end create

}//end class CreateLookup
