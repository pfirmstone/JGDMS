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

package net.jini.loader;

import java.net.MalformedURLException;
import java.rmi.server.RMIClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.common.Beta;

/**
 *
 */
@Beta
public class RiverClassLoader
{
    private final static Logger logger = Logger.getLogger(RiverClassLoader.class.getName());
    
    private static final RiverClassLoaderSpi spi = AccessController.doPrivileged(
            
            new PrivilegedAction<RiverClassLoaderSpi>()
            {
                @Override
                public RiverClassLoaderSpi run()
                {
                    return initSpi();
                }

            }
            
        );
            
    private static RiverClassLoaderSpi initSpi()
    {
        ServiceLoader<RiverClassLoaderSpi> loader = ServiceLoader.load(RiverClassLoaderSpi.class);
        
        Iterator<RiverClassLoaderSpi> iter = loader.iterator();
        
        if( iter.hasNext() ) {
            try {
                RiverClassLoaderSpi firstSpi = iter.next();
                logger.log(Level.CONFIG, "loaded: {0}", firstSpi);
                return firstSpi ;
            } catch (Exception e) {
                logger.log( Level.SEVERE, "error loading RiverClassLoaderSpi: {0}", new Object[]{e});
                throw new Error(e);
            }
        }
        
        return new DefaultRiverClassLoaderSpi();
    }
        
    public static Class<?> loadClass(String codebase, String name, ClassLoader defaultLoader)
        throws MalformedURLException, ClassNotFoundException
    {
        return spi.loadClass(codebase, name, defaultLoader);
    }

    public static Class<?> loadProxyClass(String codebase, String[] interfaceNames, ClassLoader defaultLoader)
        throws ClassNotFoundException, MalformedURLException
    {
        return spi.loadProxyClass(codebase, interfaceNames, defaultLoader);
    }

    public static String getClassAnnotation(Class<?> cls)
    {
        return spi.getClassAnnotation(cls);
    }

    public static ClassLoader getClassLoader(String codebase)
        throws MalformedURLException, SecurityException
    {
        return spi.getClassLoader(codebase);
    }

    public static Class<?> loadClass(String location, String className) 
        throws MalformedURLException, ClassNotFoundException
    {
        return spi.loadClass(className, className, null);
    }

    private static class DefaultRiverClassLoaderSpi 
        extends RiverClassLoaderSpi
    {

        public DefaultRiverClassLoaderSpi()
        {
        }

        @Override
        public Class<?> loadClass(String codebase, String name, ClassLoader defaultLoader) throws MalformedURLException, ClassNotFoundException
        {
            return RMIClassLoader.loadClass(codebase, name, defaultLoader);
        }

        @Override
        public Class<?> loadProxyClass(String codebase, String[] interfaceNames, ClassLoader defaultLoader) throws ClassNotFoundException, MalformedURLException
        {
            return RMIClassLoader.loadProxyClass(codebase, interfaceNames, defaultLoader);
        }

        @Override
        public String getClassAnnotation(Class<?> cl)
        {
            return RMIClassLoader.getClassAnnotation(cl); 
        }

        @Override
        public ClassLoader getClassLoader(String codebase) throws MalformedURLException
        {
            return RMIClassLoader.getClassLoader(codebase);
        }
    }
    
}
