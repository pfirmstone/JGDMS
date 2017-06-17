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

package org.apache.river.test.share;

import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;

import org.apache.river.admin.FiddlerAdmin;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.security.ProxyPreparer;

import net.jini.admin.Administrable;
import java.rmi.RemoteException;

/**
 * This class contains a set of static methods that provide general-purpose
 * functions related to the administration of the fiddler implementation of
 * the lookup discovery service. This utility class is intended to be
 * useful to all categories of tests that wish to administer instances of
 * that particular implementation of that service.
 *
 * @see net.jini.admin.Administrable
 * @see org.apache.river.admin.FiddlerAdmin
 */
public class FiddlerAdminUtil {

    /** Determines if the input object implements both the 
     *  <code>net.jini.admin.Administrable</code> and the
     *  <code>org.apache.river.admin.FiddlerAdmin</code> interfaces,
     *  and if yes, returns a proxy to an instance of the
     *  <code>org.apache.river.admin.FiddlerAdmin</code> interface.
     * 
     *  @param serviceObj instance of an object that implements both
     *                    <code>net.jini.admin.Administrable</code> and
     *                    <code>org.apache.river.admin.FiddlerAdmin</code>,
     *                    whose implementation can be used to administer the
     *                    fiddler implementation of the lookup discovery 
     *                    service.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         <code>null</code> is input to the <code>serviceObj</code>
     *         parameter.
     * @throws java.lang.ClassNotFoundException this exception occurs when
     *         while attempting to load either of the necessary administration
     *         interfaces, no definition of the interface(s) could be found.
     * @throws org.apache.river.qa.harness.TestException this exception occurs
     *         when the input parameter does not implement either of the
     *         necessary administration interfaces.
     *
     *  @return a <code>org.apache.river.admin.FiddlerAdmin</code> instance
     *          if the input object is administrable and implements the
     *          <code>org.apache.river.admin.FiddlerAdmin</code> interface.
     */
    public static FiddlerAdmin getFiddlerAdmin(Object serviceObj)
                                               throws ClassNotFoundException,
                                                      RemoteException,
                                                      TestException
    {
        if(serviceObj == null) {
            throw new NullPointerException("null admin object input to "
                                           +"getFiddlerAdmin()");
        }
        /* Test that the service implements the appropriate admin interfaces */
        Class administrableClass
                             = Class.forName("net.jini.admin.Administrable");
        Class fiddlerAdminClass
                   = Class.forName("org.apache.river.admin.FiddlerAdmin");
        if( !administrableClass.isAssignableFrom(serviceObj.getClass()) ) {
            throw new TestException("the service under test "
                                      +"does not implement the "
                                      +"net.jini.admin.Administrable "
                                      +"interface");
        }
        Object admin = ((Administrable)serviceObj).getAdmin();
        if( !fiddlerAdminClass.isAssignableFrom(admin.getClass()) ) {
            throw new TestException("the service under test "
                                      +"does not implement the "
                                      +"org.apache.river.admin.FiddlerAdmin "
                                      +"interface");
        }
	Configuration c = QAConfig.getConfig().getConfiguration();
	if (!(c instanceof org.apache.river.qa.harness.QAConfiguration)) { // if none configuration
	    return (FiddlerAdmin) admin;
	}
	try {
	    ProxyPreparer p = (ProxyPreparer) c.getEntry("test",
							 "fiddlerAdminPreparer", 
							 ProxyPreparer.class);
	    return ((FiddlerAdmin) p.prepareProxy(admin));
	} catch (ConfigurationException e) {
	    throw new TestException("Configuration Error", e);
	}
    }//end getFiddlerAdmin

} //end class FiddlerAdminUtil


