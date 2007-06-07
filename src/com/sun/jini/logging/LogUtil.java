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
package com.sun.jini.logging;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A set of static convenience methods used for logging.
 * This class cannot be instantiated.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.1
 */
public class LogUtil {
    
    /** This class cannot be instantiated. */
    private LogUtil() {
        throw new AssertionError(
            "com.sun.jini.logging.LogUtil cannot be instantiated");
    }    
    
    /**
     * Convenience method used to log a throw operation when message parameters 
     * and a Throwable are used. 
     *
     * @param logger logger to log to
     * @param level the log level
     * @param sourceClass class where throw occurred
     * @param sourceMethod name of the method where throw occurred
     * @param msg log message
     * @param params log message parameters
     * @param e exception thrown
     */
    public static void logThrow(Logger logger,
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