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
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.rmi.MarshalledObject;
import net.jini.core.event.RemoteEvent;

/**
 * This class provides the methods for reading <tt>RemoteEvent</tt>s from 
 * a given <tt>LogInputStream</tt>.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
class EventReader {

   /**
     * This class extends <tt>ObjectInputStream</tt> in order to obtain
     * object reading methods. This class is used in conjunction with 
     * <tt>SwitchInputStream</tt> in order to provide a re-targetable
     * input stream capability.
     */ 
    private static class EventInputStream extends ObjectInputStream {
		
	/**
	 * Simple constructor that passes its argument to the superclass
         *
         * @exception IOException if an I/O error occurs
	 */
	public EventInputStream(InputStream in) throws IOException {
	    super(in);
	}
	
	/**
	 * Overrides <tt>ObjectInputStream</tt>'s method with no-op
	 * functionality.  This prevents the underlying stream from being
	 * being accessed during construction. See <tt>SwitchInputStream</tt>.
         *
         * @exception IOException if an I/O error occurs
	 */
	protected void readStreamHeader() throws IOException {
	    // Do nothing
	}
    }

    /**
     * This class is intended to be the <tt>InputStream</tt> provided
     * to <tt>EventInputStream</tt>'s constructor. This class essentially
     * delegates <tt>InputStream</tt> functionality to the provided
     * <tt>InputStream</tt>. The <tt>setInputStream</tt> method
     * allows the underlying <tt>InputStream</tt> to be re-targeted
     * at runtime. 
     */
    private static class SwitchInputStream extends InputStream {
	
	/** The delegation target for the <tt>InputStream</tt> methods */
	private InputStream in;

        /**
         * Simple constructor that assigns the given argument to the
         * appropriate internal field.
         */
	public SwitchInputStream(InputStream in) {
	    this.in = in;
	}
	
	// documentation inherited from supertype
	public int read() throws IOException {
	    return in.read();
	}
	
	// documentation inherited from supertype
	public int read(byte[] b) throws IOException {
	    return in.read(b);
	}
	
	// documentation inherited from supertype
	public int read(byte[] b, int off, int len) throws IOException {
	    return in.read(b, off, len);
	}
	
	// documentation inherited from supertype
	public long skip(long n) throws IOException {
	    return in.skip(n);
	}
	
	// documentation inherited from supertype
	public int available() throws IOException {
	    return in.available();
	}
	
	// documentation inherited from supertype
	public void close() throws IOException {
	    in.close();
	}
	
	/** Sets the delegation target */
	public void setInputStream(InputStream in) {
	    this.in = in;
	}
    }

    /** Reference to <tt>EventInputStream</tt> for this class */
    private EventInputStream ein;

    /** Reference to <tt>SwitchInputStream</tt> for this class */
    private SwitchInputStream sin;

    /**
     * Simple constructor that creates the appropriate internal objects.
     *
     * @exception IOException if an I/O error occurs
     */
    public EventReader() throws IOException {
	sin = new SwitchInputStream(null);
	ein = new EventInputStream(sin);
    }

    /**
     * Returns the next available <tt>RemoteEvent</tt> from the stream.
     *
     * @exception IOException 
     *        Thrown if an I/O error occurs
     * @exception ClassNotFoundException Thrown if the class of a 
     *        serialized object cannot be found.
     */
    public RemoteEvent read(InputStream in) 
	throws IOException, ClassNotFoundException 
    {
	// Set the target stream
	sin.setInputStream(in);
	try {
	    // Retrieve next event which was stored as a 
	    // MarshalledObject and return its contents.
	    MarshalledObject mo = (MarshalledObject)ein.readObject();
	    return (RemoteEvent) mo.get();
	} finally {
	    // Reset target stream to null
	    sin.setInputStream(null);
	}
    }
}
