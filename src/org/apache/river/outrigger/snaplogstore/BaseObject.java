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
package org.apache.river.outrigger.snaplogstore;

import org.apache.river.outrigger.StorableObject;
import org.apache.river.outrigger.StoredObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import net.jini.space.InternalSpaceException;

/**
 * Top level wrapper class for persisting outrigger objects.
 * The target object is serialized and stored here as a byte
 * array.
 */
class BaseObject<T extends StorableObject<T>> implements StoredObject<T>, Serializable {
    static final long serialVersionUID = -400804064969360164L;

    /**
     * @serialField containing a binary blob.
     */
    private final byte[]	blob;

    BaseObject(T object) {
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	    object.store(oos);
	    oos.flush();
	    blob = baos.toByteArray();
	    oos.close();
	} catch (IOException e) {
	    throw new InternalSpaceException("Exception serializing resource", e);
	}
    }

    public T restore(T object)
      throws IOException, ClassNotFoundException {
	ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(blob));
	T result = object.restore(ois);
	ois.close();
        return result;
    }
    
    /**
     * Added to enable the serial form to be modified
     * in a backward compatible manner (if necessary) with 3.0.0 and later.  
     * Modified serial form would be a breaking change for versions
     * prior to 3.0.0 
     * 
     * @serialData 
     * @param ois
     * @throws IOException
     * @throws ClassNotFoundException 
     * @since 3.0.0
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
    }
}
