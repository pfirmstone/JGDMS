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

package org.apache.river.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.common.Beta;

/**
 * Provides river with the external host network identity.
 */
@Beta
public class LocalHostLookup
{
    private final static Logger logger = Logger.getLogger(LocalHostLookup.class.getName());
    
    private static LocalHostLookupSpi spi  = AccessController.doPrivileged(

            new PrivilegedAction<LocalHostLookupSpi>()
            {
                @Override
                public LocalHostLookupSpi run()
                {
                    return initSpi();
                }

            }

        );
    
    private static LocalHostLookupSpi initSpi()
    {
        ServiceLoader<LocalHostLookupSpi> loader = ServiceLoader.load(LocalHostLookupSpi.class);

        Iterator<LocalHostLookupSpi> iter = loader.iterator();

        if( iter.hasNext() ) {
            try {
                LocalHostLookupSpi firstSpi = iter.next();
                logger.log(Level.CONFIG, "loaded: {0}", firstSpi);
                checkForLoopback(firstSpi);
                return firstSpi ;
            } catch (Exception e) {
                logger.log( Level.SEVERE, "error loading LocalHostLookupSpi: {0}", new Object[]{e});
                throw new Error(e);
            }
        }

        final DefaultLocalHostLookupProvider defaultLocalHostLookupProvider = new DefaultLocalHostLookupProvider();
        checkForLoopback(defaultLocalHostLookupProvider);

        return defaultLocalHostLookupProvider;
    }



    public static InetAddress getLocalHost() throws UnknownHostException
    {
        return spi.getLocalHost();
    }

    public static String getHostName() throws UnknownHostException
    {
        return spi.getHostName();
    }

    public static String getHostAddress() throws UnknownHostException
    {
        return spi.getHostAddress();
    }

    private static void checkForLoopback(LocalHostLookupSpi spi)
    {
        try {
            if (spi.getLocalHost().isLoopbackAddress()) {
                logger.warning("local host is loopback");
            }
        } catch (UnknownHostException ex) {
            logger.log( Level.SEVERE, "{0} during checkForLoopback", new Object[]{ex} );
        }
    }

    static class DefaultLocalHostLookupProvider
        extends LocalHostLookupSpi
    {

        @Override
        public InetAddress getLocalHost() throws UnknownHostException
        {
            return InetAddress.getLocalHost();
        }

        @Override
        public String getHostName() throws UnknownHostException
        {
            return InetAddress.getLocalHost().getCanonicalHostName();
        }

        @Override
        public String getHostAddress() throws UnknownHostException
        {
            return InetAddress.getLocalHost().getHostAddress();
        }

    }

    private LocalHostLookup()
    {
    }
}
