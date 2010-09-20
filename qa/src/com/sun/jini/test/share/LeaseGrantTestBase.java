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

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;

import java.rmi.*;
import net.jini.core.lease.Lease;
import net.jini.admin.Administrable;

/**
 * Base class for tests which grab a lease and make sure the returned lease
 * meets give constraints.
 */
public abstract class LeaseGrantTestBase extends TestBase {
    // If true then the tests expects leases to granted
    // exactly.  If false the grant can be for less than the request
    private boolean exact = false; 

    /**
     * The length of time the lease should be asked for.
     */
    protected long durationRequest;

    /**
     * The expiration time that would be given for the duration request
     * on the most recent use.  Not used if <code>durationRequest</code> is
     * <code>Lease.ANY</code>.
     * @see #resourceRequested
     */
    protected long expirationRequest;

    /**
     * The local time just after the request.  <code>expirationRequest</code>
     * is <code>requestStart + durationRequest</code>.
     * @see #resourceRequested
     */
    protected long requestStart;

    /**
     * The length of time that leases get cliped to if they are too
     * long. A negative value indicates no cliping is expected
     */
    protected long clip = -1;

    /*
     * Acceptable slop interval between when we think lease will
     * expire and when it acctually does.
     */
    protected long slop = 2000;
    
    /**
     * Test the passed lease to see if it has been granted for an
     * acceptable length of time
     */
    protected boolean isAcceptable(Lease underTest) {
	// if we asked for ANY lease, then any duration is cool
	if (durationRequest == Lease.ANY)
	    return true;

	final long duration = underTest.getExpiration() - requestStart;

	// if we're within slop of the original request, cool
	if (withinSlop(duration, durationRequest))
	    return true;

	// if the previous test failed but cliping is allowed and the
	// duration is within slop of acceptable clip, cool
	if (clip >= 0 && withinSlop(duration, clip))
	    return true;

	// if we're not exact and we're less that the original request, cool
	// substract slop from duration to avoid overflow problems
	if (!exact && duration - slop <= durationRequest)
	     return true;

	// Ok there is one posablity left, if we asked for forever, and got
	// an expiration at the end of time, then it is a pass even if 
	// the test is "exact"
	if (underTest.getExpiration() == Long.MAX_VALUE &&
	    durationRequest == Long.MAX_VALUE)
	    return true;

	 // uncool
	 return false;
     }

     /**
      * Return <code>true</code> if the <code>duration</code> is within
      * the allowed slop range relative to the given <code>value</code>.
      */
     protected boolean withinSlop(long duration, long value) {
	 return (duration > value - slop && duration < value + slop);
     }

     /**
      * Log a requested lease and its result, for the given type of lease.
      */
     protected void logRequest(String type, Lease lease) {
	 logger.log(Level.INFO, "Lease " + type + ": " + lease);
	 logger.log(Level.INFO, "\treq:" + expirationRequest);
	 logger.log(Level.INFO, "\tgot:" + lease.getExpiration());
	 logger.log(Level.INFO, "\taprox duration:" + (lease.getExpiration() -
		     requestStart));
	 logger.log(Level.INFO, "\tdrift:" + (lease.getExpiration() - expirationRequest));
     }

     /**
      * Should be called immediately after a leased resource is
      * requested or renewed.  Sets up relevant state, namely
      * <code>requestStart</code> and <code>expirationRequest</code>.
      * @see #requestStart
      * @see #expirationRequest */
     protected void resourceRequested() {
	 requestStart = System.currentTimeMillis();

	 expirationRequest = requestStart + durationRequest;
	 // If we get a negative result then we must have had an
	 // overflow, set expirationRequest to the the end of time
	 if (expirationRequest < 0) 
	     expirationRequest = Long.MAX_VALUE;
     }

     /**
      * Parse the command line options
      *
      * <code>argv[]</code> is parsed to control various options
      *
      * <DL>
      * <DT>-exact<DD> Sets the test to fail if exact leases are not
      * granted, defaults to <code>false</code>. If this option is not 
      * set tests will pass if the returned lease is less than or equal 
      * to what was expected.
      *
      * <DT>-duration<DD> Length of time to request lease
      * for. Defaults to 20 seconds. The string "forever" is
      * interpreted as <code>Lease.FOREVER</code>.  "anylength" is
      * interpreted as <code>Lease.ANY</code>.
      *
      * <DT>-clip<DD> If set to a non-negative value and -duration is
      * larger than -clip a lease of duration of -clip will not be
      * considered an error. Defaults to -1.
      *
      * <DT>-slop <var>milliseconds</var> <DD> Set how tolerant test is
      * of clock drift and network delays.  Defaults to 1000ms.
      *
      * </DL> 
      */
     protected void parse() throws Exception {
	 super.parse();

	 // Parse command line
	 exact = getConfig().getBooleanConfigVal("com.sun.jini.test.share.exact", false);
	 clip  = getConfig().getLongConfigVal("com.sun.jini.test.share.clip", -1);
	 slop  = getConfig().getLongConfigVal("com.sun.jini.test.share.slop", 1000);

	 final String durStr = getConfig().getStringConfigVal("com.sun.jini.test.share.duration", null);
	 if (durStr == null) {
	     durationRequest = 1000 * 20;
	 } else if (durStr.equals("forever")) {
	     durationRequest = Lease.FOREVER;
	 } else if (durStr.equals("anylength")) {
	     durationRequest = Lease.ANY;
	 } else {
	     try {
		 durationRequest = Long.parseLong(durStr);
	     } catch (NumberFormatException e) {
		 throw new TestException (
		     "Malformed argument for -duration command line switch");
	     }
	 }
     }

    /**
     * Makes sure the designated service is activated by getting its admin
     */
    protected void prep(int serviceIndex) throws TestException {
	try {
	    ((Administrable)services[serviceIndex]).getAdmin();
	} catch (Throwable e) {
	    throw new TestException("Could not pre-activate service under test", e);
	}
    }
}

