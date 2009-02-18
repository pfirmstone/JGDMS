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
package com.sun.jini.test.spec.servicediscovery.discovery;

import java.util.logging.Level;

// java packages
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.rmi.RMISecurityManager;
import java.rmi.activation.ActivationException;

// Test harness imports
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATest;

// jini packages
import net.jini.admin.Administrable;
import net.jini.discovery.DiscoveryEvent;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscovery;
import com.sun.jini.admin.DestroyAdmin;
import com.sun.jini.start.ServiceStarter;
import net.jini.lookup.DiscoveryAdmin;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceID;

public class Discovery extends QATest {


    private class Ignorer implements DiscoveryListener {

        public Ignorer() {
        }

        public void discovered(DiscoveryEvent e) {
            ServiceRegistrar[] regs = e.getRegistrars();
            logger.log(Level.INFO, "discovered " + regs.length + " instance(s)");

            // new Throwable().printStackTrace();
        }

        public void discarded(DiscoveryEvent e) {
            ServiceRegistrar[] regs = e.getRegistrars();
            logger.log(Level.INFO, "discarded " + regs.length + " instance(s)");

            // new Throwable().printStackTrace();
        }
    }


    private class Discarder implements DiscoveryListener {
        ServiceID id;
        LookupDiscovery disc;
        public boolean discovery = false;
        public boolean discardery = false;
        public boolean unexpected = false;

        public Discarder(ServiceID id, LookupDiscovery disc) {
            this.id = id;
            this.disc = disc;
        }

        public void discovered(DiscoveryEvent e) {
            ServiceRegistrar[] regs = e.getRegistrars();
            logger.log(Level.INFO, "discovered " + regs.length
                    + " instance(s) - discarding");

            for (int i = 0; i < regs.length; i++) {
                if (id.equals(regs[i].getServiceID())) {
                    discovery = true;
                } else {
                    unexpected = true;
                }
                disc.discard(regs[i]);
            }

            // new Throwable().printStackTrace();
        }

        public void discarded(DiscoveryEvent e) {
            ServiceRegistrar[] regs = e.getRegistrars();
            logger.log(Level.INFO, 
		       "discarded " + regs.length + " instance(s) OK");

            for (int i = 0; i < regs.length; i++) {
                if (id.equals(regs[i].getServiceID())) {
                    discardery = true;
                } else {
                    unexpected = true;
                }
            }

            // new Throwable().printStackTrace();
        }
    }

    public void run() throws Exception {
        LookupDiscovery disc = null;
        DiscoveryAdmin admin = null;

	// Create a LookupDiscovery object.
	disc = new LookupDiscovery(Util.makeGroups("start", 700),
				   getConfig().getConfiguration());
	logger.log(Level.INFO, 
		   "constructed with " + disc.getGroups().length + " elements");
	Thread.sleep(10000);
	disc.addDiscoveryListener(new Ignorer());
	ServiceRegistrar reg = createLookup();
	admin = getAdmin(reg);
	String[] actualGroups = new String[] {
	    reg.getServiceID().toString() };
	admin.setMemberGroups(actualGroups);
	disc.addGroups(Util.makeGroups("added", 200));
	logger.log(Level.INFO, "increased to " + disc.getGroups().length
		   + " elements");
	Thread.sleep(10000);
	disc.removeGroups(Util.makeGroups("start", 300));
	logger.log(Level.INFO, 
		   "reduced to " + disc.getGroups().length + " elements");
	Thread.sleep(10000);
	disc.setGroups(Util.makeGroups("toasty", 100));
	logger.log(Level.INFO, 
		   "changed to " + disc.getGroups().length + " elements");
	Thread.sleep(10000);
	disc.setGroups(DiscoveryGroupManagement.ALL_GROUPS);
	logger.log(Level.INFO, "set to ALL_GROUPS");
	Thread.sleep(60000);

	try {
	    disc.addGroups(actualGroups);
	    throw new TestException("addGroups to null didn't throw anything");
	} catch (UnsupportedOperationException e) {
	    // expected
	}

	try {
	    disc.removeGroups(actualGroups);
	    throw new TestException("removeGroups from null didn't "
				  + "throw anything");
	} catch (UnsupportedOperationException e) {
	    // expected
	}

	disc.setGroups(DiscoveryGroupManagement.NO_GROUPS);
	logger.log(Level.INFO, "set to NO_GROUPS");
	Thread.sleep(10000);
	Discarder arder = new Discarder(reg.getServiceID(), disc);
	disc.addDiscoveryListener(arder);
	disc.addGroups(actualGroups);
	logger.log(Level.INFO, "increased to " + disc.getGroups().length
		   + " elements");
	Thread.sleep(30000);

	if (arder.discovery != true) {
	    throw new TestException("no discovered event reported");
	}

	if (arder.discardery != true) {
	    throw new TestException("no discarded event reported");
	}

	if (arder.unexpected == true) {
	    throw new TestException( "unexpected event reported");
	}
	disc.terminate();

	try {
	    disc.addGroups(actualGroups);
	    throw new TestException("addGroups after terminate didn't "
				  + "throw anything");
	} catch (IllegalStateException e) {
	}

	try {
	    disc.removeGroups(actualGroups);
	    throw new TestException("removeGroups after terminate didn't "
				  + "throw anything");
	} catch (IllegalStateException e) {
	}

	try {
	    disc.setGroups(actualGroups);
	    throw new TestException("setGroups after terminate didn't "
				  + "throw anything");
	} catch (IllegalStateException e) {
	}
    }

    protected ServiceRegistrar createLookup()
            throws Exception {

        // Create an instance of the lookup service.
        logger.log(Level.INFO, "creating lookup service");
        return manager.startLookupService();
    }

    protected DiscoveryAdmin getAdmin(ServiceRegistrar r)
            throws RemoteException {
	Object admin = ((Administrable) r).getAdmin();
	try {
            return (DiscoveryAdmin) 
		   getConfig().prepare("test.reggieAdminPreparer", admin);
        } catch (Exception e) {
	    throw new RemoteException("Problem preparing admin", e);
        }
    }
}
