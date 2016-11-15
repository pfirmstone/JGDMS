/* Copyright (c) 2010-2012 Zeus Project Services Pty Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.concurrent;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * I've thought about refactoring so the equals and comparator behaviour is
 * implemented by the wrapper classes and only accepting References in
 * constructors as this would allow the use of standard java Reference classes
 * without extension, reducing the number of classes created, 
 * however that would create serial form lock in.  The current arrangement 
 * allows for 
 * 
 * 
 * 
 * @author peter
 */
class ReferrerDecorator<T> extends AbstractReferrerDecorator<T> implements  Serializable{
    private static final long serialVersionUID = 1L;
    
    /**
     * @serialField 
     */
    private volatile Referrer<T> reference;
    
    ReferrerDecorator(Referrer<T> ref){
        if (ref == null) throw new NullPointerException("Referrer cannot be null");
        reference = ref;
    }
    
    private void readObject(ObjectInputStream in)
                                    throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (getReference() == null) throw new IOException("Attempt to write null Referrer");
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    @Override
    public void refresh(Referrer<T> r) {
            reference = r;
    }

    /**
     * @return the reference
     */
    public Referrer<T> getReference() {
        return reference;
    }
    
}
