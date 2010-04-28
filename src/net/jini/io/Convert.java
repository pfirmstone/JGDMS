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
package net.jini.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

/**
 * Rather than utilise a static class, an empty object class can be utilised
 * for conversion of a particular Object type, to preserve Generic semantics.
 * 
 * Note that java.rmi.MarshalledObject didn't take Generic parameters 
 * until Java 6 or jdk 1.6, so this doesn't compile with jdk1.5 however it
 * will run on Java 5 if compiled with jdk1.6
 * 
 * @param T - Generic Type Parameter of contained object.
 * @author Peter Firmstone
 */
public class Convert<T> {
    private static volatile Convert convert = new Convert();
    protected static void setConvert(Convert converter){
        convert = converter;
    }
    
    /**
     * Get the non generic, plain object, unchecked Convert instance.
     * @return
     */
    public static Convert getInstance(){
        return convert;
    }
    Convert(){}
    
    @SuppressWarnings("unchecked") 
    java.rmi.MarshalledObject<T> 
            toRmiMarshalledObject(net.jini.io.MarshalledObject<T> privateMO){
        // To create a java.rmi.MarshalledObject with previously
	// serialized data we first create a private
	// net.jini.io.MarshalledObject with the
	// data and then convert it to the final object by changing
	// the class during readObject(). (See resolveClass() in
	// ToMOInputStream)
	//
        java.rmi.MarshalledObject<T> mo = null;
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	    oos.writeObject(privateMO);
	    oos.flush();
	    byte[] bytes = baos.toByteArray();
	    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
	    ObjectInputStream ois = new ToMOInputStream(bais);
	    mo = (java.rmi.MarshalledObject<T>)ois.readObject();
	} catch (IOException ioe) {
	    throw new AssertionError(ioe);
	} catch (ClassNotFoundException cnfe) {
	    throw new AssertionError(cnfe);
	}
	return mo;
    }
    
    net.jini.io.MarshalledInstance<T> toMarshalledInstance(
            net.jini.io.MarshalledObject<T> mo){
        return new MarshalledInstance<T>(mo);
    }
    
    net.jini.io.MarshalledObject<T> toJiniMarshalledObject(
            net.jini.io.MarshalledInstance<T> instance){
        return instance.asMarshalledObject();
    }
    
    @SuppressWarnings("unchecked")
    net.jini.io.MarshalledObject<T> toJiniMarshalledObject(
            java.rmi.MarshalledObject<T> instance){
        net.jini.io.MarshalledObject<T> privateMO = null;
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	    oos.writeObject(instance);
	    oos.flush();
	    byte[] bytes = baos.toByteArray();
	    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
	    ObjectInputStream ois = new FromMOInputStream(bais);
	    privateMO = (net.jini.io.MarshalledObject<T>) ois.readObject();
	} catch (IOException ioe) {
	    throw new AssertionError(ioe);
	} catch (ClassNotFoundException cnfe) {
	    throw new AssertionError(cnfe);
	}
        return privateMO;
    }
    
    public java.rmi.MarshalledObject<T> 
            toRmiMarshalledObject(MarshalledInstance<T> instance){    
        if ( instance == null ) throw new NullPointerException("null reference");
	return toRmiMarshalledObject(instance.asMarshalledObject());
    }
    
    @SuppressWarnings("unchecked")
    public MarshalledInstance<T> 
            toMarshalledInstance(java.rmi.MarshalledObject<T> instance){
        if ( instance == null ) throw new NullPointerException("null reference");
        net.jini.io.MarshalledObject obj = toJiniMarshalledObject(instance);
        if ( obj == null ) throw new NullPointerException("null reference");
	return new MarshalledInstance<T>(obj);
    }
    
    private static class FromMOInputStream extends ObjectInputStream {

        public FromMOInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            if (desc.getName().equals("java.rmi.MarshalledObject")) {
                return net.jini.io.MarshalledObject.class;
            }
            return super.resolveClass(desc);
        }
    }
    
    private static class ToMOInputStream extends ObjectInputStream {
        public ToMOInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            if (desc.getName().equals("net.jini.io.MarshalledObject")) {
                return java.rmi.MarshalledObject.class;
            }
            return super.resolveClass(desc);
        }
    }
}
