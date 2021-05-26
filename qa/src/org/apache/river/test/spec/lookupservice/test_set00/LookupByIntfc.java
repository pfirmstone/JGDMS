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
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;

import org.apache.river.test.spec.lookupservice.QATestRegistrar;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceTemplate;
import java.rmi.RemoteException;

/** This class is used to test that every service item registered with
 *  the Lookup service can be successfully looked up using only the 
 *  interfaces it implements (excluding the universally-implemented
 *  Serializable interface).
 *
 *  @see org.apache.river.qa.harness.TestEnvironment
 *  @see org.apache.river.test.spec.lookupservice.QATestRegistrar
 *  @see org.apache.river.test.spec.lookupservice.QATestUtils
 */
public class LookupByIntfc extends QATestRegistrar {

    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private ServiceTemplate[][] intfcClassTmpls;
    private ServiceRegistrar proxy;
    private int nClasses = 0;
    private int nInstances = 0;
    private int nInstancesPerClass = 0;
    private int maxNIntfcPerClass = 0;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Loads and instantiates all service 
     *  classes; then registers each service class instance with the maximum
     *  service lease duration. Creates an array of ServiceTemplates in 
     *  which each element contains one of the interfaces, implemented
     *  by the corresponding service, from the set of test interfaces.
     */
    public synchronized Test construct(QAConfig sysConfig) throws Exception {
        int i,j,k;
        int indx;
        Class sClass;
        Class iClass[] = new Class[super.INTERFACES.length];

        super.construct(sysConfig); 

	logger.log(Level.FINE, "in setup() method.");

        nClasses = super.getNTestClasses();
        nInstances = super.getNInstances();
        nInstancesPerClass = super.getNInstancesPerClass();
        maxNIntfcPerClass  = super.MAX_N_INTFC_PER_CLASS;

	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	srvcRegs = super.registerAll();
	proxy = super.getProxy();

	intfcClassTmpls = new ServiceTemplate[nClasses][maxNIntfcPerClass];
	sClass = Class.forName("java.io.Serializable");
	for (i=0;i<super.INTERFACES.length;i++) {
	    iClass[i] = Class.forName(super.INTERFACES[i]);
	}
	for(i=0; i<nClasses; i++) {
	    Class c = Class.forName(TEST_SRVC_CLASSES[i]);
	    Class[] intfcClassTypes = c.getInterfaces();
	    int nInterfaces = intfcClassTypes.length;
	    for (j=0;j<nInterfaces;j++) {
		if(!intfcClassTypes[j].isAssignableFrom(sClass)) {
		    indx = 0;
		    for (k=0;k<super.INTERFACES.length;k++) {
			if(intfcClassTypes[j].isAssignableFrom(iClass[k])){
			    indx = k;
			    break;
			}
		    }
		    Class[] classTypeArray = {intfcClassTypes[j]};
		    intfcClassTmpls[i][k] = new ServiceTemplate
			(null,classTypeArray,null);
		}
	    }
	}
        return this;
    }

    /** Executes the current QA test.
     *
     *  For each registered service:  
     *     For each interface in the set of test interfaces:  
     *        Performs a match lookup using the corresponding template 
     *        created during construct and then tests that the number of matches
     *        found equals the number of matches expected; and that the set
     *        of objects returned equals the expected set of corresponding
     *        service items.
     */
    public synchronized void run() throws Exception {
	logger.log(Level.FINE, "in run() method.");
	ServiceMatches intfcM = null;

	for (int i=0; i<nClasses; i++) {
	    for (int j=0; j<super.INTERFACES.length;j++) {
		if (super.INTFC_IMPL_MATRIX[i][j] == 0)  continue;
                    intfcM = proxy.lookup(intfcClassTmpls[i][j],
                                          Integer.MAX_VALUE);
		if(intfcM.totalMatches != expectedNMatchesIntfc[i][j]) {
		    String message = "totalMatches ("+
			intfcM.totalMatches+") != expectedMatches["+
			i+"]["+j+"] ("+expectedNMatchesIntfc[i][j]+")";
		    throw new TestException(message);
		} else {
		    if (!setsAreEqual(j,intfcM)) {
			throw new TestException
			    ("At index "+j+", the services do NOT match");
		    }
		}
	    }
	}
    }

    /** Tests for equality between the returned set of services and the 
     *  expected set of services
     */
    private boolean setsAreEqual(int            intfcIndx,
                                 ServiceMatches srvcM)
    {
        int     si;
        int     i,j,k;
        boolean inSet;
        for (i=0; i<srvcM.totalMatches; i++) {
            inSet = false;
            for (j=0; (   (inSet==false)
                        &&(j<srvcM.totalMatches/nInstancesPerClass)); j++) {
                si = nInstancesPerClass*super.INTFC_TO_SI[intfcIndx][j];
                for (k=0; ((inSet==false)&&(k<nInstancesPerClass)); k++) {
                    if ((srvcItems[si+k].service.equals
                                                     (srvcM.items[i].service)))
                    {
                        inSet = true;
                    }
		}
	    }
            if (inSet == false) return false;
        }
        return true; /* success */
    }
}
