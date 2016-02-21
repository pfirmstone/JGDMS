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

package org.apache.river.api.io;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 *
 * @author peter
 */
@Serializer(replaceObType = Throwable.class)
@AtomicSerial
class ThrowableSerializer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("clazz", Throwable.class),
	    new ObjectStreamField("message", String.class),
	    new ObjectStreamField("cause", Throwable.class),
	    new ObjectStreamField("stack", StackTraceElement[].class),
	    new ObjectStreamField("suppressed", Throwable[].class)
	};
    
    private static final Logger logger = Logger.getLogger("org.apache.river.api.io.ThrowableSerializer");
    
    private final /*transient*/ Throwable throwable;
    private final Class<? extends Throwable> clazz;
    private final String message;
    private final Throwable cause;
    private final StackTraceElement[] stack;
    private final Throwable [] suppressed;
    
    ThrowableSerializer(Throwable t){
	throwable = t;
	clazz = t.getClass();
	message = t.getMessage();
	Throwable caus = t.getCause();
	if (caus == t) cause = null;
	else cause = caus;
	stack = t.getStackTrace();
	suppressed = t.getSuppressed();
    }
    
    public ThrowableSerializer(GetArg arg) throws IOException{
	this(check(arg));
    }
    
    private static Throwable check(GetArg arg) throws IOException{
	Class<? extends Throwable> clas = Valid.notNull(arg.get("clazz", null, Class.class), "clazz cannot be null");
	logger.log(Level.FINER, "deserializing {0}", clas);
	String message = arg.get("message", null, String.class);
	Throwable cause = arg.get("cause", null, Throwable.class);
	if (cause == null) {
	    logger.finer("cause is null");
	}
	StackTraceElement[] stack = arg.get("stack", null, StackTraceElement[].class);
	Throwable[] suppressed = arg.get("suppressed", null, Throwable[].class);
	Throwable result = init(clas, message, cause);
	if (stack != null) result.setStackTrace(stack);
	// Only adds suppressed if enabled by Throwable protected constructor.
	if (suppressed != null){ // compat with serial form of Throwable before Java 1.7
	    for (int i = 0, l = suppressed.length; i < l; i++){
		result.addSuppressed(suppressed[i]);
	    }
	}
	return result;
    }
    
    private static final Class[] stparams = new Class[]{String.class, Throwable.class};
    private static final Class[] separams = new Class[]{String.class, Exception.class};
    private static final Class[] serparams = new Class[]{String.class, Error.class};
    private static final Class[] tsparams = new Class[]{Throwable.class, String.class};
    private static final Class[] sparam = new Class[]{String.class};
    
    static Throwable init(Class<? extends Throwable> clas, String message, Throwable cause) throws IOException {
	Throwable result;
	try {
	    Constructor [] cons = clas.getConstructors();
	    for (int i = 0, l = cons.length; i < l; i++){
		Class [] params = cons[i].getParameterTypes();		
		if (Exception.class.isInstance(cause) && Arrays.equals(params, separams)){
		    return (Throwable) cons[i].newInstance(new Object[]{message,(Exception) cause});
		}
		if (Error.class.isInstance(cause) && Arrays.equals(params, serparams)){
		    return (Throwable) cons[i].newInstance(new Object[]{message,(Error) cause});
		}
		if (Arrays.equals(params, stparams)){
		    return (Throwable) cons[i].newInstance(new Object[]{message, cause});
		    
		}
		if (Arrays.equals(params, tsparams)){
		    return (Throwable) cons[i].newInstance(new Object[]{cause, message});
		    
		}
		if (Arrays.equals(params, sparam)){
		    result = (Throwable) cons[i].newInstance(new Object[]{message});
		    if (cause != null){
			if (RemoteException.class.isAssignableFrom(clas)){
			    ((RemoteException) result).detail = cause;
			} else {
			    try {
				result.initCause(cause);
			    } catch (IllegalStateException e){
				throw new IOException("Unable to construct " + clas + " cause already defined: " + result.getCause(), e);
			    }
			} 
		    }
		    return result;
		}
	    }
	    throw throIO(new InstantiationException("No suitable constructor found for class " + clas));
	} catch (SecurityException ex) {
	    throw throIO(ex);
	} catch (InstantiationException ex) {
	    throw throIO(ex);
	} catch (IllegalAccessException ex) {
	    throw throIO(ex);
	} catch (IllegalArgumentException ex) {
	    throw throIO(ex);
	} catch (InvocationTargetException ex) {
	    throw throIO(ex);
	}
    }
    
    static IOException throIO(Exception cause){
	return new IOException(cause);
    }

    @Override
    public int hashCode() {
	int hash = 3;
	hash = 31 * hash + Objects.hashCode(this.clazz);
	hash = 31 * hash + Objects.hashCode(this.message);
	hash = 31 * hash + Objects.hashCode(this.cause);
	hash = 31 * hash + Arrays.deepHashCode(this.stack);
	hash = 31 * hash + Arrays.deepHashCode(this.suppressed);
	return hash;
    }
    
    @Override
    public boolean equals(Object obj){
	if (this == obj) return true;
	if (!(obj instanceof ThrowableSerializer)) return false;
	ThrowableSerializer that = (ThrowableSerializer) obj;
	if (!Objects.equals(clazz, that.clazz)) return false;
	if (!Objects.equals(message, that.message)) return false;
	if (!Objects.equals(cause, that.cause)) return false;
	if (!Objects.deepEquals(stack, that.stack)) return false;
	return Objects.deepEquals(suppressed, that.suppressed);
    }
    
    Object readResolve() throws ObjectStreamException {
	if (throwable != null) return throwable;
	// The following is for standard java serialization, as throwable will be null.
	Throwable result;
	try {
	    result = init(clazz, message, cause);
	} catch (IOException ex) {
	    InvalidObjectException e = new InvalidObjectException("unable to resolve");
	    e.initCause(ex);
	    throw e;
	}
	if (stack != null) result.setStackTrace(stack);
	// Only adds suppressed if enabled by Throwable protected constructor.
	if (suppressed != null){ // compat with serial form of Throwable before Java 1.7
	    for (int i = 0, l = suppressed.length; i < l; i++){
		result.addSuppressed(suppressed[i]);
	    }
	}
	return result;
    }
    
    /**
     * @serialData 
     * @param out
     * @throws IOException 
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	ObjectOutputStream.PutField pf = out.putFields();
	pf.put("clazz", clazz);
	pf.put("message", message);
	pf.put("cause", cause);
	pf.put("stack", stack);
	pf.put("suppressed", suppressed);
	out.writeFields();
    }
    
}
