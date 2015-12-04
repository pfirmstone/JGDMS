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

package org.apache.river.test.impl.fiddler;

import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

import org.apache.river.test.spec.discoveryservice.AbstractBaseTest;
import org.apache.river.test.share.DiscoveryServiceUtil;

import org.apache.river.fiddler.FiddlerAdmin;
import org.apache.river.qa.harness.Test;

import net.jini.admin.Administrable;

import net.jini.discovery.LookupDiscoveryRegistration;
import net.jini.discovery.LookupDiscoveryService;

import net.jini.core.lease.Lease;

import java.util.logging.Level;

/**
 * Test for the following bug:
 * 
 * 4984948 Fiddler should implement toString() for various Fiddler proxies
 *
 * This test starts a Fiddler implementation of the lookup discovery service,
 * retrieves an instance of each of the proxies affected by the above bug,
 * and invokes the <code>toString</code> method on each of those proxies,
 * logging the result of each invocation. This test is considered to have
 * passed if no exceptions occur and if, upon inspection, the output of each
 * invocation of <code>toString</code> produces a <code>String</code> with
 * the intended contents and format.
 */
public class ProxyToString extends AbstractBaseTest {

    private LookupDiscoveryService      fiddlerProxy        = null;
    private FiddlerAdmin                fiddlerAdminProxy   = null;
    private LookupDiscoveryRegistration fiddlerRegistration = null;
    private Lease                       fiddlerLease        = null;

    /** Starts one fiddler implementation of the lookup discovery service.
     *  Retrieves the service proxy.
     *  Retrieves the admin proxy.
     *  Requests a registration with the service; obtains a registration proxy.
     *  Retrieves the lease on the registration.
     */
    public Test construct(QAConfig config) throws Exception {

        super.construct(config);//start fiddler and retrieve the service proxy

        fiddlerProxy = discoverySrvc;//discoverySrvc set in AbstractBaseTest

        if( !(fiddlerProxy instanceof Administrable) ) {
            throw new Exception("service proxy is NOT Administrable");
        } else {
            Object admin = ((Administrable)fiddlerProxy).getAdmin();
            if( !(admin instanceof FiddlerAdmin) ) {
                throw new Exception("admin proxy is NOT a FiddlerAdmin");
            } else {
                fiddlerAdminProxy = (FiddlerAdmin)admin;
            }//endif
        }//endif
        DiscoveryServiceUtil.BasicEventListener listener = new DiscoveryServiceUtil.BasicEventListener();
        listener.export();
        fiddlerRegistration = DiscoveryServiceUtil.getRegistration 
                               (fiddlerProxy,
                                listener);

        fiddlerLease = getPreparedLease(fiddlerRegistration);
        return this;
    }//end construct

    /** Invokes the <code>toString</code> method on each of the proxies
     *  retrieved during construct and logs the results. If no exception occurs,
     *  then this test passes. The output should then be inspected to verify
     *  that each method produces a <code>String</code> with the intended
     *  content and format.
     */
    public void run() throws Exception {

        /* Insert blank lines for readability */
        logger.log(Level.INFO, "{0}", fiddlerProxy);
        logger.log(Level.INFO, "   ");
        logger.log(Level.INFO, "{0}", fiddlerAdminProxy);
        logger.log(Level.INFO, "   ");
        logger.log(Level.INFO, "{0}", fiddlerRegistration);
        logger.log(Level.INFO, "   ");
        logger.log(Level.INFO, "{0}", fiddlerLease);

    }//end run

} //end class ProxyToString


