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

package com.sun.jini.test.spec.servicediscovery.cache;

import java.util.logging.Level;

import net.jini.core.lookup.ServiceItem;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.Test;

/**
 * This class verifies that the <code>discard</code> method of the
 * <code>LookupCache</code> operates as specified.
 * 
 * Regression test for Bug ID 4358209
 */
public class CacheDiscard extends CacheLookup {

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts N lookup services 
     *  2. Registers M test services with the lookup services started above
     *  3. Creates a service discovery manager that discovers the lookup
     *     services started above
     *  4. Creates a template that will match the test services based on
     *     service type only
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        testDesc = "single service cache lookup and then discard -- "
                   +"services pre-registered, "
                   +"no first-stage filter, no second-stage filter";
        return this;
    }//end construct

    /** Defines the actual steps of this particular test.
     *  
     *  1. Requests the creation of a <code>LookupCache</code>
     *  2. Invokes the desired version of the <code>lookup</code> method to
     *     query the cache for the desired expected service. This provides
     *     the test with the service reference to discard.
     *  3. Discards the retrieved service.
     *  4. Queries the cache again to verify that the service has indeed
     *     been discarded as expected.
     */
    protected void applyTestDef() throws Exception {
        /* Query the cache for the desired registered service. */
        super.applyTestDef();
        /* Discard the service item returned by the first lookup */
        logger.log(Level.FINE, "discarding the service reference "
                                        +"from the cache");
        cache.discard(srvcItem.service);
        /* Again query the cache for the service. */
        logger.log(Level.FINE, "re-querying the cache for the "
                                        +"discarded service reference");
        srvcItem = cache.lookup(null);
        ServiceItem discardedSrvcItem = cache.lookup(null);
        if(discardedSrvcItem != null) { // failed
	    if(discardedSrvcItem.service == null) {
		throw new TestException
		    (" -- non-null service item returned, but "
		     +"component of returned service is null");
	    }//endif
	    if( srvcItem.equals(discardedSrvcItem) ) {
		throw new TestException
		    (" -- service still in cache -- service item "
		     +"returned equals original service item");
	    } else {
		throw new TestException
		    (" -- service item returned is non-null, but "
		     +"does not equal the original service item");
	    }
	}
    }//end applyTestDef

}//end class CacheDiscard


