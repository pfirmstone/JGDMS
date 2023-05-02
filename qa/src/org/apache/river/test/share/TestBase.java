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


// java.*
import org.apache.river.admin.AdminIterator;
import org.apache.river.admin.JavaSpaceAdmin;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.admin.Administrable;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.transaction.Transaction;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lookup.DiscoveryAdmin;
import net.jini.security.ProxyPreparer;
import net.jini.space.JavaSpace;
import org.apache.river.admin.DestroyAdmin;
import org.apache.river.api.security.CombinerSecurityManager;
import org.apache.river.thread.NamedThreadFactory;
import org.apache.river.tool.SecurityPolicyWriter;

/**
 * Base class for spaces QA tests.  Provides convenience functions for
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
public abstract class TestBase extends QATestEnvironment {
    volatile DiscoveryAdmin admin = null;

    /**
     * Holds instances to LRS proxy objects returned from StartService.
     */
    private final ArrayList startedServices = new ArrayList();//access using synchronized

    /** URL to find lookup, null if we are in standAlone mode */
    protected volatile LookupLocator locator = null;

    /** Lookup groups to find lookup, null if we are in standAlone mode */
    protected volatile String groups[] = null;

    /**
     * Number of milliseconds to wait after cleaning up the services.
     */
    protected volatile long cleanupWait = 0;

    /** True is we are in standalone mode */
    protected volatile boolean standAlone;

    /**
     * Flag that indicates we should try to scrub the services once we find them
     */
    protected volatile boolean scrub = false;

    /** Flag that indicates we should not destroy on exit. */
    protected volatile boolean destroy = true;

    /**
     * Class name to substitute for Administrable when looking/starting
     * services to test
     */
    protected volatile String administrableSubstitute = null;

    /**
     * Class name to substitute for JavaSpace when looking/starting
     * services to test
     */
    protected volatile String javaSpaceSubstitute = null;

    /** List of leases to cancel during cleanup */
    private final List leaseList = new java.util.LinkedList();//access using synchronized

    /**
     * Set of services to test.  @see#specifyServices for details
     * 
     * Only updated while holding lock to startedServices.
     */
    protected volatile Object[] services;

    /**
     * True if we should be using lookup
     */
    private volatile boolean useLookup;

    /**
     * If we kill a VM during the test the min time to wait before restart
     */
    protected volatile long minPostKillWait;

    // Do we wait at the end
    protected volatile boolean waitAtEnd;

    /**
     * the name of service for which these test are written
     */
    protected final String serviceName = "net.jini.lease.LeaseRenewalService";

    public Test construct(QAConfig config) throws Exception {
        super.construct(config);

        // output the name of this test
        logger.log(Level.FINE, "Test Name = " + this.getClass().getName());

        // set security manager
        if (System.getSecurityManager() == null) {
        System.setSecurityManager(new CombinerSecurityManager());
//	    System.setSecurityManager(new SecurityPolicyWriter()); // Seems to be ok here with jsse
        }
        return new Test(){

            public void run() throws Exception {
                // do nothing
            }
            
        };
    }

    protected void specifyServices(Class[] serviceClasses)
            throws TestException {
        try {
            final String serviceClassNames[] = substitute(serviceClasses);
            this.dbgSpecSrvcs(serviceClasses, serviceClassNames);

            if (useLookup) {
                ServiceRegistrar lookupProxy =
		    getManager().startLookupService(); // prepared by util
		// prepared by DiscoveryAdminUtil
                admin = DiscoveryAdminUtil.getDiscoveryAdmin(lookupProxy);
            }

            // Setup services
            synchronized (startedServices){
                for (int i = 0; i < serviceClasses.length; i++) {
                    logger.log(Level.FINE, "Starting service #" + i + ": "
                            + serviceClassNames[i]);
                    startedServices.add(getManager().startService(serviceClassNames[i]));
                }
                services = startedServices.toArray(new Object[startedServices.size()]);
            }
            if (scrub) {
                for (int i = 0; i < services.length; i++) {
                    if (services[i] instanceof JavaSpace) {
                        scrubSpace((JavaSpace) services[i]);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TestException("Exception has been caught in"
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
	    if (!getManager().killVM(o)) {
		logger.log(Level.SEVERE, "Could not call killVM for service " + o);
            } else {
		// get delay in seconds
		int killDelay = 
		    getConfig().getIntConfigVal("org.apache.river.qa.harness.killvm.delay", 0);
		if (killDelay > 0) {
		    try {
			Thread.sleep(killDelay * 1000);
		    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
	}
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
    }
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
	if (c instanceof org.apache.river.qa.harness.QAConfiguration) {
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
	if (c instanceof org.apache.river.qa.harness.QAConfiguration) {
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
        synchronized (startedServices){ //to avoid interleved write.
            Object [] serv = services;
            serv[index] = null;
            services = serv; //guarantees change to volatile array is visible to other threads.
        }
    }

    protected void parse() throws Exception {
        javaSpaceSubstitute = getConfig().getStringConfigVal("org.apache.river.test.share."
                + "space", null);
        scrub = getConfig().getBooleanConfigVal("org.apache.river.test.share.scrub", false);
        destroy = !getConfig().getBooleanConfigVal("org.apache.river.test.share.noDestroy",
                false);
        administrableSubstitute = 
	    getConfig().getStringConfigVal("org.apache.river.test."
                + "share.administrable", null);
        useLookup = getConfig().getBooleanConfigVal("org.apache.river.test.share.lookup",
                true);
        minPostKillWait = getConfig().getLongConfigVal("org.apache.river.test.share."
                + "restart_wait", 1000);
        waitAtEnd = getConfig().getBooleanConfigVal("org.apache.river.test.share.waitAtEnd",
                false);
        cleanupWait = getConfig().getLongConfigVal("org.apache.river.test.share.cleanupWait",
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
              for (int i = 0; i < services.length; i++) {
                  if (services[i] != null) {
                      try {
                          Administrable service = (Administrable) services[i];
                          DestroyAdmin dadmin = (DestroyAdmin) service.getAdmin();
                          dadmin.destroy();
                      } catch (NoSuchObjectException ex) {

                          /*
                           * Ignore it: it means that activatable object
                           * no longer registered
                           */
                      } catch (Throwable t) {
                          cleanupFailure("Trouble destroying " + services[i] +
                              " " + t.getMessage());
                      }
                  }
              }

              if (admin != null) {
                  try {
                      DestroyAdmin dadmin = (DestroyAdmin) admin;
                      dadmin.destroy();
                  } catch (Throwable t) {
                      cleanupFailure("Trouble destroying " + admin + " " +
                          t.getMessage());
                  }
              }

            if (destroy) {
		ExecutorService executor;
		List<Future> leaseCancelTasks = new LinkedList<Future>();
                AccessControlContext context = AccessController.getContext();
                synchronized (leaseList) {
		    executor = 
			new ThreadPoolExecutor(0, 
				10,
				60L,
				TimeUnit.SECONDS,
				new ArrayBlockingQueue(leaseList.size() + 1), // Add one in case it's zero.
				new NamedThreadFactory("Test Lease cancel thread", true)
			);
                    for (Iterator i = leaseList.iterator(); i.hasNext();) {
			leaseCancelTasks.add(executor.submit(new Callable(){

			    @Override
			    public Object call() throws Exception {
                                return AccessController.doPrivileged(
                                    new PrivilegedExceptionAction(){
                                        @Override
                                        public Object run() throws Exception {
                                            ((LeaseRec) i.next()).cancel();
                                            return Boolean.TRUE;
                                        }

                                    },
                                    context
                                );
			    }
			    
			}));
                        
                    }
                }
		Iterator<Future> leaseCancelResults = leaseCancelTasks.iterator();
		while (leaseCancelResults.hasNext()){
		    Future result = leaseCancelResults.next();
		    try {
			result.get(cleanupWait, TimeUnit.SECONDS);
		    } catch (TimeoutException ex){
			String message = 
			    "Warning: Test TestBase did not shutdown cleanly!\nReason: " 
				+ ex.getMessage();
			logger.log(Level.INFO, message);
			ex.printStackTrace();
			result.cancel(true);
		    } catch (ExecutionException ex){
			Throwable cause = ex.getCause();
			if (cause instanceof TestException) throw (TestException) cause;
		    } catch (InterruptedException e){
			Thread.currentThread().interrupt();
		    }
		}
		executor.shutdownNow();
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
