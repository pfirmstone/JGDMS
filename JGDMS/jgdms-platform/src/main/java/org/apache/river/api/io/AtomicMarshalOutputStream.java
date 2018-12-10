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

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.rmi.MarshalledObject;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupDesc.CommandEnvironment;
import java.rmi.activation.ActivationGroupID;
import java.rmi.server.UID;
import java.security.Permission;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.x500.X500Principal;
import net.jini.export.ProxyAccessor;
import net.jini.export.DynamicProxyCodebaseAccessor;
import net.jini.io.MarshalOutputStream;
import org.apache.river.api.io.ActivationGroupDescSerializer.CmdEnv;

/**
 * This AtomicMarshalOutputStream, replaces a number of Java Object's in the stream 
 * with Serializer's that ordinarily would not be deserializable by 
 * AtomicMarshalInputStream or would not be safe to be deserialized, this 
 * includes, but is not limited to Java Collections classes, Throwable 
 * subclasses and object versions of primitive values.
 * 
 * @author peter
 */
public class AtomicMarshalOutputStream extends MarshalOutputStream {
    private static final Logger logger = Logger.getLogger("org.apache.river.api.io");
    
    final DelegateObjectOutputStream d;
    
    final ClassLoader defaultLoader;
    
    public AtomicMarshalOutputStream(OutputStream out, Collection context) throws IOException {
	this(out, null, context, false);
    }
    
    /**
     *
     * @param out
     * @param defaultLoader
     * @param context
     * @param writeCodebaseAnnotations if false stream format is generally compatible with 
     * ObjectOutputStream and annotations aren't written to stream, if true,
     * it's generally compatible with MarshalOutputStream and annotations are
     * written to stream.
     * @throws IOException
     */
    public AtomicMarshalOutputStream(OutputStream out,
				     ClassLoader defaultLoader,
				     Collection context,
				     boolean writeCodebaseAnnotations) 
	throws IOException
    {
	super(context);
	this.defaultLoader= defaultLoader;
	d = new DelegateObjectOutputStream(out, this, writeCodebaseAnnotations);
	d.enableReplaceObject(true);
    }
    
    @Override
    public final void writeObjectOverride(Object obj) throws IOException {
	d.writeObject(obj);
	// This is where we check the number of Object's cached and
	// reset if we're getting towards half our limit.
	// One day this limit will become too small.
	if (d.numObjectsCached > 32768) reset();
    }
    
    @Override
    protected void writeAnnotation(String annotation) throws IOException {
	super.writeAnnotation(annotation);
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
	boolean result = super.enableReplaceObject(enable);
        d.enableReplaceObject = enable;
	return result;
    }
    
    
    /**
     * This implementation replaces the following types in the stream:
     * 
     * 
     * @param obj
     * @return
     * @throws IOException 
     */
    @Override
    protected Object replaceObject(Object obj) throws IOException{
	return d.defaultReplaceObject(obj);
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
    
	final Map<Class,Class> serializers;
	final AtomicMarshalOutputStream aout;
	int numObjectsCached = 0;
	final boolean writeAnnotations;
	boolean enableReplaceObject;
	
	DelegateObjectOutputStream(OutputStream out, AtomicMarshalOutputStream aout, boolean writeCodebaseAnnotations) throws IOException{
	    super(out);
	    this.aout = aout;
	    this.writeAnnotations = writeCodebaseAnnotations;
	    this.serializers = new HashMap<Class,Class>(24);
	    serializers.put(Byte.class, ByteSerializer.class);
	    serializers.put(Short.class, ShortSerializer.class);
	    serializers.put(Integer.class, IntSerializer.class);
	    serializers.put(Long.class, LongSerializer.class);
	    serializers.put(Double.class, DoubleSerializer.class);
	    serializers.put(Float.class, FloatSerializer.class);
	    serializers.put(Character.class, CharSerializer.class);
	    serializers.put(Boolean.class, BooleanSerializer.class);
	    serializers.put(Properties.class, PropertiesSerializer.class);
	    serializers.put(URL.class, URLSerializer.class);
	    serializers.put(URI.class, URISerializer.class);
	    serializers.put(UID.class, UIDSerializer.class);
	    serializers.put(File.class, FileSerializer.class);
	    serializers.put(MarshalledObject.class, MarshalledObjectSerializer.class);
	    serializers.put(ActivationGroupDesc.class, ActivationGroupDescSerializer.class);
	    serializers.put(ActivationGroupID.class, ActivationGroupIDSerializer.class);
	    serializers.put(ActivationDesc.class, ActivationDescSerializer.class);
	    serializers.put(CommandEnvironment.class, CmdEnv.class);
	    serializers.put(StackTraceElement.class, StackTraceElementSerializer.class);
            serializers.put(X500Principal.class, X500PrincipalSerializer.class);
	    serializers.put(Date.class, DateSerializer.class);
	}
	
	@Override
	protected void annotateClass(Class<?> cl) throws IOException {
	    if (writeAnnotations) aout.annotateClass(cl);
	}

	@Override
	public void annotateProxyClass(Class<?> cl) throws IOException {
	    if (writeAnnotations) aout.annotateProxyClass(cl);
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
	    if (enableReplaceObject) {
		return aout.replaceObject(obj);
	    } else {
		return defaultReplaceObject(obj);
	    }
	}
	
	Object defaultReplaceObject(Object obj) throws IOException {
	    /* If we have a ProxyAccessor, lets replace it now, because it's
	     * likely to support AtomicSerial, unlike other serializers that don't.
	     */
	    logger.log(Level.FINEST, "Object in stream instance of: {0}", obj.getClass());
	    try {
		if (obj instanceof DynamicProxyCodebaseAccessor ){
		    logger.log(Level.FINEST, "Object in stream instance of DynamicProxyCodebaseAccessor");
		    obj = 
		    ProxySerializer.create(
			    (DynamicProxyCodebaseAccessor) obj,
			    aout.defaultLoader,
			    aout.getObjectStreamContext()
		    );
		} else if (obj instanceof ProxyAccessor ) {
		    logger.log(Level.FINEST, "Object in stream instance of ProxyAccessor");
		    obj = 
		    ProxySerializer.create(
			    (ProxyAccessor) obj,
			    aout.defaultLoader,
			    aout.getObjectStreamContext()
		    );
		}
	    } catch (IOException e) {
		logger.log(Level.FINE, "Unable to create ProxyAccessorSerializer", e);
		throw e;
	    }
	    Class c = obj.getClass();
	    Class s = serializers.get(c);
	    if (c.isAnnotationPresent(AtomicSerial.class)){} // Ignore
	    else if (c.isAnnotationPresent(AtomicExternal.class)){} // Ignore
	    // REMIND: stateless objects, eg EmptySet?
	    else if (s != null){
		try {
		    Constructor constructor = s.getDeclaredConstructor(c);
		    obj = constructor.newInstance(obj);
		} catch (NoSuchMethodException ex) {
		    logger.log(Level.FINE, "Unable to contruct serializer", ex);
		} catch (SecurityException ex) {
		    logger.log(Level.FINE, "Unable to contruct serializer", ex);
		} catch (InstantiationException ex) {
		    logger.log(Level.FINE, "Unable to contruct serializer", ex);
		} catch (IllegalAccessException ex) {
		    logger.log(Level.FINE, "Unable to contruct serializer", ex);
		} catch (IllegalArgumentException ex) {
		    logger.log(Level.FINE, "Unable to contruct serializer", ex);
		} catch (InvocationTargetException ex) {
		    logger.log(Level.FINE, "Unable to contruct serializer", ex);
		}
	    }
	    else if (obj instanceof Map) obj = new MapSerializer((Map) obj);
	    else if (obj instanceof Set) obj = new SetSerializer((Set) obj);
	    else if (obj instanceof Collection) obj = new ListSerializer((Collection) obj);
	    else if (obj instanceof Permission) obj = new PermissionSerializer((Permission) obj);
	    else if (obj instanceof Throwable) obj = new ThrowableSerializer((Throwable) obj);
	    logger.log(Level.FINEST, "Returning object in stream instance of: {0}", obj.getClass());
	    return obj;
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
