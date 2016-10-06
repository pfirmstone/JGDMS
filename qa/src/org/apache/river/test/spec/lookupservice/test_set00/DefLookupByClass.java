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
package org.apache.river.test.spec.lookupservice.test_set00;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.export.ServiceProxyAccessor;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.lookupservice.QATestRegistrar;

/** This class is used to test that every service item registered with
 *  the Lookup service can be successfully looked up using only its class type.
 *
 *  @see org.apache.river.qa.harness.TestEnvironment
 *  @see org.apache.river.test.spec.lookupservice.QATestRegistrar
 *  @see org.apache.river.test.spec.lookupservice.QATestUtils
 */
public class DefLookupByClass extends QATestRegistrar {

    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private ServiceTemplate[] exactClassTmpls;
    private ServiceRegistrar proxy;
    private int nClasses = 0;
    private int nInstances = 0;
    private int nInstancesPerClass = 0;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Loads and instantiates all service 
     *  classes; then registers each service class instance with the maximum
     *  service lease duration. Creates an array of ServiceTemplates in 
     *  which each element contains the class type of one of the registered
     *  services.
     * @param sysConfig
     * @return 
     * @throws java.lang.Exception
     */
    @Override
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
        nClasses = super.getNTestClasses();
        nInstances = super.getNInstances();
        nInstancesPerClass = super.getNInstancesPerClass();
	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	srvcRegs = super.registerAll();
	proxy = super.getProxy();
	exactClassTmpls = new ServiceTemplate[nClasses];
	for(int i=0; i<nClasses; i++) {
	    Class c = Class.forName(TEST_SRVC_CLASSES[i]);
	    Class[] exactClassType = {c};
	    exactClassTmpls[i] = new ServiceTemplate(null,
						     exactClassType,null);
	}
        return this;
    }

    /** Executes the current QA test.
     *
     *  For each service registered:  
     *      Performs a match lookup using the corresponding template 
     *      created during construct and then tests that the number of matches
     *      found equals the number of matches expected; and that the set
     *      of objects returned equals the expected set of corresponding
     *      service items.
     * @throws java.lang.Exception
     */
    @Override
    public void run() throws Exception {
	Object [] exactM;
	for (int i=0; i<nClasses; i++) {
	    exactM = proxy.lookUp(exactClassTmpls[i],Integer.MAX_VALUE);
	    if (exactM.length != expectedNMatchesExact[i]) {
		throw new TestException
		    ("totalMatches ("+exactM.length+
		     ") != expectedMatches["+
		     i+"] ("+expectedNMatchesExact[i]+")");
	    } else {
		if (!setsAreEqual(i,exactM)) {
		    throw new TestException
			("At index "+i+", the services do NOT match");
		}
	    }
	}
    }

    /** Tests for equality between the returned set of services and the 
     *  expected set of services
     */
    private boolean setsAreEqual(int classIndx, Object [] srvcM) throws TestException
    {
        int     s0,si;
        int     i,j,k;
        s0 = nInstancesPerClass*classIndx;

        iLoop:
            for (i=0; i<srvcM.length; i++) 
	    {
                si = s0;
                for (j=0;(j<srvcM.length/nInstancesPerClass); j++) 
		{
                    si = si+(nInstancesPerClass*j);
                    for (k=0; k<nInstancesPerClass; k++) 
		    {
			Object service;
			try {
			    service = 
				((ServiceProxyAccessor)srvcM[i]).getServiceProxy();
			} catch (RemoteException ex) {
			    throw new TestException(
			    "RemoteException occured while attempting to retrive service proxy", ex);
			}
                        if ((srvcItems[si+k].service.equals(service)))
                        {
                           continue iLoop;
	                }
		    }
	        }
                return false;
            }
        return true; /* success */
    }
}
