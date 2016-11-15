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
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;

/**
 *
 * @author peter
 */
class SerializationConstructor {
    final LinkedByteOutputStream bout;
    final ObjectInputStream ois;
    final SubObjectOutputStream oos;
    
    SerializationConstructor() throws IOException {
	bout = new LinkedByteOutputStream(1024);
	oos = new SubObjectOutputStream(bout);
	ois = new ObjectInputStream(bout.inputStream());
    }
    
    public void writeObject(Object o, Class replacement) throws IOException{
	oos.writeObject(o, replacement);
    }
    
    public Object readObject() throws IOException, ClassNotFoundException{
	try {
	    return ois.readObject();
	} finally {
	    bout.reset();
	}
    }
    
    private static class SubObjectOutputStream extends ObjectOutputStream {
	Class orig;
	Class replacement;
	
	SubObjectOutputStream(OutputStream out) throws IOException{
	    super(out);
	    replacement = null;
	}
	
	public void writeObject(Object o, Class replacement) throws IOException{
	    this.replacement = replacement;
	    orig = o.getClass();
	    writeObject(o);
	    this.replacement = null;
	    orig = null;
	}
	
	@Override
	protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException{
	    if (orig.equals(desc.forClass())){
		desc = ObjectStreamClass.lookup(replacement);
	    }
	    super.writeClassDescriptor(desc);
	}
    }
    
    private static class LinkedByteOutputStream extends OutputStream {
	byte [] buf;
	int count;
	int pos;
	LinkedByteInputStream in;
	
	LinkedByteOutputStream(int size){
	   buf = new byte[size];
	   count = 0;
	   in = new LinkedByteInputStream();
	}
	
	byte[] getBuffer(){
	    return buf;
	}

	@Override
	public void write(int b) throws IOException {
	    grow();
	    buf[count] = (byte) b;
	    count++;
	}
	
	private void grow(){
	    if (count + 1 > buf.length) {
		buf = new byte[buf.length * 2];
	    }
	}
	
	public void reset(){
	    count = 0;
	    pos = 0;
	}
	
	public int size(){
	    return count;
	}
	
	public InputStream inputStream(){
	    return in;
	}
	
	private class LinkedByteInputStream extends InputStream {
	    
	    @Override
	    public int read() throws IOException {
		return (pos <= count) ? (buf[pos++] & 0xff) : -1;
	    }
	    
	    @Override
	    public int available(){
		return count + 1 - pos;
	    }
	    
	}
    }
}
