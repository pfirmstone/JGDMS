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
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

/**
 * Observer class that gets notified when a change has been noted,
 * that is a file has been found, by an Observable object. 
 * This class is normally passed into a class that extends 
 * <code>Observable</code>
 *
 * @author Sun Microsystems, Inc.
 *
 * @see java.util.Observable
 * @see java.util.Observer
 * @see com.sun.jini.system.FileWalker
 */
public class FileObserver implements Observer {
    private ArrayList fileList = (new ArrayList());
    private ArrayList timeList = (new ArrayList());
    private File currentFile = null;
    private int lcv = 0;
    private String suffix;

    /**
     * Constructor that allows the user to specify interest in a 
     * particular suffix. Only files with the provide suffix 
     * will be saved and returned to the user. All others will fall
     * through.
     * <p>
     * If suffix is an empty string then all files will be matched.
     */
    public FileObserver(String suffix) {
	this.suffix = suffix;
    }

    /**
     * Called when an Observer monitors a change.
     * When a change is noted by the item being observed,
     * in this case the given root build directory,
     * this method gets called.
     */
    public void update(Observable o,Object arg) {
	currentFile = (File)arg;
	if (currentFile.getName().endsWith(suffix)) {
	    //System.out.println("found file:"+currentFile.getName());
	    fileList.add(lcv,currentFile.getAbsolutePath());
	    timeList.add(lcv,new Long(currentFile.lastModified()));
	    lcv++;
	}
    }	

    /**
     * Get the complete list of found files. The
     * list contains String representations of the file names.
     * 
     * @return ArrayList - List of found files.
     */
    public ArrayList getFileList() {
	return fileList;
    }

    /**
     * Get the time stamps of the found files. There is a
     * 1-1 correspondence with the contents of getFileList.
     * The lit contains Long representations of the file time stamps.
     *
     * @see #getFileList
     * @return ArrayList - List of found files date stamps
     */
    public ArrayList getFileTimeList() {
	return timeList;
    }
}


