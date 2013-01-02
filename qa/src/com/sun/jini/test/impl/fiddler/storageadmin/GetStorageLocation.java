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

package com.sun.jini.test.impl.fiddler.storageadmin;

import java.util.logging.Level;

import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest;

import com.sun.jini.test.share.StorageAdminUtil;

import com.sun.jini.qa.harness.TestException;

import com.sun.jini.admin.StorageLocationAdmin;

import net.jini.discovery.LookupDiscoveryService;

import java.rmi.RemoteException;
import com.sun.jini.qa.harness.AbstractServiceAdmin;
import com.sun.jini.qa.harness.Test;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully return the location in which the service currently stores
 * its persistent state.
 *
 */
public class GetStorageLocation extends AbstractBaseTest {

    private String expectedLocation = null;

    /** Constructs an instance of this class. Initializes this classname */
    public GetStorageLocation() {
        subCategories = new String[] {"fiddlerstorageadmin"};
    }//end constructor

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service, and then retrieves from the
     *  tests's initial configuration, the location to which the service
     *  is expected to store its persistent state.
     */
    public Test construct(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        if (getManager().getAdmin(discoverySrvc) == null) {
	    return this;
        }
        expectedLocation = ((AbstractServiceAdmin) (getManager().getAdmin(discoverySrvc))).getLogDir();
        logger.log(Level.FINE, ""
                          +": expectedLocation = "+expectedLocation);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, retrieves the location to which the service
     *     is currently storing its persistent state.
     *  3. Determines if the location retrieved through the admin is
     *     equivalent to the expected location.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, ""+": run()");
        if(discoverySrvc == null) {
            throw new TestException(
                                 "could not successfully start the service "
                                 +serviceName);
        }
	try {
            StorageLocationAdmin locAdmin
                    = StorageAdminUtil.getStorageLocationAdmin(discoverySrvc);
            String curLocation = locAdmin.getStorageLocation();
            logger.log(Level.FINE, ""+": curLocation = "+curLocation);
            if(!expectedLocation.equals(curLocation)) {
                throw new TestException(
                           "current location not equal to expected location");
            }
            return;
        } catch (ClassNotFoundException e) {
            throw new TestException(
                         "problems loading either the interface "
                        +"net.jini.admin.Administrable, or the "
                        +"interface com.sun.jini.admin.StorageLocationAdmin");
        } catch (TestException e) {
            throw new TestException(e.toString());
	} catch (RemoteException e) {
	    logger.log(Level.INFO, "Test terminated prematurely due to RemoteException");
            e.printStackTrace();
	    throw new TestException( "Unexpected Exception -- "
                                              +e.toString());
	}
    }//end run

} //end class GetStorageLocation


