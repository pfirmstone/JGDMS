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
import java.io.OutputStream;
import java.security.AccessController;
import java.security.Guard;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.security.CachingSecurityManager;

/**
 * <p>This is a simple FileOutputStream delegate to replace a java.io.FileOutputStream
 * in code that is downloaded where trust is not assurred.
 * </p><p>
 * The client must have a DelegatePermission representing a FilePermission, or
 * a FilePermission, the
 * DelegatePermission is assigned to prevent a client from bypassing the
 * delegate and creating a java.io.FileOutputStream.
 * </p><p>
 * The next step of course is to determine any additional features that are
 * desireable.
 * </p>
 * 
 * 
 * @author Peter Firmstone
 */
public class FileOutputStream extends OutputStream {
    private final java.io.FileOutputStream out;
    private final Guard g;
    
    public FileOutputStream(final String name, final boolean append) 
	    throws FileNotFoundException {
	Permission p = new FilePermission(name, "write");
	g = DelegatePermission.get(p);
	java.io.FileOutputStream output = null;
        SecurityManager sm = System.getSecurityManager();
        if (sm instanceof CachingSecurityManager){
            try {
                output = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<java.io.FileOutputStream>() {
                        public java.io.FileOutputStream run() throws FileNotFoundException {
                            return new java.io.FileOutputStream(name, append);
                        }
                    }
                );
            } catch (PrivilegedActionException ex) {
                Throwable e = ex.getCause();
                constrEx(e,p);
            }
        }else{
            /* No CachingSecurityManager installed, since guard doesn't
             * do anything, don't create FileOutputStream using
             * doPrivileged or it will create a security vulnerability.
             */
            output = new java.io.FileOutputStream(name, append);
        }
	out = output;
    }
    
    public FileOutputStream(String name) throws FileNotFoundException{
        this(name, false);
    }
	
    public FileOutputStream(final File file, final boolean append) throws FileNotFoundException {
	Permission p = new FilePermission(file.getPath(),"write");
	g = DelegatePermission.get(p);
	java.io.FileOutputStream output = null;
        SecurityManager sm = System.getSecurityManager();
        if (sm instanceof CachingSecurityManager){
            try {
                output = AccessController.doPrivileged(
                        new PrivilegedExceptionAction<java.io.FileOutputStream>() {
                    public java.io.FileOutputStream run() throws FileNotFoundException {
                        return new java.io.FileOutputStream(file, append);
                    }
                });
            } catch (PrivilegedActionException ex) {	    
                Throwable e = ex.getCause();
                constrEx(e,p);
            }
        } else {
            /* No CachingSecurityManager installed, since guard doesn't
             * do anything, don't create FileOutputStream using
             * doPrivileged or it will create a security vulnerability.
             */
            output = new java.io.FileOutputStream(file, append);
        }
	out = output;
    }
	
    public FileOutputStream(File file) throws FileNotFoundException, Throwable {
        this(file, false);
    }
	
    public FileOutputStream(final FileDescriptor fdObj) {
	g = DelegatePermission.get(new RuntimePermission("readFileDescriptor"));
        java.io.FileOutputStream output = null;
        SecurityManager sm = System.getSecurityManager();
        if (sm instanceof CachingSecurityManager){
            output = AccessController.doPrivileged(new PrivilegedAction<java.io.FileOutputStream>() {
                public java.io.FileOutputStream run() {
                    return new java.io.FileOutputStream(fdObj);
                }
            });
        }else{
            output = new java.io.FileOutputStream(fdObj);
        }
        out = output;
    }
    
    private void constrEx(Throwable e, Permission p) throws FileNotFoundException {
	if ( e instanceof FileNotFoundException) {
		throw (FileNotFoundException)e;
	}
	if ( e instanceof SecurityException) {
	    Logger.getLogger(FileOutputStream.class.getName())
		    .log(Level.SEVERE, "FileOutputStream delegate missing: " +
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

    @Override
    public void write(int b) throws IOException {
	checkGuard();
	out.write(b);
    }
    
    @Override
    public void write(byte[] b) throws IOException{
	checkGuard();
	out.write(b);
    }
    
    @Override
    public void write(byte[]b, int off, int len) throws IOException{
	checkGuard();
	out.write(b, off, len);
    }
    
    @Override
    public void close() throws IOException {
	out.close();
    }
}
