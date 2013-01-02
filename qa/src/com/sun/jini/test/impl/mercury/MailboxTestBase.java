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
package com.sun.jini.test.impl.mercury;

import java.util.logging.Level;

// Test harness specific classes


import java.rmi.RemoteException;


import com.sun.jini.constants.TimeConstants;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.TestBase;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

import net.jini.admin.Administrable;
import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;



public abstract class MailboxTestBase extends TestBase implements Test {

    protected static final String MAILBOX_PROPERTY_NAME = "com.sun.jini.test.impl.mercury.serviceInterface";    
    protected static final String MAILBOX_IF_NAME = EventMailbox.class.getName();
    protected static final String PULL_MAILBOX_IF_NAME = PullEventMailbox.class.getName();
    
    protected void parse() throws Exception {
	 super.parse();
    }

    protected EventMailbox[] getMailboxes(int count) 
	throws java.io.IOException, TestException  
    {
	Class[] classes = new Class[count];
	EventMailbox[] mbs = new EventMailbox[count];
	int i = 0;

	for (i = 0; i < count; i++) {
	    classes[i] = EventMailbox.class;
	}

	specifyServices(classes);

	for (i = 0; i < count; i++) {
	    mbs[i] = (EventMailbox)services[i]; 
	}

	return mbs;
    }
    
    protected PullEventMailbox[] getPullMailboxes(int count) 
	throws java.io.IOException, TestException  
    {
	Class[] classes = new Class[count];
	PullEventMailbox[] mbs = new PullEventMailbox[count];
	int i = 0;

	for (i = 0; i < count; i++) {
	    classes[i] = PullEventMailbox.class;
	}

	specifyServices(classes);

	for (i = 0; i < count; i++) {
	    mbs[i] = (PullEventMailbox)services[i]; 
	}

	return mbs;
    }
    
    protected EventMailbox getMailbox() throws java.io.IOException, TestException {
	specifyServices(new Class[]{EventMailbox.class});
	EventMailbox mb = (EventMailbox)services[0];

        if (mb == null)
            throw new TestException ("Got null ref for EventMailbox service");
        logger.log(Level.INFO, "Got reference to EventMailbox service: " + mb);

        return mb;
    }

    protected PullEventMailbox getPullMailbox() throws java.io.IOException, TestException {
	specifyServices(new Class[]{PullEventMailbox.class});
	PullEventMailbox mb = (PullEventMailbox)services[0];

        if (mb == null)
            throw new TestException ("Got null ref for PullEventMailbox service");
        logger.log(Level.INFO, "Got reference to PullEventMailbox service: " + mb);

        return mb;
    }
    
    protected EventMailbox getConfiguredMailbox() throws java.io.IOException, TestException {
	String mbType =
            getConfig().getStringConfigVal(
		MAILBOX_PROPERTY_NAME,
		MAILBOX_IF_NAME);

	logger.log(Level.INFO, "Getting ref to {0}", mbType);
	EventMailbox mb = null;
	if (mbType.equals(MAILBOX_IF_NAME)) {
	    mb = getMailbox();
	} else if (mbType.equals(PULL_MAILBOX_IF_NAME)) {
            mb = getPullMailbox();
	} else {
            throw new TestException(
		"Unsupported mailbox type requested" + mbType);
	}
        return mb;
    }
    
    protected Object getMailboxAdmin(EventMailbox mb) 
        throws java.io.IOException, TestException, ConfigurationException
    {
    
	logger.log(Level.INFO, "\tCalling getMailboxAdmin()");
	Object admin = ((Administrable)mb).getAdmin();
	if (admin == null) {
	    throw new TestException( 
		"Could not get service's Administrable interface");
	}

        Configuration serviceConf = getConfig().getConfiguration();
	ProxyPreparer preparer = new BasicProxyPreparer();
	if (serviceConf instanceof com.sun.jini.qa.harness.QAConfiguration) {
	    preparer = 
		(ProxyPreparer) serviceConf.getEntry("test", 
						     "mercuryAdminPreparer",
						     ProxyPreparer.class);
	}
	logger.log(Level.INFO, "\tadmin proxy preparer: " + preparer);
	admin = preparer.prepareProxy(admin);
	logger.log(Level.INFO, "\tPrepared admin proxy: " + admin);
	
	return admin;
    }	
    
    protected RemoteEventListener getMailboxListener(MailboxRegistration mr) 
        throws java.io.IOException, TestException, ConfigurationException
    {
    
	logger.log(Level.INFO, "\tCalling getMailboxListener()");
	if (mr == null) {
	    throw new TestException( 
		"MailboxRegistration argument cannot be null");
	}

        Configuration serviceConf = getConfig().getConfiguration();
        ProxyPreparer preparer = new BasicProxyPreparer();
        if (serviceConf instanceof com.sun.jini.qa.harness.QAConfiguration) {
            preparer = 
		(ProxyPreparer) serviceConf.getEntry("test", 
						     "mercuryListenerPreparer",
						     ProxyPreparer.class);
        }
	logger.log(Level.INFO, "\tmailbox listener preparer: " + preparer);
	RemoteEventListener proxy = 
	    (RemoteEventListener) preparer.prepareProxy(mr.getListener());
	logger.log(Level.INFO, "\tPrepared mailbox listener proxy: " + proxy);
	
	return proxy;
    }	
    
    protected RemoteEventListener getPullMailboxListener(MailboxPullRegistration mr) 
        throws java.io.IOException, TestException, ConfigurationException
    {
    
	logger.log(Level.INFO, "\tCalling getPullMailboxListener()");
	if (mr == null) {
	    throw new TestException( 
		"MailboxPullRegistration argument cannot be null");
	}

        Configuration serviceConf = getConfig().getConfiguration();
        ProxyPreparer preparer = new BasicProxyPreparer();
        if (serviceConf instanceof com.sun.jini.qa.harness.QAConfiguration) {
            preparer = 
		(ProxyPreparer) serviceConf.getEntry("test", 
						     "mercuryListenerPreparer",
						     ProxyPreparer.class);
        }
	logger.log(Level.INFO, "\tmailbox listener preparer: " + preparer);
	RemoteEventListener proxy = 
	    (RemoteEventListener) preparer.prepareProxy(mr.getListener());
	logger.log(Level.INFO, "\tPrepared mailbox listener proxy: " + proxy);
	
	return proxy;
    }	
    
    protected Lease getMailboxLease(MailboxRegistration mr) 
        throws java.io.IOException, TestException, ConfigurationException
    {
    
	logger.log(Level.INFO, "\tCalling getMailboxLLease()");
	if (mr == null) {
	    throw new TestException( 
		"MailboxRegistration argument cannot be null");
	}

        Configuration serviceConf = getConfig().getConfiguration();
        ProxyPreparer preparer = new BasicProxyPreparer();
        if (serviceConf instanceof com.sun.jini.qa.harness.QAConfiguration) {
            preparer = 
		(ProxyPreparer) serviceConf.getEntry("test", 
						     "mercuryLeasePreparer",
						     ProxyPreparer.class);
        }
	logger.log(Level.INFO, "\tmailbox lease preparer: " + preparer);
	Lease proxy = 
	    (Lease) preparer.prepareProxy(mr.getLease());
	logger.log(Level.INFO, "\tPrepared mailbox lease proxy: " + proxy);
	
	return proxy;
    }	

    protected Lease getPullMailboxLease(MailboxPullRegistration mr) 
        throws java.io.IOException, TestException, ConfigurationException
    {
    
	logger.log(Level.INFO, "\tCalling getPullMailboxLease()");
	if (mr == null) {
	    throw new TestException( 
		"MailboxPullRegistration argument cannot be null");
	}

        Configuration serviceConf = getConfig().getConfiguration();
        ProxyPreparer preparer = new BasicProxyPreparer();
        if (serviceConf instanceof com.sun.jini.qa.harness.QAConfiguration) {
            preparer = 
		(ProxyPreparer) serviceConf.getEntry("test", 
						     "mercuryLeasePreparer",
						     ProxyPreparer.class);
        }
	logger.log(Level.INFO, "\tmailbox lease preparer: " + preparer);
	Lease proxy = 
	    (Lease) preparer.prepareProxy(mr.getLease());
	logger.log(Level.INFO, "\tPrepared mailbox lease proxy: " + proxy);
	
	return proxy;
    }	
    
    protected MailboxRegistration[] getRegistrations(EventMailbox mb, 
						     long[] durations) 
	throws RemoteException, TestException, ConfigurationException
    {
	int count = durations.length;
	MailboxRegistration[] mbrs = new MailboxRegistration[count];
	for (int i = 0; i < count; i++) {
	    mbrs[i] = getRegistration(mb, durations[i]);
	}
	return mbrs;
    }

    protected MailboxPullRegistration[] getPullRegistrations(
        PullEventMailbox mb, long[] durations) 
	throws RemoteException, TestException, ConfigurationException
    {
	int count = durations.length;
	MailboxPullRegistration[] mbrs = new MailboxPullRegistration[count];
	for (int i = 0; i < count; i++) {
	    mbrs[i] = getPullRegistration(mb, durations[i]);
	}
	return mbrs;
    }
    
    protected MailboxRegistration getRegistration(EventMailbox mb, 
						  long duration) 
	throws RemoteException, TestException, ConfigurationException
    {
	MailboxRegistration mr = null;
	try {
	     mr = mb.register(duration);
	} catch (LeaseDeniedException lde) {
	     throw new TestException ("MailboxRegistration request was denied.");
	}
	if (mr == null)
	     throw new TestException ("Got null ref for MailboxRegistration object");
	logger.log(Level.INFO, 
		   "Got reference to MailboxRegistration object: " + mr);
	
        Configuration serviceConf = getConfig().getConfiguration();
        ProxyPreparer preparer = new BasicProxyPreparer();
        if (serviceConf instanceof com.sun.jini.qa.harness.QAConfiguration) {
            preparer = 
		(ProxyPreparer) serviceConf.getEntry("test", 
						     "mercuryRegistrationPreparer",
						     ProxyPreparer.class);
        }
	logger.log(Level.INFO, "\tregistration proxy preparer: " + preparer);
	mr = (MailboxRegistration)preparer.prepareProxy(mr);
	logger.log(Level.INFO, "\tPrepared registration proxy: " + mr);
	
	return mr;
    }

    protected MailboxPullRegistration getPullRegistration(
        PullEventMailbox mb, long duration) 
	throws RemoteException, TestException, ConfigurationException
    {
	MailboxPullRegistration mr = null;
	try {
	     mr = mb.pullRegister(duration);
	} catch (LeaseDeniedException lde) {
	     throw new TestException ("MailboxPullRegistration request was denied.");
	}
	if (mr == null)
	     throw new TestException ("Got null ref for MailboxPullRegistration object");
	logger.log(Level.INFO, 
		   "Got reference to MailboxPullRegistration object: " + mr);
	
        Configuration serviceConf = getConfig().getConfiguration();
        ProxyPreparer preparer = new BasicProxyPreparer();
        if (serviceConf instanceof com.sun.jini.qa.harness.QAConfiguration) {
            preparer = 
		(ProxyPreparer) serviceConf.getEntry("test", 
						     "mercuryRegistrationPreparer",
						     ProxyPreparer.class);
        }
	logger.log(Level.INFO, "\tregistration proxy preparer: " + preparer);
	mr = (MailboxPullRegistration)preparer.prepareProxy(mr);
	logger.log(Level.INFO, "\tPrepared registration proxy: " + mr);
	
	return mr;
    }

    protected void checkLease(Lease l, long duration) throws TestException {
	if (!leaseRequestOK(l, duration)) 
	    throw new TestException ("Lease request for " + duration + "  not granted");
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
