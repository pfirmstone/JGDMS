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
package com.sun.jini.outrigger.snaplogstore;

import com.sun.jini.config.Config;
import com.sun.jini.outrigger.LogOps;
import com.sun.jini.outrigger.Recover;
import com.sun.jini.outrigger.OutriggerServerImpl;
import com.sun.jini.outrigger.Store;
import com.sun.jini.system.FileSystem;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.space.InternalSpaceException;

/**
 * @author Sun Microsystems, Inc.
 *
 * @see com.sun.jini.outrigger.OutriggerServerImpl
 */
public class LogStore implements Store {
    private LogOutputFile	log;
    private final String	path;
    private BackEnd		be;
    private int			maxOps;

    /** Logger for logging persistent store related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.storeLoggerName);

    /**
     * Create a new <code>LogStore</code>.
     * @param config the directory to use for persistence.
     */
    public LogStore(Configuration config) throws ConfigurationException {
	path = (String)Config.getNonNullEntry(config,
				OutriggerServerImpl.COMPONENT_NAME,
				OutriggerServerImpl.PERSISTENCE_DIR_CONFIG_ENTRY,
				String.class);

	logger.log(Level.CONFIG, "using directory {0}", path);

	FileSystem.ensureDir(path);

	be = new BackEnd(path);

	maxOps = Config.getIntEntry(config, 
				OutriggerServerImpl.COMPONENT_NAME,
				"maxOps",
				1000, 1, Integer.MAX_VALUE);
    }

    /**
     * Setup store, recover previous state if any.
     *
     * @param space object used for recovery of previous state
     *
     * @return object used to persist state
     */
    public LogOps setupStore(Recover space) {
	try {
	    be.setupStore(space);

	    // Use the log type as the file prefix
	    //
	    log = new LogOutputFile(
		new File(path, LogFile.LOG_TYPE).getAbsolutePath(),
		maxOps);

	    log.observable().addObserver(be);
	} catch (IOException e) {
	    final String msg = "LogStore: log creation failed";
	    final InternalSpaceException ise = 
		new InternalSpaceException(msg, e);
	    logger.log(Level.SEVERE, msg, ise);
	    throw ise;
	}
	return log;
    }

    /**
     * Destroy everything.
     */
    public void destroy() throws IOException {
	be.destroy();
	log.destroy();
	new File(path).delete();
    }

    // Inherit from super
    public void close() throws IOException {
	be.close();
	log.close();
    }
}
