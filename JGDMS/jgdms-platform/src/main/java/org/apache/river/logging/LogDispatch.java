/*
 * Copyright 2021 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.logging;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.apache.river.thread.NamedThreadFactory;

/**
 * Tool that allows logging to be dispatched, or handed off onto a queue
 * for processing. This is intended to allow logging to occur, where logging
 * may cause deadlock, or other problems.
 * 
 * @author peter
 */
public class LogDispatch {
    
    final static ExecutorService LOG_EXEC = 
		new ThreadPoolExecutor(0, 1, 1L, TimeUnit.SECONDS,
		    new LinkedBlockingQueue(),
		    new NamedThreadFactory("JGDMS LogDispatch", true)
		);
    
    private static Void check(Object o, String s) throws NullPointerException{
        if (o == null) throw new NullPointerException(s);
        return null;
    }
    
    private final Logger logger;
    private final String sourceClassName;
    
    /**
     * Construct a LogDispatch, dispatching log tasks to the designated logger using 
     * an ExecutorService.
     * 
     * @param logger the designated logger.
     * @param sourceClassName name of the Class to be reported in logs.
     */
    public LogDispatch(Logger logger, String sourceClassName){
        this(logger, sourceClassName, check(logger, "Logger cannot be null"));
    }
    
    private LogDispatch(Logger logger, String sourceClassName, Void loggerNotNull){
        this.logger = logger;
        this.sourceClassName = sourceClassName;
    }
    
    /**
     * Log a message, with no arguments.
     * <p>
     * If the logger is currently enabled for the given message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   message     The string message (or a key in the message catalog)
     */
    public void log(Level level, String message){
        log(level, message, null, null);
    }
    
    /**
     * Log a message, with associated Throwable information.
     * <p>
     * If the logger is currently enabled for the given message
     * level then the given arguments are stored in a LogRecord
     * which is forwarded to all registered output handlers.
     * <p>
     * Note that the thrown argument is stored in the LogRecord thrown
     * property, rather than the LogRecord parameters property.  Thus it is
     * processed specially by output Formatters and is not treated
     * as a formatting parameter to the LogRecord message property.
     *
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   message     The string message (or a key in the message catalog)
     * @param   thrown  Throwable associated with log message.
     */
    public void log(Level level, String message, Throwable thrown){
        log(level, message, null, thrown);
    }
    
    /**
     * Log a message, with an array of object arguments.
     * <p>
     * If the logger is currently enabled for the given message
     * level then a corresponding LogRecord is created and forwarded
     * to all the registered output Handler objects.
     *
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   message     The string message (or a key in the message catalog)
     * @param   parameters  array of parameters to the message
     */
    public void log(Level level, String message, Object[] parameters){
        log(level, message, parameters, null);
    }
    
    /**
     * Log a message, with associated Throwable information.
     * <p>
     * If the logger is currently enabled for the given message
     * level then the given arguments are stored in a LogRecord
     * which is forwarded to all registered output handlers.
     * <p>
     * Note that the thrown argument is stored in the LogRecord thrown
     * property, rather than the LogRecord parameters property.  Thus it is
     * processed specially by output Formatters and is not treated
     * as a formatting parameter to the LogRecord message property.
     *
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   message     The string message (or a key in the message catalog)
     * @param parameters    array of parameters to the message
     * @param   thrown  Throwable associated with log message.
     */
    public void log(final Level level,
		    final String message,
		    final Object[] parameters,
		    final Throwable thrown)
    {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = new LogRecord(level, message);
        lr.setSourceClassName(sourceClassName);
        lr.setParameters(parameters);
        lr.setThrown(thrown);
        log(lr);
	
    }
    
    /**
     * Log a message, specifying source class and method,
     * with an array of object arguments.
     * <p>
     * If the logger is currently enabled for the given message
     * level then a corresponding LogRecord is created and forwarded
     * to all the registered output Handler objects.
     *
     * @param   level   One of the message level identifiers, e.g., SEVERE
     * @param   sourceClass    name of class that issued the logging request
     * @param   sourceMethod   name of method that issued the logging request
     * @param   message     The string message (or a key in the message catalog)
     * @param   parameters  Array of parameters to the message
     */
    public void logp(Level level, String sourceClass, String sourceMethod,
                                                String message, Object[] parameters) {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = new LogRecord(level, message);
        lr.setSourceClassName(sourceClass);
        lr.setSourceMethodName(sourceMethod);
        lr.setParameters(parameters);
        log(lr);
    }
    
    /**
     * Log a LogRecord.
     *
     * @param record the LogRecord to be published
     */
    public void log(LogRecord record) {
        LOG_EXEC.submit(() -> {
            logger.log(record);
        });
    }
    
    /**
     * Check if a message of the given level would actually be logged
     * by this logger.  This check is based on the Loggers effective level,
     * which may be inherited from its parent.
     * 
     * Unlike other methods, this doesn't dispatch a task.
     *
     * @param   level   a message logging level
     * @return  true if the given message level is currently being logged.
     */
    public boolean isLoggable(Level level) {
        return logger.isLoggable(level);
    }
    
    /**
     * Get the name for this logger.
     * @return logger name.  Will be null for anonymous Loggers.
     */
    public String getName() {
        return logger.getName();
    }
    
}
