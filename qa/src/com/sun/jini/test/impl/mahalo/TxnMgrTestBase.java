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
package com.sun.jini.test.impl.mahalo;

import java.util.logging.Level;

// Test harness specific classes


import java.rmi.RemoteException;


import com.sun.jini.constants.TimeConstants;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.TestBase;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

import net.jini.admin.Administrable;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionManager.Created;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;



public abstract class TxnMgrTestBase extends TestBase {

    protected void parse() throws Exception {
	 super.parse();
    }

    protected TransactionManager[] getTransactionManagers(int count) 
	throws java.io.IOException, TestException  
    {
	Class[] classes = new Class[count];
	TransactionManager[] mbs = new TransactionManager[count];
	int i = 0;

	for (i = 0; i < count; i++) {
	    classes[i] = TransactionManager.class;
	}

	specifyServices(classes);

	for (i = 0; i < count; i++) {
	    mbs[i] = (TransactionManager)services[i]; 
	}

	return mbs;
    }

    protected TransactionManager getTransactionManager() 
	throws java.io.IOException, TestException 
    {
	specifyServices(new Class[]{TransactionManager.class});
	TransactionManager mb = (TransactionManager)services[0];

        if (mb == null)
            throw new TestException ("No TransactionManager service");
        logger.log(Level.INFO, "Got reference to TransactionManager: " + mb);

        return mb;
    }
    
    protected Object getTransactionManagerAdmin(TransactionManager mb) 
        throws java.io.IOException, TestException, ConfigurationException
    {
    
	logger.log(Level.INFO, "\tCalling getTransactionManagerAdmin()");
	Object admin = ((Administrable)mb).getAdmin();
	if (admin == null) {
	    throw new TestException("Could not get service's "
				  + "Administrable interface");
	}

        Configuration serviceConf = getConfig().getConfiguration();
	ProxyPreparer preparer = new BasicProxyPreparer();
	if (serviceConf instanceof com.sun.jini.qa.harness.QAConfiguration) {
	    preparer = 
		(ProxyPreparer) serviceConf.getEntry("test", 
						     "mahaloAdminPreparer",
						     ProxyPreparer.class);
	}
	admin = preparer.prepareProxy(admin);
	return admin;
    }	
    
    protected Lease getTransactionManagerLease(TransactionManager.Created mr) 
        throws java.io.IOException, TestException, ConfigurationException
    {
    
	logger.log(Level.INFO, "\tCalling getTransactionManagerLease()");
	if (mr == null) {
	    throw new TestException("Created argument cannot be null");
	}

        Configuration serviceConf = getConfig().getConfiguration();
	ProxyPreparer preparer = new BasicProxyPreparer();
	if (serviceConf instanceof com.sun.jini.qa.harness.QAConfiguration) {
	    preparer = 
		(ProxyPreparer) serviceConf.getEntry("test", 
						     "mahaloLeasePreparer",
						     ProxyPreparer.class);
	}
	Lease proxy = (Lease) preparer.prepareProxy(mr.lease);
	return proxy;
    }	

    protected Created[] getCreateds(TransactionManager mb, long[] durations) 
	throws RemoteException, TestException, ConfigurationException
    {
	int count = durations.length;
	Created[] mbrs = new Created[count];
	for (int i = 0; i < count; i++) {
	    mbrs[i] = getCreated(mb, durations[i]);
	}
	return mbrs;
    }

    protected Created getCreated(TransactionManager mb, long duration) 
	throws RemoteException, TestException, ConfigurationException
    {
	Created mr = null;
	try {
	     mr = mb.create(duration);
	} catch (LeaseDeniedException lde) {
	     throw new TestException ("Created request was denied.");
	}
	if (mr == null)
	     throw new TestException ("Got null ref for Created object");
	logger.log(Level.INFO, "Got reference to Created object: " + mr);
	
	return mr;
    }

    protected void checkLease(Lease l, long duration) throws TestException {
	if (!leaseRequestOK(l, duration)) 
	    throw new TestException (
		"Lease request for " + duration + "  not granted");
	logger.log(Level.INFO, "Lease request for " + duration + "  granted");
    }

    private boolean leaseRequestOK(Lease l, long durationRequest) {
        // Check for "any" request
	if(durationRequest == Lease.ANY) // any lease is acceptable
	    return true;

	long actualExpiration = l.getExpiration();
	long desiredExpiration = System.currentTimeMillis() + durationRequest;

	// Check for "addition" overflow
	if (desiredExpiration < 0)
	    desiredExpiration =  Long.MAX_VALUE;

        return (actualExpiration <= desiredExpiration); 
    }
}
