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
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * Classes annotated with this are expected to have a single argument public
 * constructor that accepts an ObjectInput instance and implement Externalizable.
 * <p>
 * The constructor is required to validate all input before calling a super
 * constructor.
 * <p>
 * The constructor replaces the readExternal method in Externalizable.
 * 
 * @see Externalizable.
 * @author peter
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AtomicExternal {
    
   /**
     * Factory to test AtomicExternal instantiation compliance.
     */
    public static final class Factory {
	private Factory(){} // Non instantiable.
	
	/**
	 * Convenience method for testing implementing class constructor
	 * signature compliance.
	 * <p>
	 * De-serializers are free to implement higher performance instantiation
	 * that complies with this contract.
	 * <p>
	 * Only public and package default constructors can be called by 
	 * de-serializers.  Package default constructors have been provided
	 * to prevent implementations from polluting public api,
	 * but should be treated as public constructors.
	 * <p>
	 * Constructors with private visibility cannot be called.
	 * <p>
	 * Constructors with protected visibility can only be called by 
	 * subclasses, not de-serializers.
	 * 
	 * @param <T> AtomicExternal implementation type.
	 * @param type AtomicExternal implementing class.
	 * @param arg ObjectInput argument to pass to implementing constructor.
	 * @return new instance of T.
	 * @throws java.io.InvalidClassException if constructor is non compliant
	 * or doesn't exist. 
	 * @throws java.io.InvalidObjectException if invariant check fails
	 * @throws NullPointerException if arg or type is null.
	 */
	public static <T> T instantiate(final Class<T> type, final ObjectInput arg)
		throws IOException {
	    if (arg == null) throw new NullPointerException();
	    if (type == null) throw new NullPointerException();
	    final Class[] param = { ObjectInput.class };
	    Object[] args = { arg };
	    Constructor<T> c;
	    try {
		c = AccessController.doPrivileged(
		    new PrivilegedExceptionAction<Constructor<T>>(){

			@Override
			public Constructor<T> run() throws Exception {
			    Constructor<T> c = type.getDeclaredConstructor(param);
			    int mods = c.getModifiers();
			    switch (mods){
				case Modifier.PUBLIC:
				    c.setAccessible(true); //In case constructor is public but class not.
				    return c;
				case Modifier.PROTECTED:
				    throw new InvalidClassException( type.getCanonicalName(),
					"protected constructor cannot be called by de-serializer");
				case Modifier.PRIVATE:
				    throw new InvalidClassException( type.getCanonicalName(),
					"private constructor cannot be called by de-serializer");
				default: // Package private
				    throw new InvalidClassException( type.getCanonicalName(),
					"Package private constructor cannot be called by de-serializer");
			    }
			}

		    });
		return c.newInstance(args);
	    } catch (PrivilegedActionException ex) {
		Exception e = ex.getException();
		if (e instanceof NoSuchMethodException) throw new InvalidClassException(type.getCanonicalName(), "No matching AtomicSerial constructor signature found");
		if (e instanceof SecurityException ) throw (SecurityException) e;
		if (e instanceof InvalidClassException ) throw (InvalidClassException) e;
		InvalidClassException ice = new InvalidClassException("Unexpected exception while attempting to access constructor");
		ice.initCause(ex);
		throw ice;
	    } catch (InvocationTargetException ex) {
		Throwable e = ex.getCause();
		if (e instanceof InvalidObjectException) throw (InvalidObjectException) e;
		if (e instanceof IOException) throw (IOException) e;
		if (e instanceof RuntimeException) throw (RuntimeException) e;
		InvalidObjectException ioe = new InvalidObjectException(
		    "Construction failed: " + type);
		ioe.initCause(ex);
		throw ioe;
	    } catch (IllegalAccessException ex) {
		throw new AssertionError("This shouldn't happen ", ex);
	    } catch (IllegalArgumentException ex) {
		throw new AssertionError("This shouldn't happen ", ex);
	    } catch (InstantiationException ex) {
		throw new InvalidClassException(type.getCanonicalName(), ex.getMessage());
	    }
	}
    }
}
