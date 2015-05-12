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

package org.apache.river.example.hello;

import java.rmi.RemoteException;
import java.security.PrivilegedExceptionAction;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.NoSuchEntryException;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.LookupDiscovery;
import net.jini.lookup.ServiceDiscoveryManager;
import net.jini.lookup.ServiceItemFilter;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

/**
 * Defines an application that makes calls to a remote Hello server.
 *
 * The application uses the following arguments:
 *
 * [all] - All arguments are used as options when getting the configuration
 *
 * The application uses the following configuration entries, with component
 * org.apache.river.example.hello.Client:
 *
 * loginContext
 *   type: LoginContext
 *   default: null
 *   If non-null, specifies the JAAS login context to use for performing a JAAS
 *   login and supplying the Subject to use when running the client. If null,
 *   no JAAS login is performed.
 *
 * preparer
 *   type: ProxyPreparer
 *   default: new BasicProxyPreparer()
 *   Proxy preparer for the server proxy
 *
 * serviceDiscovery
 *   type: ServiceDiscoveryManager
 *   default: new ServiceDiscoveryManager(
 *                new LookupDiscovery(new String[] { "" }, config),
 *                null, config)
 *   Object used for discovering a server that implement Hello.
 *
 * @author Sun Microsystems, Inc.
 */
public class Client {

    /**
     * Starts an application that makes calls to a remote Hello implementation.
     */
    public static void main(String[] args) throws Exception {
	/*
	 * The configuration contains information about constraints to apply to
	 * the server proxy.
	 */
	final Configuration config = ConfigurationProvider.getInstance(args);
	LoginContext loginContext = (LoginContext) config.getEntry(
	    "org.apache.river.example.hello.Client", "loginContext",
	    LoginContext.class, null);
	if (loginContext == null) {
	    mainAsSubject(config);
	} else {
	    loginContext.login();
	    Subject.doAsPrivileged(
		loginContext.getSubject(),
		new PrivilegedExceptionAction() {
		    public Object run() throws Exception {
			mainAsSubject(config);
			return null;
		    }
		},
		null);
	}
    }

    /**
     * Performs the main operations of the application with the specified
     * configuration and assuming that the appropriate subject is in effect.
     */
    static void mainAsSubject(Configuration config) throws Exception {

	/* Get the service discovery manager, for discovering other services */
	ServiceDiscoveryManager serviceDiscovery;
	try {
	    serviceDiscovery = (ServiceDiscoveryManager) config.getEntry(
		"org.apache.river.example.hello.Client", "serviceDiscovery",
		ServiceDiscoveryManager.class);
	} catch (NoSuchEntryException e) {
	    /* Default to search in the public group */
	    serviceDiscovery = new ServiceDiscoveryManager(
		new LookupDiscovery(new String[] { "" }, config),
		null, config);
	}

        /* Retrieve the server proxy preparer from the configuration */
	ProxyPreparer preparer = (ProxyPreparer) config.getEntry(
	    "org.apache.river.example.hello.Client",
	    "preparer", ProxyPreparer.class, new BasicProxyPreparer());

        /* Create the filter to pass to the SDM for proxy preparation */
        ServiceItemFilter filter = new ProxyPreparerFilter(preparer);

	/* Look up the remote server (SDM performs proxy preparation) */
	ServiceItem serviceItem = serviceDiscovery.lookup(
	    new ServiceTemplate(null, new Class[] { Hello.class }, null),
	    filter, Long.MAX_VALUE);
	Hello server = (Hello) serviceItem.service;

	/* Use the server */
	System.out.println("Server says: " + server.sayHello());

	/* Exit to close any thread created by the callback handler's GUI */
	System.exit(0);
    }

    /** Performs proxy preparation on discovered services. For more information
     *  see the javadoc for the net.jini.lookup.ServiceItemFilter interface.
     */
    private static class ProxyPreparerFilter implements ServiceItemFilter {
        private ProxyPreparer preparer;

        ProxyPreparerFilter(ProxyPreparer preparer) {
            this. preparer = preparer;
        }

        /** See the javadoc for the ServiceItemFilter.check method. */
	public boolean check(ServiceItem item) {
            try {
                item.service = preparer.prepareProxy(item.service);
                return true;  //pass
            } catch(SecurityException e) {//definite exception
                return false; //fail: don't try again
            } catch(RemoteException e) {//indefinite exception
                item.service = null;
                return true;  //null & true == indefinite: will retry later
            }
	}
    }
}
