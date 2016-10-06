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
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.export.ServiceProxyAccessor;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.lookupservice.QATestRegistrar;

/** This class is used to test that every service item registered with
 *  the Lookup service can be successfully looked up using only the 
 *  super classes it extends (excluding the universally-extended Object class).
 *
 *  @see org.apache.river.qa.harness.TestEnvironment
 *  @see org.apache.river.test.spec.lookupservice.QATestRegistrar
 *  @see org.apache.river.test.spec.lookupservice.QATestUtils
 */
public class DefLookupBySuper extends QATestRegistrar {

    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private ServiceTemplate[][] superClassTmpls;
    private ServiceRegistrar proxy;
    private int nClasses = 0;
    private int nInstances = 0;
    private int nInstancesPerClass = 0;
    private int maxChainLen = 0;
    private int[] chainLen;


    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Loads and instantiates all service 
     *  classes; then registers each service class instance with the maximum
     *  service lease duration. For each registered service, retrieves the
     *  "chain" of super classes and creates an array of ServiceTemplates 
     *  in which each element contains one of the retrieved super classes 
     *  (excluding the Object class).
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        int i,j,k;
        boolean exit_loop;
        int indx;

	super.construct(sysConfig); 

	logger.log(Level.FINE, "in setup() method.");

        nClasses = super.getNTestClasses();
        nInstances = super.getNInstances();
        nInstancesPerClass = super.getNInstancesPerClass();
        maxChainLen  = super.MAX_N_SUPER_CHAIN_LEN;

	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	srvcRegs = super.registerAll();
	proxy = super.getProxy();

	superClassTmpls = new ServiceTemplate[nClasses][maxChainLen];

	Class sClass[] = new Class[maxChainLen];
	chainLen = new int[nClasses];
	for(i=0; i<nClasses; i++) {
	    Class c = Class.forName(TEST_SRVC_CLASSES[i]);
	    /* build the super class "chain" corresponding to class i */
	    sClass[0] = c;
	    for (j=1,exit_loop=false;
		 ((exit_loop==false)&&(j<maxChainLen));j++) {
		sClass[j] = sClass[j-1].getSuperclass();
		if ( !sClass[j].isAssignableFrom(Object.class) ) {
		    Class[] superClassType = {sClass[j]};
		    superClassTmpls[i][j] = new ServiceTemplate
			(null,superClassType,null);
		} else {
		    exit_loop = true;
		    chainLen[i] = j;
		}
	    }
	}
        return this;
    }

    /** Executes the current QA test.
     *
     *  For each registered service:  
     *     For each super class in the set of super classes:  
     *        Performs a match lookup using the corresponding template 
     *        created during construct and then tests that the number of matches
     *        found equals the number of matches expected; and that the set
     *        of objects returned equals the expected set of corresponding
     *        service items.
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	logger.log(Level.FINE, "in run() method.");
	Object [] superM;

	for (int i=0; i<nClasses; i++) {
	    /** Loop from j = 1 because the chain starts with the class
	     *  itself; and this test is testing only its super classes
	     */
	    for (int j=1; j<chainLen[i];j++) {
		if (super.expectedNMatchesSuper[i][j] == 0)  continue;
		superM = proxy.lookUp(superClassTmpls[i][j],
				      Integer.MAX_VALUE);
		if(superM.length != expectedNMatchesSuper[i][j]) {
		    String message = 
			"totalMatches ("+ superM.length +
			") != expectedMatches[" +
			i + "]["+ j +
			"] ("+expectedNMatchesSuper[i][j]+")";
		    throw new TestException(message);
		} else {
		    if (!setsAreEqual(i,superM)) {
			throw new TestException
			    ("At index "+i+", the services do NOT match");
		    }
		}
	    }
	}
    }

    /** Tests for equality between the returned set of services and the 
     *  expected set of services
     */
    private boolean setsAreEqual(int            classIndx,
                                 Object [] srvcM) throws TestException
    {
        int     s0,si;
        int     n;
        int     i,j,k;
        n  = classIndx - (classIndx%chainLen[classIndx]);
        s0 = nInstancesPerClass*n;

        iLoop:
            for (i=0; i<srvcM.length; i++) {
                si = s0;
                for (j=0; (j<srvcM.length/nInstancesPerClass); j++) 
		{
                    si = si+(nInstancesPerClass*j);
                    for (k=0; k<nInstancesPerClass; k++) 
		    {
			Object service = null;
			try {
			    service = ((ServiceProxyAccessor)srvcM[i]).getServiceProxy();
			} catch (RemoteException ex) {
			    throw new TestException(
				"RemoteException thrown when attempting to obtain service proxy", ex);
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

