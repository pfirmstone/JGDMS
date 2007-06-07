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
package com.sun.jini.mercury;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.SyncFailedException;
import java.rmi.MarshalledObject;
import net.jini.core.event.RemoteEvent;

/**
 * This class provides the interface for writing <tt>RemoteEvent</tt>s to 
 * a given <tt>LogOutputStream</tt>.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
class EventWriter {
    
    /**
     * This class extends <tt>ObjectOutputStream</tt> in order to obtain
     * object writing methods. This class is used in conjunction with 
     * <tt>SwitchOutputStream</tt> in order to provide a re-targetable
     * output stream capability.
     */
    private static class EventOutputStream extends ObjectOutputStream {
	
	/**
	 * Simple constructor that passes its argument to the superclass
         *
         * @exception IOException if an I/O error occurs
	 */
	public EventOutputStream(OutputStream out) throws IOException {
	    super(out);
	}
	
	/**
	 * Overrides <tt>ObjectOutputStream</tt>'s method with no-op
	 * functionality.  This prevents the underlying stream from being
	 * being accessed during construction. See <tt>SwitchOutputStream</tt>.
         *
         * @exception IOException if an I/O error occurs
	 */
	protected void writeStreamHeader() throws IOException {
	    // Do nothing
	}
    }

    /**
     * This class is intended to be the <tt>OutputStream</tt> provided
     * to <tt>EventOutputStream</tt>'s constructor. This class essentially
     * delegates <tt>OutputStream</tt> functionality to the provided
     * <tt>LogOutputStream</tt>. The <tt>setOutputStream</tt> method
     * allows the underlying <tt>LogOutputStream</tt> to be re-targeted
     * at runtime. 
     */
    private static class SwitchOutputStream extends OutputStream {
	
	/** The delegation target for the <tt>OutputStream</tt> methods */
	private LogOutputStream out;

        /**
         * Simple constructor that assigns the given argument to the
         * appropriate internal field.
         */
	public SwitchOutputStream(LogOutputStream out) {
	    this.out = out;
	}
	
	// documentation inherited from supertype
	public void write(int b) throws IOException {
	    out.write(b);
	}
	
	// documentation inherited from supertype
	public void write(byte[] b) throws IOException {
	    out.write(b);
	}
	
	// documentation inherited from supertype
	public void write(byte[] b, int off, int len) throws IOException {
	    out.write(b, off, len);
	}
	
	// documentation inherited from supertype
	public void flush() throws IOException {
	    out.flush();
	}
	
	// documentation inherited from supertype
	public void close() throws IOException {
	    out.close();
	}
	
	/** Sets the delegation target */
	public void setOutputStream(LogOutputStream out) {
	    this.out = out;
	}

    }
    
    /** Reference to <tt>EventOutputStream</tt> for this class */
    private EventOutputStream eout;

    /** Reference to <tt>SwitchOutputStream</tt> for this class */
    private SwitchOutputStream sout;

    /**
     * Simple constructor that creates the appropriate internal objects.
     *
     * @exception IOException if an I/O error occurs
     */
    public EventWriter() throws IOException {
	// default delegation target is null
	sout = new SwitchOutputStream(null);

	// Use the "switchable" output stream as the target
	eout = new EventOutputStream(sout);
    }
    
    /**
     * Writes the given <tt>RemoteEvent</tt> to the provided
     * <tt>LogOutputStream</tt>. It also attempts to synchronize
     * the write with the underlying storage mechanism.
     *
     * @exception IOException 
     *        Thrown if an I/O error occurs
     * @exception SyncFailedException
     *	      Thrown when the buffers cannot be flushed,
     *	      or because the system cannot guarantee that all the
     *	      buffers have been synchronized with physical media. 
     */
    public void write(RemoteEvent ev, LogOutputStream out) 
        throws IOException, SyncFailedException
    {
        // Set stream target
	sout.setOutputStream(out);
	try {
	    // Wrap the event as a MarshalledObject so that
	    // we can ensure a successful read later on (i.e.
	    // should always be able to read MarshalledObject
	    // but we might not be able to reconstruct a 
	    // RemoteEvent object because of codebase problems).
	    MarshalledObject mo = new MarshalledObject(ev);
	    eout.reset();
	    eout.writeObject(mo);
	    eout.flush();
	    out.sync();
	} finally {
	    // reset target to null
	    sout.setOutputStream(null);
	}
    }
}
