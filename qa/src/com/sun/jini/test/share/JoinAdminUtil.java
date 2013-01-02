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

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;

import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import java.rmi.RemoteException;

/**
 * This class contains a set of static methods that provide general-purpose
 * functions related to the administration of a service's participation in
 * the join protocol. This utility class is intended to be useful to all
 * categories of tests that wish to perform join administration.
 *
 * @see net.jini.admin.Administrable
 * @see net.jini.admin.JoinAdmin
 */
public class JoinAdminUtil {
    /** Determines if the input object implements both the 
     *  <code>net.jini.admin.Administrable</code> and the
     *  <code>net.jini.admin.JoinAdmin</code> interfaces, and
     *  if yes, returns a proxy to an instance of the
     *  <code>net.jini.admin.JoinAdmin</code> interface.
     * <p>
     * The returned object is prepared using the preparer obtained
     * from the test configuration using the default preparar name
     * for the service. The only tests which calls this utility
     * are fiddler tests, so this method unconditionally prepares
     * the proxy using the <code>test.fiddlerAdminPreparer</code>
     * entry. As a safeguard, an <code>IllegalArgumentException</code>
     * is thrown if the service argument is not an instance of
     * <code>net.jini.discovery.LookupDiscoveryService</code>.
     * 
     *  @param serviceObj instance of an object that implements both
     *                    <code>net.jini.admin.Administrable</code> and
     *                    <code>net.jini.admin.JoinAdmin</code>, whose
     *                    implementation can be used to administer a
     *                    service's participation in the join protocol.
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
     *  @return an instance of <code>net.jini.admin.JoinAdmin</code>
     *          if the input object is administrable and implements the
     *          <code>net.jini.admin.JoinAdmin</code> interface.
     */
    public static JoinAdmin getJoinAdmin(Object serviceObj)
                                               throws ClassNotFoundException,
                                                      RemoteException,
                                                      TestException
    {
        if(serviceObj == null) {
            throw new NullPointerException("null admin object input to "
                                           +"getJoinAdmin()");
        }
        /* Test that the service implements the appropriate admin interfaces */
        Class administrableClass
                             = Class.forName("net.jini.admin.Administrable");
        Class joinAdminClass = Class.forName("net.jini.admin.JoinAdmin");
        if( !administrableClass.isAssignableFrom(serviceObj.getClass()) ) {
            throw new TestException("the service under test "
                                      +"does not implement the "
                                      +"net.jini.admin.Administrable "
                                      +"interface");
        }
        Object admin = ((Administrable)serviceObj).getAdmin();
        if( !joinAdminClass.isAssignableFrom(admin.getClass()) ) {
            throw new TestException("the service under test "
                                      +"does not implement the "
                                      +"net.jini.admin.JoinAdmin "
                                      +"interface");
        }
	admin = QAConfig.getConfig().prepare("test.fiddlerAdminPreparer",
						  admin);
        return ((JoinAdmin)admin);
    }//end getJoinAdmin

} //end class JoinAdminUtil


