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

import java.io.IOException;
import java.rmi.RemoteException;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully change the location in which the service currently stores
 * its persistent state.
 *
 */
public class SetStorageLocation extends AbstractBaseTest {

    private String expectedLocation = null;

    /** Constructs an instance of this class. Initializes this classname */
    public SetStorageLocation() {
        subCategories = new String[] {"fiddlerstorageadmin"};
    }//end constructor

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, retrieves the location to which the service
     *     is currently storing its persistent state and constructs a new
     *     location different from the current location.
     *  3. Through the admin, changes to a new location the current
     *     location to which the service is currently storing its
     *     persistent state
     *  4. Through the admin, retrieves the location and determines if
     *     the new location is equivalent to expected location
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
            String oldLocation = locAdmin.getStorageLocation();
            logger.log(Level.FINE, ""
                              +": oldLocation = "+oldLocation);
            expectedLocation = oldLocation+"_New";
            logger.log(Level.FINE, ""
                              +": expectedLocation = "+expectedLocation);
            locAdmin.setStorageLocation(expectedLocation);
            String newLocation = locAdmin.getStorageLocation();
            logger.log(Level.FINE, ""+": newLocation = "+newLocation);
            if(!expectedLocation.equals(newLocation)) {
                throw new TestException(
                               "new location not equal to expected location");
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
        } catch (IOException e) {
            throw new TestException(
                                 "IOException encountered while attempting to "
                                 +"set the new storage location\n"
                                 +e.toString());
	}
    }//end run

} //end class SetStorageLocation


