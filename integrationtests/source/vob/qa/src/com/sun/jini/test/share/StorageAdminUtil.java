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

package com.sun.jini.test.share;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;

import com.sun.jini.admin.StorageLocationAdmin;

import net.jini.admin.Administrable;
import java.rmi.RemoteException;

/**
 * This class contains a set of static methods that provide general-purpose
 * functions related to the administration of the location to which a 
 * stores its persistent state. This utility class is intended to be
 * useful to all categories of tests that wish to perform storage location
 * administration.
 *
 * @see net.jini.admin.Administrable
 * @see com.sun.jini.admin.StorageLocationAdmin
 */
public class StorageAdminUtil {
    /** Determines if the input object implements both the 
     *  <code>net.jini.admin.Administrable</code> and the
     *  <code>com.sun.jini.admin.StorageLocationAdmin</code> interfaces,
     *  and if yes, returns a proxy to an instance of the
     *  <code>com.sun.jini.admin.StorageLocationAdmin</code> interface.
     * 
     *  @param serviceObj instance of an object that implements both
     *                    <code>net.jini.admin.Administrable</code> and
     *                    <code>com.sun.jini.admin.StorageLocationAdmin</code>,
     *                    whose implementation can be used to administer the
     *                    location in which a service stores its persistent
     *                    state.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         <code>null</code> is input to the <code>serviceObj</code>
     *         parameter.
     * @throws java.lang.ClassNotFoundException this exception occurs when
     *         while attempting to load either of the necessary administration
     *         interfaces, no definition of the interface(s) could be found.
     * @throws com.sun.jini.qa.harness.TestException this exception occurs
     *         when the input parameter does not implement either of the
     *         necessary administration interfaces.
     *
     *  @return a <code>com.sun.jini.admin.StorageLocationAdmin</code> instance
     *          if the input object is administrable and implements the
     *          <code>com.sun.jini.admin.StorageLocationAdmin</code> interface.
     */
    public static StorageLocationAdmin getStorageLocationAdmin
                                                            (Object serviceObj)
                                               throws ClassNotFoundException,
                                                      RemoteException,
                                                      TestException
    {
        if(serviceObj == null) {
            throw new NullPointerException("null admin object input to "
                                           +"getStorageAdmin()");
        }
        /* Test that the service implements the appropriate admin interfaces */
        Class administrableClass
                             = Class.forName("net.jini.admin.Administrable");
        Class storageAdminClass
                   = Class.forName("com.sun.jini.admin.StorageLocationAdmin");
        if( !administrableClass.isAssignableFrom(serviceObj.getClass()) ) {
            throw new TestException("the service under test "
                                      +"does not implement the "
                                      +"net.jini.admin.Administrable "
                                      +"interface");
        }
        Object admin = ((Administrable)serviceObj).getAdmin();
        if( !storageAdminClass.isAssignableFrom(admin.getClass()) ) {
            throw new TestException("the service under test "
                                      +"does not implement the "
                                    +"com.sun.jini.admin.StorageLocationAdmin "
                                      +"interface");
        }
        return ((StorageLocationAdmin)admin);
    }//end getStorageAdmin

} //end class StorageAdminUtil


