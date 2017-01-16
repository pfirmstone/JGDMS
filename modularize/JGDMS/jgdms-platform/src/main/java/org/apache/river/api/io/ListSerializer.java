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
import java.io.Serializable;
import java.util.AbstractList;
import java.util.Collection;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Immutable list backed by an array specifically for serialization.
 * @author peter
 */
@AtomicSerial
class ListSerializer<T> extends AbstractList<T> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * @serial 
     */
    private final T[] elements;
    
    public ListSerializer(GetArg arg) throws IOException {
	this((T[]) arg.get("elements", new Object [0], Object[].class));
    }
    
    private ListSerializer(T [] elem){
	elements = elem;
    }
    
    public ListSerializer(){
	elements = (T[]) new Object[0];
    }
    
    public ListSerializer(Collection<T> list){
	elements = list.toArray((T[])new Object[list.size()]);
    }

    @Override
    public T get(int index) {
	return elements[index];
    }

    @Override
    public int size() {
	return elements.length;
    }
    
    /**
     * @serialData 
     * @param out
     * @throws IOException 
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }
    
}
