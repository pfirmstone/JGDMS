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

package org.apache.river.api.delegates;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FilePermission;
import java.io.IOException;
import java.security.AccessController;
import java.security.Guard;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author Peter Firmstone
 */
public class FileInputStream extends java.io.InputStream {
    private final Guard g;
    private final java.io.FileInputStream in;
    public FileInputStream(final String name) throws FileNotFoundException {
	Permission p = new FilePermission(name, "read");
	g = DelegatePermission.get(p);
	java.io.FileInputStream input = null;
	try {	    
	    // Permission check is delayed.
	    input = AccessController.doPrivileged(
		    new PrivilegedExceptionAction<java.io.FileInputStream>() {
		public java.io.FileInputStream run() throws FileNotFoundException {
		    return new java.io.FileInputStream(name);
		}
	    });
	} catch (PrivilegedActionException ex) {
	    constrEx(ex.getCause(), p);
	}
	in = input;
    }
    
    public FileInputStream(final File file) throws FileNotFoundException {
	Permission p = new FilePermission(file.getPath(), "read");
	g = DelegatePermission.get(p);
	java.io.FileInputStream input = null;
	try {
	    input = AccessController.doPrivileged(
		    new PrivilegedExceptionAction<java.io.FileInputStream>() {
		public java.io.FileInputStream run() throws FileNotFoundException {
		    return new java.io.FileInputStream(file);
		}
	    });
	} catch (PrivilegedActionException ex) {
	    constrEx(ex.getCause(), p);
	}
	in = input;
    }
    
    public FileInputStream(final FileDescriptor fdObj) {
	g = DelegatePermission.get(new RuntimePermission("readFileDescriptor"));
	in = AccessController.doPrivileged(new PrivilegedAction<java.io.FileInputStream>() {
	    public java.io.FileInputStream run() {
		return new java.io.FileInputStream(fdObj);
	    }
	});
    }
    
    private void constrEx(Throwable e, Permission p) throws FileNotFoundException {
	if ( e instanceof FileNotFoundException) {
		throw (FileNotFoundException)e;
	}
	if ( e instanceof SecurityException) {
	    Logger.getLogger(FileInputStream.class.getName())
		    .log(Level.SEVERE, "FileInputStream delegate missing: " +
		    p.toString() , e);
	    throw (SecurityException)e;
	}
	if ( e instanceof NullPointerException) {
	    throw (NullPointerException)e;
	}
	// We shouldn't get to here, if we do bail out, the client can't handle
	// it either.
	throw new RuntimeException(e);
    }

    @Override
    public int read() throws IOException {
	checkGuard();
	return in.read();
    }
    
    @Override
    public int read(byte b[]) throws IOException {
	checkGuard();
	return in.read(b);
    }
    
    @Override
    public int read(byte b[], int off, int len) throws IOException {
	checkGuard();
	return in.read(b, off, len);
    }
    
    @Override
    public long skip(long n) throws IOException {
	checkGuard();
	return in.skip(n);
    }
    
    @Override
    public int available() throws IOException {
	checkGuard();
	return in.available();
    }
    
    @Override
    public void close() throws IOException {
	in.close();
    }
    
    private void checkGuard() {
	try {
	    g.checkGuard(this);
	} catch (SecurityException e){
	    try {
		this.close();
	    } catch (IOException ex) {
		Logger.getLogger(FileInputStream.class.getName()).log(Level.SEVERE, null, ex);
	    }
	    throw e;
	}
    }
}
