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

package org.apache.river.logging;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>
 * Defines a {@link java.util.logging.LogManager} that insures that the {@link
 * Levels#FAILED Levels.FAILED} and {@link Levels#HANDLED Levels.HANDLED}
 * fields, instances of {@link Level}, have been initialized, and that can
 * periodically check for changes to the logging configuration file and force
 * it to be reread.  Use this class as the value of the
 * <code>java.util.logging.manager</code> system property to permit specifying
 * the symbolic names for the <code>FAILED</code> and <code>HANDLED</code>
 * logging levels in standard logging configuration files, or to allow changes
 * to the logging configuration file to be noticed. </p>
 * <p>
 * The <code>org.apache.river.logging.interval</code> logging property (obtained
 * using {@link java.util.logging.LogManager#getProperty
 * LogManager.getProperty}) specifies the time interval in milliseconds
 * between probes to see if the logging configuration file has changed;
 * periodic checking only takes place if the value is greater than zero. (If a
 * new logging configuration file is read, this property can be redefined.)
 * The logging configuration file is specified by the
 * <code>java.util.logging.config.file</code> system property (which is
 * sampled at every probe), if defined, otherwise it is the
 * <code>logging.properties</code> file in the <code>lib</code> subdirectory
 * of the directory specified by the <code>java.home</code> system property.
 * The file is read if the name of the file differs from that used in the
 * previous probe or if the file has a different modification time. </p>
 * <p>
 * This implementation uses the {@link java.util.logging.Logger} named
 * <code>org.apache.river.logging.LogManager</code> to log information at the
 * following logging levels: </p>
 *
 * <table border="1" cellpadding="5" summary="Describes logging performed
 *	  by the LogManager class at different logging levels">
 * <caption><b><code>org.apache.river.logging.LogManager</code></b></caption>
 * <tr><th scope="col">Level<th scope="col">Description
 * <tr><td>{@link Level#WARNING WARNING}<td>if an exception occurs while
 * rereading the logging configuration file
 * <tr><td>{@link Level#CONFIG CONFIG}<td>each time the logging configuration
 * file is successfully reread
 * <tr><td>{@link Level#CONFIG CONFIG}<td>termination of probes because
 * interval is less than or equal to zero
 * </table>
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class LogManager extends java.util.logging.LogManager {
    private Probe probe = null;

    /** Creates an instance of this class. */
    public LogManager() {
	/* Refer to the levels to make sure that they are defined */
	Levels.FAILED.toString();
	Levels.HANDLED.toString();
    }

    /**
     * Reinitialize the logging properties and reread the logging
     * configuration, and initiate probes if the probe interval is greater
     * than zero.
     */
    public void readConfiguration(InputStream ins) throws IOException {
	super.readConfiguration(ins);
	synchronized (this) {
	    if (probe == null) {
		long interval = getInterval();
		if (interval > 0) {
		    probe = new Probe(interval);
		    probe.start();
		}
	    }
	}
    }

    /** Return the probe interval. */
    private long getInterval() {
	String val = getProperty("org.apache.river.logging.interval");
	if (val != null) {
	    try {
		return Long.decode(val).longValue();
	    } catch (NumberFormatException e) {
	    }
	}
	return 0;
    }

    /** Return the logging configuration file name. */
    private static File getFile() {
	String fname = System.getProperty("java.util.logging.config.file");
	if (fname != null) {
	    return new File(fname);
	} else {
	    return new File(System.getProperty("java.home"),
			    "lib" + File.separator + "logging.properties");
	}
    }

    /** Thread to probe for config file changes and force reread */
    private class Probe extends Thread {
	/** Time in milliseconds between probes */
	private long interval;
	/** The last file read */
	private File prevFile;
	/** The lastModified time of prevFile */
	private long prevModified;

	Probe(long interval) {
	    super("LogManager config file probe");
	    setDaemon(true);
	    this.interval = interval;
	    prevFile = getFile();
	    prevModified = prevFile.lastModified();
	}

	public void run() {
	    Logger logger =
		Logger.getLogger("org.apache.river.logging.LogManager");
	    try {
		while (interval > 0) {
		    Thread.sleep(interval);
		    File file = getFile();
		    long lastModified = file.lastModified();
		    if (lastModified > 0 &&
			(!file.equals(prevFile) ||
			 lastModified != prevModified))
		    {
			try {
			    readConfiguration();
			    interval = getInterval();
			    logger.log(Level.CONFIG,
				       "logging config file reread complete," +
				       " new interval is {0}",
				       Long.valueOf(interval));
			} catch (Throwable t) {
			    try {
                                logger.log(Level.WARNING,
                                    "exception reading logging config file",t);
                            } catch (Throwable t2) {}
			}
			prevFile = file;
			prevModified = lastModified;
		    }
		}
	    } catch (InterruptedException e) {
	    } finally {
		synchronized (LogManager.this) {
		    probe = null;
		}
		logger.config("logging config file probe terminating");
	    }
	}
    }
}
