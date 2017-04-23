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

package org.apache.river.jeri.internal.runtime;

import java.net.InetAddress;
import java.net.SocketPermission;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.Guard;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.apache.river.config.LocalHostLookup;
import org.apache.river.logging.Levels;

/**
 * This was common security code in all ServerEndpoint.enumerateListenEndpoints
 * calls, rather than duplicate this code, due to it's performance and
 * security sensitivity, it has been separated here to avoid duplication.
 */
public class LocalHost {
    
    private final InetAddress localAdd;
    private final UnknownHostException exception;
    private final Guard exposeLocalAdd;
    private final Logger logger;
    private final Class clazz;
    
    public LocalHost(Logger logger, Class clazz) {
        /* The following was originally in 
         * enumerateListenEndpoints(ListenContext listenContext)
         * however, InetAddress.getLocalHost() proved to be a hotspot in 
         * mahalo RandomStressTests for test code, which was attempting to
         * stress Mahalo, but this was futile, given the test CPU usage
         * for the test itself was 10x Mahalo, which wasn't raising a sweat.
         * - Peter Firmstone 28th April 2014
         */
        this.logger = logger;
        this.clazz = clazz;
        InetAddress localAddr = null;
        UnknownHostException exc = null;
        try {
            localAddr = (InetAddress) AccessController.doPrivileged(
                new PrivilegedExceptionAction() {
                    public Object run() throws UnknownHostException {
                        return LocalHostLookup.getLocalHost();
                    }
                });
        } catch (PrivilegedActionException e) {
            Exception uhe = e.getException();
            if (uhe instanceof UnknownHostException){
                exc = (UnknownHostException) uhe;
                if (logger != null && logger.isLoggable(Levels.FAILED)){
                    logThrow(
			logger, Levels.FAILED, clazz, 
			"enumerateListenEndpoints",
			"LocalHostLookup.getLocalHost() throws", null, uhe);
                }
            }
        }
        
        localAdd = localAddr;
        exception = exc;
        // If exception occurs the localAdd will be null.
        exposeLocalAdd = new SocketPermission("localhost", "resolve");
    }
    
   public String check(String localHost, Object caller) throws UnknownHostException {
        if (localHost == null) {
            /*
            * Only expose UnknownHostException thrown directly by
            * InetAddress.getLocalHost if it would also be thrown
            * in the caller's security context; otherwise, throw
            * a new UnknownHostException without the host name.
            */
            if (exception != null){
                try {
                    exposeLocalAdd.checkGuard(null);
                } catch (SecurityException e){
                    throw new UnknownHostException("access to resolve local host denied");
                }
                throw exception;
            }
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		try {
		    sm.checkConnect(localAdd.getHostName(), -1);
		} catch (SecurityException e) {
                    if (logger != null && logger.isLoggable(Levels.FAILED)) {
			logThrow(
			    logger, Levels.FAILED, clazz, 
			    "enumerateListenEndpoints",
			    "enumerate listen endpoints for {0}\nthrows",
                            new Object[] {caller}, 
                            e
                        );
		    }
		    throw new SecurityException(
			"access to resolve local host denied");
		}
	    }
	    localHost = localAdd.getHostAddress();
	}
        return localHost;
    }
   
    
   
   /* -- Logging -- */

    /**
     * Logs a throw. Use this method to log a throw when the log message needs
     * parameters.
     *
     * @param logger logger to log to
     * @param level the log level
     * @param sourceClass class where throw occurred
     * @param sourceMethod name of the method where throw occurred
     * @param msg log message
     * @param params log message parameters
     * @param e exception thrown
     */
    static void logThrow(Logger logger,
			 Level level,
			 Class sourceClass,
			 String sourceMethod,
			 String msg,
			 Object[] params,
			 Throwable e)
    {
	LogRecord r = new LogRecord(level, msg);
	r.setLoggerName(logger.getName());
	r.setSourceClassName(sourceClass.getName());
	r.setSourceMethodName(sourceMethod);
	r.setParameters(params);
	r.setThrown(e);
	logger.log(r);
    }
    
}
