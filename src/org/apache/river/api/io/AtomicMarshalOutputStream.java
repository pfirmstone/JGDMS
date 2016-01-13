/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.api.io;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.security.Permission;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import net.jini.io.MarshalOutputStream;

/**
 *
 * @author peter
 */
public class AtomicMarshalOutputStream extends MarshalOutputStream {
    
    final DelegateObjectOutputStream d;
    
    
    public AtomicMarshalOutputStream(OutputStream out, Collection context) throws IOException {
	this(out, context, false);
    }
    
    /**
     *
     * @param out
     * @param context
     * @param objectOutputStreamMode if true stream format is compatible with 
     * ObjectOutputStream, if false, it's compatible with MarshalOutputStream.
     * @throws IOException
     */
    public AtomicMarshalOutputStream(OutputStream out, Collection context, boolean objectOutputStreamMode) throws IOException{
	super(context);
	d = new DelegateObjectOutputStream(out, this, objectOutputStreamMode);
    }

    @Override
    public void writeObjectOverride(Object obj) throws IOException {
	d.writeObject(obj);
	// This is where we check the number of Object's cached and
	// reset if we're getting towards half our limit.
	// One day this limit will become too small.
	if (d.numObjectsCached > 32768) reset();
    }
    
    @Override
    public void writeUnshared(Object obj) throws IOException {
	d.writeUnshared(obj);
    }

    @Override
    public void write(int b) throws IOException {
	d.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
	d.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
	d.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
	d.flush();
    }

    @Override
    public void close() throws IOException {
	d.close();
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
	d.writeBoolean(v);
    }

    @Override
    public void writeByte(int v) throws IOException {
	d.writeByte(v);
    }

    @Override
    public void writeShort(int v) throws IOException {
	d.writeShort(v);
    }

    @Override
    public void writeChar(int v) throws IOException {
	d.writeChar(v);
    }

    @Override
    public void writeInt(int v) throws IOException {
	d.writeInt(v);
    }

    @Override
    public void writeLong(long v) throws IOException {
	d.writeLong(v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
	d.writeFloat(v);
    }

    @Override
    public void writeDouble(double v) throws IOException {
	d.writeDouble(v);
    }

    @Override
    public void writeBytes(String s) throws IOException {
	d.writeBytes(s);
    }

    @Override
    public void writeChars(String s) throws IOException {
	d.writeChars(s);
    }

    @Override
    public void writeUTF(String s) throws IOException {
	d.writeUTF(s);
    }
 
    @Override
    protected void drain() throws IOException {
	d.drain();
    }
    
    @Override
    protected boolean enableReplaceObject(boolean enable)
        throws SecurityException
    {
        return d.enableReplaceObject(enable);
    }
    
    @Override
    protected void writeClassDescriptor(ObjectStreamClass desc)
	throws IOException
    {
	d.writeClassDesc(desc);
    }
    
    @Override
    public void useProtocolVersion(int version) throws IOException {
	d.useProtocolVersion(version);
    }
    
    /**
     * Reset will disregard the state of any objects already written to the
     * stream.  The state is reset to be the same as a new ObjectOutputStream.
     * The current point in the stream is marked as reset so the corresponding
     * ObjectInputStream will be reset at the same point.  Objects previously
     * written to the stream will not be referred to as already being in the
     * stream.  They will be written to the stream again.
     *
     * @throws  IOException if reset() is invoked while serializing an object.
     */
    @Override
    public void reset() throws IOException {
	d.numObjectsCached = 0;
	d.reset();
    }
    
    @Override
    public void defaultWriteObject() throws IOException {
	d.defaultWriteObject();
    }
    
    @Override
    public ObjectOutputStream.PutField putFields() throws IOException {
	return d.putFields();
    }
    
    @Override
    public void writeFields() throws IOException {
	d.writeFields();
    }
    
    private static final class DelegateObjectOutputStream extends ObjectOutputStream {
    
	final AtomicMarshalOutputStream aout;
	int numObjectsCached = 0;
	final boolean objectOutputStreamMode;
	
	DelegateObjectOutputStream(OutputStream out, AtomicMarshalOutputStream aout, boolean objectOutputStreamMode) throws IOException{
	    super(out);
	    this.aout = aout;
	    this.objectOutputStreamMode = objectOutputStreamMode;
	}
	
	    @Override
	protected void annotateClass(Class<?> cl) throws IOException {
	    if (objectOutputStreamMode) super.annotateClass(cl);
	    aout.annotateClass(cl);
	}

	@Override
	public void annotateProxyClass(Class<?> cl) throws IOException {
	    if (objectOutputStreamMode) super.annotateProxyClass(cl);
	    else aout.annotateProxyClass(cl);
	}

	@Override
	public void drain() throws IOException {
	    super.drain();
	}

	/**
	 * Important objects that cannot be safely serialized are substituted.
	 */
	@Override
	public Object replaceObject(Object obj) throws IOException {
	    numObjectsCached++;
	    if (obj instanceof Map){
		obj = new SerialMap((Map) obj);
	    } else if (obj instanceof Set){
		obj = new SerialSet((Set) obj);
	    } else if (obj instanceof Collection){
		obj = new SerialList((Collection) obj);
	    } else if (obj instanceof Permission) {
		obj = new PermissionSerializer((Permission) obj);
	    }
	    return aout.replaceObject(obj);
	}

	@Override
	public void writeClassDescriptor(ObjectStreamClass desc)
	    throws IOException
	{
	    aout.writeClassDescriptor(desc);
	}
	
	void writeClassDesc(ObjectStreamClass desc) throws IOException {
	    super.writeClassDescriptor(desc);
	}

	@Override
	public boolean enableReplaceObject(boolean enable)
	    throws SecurityException
	{
	    return super.enableReplaceObject(enable);
	}
    }
    
}
