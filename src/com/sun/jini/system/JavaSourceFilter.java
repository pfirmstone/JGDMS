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
import java.io.FileFilter;

/**
 * Implementation of FilenameFilter to allow us to just
 * look for java files by default, or other file extensions if
 * the user wishes to use a different extension.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class JavaSourceFilter implements FileFilter {
    /**
     * The extension that we we want to look for.
     */
    private String fileExtension;

    /**
     * Create a filter for *.java files.
     */
    public JavaSourceFilter() {
	this(".java");
    }

    /**
     * Create a filter for a user defined extension.
     * If an empty string is passed then all files
     * will be matched.
     *
     * @param fileExtension  The extension the user is looking for.
     */
    public JavaSourceFilter(String fileExtension) {
	this.fileExtension = fileExtension;
    }

    /**
     * Method required by FilenameFilter interface.
     * It is called by File.list() to get a subset of
     * the files in the given directory.
     *
     * @param filename The current file we are looking at.
     */
    public boolean accept(File filename){
	return (filename.toString().endsWith(fileExtension));
    }
}




