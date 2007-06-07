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

import com.sun.jini.outrigger.OutriggerServerImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The base class for the logging file classes.  This class provides
 * the common functionality, but you should not instantiate it.
 * <p>
 * (Note -- I use <code>protected</code> here as an advisory notice.
 * Clearly, since this is package code, all classes in the package have
 * access, but fields marked <code>protected</code> are expected to be
 * used only by subclasses.  Use good taste.)
 *
 * @author Sun Microsystems, Inc.
 *
 * @see LogOutputFile
 * @see LogInputFile
 */
class LogFile {
    /**
     * The directory in which the log files live.
     */
    protected File baseDir;

    /**
     * The base part of the file name (e.g., <code>"log."</code> for
     * <code>"log.0"</code>, <code>"log.1"</code>, ...)
     */
    protected String baseFile;

    /**
     * The type of log stream
     */
    static final String LOG_TYPE = "LogStore";

    /**
     * The version of the log stream (the highest one known).
     */
    protected static final int LOG_VERSION = 3;

    /** A log entry that records a boot. */
    protected static final byte BOOT_OP		= 1;
    /** A log entry that records the join state. */
    protected static final byte JOINSTATE_OP	= 11;
    /** A log entry that records a <code>write</code>. */
    protected static final byte WRITE_OP	= 2;
    /** A log entry that records a <code>take</code>. */
    protected static final byte TAKE_OP		= 3;
    /** A log entry that records a <code>notify</code>. */
    protected static final byte REGISTER_OP	= 4;
    /** A log entry that records a <code>notify</code>. */
    protected static final byte RENEW_OP	= 5;
    /** A log entry that records a notification and new sequence number. */
    protected static final byte NOTIFIED_OP	= 6;
    /** A log entry that records a <code>cancel</code>. */
    protected static final byte CANCEL_OP	= 7;
    /** A log entry that records a transaction <code>prepare</code>. */
    protected static final byte PREPARE_OP	= 8;
    /** A log entry that records a transaction <code>commit</code>. */
    protected static final byte COMMIT_OP	= 9;
    /** A log entry that records a transaction <code>abort</code>. */
    protected static final byte ABORT_OP	= 10;
    /** A log entry that records the service's <code>Uuid</code>. */
    protected static final byte UUID_OP 	= 12;
    /** A log entry that records a batch <code>write</code>. */
    protected static final byte BATCH_WRITE_OP 	= 13;
    /** A log entry that records a batch <code>take</code>. */
    protected static final byte BATCH_TAKE_OP 	= 14;

    /** Logger for logging persistent store related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.storeLoggerName);

    /**
     * Create a log file with the given base directory, base file name
     * within the directory.  This is intended only for
     * when you are sure of the exact values -- no modifications are
     * made to the values.
     */
    protected LogFile(File baseDir, String baseFile) {
	this.baseDir = baseDir;
	this.baseFile = baseFile;
    }

    /**
     * Create a log file from the given template.  If
     * <code>basePath</code> has a directory component, it is used as
     * the base directory.  Otherwise the base directory is
     * <code>"."</code>.  If <code>basePath</code> names a directory,
     * the base name will be <code>""</code>.  Otherwise the file
     * component is used as the base, with a "." added at the end if it
     * is not already present.
     */
    protected LogFile(String basePath) throws IOException {
	baseDir = new File(basePath);
	if (baseDir.isDirectory()) {
	    baseFile = "";
	} else {
	    baseFile = baseDir.getName();
	    String pname = baseDir.getParent();
	    if (pname == null)
		pname = ".";
	    baseDir = new File(pname);
	    if (baseFile.charAt(baseFile.length() - 1) != '.')
		baseFile += ".";
	}
    }

    /**
     * Fill in a list of existing matching log files, oldest to newest,
     * returning the highest number used as a suffix, or -1 if
     * no files were found.  If two files have the same time, they are
     * sorted by the numeric value of the suffix.
     */
    int existingLogs(Collection files) {
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE, "scanning {0} for {1} baseFile",
		       new Object[]{baseDir, baseFile});
	}

	String[] inDir = baseDir.list();	// directory contents
	TreeMap found = new TreeMap();		// the files we've found
	int highest = -1;			// largest # suffix seen

	// no directory or files (can happen on destroy)
	if (inDir == null)
	    return highest;

    fileLoop:
	for (int f = 0; f < inDir.length; f++) {

	    String name = inDir[f];

	    logger.log(Level.FINE, "checking {0}", name);

	    if (!name.startsWith(baseFile))		// is it one of ours?
		continue;

	    // ensure that there is a numerical suffix
	    int num;
	    try {
		num = Integer.parseInt(name.substring(baseFile.length()));
		if (num > highest)		       // keep track of highest
		    highest = num;
	    } catch (NumberFormatException e) {
		continue fileLoop;		       // can't be one of ours
	    }

	    found.put(new Integer(num), new File(baseDir, name));
	}

	files.addAll(found.values());
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE, "returning {0} files",
		       new Integer(files.size()));
	    Iterator it = files.iterator();
	    while (it.hasNext())
		logger.log(Level.FINE, it.next().toString());
	}

	return highest;
    }

    /**
     * Destroy all log files associated with this stream.
     */
    void destroy() {
	if (logger.isLoggable(Level.FINE))
	    logger.log(Level.FINE, "destroy");

	ArrayList files = new ArrayList();
	existingLogs(files);
	for (int i = 0; i < files.size(); i++) {
	    File log = (File) files.get(i);
	    try {
		if (!log.delete()) {
		    logger.log(Level.INFO, "Could not delete {0}", log);
		}
	    } catch (SecurityException e) {
		if (!log.delete()) {
		    logger.log(Level.INFO,
			       "SecurityException : Could not delete " + log,
			       e);
		}
	    }
	}
    }
}
