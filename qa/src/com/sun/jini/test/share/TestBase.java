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

import java.util.logging.Level;

// java.*
import java.rmi.RMISecurityManager;
import java.rmi.UnmarshalException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

// net.jini
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lease.Lease;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.lease.LeaseRenewalSet;
import net.jini.space.JavaSpace;
import net.jini.admin.Administrable;
import net.jini.lookup.DiscoveryAdmin;

// com.sun.jini
import com.sun.jini.outrigger.JavaSpaceAdmin;
import com.sun.jini.outrigger.AdminIterator;
import com.sun.jini.admin.DestroyAdmin;

import com.sun.jini.qa.harness.Admin;
import com.sun.jini.qa.harness.ActivatableServiceStarterAdmin;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.test.share.DiscoveryAdminUtil;

import java.util.logging.Level;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.security.ProxyPreparer;

/**
 * Base class for spaces QA tests.  Provides convince functions for
 * logging failure, starting/finding and cleaning up the services under
 * test.  Also sets up a command line parser.
 * <p>
 * Tests provide an implementation of <code>runTest()</code> that
 * performs the appropriate tests, using
 * <code>specifyServices()</code> to indicate what services they need
 * for the test.  Test failure should be indicated by calling
 * <code>fail()</code> (or its close relative,
 * <code>setupFailure()</code>) Passing can be indicated by calling
 * <code>pass()</code> or by returning from <code>runTest()</code>
 * normally. Tests should not invoke <code>Status.exit()</code>
 * directly.
 * <p>
 * If a test needs to parse command line arguments it should override
 * <code>parse()</code> and use the <code>CmdLine</code> command line
 * parser provided in <code>line</code>.
 * <p>
 * If a test has to perform any post test cleanup it should override
 * <code>cleanup()</code>
 *
 * Command line arguments
 * <p>
 * <DL>
 * <DT>-external <var>jini-url</var><DD> The test will find the
 * necessary services though the designated lookup service. Normally
 * the test will start any necessary services with the exception of an
 * activation demon.
 *
 * <DT>-scrub<DD> Can be used with <code>-external</code>. The test
 * will attempt to put the services in a "clean" state before running
 * the test.  Note this is currently implemented only for JavaSpaces.
 *
 * <DT>-administrable <var>classname</var><DD>Particular service to
 * spawn/look for if the test needs and <code>Administrable</code>
 * service.
 *
 * <DT>-space <var>classname</var><DD>Particular type of JavaSpace
 * to spawn/look for if the test needs a <code>JavaSpace</code>.
 *
 * <DT>-waitAtEnd<DD> Wait for user input before exiting or killing services
 * under test
 *
 * <DT>-cleanupWait <var>long</var> </DT>
 * <DD> The number of milliseconds to wait after cleaning up (i.e.
 *      destroying the services) at the end of a test run.
 *      Defaults to 0.
 * </DD>
 *
 * </DL>
 *
 * Note: the "Sub" command line options (ie
 * <code>-administrableSub</code>, <code>-spaceSub</code> have
 * slightly different constraints depending on whether
 * <code>-external</code> is being used or not.  In stand alone mode
 * the test needs a concrete class that it can spawn.  In external
 * mode these option only need be specified so the test can uniquely
 * identify the service to test.  Thus if the only service the test
 * needed was a <code>JavaSpace</code>, and the designated lookup
 * service had only two registrants, the lookup service itself and a
 * JavaSpace, no command line argument would be necessary.  If the
 * test needed an <code>Administrable</code> the
 * <code>-administrableSub</code> command line argument would have to
 * be used to let the test know if it should be testing the lookup
 * service or the JavaSpace.
 */
public abstract class TestBase extends QATest {
    DiscoveryAdmin admin = null;

    /**
     * Holds instances to LRS proxy objects returned from StartService.
     */
    private ArrayList startedServices = new ArrayList();

    /** URL to find lookup, null if we are in standAlone mode */
    protected LookupLocator locator = null;

    /** Lookup groups to find lookup, null if we are in standAlone mode */
    protected String groups[] = null;

    /**
     * Number of milliseconds to wait after cleaning up the services.
     */
    protected long cleanupWait = 0;

    /** True is we are in standalone mode */
    protected boolean standAlone;

    /**
     * Flag that indicates we should try to scrub the services once we find them
     */
    protected boolean scrub = false;

    /** Flag that indicates we should not destroy on exit. */
    protected boolean destroy = true;

    /**
     * Class name to substitute for Administrable when looking/starting
     * services to test
     */
    protected String administrableSubstitute = null;

    /**
     * Class name to substitute for JavaSpace when looking/starting
     * services to test
     */
    protected String javaSpaceSubstitute = null;

    /** List of leases to cancel during cleanup */
    private List leaseList = new java.util.LinkedList();

    /**
     * Set of services to test.  @see#specifyServices for details
     */
    protected Object[] services;

    /**
     * True if we should be using lookup
     */
    private boolean useLookup;

    /**
     * If we kill a VM during the test the min time to wait before restart
     */
    protected long minPostKillWait;

    // Do we wait at the end
    protected boolean waitAtEnd;

    /**
     * the name of service for which these test are written
     */
    protected final String serviceName = "net.jini.lease.LeaseRenewalService";

    public void setup(QAConfig config) throws Exception {
        super.setup(config);

        // output the name of this test
        logger.log(Level.FINE, "Test Name = " + this.getClass().getName());

        // set security manager
        System.setSecurityManager(new RMISecurityManager());
    }

    protected void specifyServices(Class[] serviceClasses)
            throws TestException {
        try {
            final String serviceClassNames[] = substitute(serviceClasses);
            this.dbgSpecSrvcs(serviceClasses, serviceClassNames);

            if (useLookup) {
                ServiceRegistrar lookupProxy =
		    manager.startLookupService(); // prepared by util
		// prepared by DiscoveryAdminUtil
                admin = DiscoveryAdminUtil.getDiscoveryAdmin(lookupProxy);
            }

            // Setup services
            for (int i = 0; i < serviceClasses.length; i++) {
                logger.log(Level.FINE, "Starting service #" + i + ": "
                        + serviceClassNames[i]);
                startedServices.add(manager.startService(serviceClassNames[i]));
            }
            services = startedServices.toArray(new Object[] {});

            if (scrub) {
                for (int i = 0; i < services.length; i++) {
                    if (services[i] instanceof JavaSpace) {
                        scrubSpace((JavaSpace) services[i]);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TestException("Exception has been catched in"
                    + " specifyServices: " + ex.getMessage());
        }
    }

    private String[] substitute(Class[] source) {
        String[] rslt = new String[source.length];

        for (int i = 0; i < source.length; i++) {
            if (source[i] == Administrable.class
                    && administrableSubstitute != null) {
                rslt[i] = administrableSubstitute;
            } else if (source[i] == JavaSpace.class
                    && javaSpaceSubstitute != null) {
                rslt[i] = javaSpaceSubstitute;
            } else {
                rslt[i] = source[i].getName();
            }
        }
        return rslt;
    }
    private static final int BLOCKING_FACTOR = 10;

    protected void scrubSpace(JavaSpace space) throws TestException {
        try {
            JavaSpaceAdmin admin = (JavaSpaceAdmin) ((Administrable)
                space).getAdmin();
	    admin = (JavaSpaceAdmin) 
		    getConfig().prepare("test.outriggerAdminPreparer",
					admin);
            final AdminIterator i = admin.contents(null, null, BLOCKING_FACTOR);

            while (i.next() != null) {
                i.delete();
            }
            i.close();
        } catch (Exception e) {
            throw new TestException(e.getMessage(),e);
        }
    }
    private void dbgSpecSrvcs(Class[] serviceClasses,
            String[] serviceClassNames) {
        try {

            // What classes
            StringBuffer b = new StringBuffer("TestBase.specifyServices({");

            if (serviceClasses != null) {
                int i;

                for (i = 0; i < serviceClasses.length; i++) {
                    if (i > 0) {
                        b.append(",");
                    }

                    if (serviceClassNames[i] != null) {
                        b.append(serviceClassNames[i]);
                    }
                    String n = serviceClasses[i].getName();

                    if (!n.equals(serviceClassNames[i])) {
                        b.append("=");
                        b.append(n);
                    }
                }
            }
            b.append("})");
            logger.log(Level.FINE, b.toString());

            // Which parameters?
            logger.log(Level.FINE, "specifyServices groups=" + (this.groups == null ?
                    "null" :
                    java.util.Arrays.asList(this.groups).toString())
                    + " locator=" + this.locator + " useLookup ="
                    + this.useLookup);
        } catch (Throwable uhoh) {

            // don't kick the bucket just because the debug code is undebugged
            logger.log(Level.FINE, "TestBase.dbgSpecSrvcs threw:");
            uhoh.printStackTrace();
        }
    }

    private long shutdownNoSleep(int index) throws Exception {
        Object o = services[index];
        try {
	    if (!manager.killVM(o)) {
		logger.log(Level.SEVERE, "Could not call killVM for service " + o);
            } else {
		// get delay in seconds
		int killDelay = 
		    getConfig().getIntConfigVal("com.sun.jini.qa.harness.killvm.delay", 0);
		if (killDelay > 0) {
		    try {
			Thread.sleep(killDelay * 1000);
		    } catch (InterruptedException ignore) {
		    }
		}
	    }
	    return minPostKillWait; //XXX do I need to support the suggested wait time?
        } catch (ClassCastException e) {
            throw new UnsupportedOperationException("Don't know how to shutdown"
                    + " a " + o.getClass().getName());
        }
    }

    /**
     * Attempts to shutdown the designated service.  Throws
     * UnsupportedOperationException if it does not know how to
     * shutdown the designated service.  Sleep for the time suggested
     * by the service or by what is indicated by the restart_wait command line
     * arg, which ever is greater.
     */
    protected void shutdown(int index) throws Exception {
        Object o = services[index];

        try {
            final long suggestedWait = shutdownNoSleep(index);
            final long willWait = Math.max(minPostKillWait, suggestedWait);
            logger.log(Level.INFO, "Shutdown worked, sleeping for " + willWait
                + " milliseconds...");
            Thread.sleep(willWait);
            logger.log(Level.INFO, "...awake");
        } catch (InterruptedException e) {}
    }

    /**
     * Attempts to shutdown the designated service.  Throws
     * UnsupportedOperationException if it does not know how to
     * shutdown the designated service.  Sleep for time indicated by the
     * the second argument.  If the second argument is less than the time
     * suggested by the service throws an IllegalArgumentException
     */
    protected void shutdown(int index, long wait) throws Exception {
        Object o = services[index];

        try {
            final long suggestedWait = shutdownNoSleep(index);

            if (suggestedWait > wait) {
                throw new IllegalArgumentException("shutdown():Wait is "
                        + "less than wait suggested by service");
            }
            logger.log(Level.INFO, "Shutdown worked, sleeping for " + wait
                    + " milliseconds...");
            Thread.sleep(wait);
            logger.log(Level.INFO, "awake");
        } catch (InterruptedException e) {}
    }

    /**
     * TestBase keeps a list of leases that should be canceled in the
     * cleanup phase, this method adds a lease to that list.  Note the
     * leases on this list are not canceled if <code>TestBase</code>
     * thinks the these resouces are going to be cleaned up someother
     * way.
     *
     * @param lease     The Lease to be canceled
     * @param unknownOk If <code>true</code>
     *                  <code>UnknownLeaseException</code> will be
     *                  ignored when this lease is cancled.
     */
    public void addLease(Lease lease, boolean unknownOk) {

        // If we are just going to destroy all of the services, don't bother.
        if (standAlone) {
            return;
        }
        synchronized (leaseList) {
            leaseList.add(new LeaseRec(lease, unknownOk));
        }
    }

    
    /**
     * TestBase keeps a list of leases that should be canceled in the
     * cleanup phase, this method adds a lease to that list. Before
     * adding the lease, it is prepared using the <code>ProxyPreparer</code>
     * named <code>test.outriggerLeasePreparer</code>. Note the
     * leases on this list are not canceled if <code>TestBase</code>
     * thinks the these resouces are going to be cleaned up someother
     * way.
     *
     * @param lease     The Lease to be canceled
     * @param unknownOk If <code>true</code>
     *                  <code>UnknownLeaseException</code> will be
     *                  ignored when this lease is cancled.
     */
    public void addOutriggerLease(Lease lease, boolean unknownOk) 
	throws TestException 
    {
	ProxyPreparer p = null;
	Configuration c = getConfig().getConfiguration();
	if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
	    try {
		p = (ProxyPreparer) c.getEntry("test",
					       "outriggerLeasePreparer",
					       ProxyPreparer.class);
		lease = (Lease) p.prepareProxy(lease);
	    } catch (ConfigurationException e) {
		throw new TestException("Configuration error", e);
	    } catch (RemoteException e) {
		throw new TestException("RemoteException preparing lease",e);
	    }
	}
	addLease(lease, unknownOk);
    }

    /**
     * TestBase keeps a list of leases that should be canceled in the
     * cleanup phase, this method adds a lease to that list. Before
     * adding the lease, it is prepared using the <code>ProxyPreparer</code>
     * named <code>test.outriggerLeasePreparer</code>. Note the
     * leases on this list are not canceled if <code>TestBase</code>
     * thinks the these resouces are going to be cleaned up someother
     * way.
     *
     * @param lease     The Lease to be canceled
     * @param unknownOk If <code>true</code>
     *                  <code>UnknownLeaseException</code> will be
     *                  ignored when this lease is cancled.
     */
    public void addMahaloLease(Lease lease, boolean unknownOk) 
	throws TestException 
    {
	ProxyPreparer p = null;
	Configuration c = getConfig().getConfiguration();
	if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
	    try {
		p = (ProxyPreparer) c.getEntry("test",
					       "mahaloLeasePreparer",
					       ProxyPreparer.class);
		lease = (Lease) p.prepareProxy(lease);
	    } catch (ConfigurationException e) {
		throw new TestException("Configuration error", e);
	    } catch (RemoteException e) {
		throw new TestException("RemoteException preparing lease",e);
	    }
	}
	addLease(lease, unknownOk);
    }

    private class LeaseRec {
        final private Lease lease;
        final private boolean unknownOk;

        LeaseRec(Lease lease, boolean unknownOk) {
            this.lease = lease;
            this.unknownOk = unknownOk;
        }

        private void cancel() throws TestException {
            try {
                lease.cancel();
            } catch (UnknownLeaseException e) {
                if (!unknownOk) {
                    cleanupFailure("UnknownLeaseException canceling lease:"
                            + e.getMessage());
                }
            } catch (NoSuchObjectException ex) {

                /*
                 * Ignore it: it means that activatable object no longer
                 * registered
                 */
            } catch (Throwable e) {
                cleanupFailure("Could not cancel lease:" + e.getMessage());
            }
        }
    }

    /**
     * Indicates cleanup failed, should only be called in the context
     * of cleanup.  This method may return.
     */
    protected void cleanupFailure(String msg) throws TestException {
        throw new TestException(msg);
    }

    protected void cleanupFailure(String msg, Throwable t) throws TestException {
        throw new TestException(msg,t);
    }

    protected void setupFailure(String msg) throws TestException {
        throw new TestException(msg);
    }

    protected void setupFailure(String msg, Throwable t) throws TestException {
        throw new TestException(msg,t);
    }



    /**
     * Called to tell the class that the specified service has been
     * destroyed.  This will set its entry in the services array to null
     */
    protected void serviceDestroyed(int index) {
        services[index] = null;
    }

    protected void parse() throws Exception {
        javaSpaceSubstitute = getConfig().getStringConfigVal("com.sun.jini.test.share."
                + "space", null);
        scrub = getConfig().getBooleanConfigVal("com.sun.jini.test.share.scrub", false);
        destroy = !getConfig().getBooleanConfigVal("com.sun.jini.test.share.noDestroy",
                false);
        administrableSubstitute = 
	    getConfig().getStringConfigVal("com.sun.jini.test."
                + "share.administrable", null);
        useLookup = getConfig().getBooleanConfigVal("com.sun.jini.test.share.lookup",
                true);
        minPostKillWait = getConfig().getLongConfigVal("com.sun.jini.test.share."
                + "restart_wait", 1000);
        waitAtEnd = getConfig().getBooleanConfigVal("com.sun.jini.test.share.waitAtEnd",
                false);
        cleanupWait = getConfig().getLongConfigVal("com.sun.jini.test.share.cleanupWait",
                0);

        if (cleanupWait < 0) {
            cleanupWait = 0;
        }
    }

    /**
     * Performs cleanup actions necessary to achieve a graceful exit of
     * the current QA test.
     * @exception TestException will usually indicate an "unresolved"
     * condition because at this point the test has completed.
     */
    public void tearDown() {
        try {
//              for (int i = 0; i < services.length; i++) {
//                  if (services[i] != null) {
//                      try {
//                          Administrable service = (Administrable) services[i];
//                          DestroyAdmin dadmin = (DestroyAdmin) service.getAdmin();
//                          dadmin.destroy();
//                      } catch (NoSuchObjectException ex) {

//                          /*
//                           * Ignore it: it means that activatable object
//                           * no longer registered
//                           */
//                      } catch (Throwable t) {
//                          cleanupFailure("Trouble destroying " + services[i] +
//                              " " + t.getMessage());
//                      }
//                  }
//              }

//              if (admin != null) {
//                  try {
//                      DestroyAdmin dadmin = (DestroyAdmin) admin;
//                      dadmin.destroy();
//                  } catch (Throwable t) {
//                      cleanupFailure("Trouble destroying " + admin + " " +
//                          t.getMessage());
//                  }
//              }

            if (destroy) {
                synchronized (leaseList) {
                    for (Iterator i = leaseList.iterator(); i.hasNext();) {
                        ((LeaseRec) i.next()).cancel();
                    }
                }

                try {
                    logger.log(Level.FINE, "Waiting " + cleanupWait
                            + " ms after cleanup() call");

                    if (cleanupWait > 0) {
                        Thread.sleep(cleanupWait);
                    }
                } catch (InterruptedException e) {

                    // Do nothing.
                }
            }
        } catch (Exception ex) {
            String message = "Warning: Test TestBase did not shutdown "
                    + "cleanly!\n" + "Reason: " + ex.getMessage();
            logger.log(Level.INFO, message);
            ex.printStackTrace();
        }
	super.tearDown();
    }

    /**
     * Assert that <code>entry</code> equals <code>other</code>.  Equivalent
     * to <code>assertEquals(entry, other, desc, true)</code>
     */
    public void assertEquals(Entry entry, Entry other, String desc)
            throws TestException {
        assertEquals(entry, other, desc, true);
    }

    /**
     * Assert that <code>entry</code> equals <code>other</code>, if
     * <code>shouldBe</code> is <code>true</code>, or that it isn't, if
     * <code>shouldBe</code> is <code>false</code>.
     */
    public void assertEquals(Entry entry, Entry other, String desc,
            boolean shouldBe) throws TestException {
        boolean matches;

        if (entry == null || other == null) {
            matches = (entry == null && other == null);
        } else {
            matches = entry.equals(other);
        }

        // otherwise use equals()
        if (matches != shouldBe) {
            new Throwable().printStackTrace();
            String failOp = (shouldBe ? "!=" : "==");
            throw new TestException(desc + " " + failOp + " original: ["
                    + other + "] " + failOp + " [" + entry + "]");
        }
    }

    /**
     * Fail the tests writing <code>msg</code> in the log and
     * including it in the fail message.  If <code>t</code> is non
     * null its stack trace will also be dumped to the log.
     */
    synchronized protected void fail(String msg, Throwable t)
            throws TestException {
        logger.log(Level.FINE, msg);

        if (t != null) {
            t.printStackTrace();
        }
        throw new TestException(msg, t);
    }

    /**
     * Fail the tests writing <code>msg</code> in the log and
     * including it in the fail message.
     */
    protected void fail(String msg) throws TestException {
        fail(msg, null);
    }

    protected LeaseRenewalSet prepareSet(LeaseRenewalSet set) 
        throws TestException
    {
	Object s = getConfig().prepare("test.normRenewalSetPreparer", set);
	return (LeaseRenewalSet) s;
    }

    protected EventRegistration prepareNormEventRegistration(EventRegistration reg) 
        throws TestException
    {
	Object r = getConfig().prepare("test.normEventRegistrationPreparer", reg);
	return (EventRegistration) r;
    }

    protected Lease prepareNormLease(Lease lease) throws TestException {
	Object l = getConfig().prepare("test.normLeasePreparer", lease);
	return (Lease) l;
    }
}
