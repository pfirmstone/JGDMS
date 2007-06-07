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
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.space.InternalSpaceException;

/**
 *
 * @author Sun Microsystems, Inc.
 */
class SnapshotFile extends LogFile {
    private RandomAccessFile	snapshotFile = null;// current snapshot file
    private String		fileName = null; // current snapshot file name
    private String		previousFilename = null; // previous snapshot
    private ObjectOutputStream	out;	   // current snapshot stream
    private int			suffix;	   // the current suffix number

    /** Logger for logging persistent store related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.storeLoggerName);

    /**
     */
    SnapshotFile(String basePath, File[] recover) throws IOException {
	super(basePath);
	ArrayList snapshots = new ArrayList();
	suffix = existingLogs(snapshots);

	// Make sure there are at most two snapshots
	//
	if (snapshots.size() > 2)
	    throw new InternalSpaceException("More than two snapshot files "+
					"are present");

	// If there are two snapshots, delete the second (newest) one
	//
	if (snapshots.size() == 2) {
	    File file = (File)snapshots.get(1);
	    file.delete();

	    // if there are two files and suffix==1 then we restarted while
	    // writing the first (real) snapshot file. So report back null
	    // (snapshot.0 is a dummy file) and restart the file numbers.
	    //
	    if (suffix == 1) {
		suffix = 0;
		recover[0] = null;
	    } else {
		recover[0] = (File)(snapshots.get(0));
		previousFilename = recover[0].getName();
	    }
	} else if (snapshots.size() == 1) {
	    File file = (File)(snapshots.get(0));
	    previousFilename = file.getName();

	    // If there is one file, and the suffix==0 then we restarted
	    // sometime after the "file.delete()" line above and all that
	    // is left is the dummy .0 snapshot file.
	    //
	    if (suffix == 0)
		recover[0] = null;
	    else
	    	recover[0] = file;

	} else { // (snapshot.size() == 0) also (suffix == -1)

	    // first time, so create a dummy .0 file
	    next();
	    commit();
	    recover[0] = null;
	}
    }

    /**
     * Switch this over to the next path in the list
     */
    ObjectOutputStream next() throws IOException {

	suffix++;			// go to next suffix
	fileName = baseFile + suffix;
	snapshotFile = new RandomAccessFile(baseDir.getPath() + File.separator +
				       fileName, "rw");
	out = new ObjectOutputStream(new LogOutputStream(snapshotFile));
	return out;
    }

    void commit() throws IOException {
	if (snapshotFile != null) {
	    try {
		close();   	        // close the stream and the file
	    } catch (IOException ignore) { } // assume this is okay
	}

	// delete previous snapshot file if there was one
	if (previousFilename != null) {
	    File file = new File(baseDir, previousFilename);
	    file.delete();
	}
	previousFilename = fileName;
    }

    /**
     * Close the log, but don't remove it.
     */
    synchronized void close() throws IOException {
	if (snapshotFile != null) {
	    try {
		out.close();
		snapshotFile.close();
	    } finally {
		snapshotFile = null;
	    }
	}
    }

    /**
     * Override destroy so we can try to close snapshotFile before calling
     * super tries to delete all the files.
     */
    void destroy() {
	try {
	    close();
	} catch (Throwable t) {
	    // Don't let failure keep us from deleting the files we can	    
	}
	super.destroy();
    }
}
