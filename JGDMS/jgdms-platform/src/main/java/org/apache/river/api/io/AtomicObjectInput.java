/*
 * Copyright 2021 The Apache Software Foundation.
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
package org.apache.river.api.io;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.ObjectInputValidation;

/**
 * AtomicObjectInput checks that the class types are the expected types in the
 * stream prior to deserialization, this includes not only the class parameter
 * to the readObject method below, but also all field class types, prior to the
 * creation of any Objects, read from the stream, if any class is not the expected
 * type, an exception will be thrown, failure will occur atomically, such that
 * no partially constructed objects will be created.
 *
 * @author peter
 * @see AtomicSerial
 * @since 3.1.1
 */
public interface AtomicObjectInput extends ObjectInput {
    
     /**
     * <p>
     * Reads the object from the source stream. In this case,
     * the Object will only be read from the stream if the type matches.
     * </p>
     * @param <T>
     * @param type
     * @return the new object read.
     * @throws ClassNotFoundException
     *             if the class of one of the objects in the object graph cannot
     *             be found.
     * @throws IOException
     *             if an error occurs while reading from the source stream.
     * @throws InvalidObjectException
     *             if the object is not the expected type.
     */
    public <T> T readObject(Class<T> type) throws IOException, ClassNotFoundException;
    
    /**
     * Registers a callback for post-deserialization validation of objects. It
     * allows to perform additional consistency checks before the {@code
     * readObject()} method of this class returns its result to the caller. This
     * method can only be called from within an @AtomicSerial constructor. It can be called
     * multiple times. Validation callbacks are then done in order of decreasing
     * priority, defined by {@code priority}.
     * 
     * Note that BasicObjectEndpoint requires ObjectInput stream implementations
     * to support this method.
     * 
     * @param object
     *            an object that can validate itself by receiving a callback.
     * @param priority
     *            the validator's priority.
     * @throws InvalidObjectException
     *             if {@code object} is {@code null}.
     * @throws NotActiveException
     *             if this stream is currently not reading objects. In that
     *             case, calling this method is not allowed.
     * @see ObjectInputValidation#validateObject()
     */
    public void registerValidation(ObjectInputValidation object,
            int priority) throws NotActiveException, InvalidObjectException;
    
}
