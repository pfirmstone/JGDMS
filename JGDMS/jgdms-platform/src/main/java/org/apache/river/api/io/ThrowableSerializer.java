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

import org.apache.river.api.io.AtomicSerial.SerialForm;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.io.WriteAbortedException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 *
 * @author peter
 */
@Serializer(replaceObType = Throwable.class)
@AtomicSerial
public class ThrowableSerializer implements Serializable, Resolve {
    private static final long serialVersionUID = 1L;
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields
            = serialForm();
    
    /**
     * Serial argument / field names
     */
    private static final String CLASS = "clazz";
    private static final String MESSAGE = "message";
    private static final String CAUSE = "cause";
    private static final String STACK = "stack";
    private static final String SUPPRESSED = "suppressed";
    private static final String CLASSNAME = "classname";
    private static final String LENGTH = "length";
    private static final String EOF = "eof";
    
    public static SerialForm [] serialForm(){
        return new SerialForm []{
            new SerialForm(CLASS, Class.class),
	    new SerialForm(MESSAGE, String.class),
	    new SerialForm(CAUSE, Throwable.class),
	    new SerialForm(STACK, StackTraceElement[].class),
	    new SerialForm(SUPPRESSED, Throwable[].class),
	    new SerialForm(CLASSNAME, String.class),
            new SerialForm(LENGTH, int.class),
            new SerialForm(EOF, boolean.class),
        };
    }
    
    public static void serialize(AtomicSerial.PutArg args, ThrowableSerializer obj) throws IOException {
        putArgs(args, obj);
        args.writeArgs();
    }
    
    private static void putArgs(ObjectOutputStream.PutField pf, ThrowableSerializer obj) {
        pf.put(CLASS, obj.clazz);
	pf.put(MESSAGE, obj.message);
	pf.put(CAUSE, obj.cause);
	pf.put(STACK, obj.stack);
	pf.put(SUPPRESSED, obj.suppressed);
        pf.put(CLASSNAME, obj.classname);
        pf.put(LENGTH, obj.length);
        pf.put(EOF, obj.eof);
    }
    
    private static final Logger logger = Logger.getLogger("org.apache.river.api.io.ThrowableSerializer");
    
    /**
     * detailMessage is part of Throwable's serial form, so is unlikely to
     * change, however it is possible to have different fields than serial
     * form.
     */
//    private static final Field detailMessage;
//    
//    static {
//	detailMessage = AccessController.doPrivileged(new PrivilegedAction<Field>(){
//
//	    @Override
//	    public Field run() {
//		try {
//		    Field mes = Throwable.class.getDeclaredField("detailMessage");
//		    mes.setAccessible(true);
//		    return mes;
//		} catch (NoSuchFieldException ex) {
//		    logger.log(Level.FINE, "unable to access detailMessage field in Throwable", ex);
//		} catch (SecurityException ex) {
//		    logger.log(Level.FINE, "unable to access detailMessage field in Throwable", ex);
//		}
//		return null;
//	    }
//	    
//	});
//	
//    }
    
    private final /*transient*/ Throwable throwable;
    private final Class<? extends Throwable> clazz;
    private final String message;
    private final Throwable cause;
    private final StackTraceElement[] stack;
    private final Throwable [] suppressed;
    private final String classname; // To support InvalidClassException
    private final int length; // To support OptionalDataException
    private final boolean eof; // To support OptionalDataException
    
    ThrowableSerializer(Throwable t){
	throwable = t;
	clazz = t.getClass();
	cause = t.getCause();
	stack = t.getStackTrace();
	suppressed = t.getSuppressed();
        if (t instanceof InvalidClassException) {
            classname = ((InvalidClassException)t).classname;
        } else if (t instanceof URISyntaxException){
	    classname = ((URISyntaxException)t).getInput();
	} else {
            classname = null;
        }
        if (t instanceof OptionalDataException) {
            length = ((OptionalDataException)t).length;
            eof = ((OptionalDataException)t).eof;
        } else if (t instanceof URISyntaxException){
	    length = ((URISyntaxException)t).getIndex();
	    eof = false;
	} else {
            length = 0;
            eof = false;
        }
        /*
        * Numeous subclasses override Throwable.getMessage.  Unfortunately we
        * can't rely on reflection in future, so in order to best retrieve the
        * original message...
        */
        try {
            Class clas = t.getClass().getMethod("getMessage").getDeclaringClass();
            if (Throwable.class == clas){
                message = t.getMessage();
                return;
            }
        } catch (NoSuchMethodException ex) {
            throw new IncompatibleClassChangeError("Throwable missing method getMessage: " + ex.toString());
        }
        if (t instanceof RemoteException && ((RemoteException)t).detail != null){
            String mess = t.getMessage();
            String remoteMess = "; nested exception is: \n\t";
            int endMessage = mess.indexOf(remoteMess);
            message = mess.substring(0, endMessage);
        } else if (t instanceof URISyntaxException){
            message = ((URISyntaxException)t).getReason();
        } else if (t instanceof  InvalidClassException  && ((InvalidClassException)t).classname != null){
            String classnme = ((InvalidClassException)t).classname + "; ";
            String mess = t.getMessage();
            message = mess.substring(classnme.length());
        } else if (t instanceof WriteAbortedException && ((WriteAbortedException)t).detail != null){
            String detail = "; " + ((WriteAbortedException)t).detail.toString();
            String mess = t.getMessage();
            int endMessage = mess.indexOf(detail);
            message = mess.substring(0, endMessage);
        } else {
            message = t.getMessage();
            logger.log(Level.FINE, "unable to access detailMessage field in Throwable, using overridden getMessage method result instead");
        }
	
//	if (detailMessage != null){
//	    String mess = null;
//	    try {
//		mess = (String) detailMessage.get(t);
//	    } catch (IllegalArgumentException ex) {
//		logger.log(Level.FINE, "unable to access detailMessage", ex);
//	    } catch (IllegalAccessException ex) {
//		logger.log(Level.FINE, "unable to access detailMessage", ex);
//	    }
//	    if (mess != null) {
//		message = mess;
//	    } else {
//		logger.log(Level.FINE, "Warning getMessage() may be overridden");
//		message = t.getMessage();
//	    }
//	} else {
//	    logger.log(Level.FINE,"Warning getMessage() may be overridden");
//	    message = t.getMessage();
//	}
    }
    
    public ThrowableSerializer(GetArg arg) throws IOException, ClassNotFoundException{
	this(check(arg));
    }
    
    private static Throwable check(GetArg arg) throws IOException, ClassNotFoundException{
	@SuppressWarnings("unchecked")
	Class<? extends Throwable> clas = null;
	try {
	    clas = Valid.notNull(arg.get("clazz", null, Class.class), "clazz cannot be null");
	} catch (ClassNotFoundException e){
	    throw e;
	    //TODO: create a throwable with exception class name, message and
	    //stack trace then add it as a suppressed exception.
	}
        if (!Throwable.class.isAssignableFrom(clas)) 
	    throw new InvalidObjectException("clazz must be assignable to Throwable");
	logger.log(Level.FINER, "deserializing {0}", clas);
	String message = arg.get("message", null, String.class);
	Throwable cause = arg.get("cause", null, Throwable.class);
	if (cause == null) {
	    logger.finer("cause is null");
	}
	StackTraceElement[] stack = arg.get("stack", null, StackTraceElement[].class);
	Throwable[] suppressed = arg.get("suppressed", null, Throwable[].class);
        String classname = arg.get("classname", null, String.class);
        int length = arg.get("length", 0);
        boolean eof = arg.get("eof", false);
        Throwable result;
        if (InvalidClassException.class.equals(clas)){
            result = new InvalidClassException(classname, message);
            if (cause != null) result.initCause(cause);
	} else if (URISyntaxException.class.equals(clas)){
	    result = new URISyntaxException(classname, message, length);
	    if (cause != null) result.initCause(cause);
        } else if (OptionalDataException.class.equals(clas)){
            try {
                if (length > 0) {
                    Constructor<OptionalDataException> c 
                        = OptionalDataException.class
                                .getDeclaredConstructor(new Class[]{int.class});
                    c.setAccessible(true);
                    result = c.newInstance(new Object[]{length});
                    if (cause != null) result.initCause(cause);
                } else if (eof == true){
                    Constructor<OptionalDataException> c 
                        = OptionalDataException.class
                                .getDeclaredConstructor(new Class[]{boolean.class});
                    result = c.newInstance(new Object[]{eof});
                    if (cause != null) result.initCause(cause);
                } else {
                    throw new InvalidObjectException("Failed invariant checks for OptionalDataException");
                }
            } catch (InstantiationException ex) {
                throw new IOException("Unable to instantiate java.io.OptionalDataException", ex);
            } catch (IllegalAccessException ex) {
                throw new IOException("Unable to instantiate java.io.OptionalDataException", ex);
            } catch (IllegalArgumentException ex) {
                throw new IOException("Unable to instantiate java.io.OptionalDataException", ex);
            } catch (InvocationTargetException ex) {
                throw new IOException("Unable to instantiate java.io.OptionalDataException", ex);
            } catch (NoSuchMethodException ex) {
                return new org.apache.river.api.io.OptionalDataException(length, eof);
            } catch (SecurityException ex) {
                throw new IOException("Unable to instantiate java.io.OptionalDataException", ex);
            }
        } else {
            result = init(clas, message, cause);
        }
	if (stack != null) result.setStackTrace(stack);
	// Only adds suppressed if enabled, that is not disabled by Throwable protected constructor.
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
    private static final Class[] tparam = new Class[]{Throwable.class};
    
    static Throwable init(Class<? extends Throwable> clas, String message, Throwable cause) throws IOException {
	Throwable result;
	try {
	    Constructor [] cons = clas.getConstructors();
            if (Exception.class.isInstance(cause)){
                Constructor c = getConstructor(cons, separams);
                if (c != null){
                    return (Throwable) c.newInstance(new Object[]{message,(Exception) cause});
                }
            }
            if (Error.class.isInstance(cause)){
                Constructor c = getConstructor(cons, serparams);
                if (c != null){
                    return (Throwable) c.newInstance(new Object[]{message,(Error) cause});
                }
            }
            Constructor c = getConstructor(cons, stparams);
            if (c != null){
                return (Throwable) c.newInstance(new Object[]{message, cause});
            } 
            c = getConstructor(cons, tsparams);
            if (c != null){
                return (Throwable) c.newInstance(new Object[]{cause, message});
            }
	    if (message == null){/* Some Throwable's don't allow initCause */
		c = getConstructor(cons, tparam);
		if (c != null){
		    return (Throwable) c.newInstance(new Object[]{cause});
		}
	    }
            c = getConstructor(cons, sparam);
            if (c != null){
                result = (Throwable) c.newInstance(new Object[]{message});
                if (cause != null){
                    if (RemoteException.class.isAssignableFrom(clas)){
                        ((RemoteException) result).detail = cause;
                    } else {
                        try {
                            result.initCause(cause);
                        } catch (IllegalStateException e){
			    /* Previously an IOException was thrown, however
			     * this can cause breakages, while the alternative
			     * of dropping the cause leads to data loss.
			     * The most useful option at this time is to add
			     * this and the cause as suppressed exceptions.
			    */
			    result.addSuppressed(cause);
			    result.addSuppressed(e);
                        }
                    } 
                }
                return result;
            }
            result = clas.getDeclaredConstructor().newInstance(); /* None of the above constructors match */
            if (cause != null){
                if (RemoteException.class.isAssignableFrom(clas)){
                    ((RemoteException) result).detail = cause;
                } else {
                    try {
                        result.initCause(cause);
                    } catch (IllegalStateException e){
			/* Previously an IOException was thrown, however
			 * this can cause breakages, while the alternative
			 * of dropping the cause leads to data loss.
			 * The most useful option at this time is to add
			 * this and the cause as suppressed exceptions.
			*/
			result.addSuppressed(cause);
			result.addSuppressed(e);
                    }
                } 
            }
            return result;
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
	} catch (NoSuchMethodException ex) {
            throw throIO(ex);
        }
    }
    
    static Constructor getConstructor(Constructor [] cons, Class [] paramTypes){
        for (int i=0,l=cons.length; i<l; i++){
            if (Arrays.equals(cons[i].getParameterTypes(), paramTypes)) return cons[i];
        }
        return null;
    }
    
    static IOException throIO(Exception cause){
	return new IOException(cause);
    }
    
    @Override
    public Object readResolve() throws ObjectStreamException {
	if (throwable != null) return throwable;
	// The following is for standard java serialization, as throwable will be null.
	Throwable result;
	try {
            if (InvalidClassException.class.equals(clazz)){
                result = new InvalidClassException(classname, message);
                if (cause != null) result.initCause(cause);
            } else if (OptionalDataException.class.equals(clazz)){
                try {
                    if (length > 0 && eof == false) {
                        Constructor<OptionalDataException> c;

                            c = OptionalDataException.class
                                    .getDeclaredConstructor(new Class[]{int.class});
                            c.setAccessible(true);
                            result = c.newInstance(new Object[]{length});

                    } else if (eof == true && length == 0){
                        Constructor<OptionalDataException> c 
                            = OptionalDataException.class
                                    .getDeclaredConstructor(new Class[]{boolean.class});
                        result = c.newInstance(new Object[]{eof});
                    } else {
                        throw new InvalidObjectException("Failed invariant checks for OptionalDataException");
                    }
                } catch (InstantiationException ex) {
                    throw new IOException("Unable to instantiate java.io.OptionalDataException", ex);
                } catch (IllegalAccessException ex) {
                    throw new IOException("Unable to instantiate java.io.OptionalDataException", ex);
                } catch (IllegalArgumentException ex) {
                    throw new IOException("Unable to instantiate java.io.OptionalDataException", ex);
                } catch (InvocationTargetException ex) {
                    throw new IOException("Unable to instantiate java.io.OptionalDataException", ex);
                } catch (NoSuchMethodException ex) {
                    return new org.apache.river.api.io.OptionalDataException(length, eof);
                } catch (SecurityException ex) {
                    throw new IOException("Unable to instantiate java.io.OptionalDataException", ex);
                }
            } else {
                result = init(clazz, message, cause);
            }
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
	putArgs(out.putFields(), this);
	out.writeFields();
    }
    
    /**
     * 
     * @param in
     * @throws IOException
     * @throws ClassNotFoundException 
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
	in.defaultReadObject();
    }

}
