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

package com.sun.jini.system;

import java.io.File;
import java.util.Observable;

/**
 * Class to recursively traverse a given directory. Each time
 * a file is found the Observer object added to this one will
 * be called.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class FileWalker extends Observable {

    /**
     * Method to call that starts the directory traversal.
     * Make sure that a FileObserver has been added prior
     * to invoking this method.
     * 
     * @param file  The top of the directory to traverse.
     * @param includeDirectories   If true it will recursively
     *                             traverse the given directory.
     *                             If false, just do the files 
     *                             currently found in the given
     *                             directory.
     * 
     * @see java.util.Observable
     * @see java.util.Observer
     * @see com.sun.jini.system.FileObserver
     */
    public void walk (File file, boolean includeDirectories) {
	if (file.isDirectory()) {
	    if (includeDirectories) {
		setChanged();
		notifyObservers(file);
	    }
	    String[] filenames = file.list();
	    if (filenames != null) {
		for (int i=0; i<filenames.length; i++) {
		    walk(new File(file,filenames[i]),includeDirectories);
		}
	    }
	} else {
	    setChanged();
	    notifyObservers(file);
	}
    }
}



