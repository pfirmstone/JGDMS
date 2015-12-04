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
package org.apache.river.test.spec.jeri.util;

import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

import java.util.Arrays;
import java.util.logging.Logger;
import java.rmi.RemoteException;

/**
 * A fake serializable object that can be configured to throw exceptions
 * in it's custom <code>readObject</code> and <code>writeObject</code>
 * methods.
 */
public class FakeArgument extends RemoteException implements Serializable {

    Logger logger;
    Throwable writeObjectException;
    Throwable readObjectException;

    /**
     * Constructs a FakeArgument.  
     * Equivalent to calling FakeArgument(null,null).
     */
    public FakeArgument() {
        this(null,null);
    }

    /**
     * Constructs a FakeArgument.
     *
     * @param wo_exc the exception that the custom <code>writeObject</code>
     *        should throw; must be instanceof IOException or RuntimeException
     * @param ro_exc the exception that the custom <code>readObject</code>
     *        should throw; must be instanceof IOException, RuntimeException,
     *        or ClassNotFoundException
     */
    public FakeArgument(Throwable wo_exc, Throwable ro_exc) {
        logger = Logger.getLogger("org.apache.river.qa.harness.test");
        logger.entering(getClass().getName(),"constructor",
            new Object[] {wo_exc, ro_exc});
        writeObjectException = wo_exc;
        readObjectException = ro_exc;
    }

    /**
     * Two <code>FakeArguments</code> are equal if both have identical
     * <code>writeObjectException</code> and <code>readObjectException</code>
     * fields.
     */
    public boolean equals(Object object) {
        logger.entering(getClass().getName(),"equals",object);
        if (object == null || !(object instanceof FakeArgument)) {
            return false;
        }

        FakeArgument fa = (FakeArgument)object;
        if (writeObjectException == null || fa.writeObjectException == null) {
            return writeObjectException == null &&
                   fa.writeObjectException == null;
        }

        if (readObjectException == null || fa.readObjectException == null) {
            return readObjectException == null &&
                   fa.readObjectException == null;
        }

        return Arrays.equals(
                   writeObjectException.getStackTrace(),
                   fa.writeObjectException.getStackTrace())
               &&
               Arrays.equals(
                   readObjectException.getStackTrace(),
                   fa.readObjectException.getStackTrace());
    }

    /**
     * Custom <code>writeObject</code> method that will throw the
     * write object exception passed in during construction.  If no 
     * write object exception was passed in, then this method will
     * write the objects state to <code>out</code>.
     *
     * @throws wo_exc if wo_exc is not null
     * @throws AssertionError if wo_exc is not null and
     *        wo_exc is not instanceof IOException, RuntimeException or Error
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        logger.entering(getClass().getName(),"writeObject");
        if (writeObjectException != null) {
            if (writeObjectException instanceof IOException) {
                throw (IOException) writeObjectException;
            } else if (writeObjectException instanceof RuntimeException) {
                throw (RuntimeException) writeObjectException;
            } else if (writeObjectException instanceof Error) {
                throw (Error) writeObjectException;
            } else {
                throw new AssertionError();
            }
        } else {
            out.writeObject(writeObjectException);
            out.writeObject(readObjectException);
        }
    }

    /**
     * Custom <code>readObject</code> method that reads the objects
     * state from <code>in</code>.  
     *
     * <p>If a read object exception is unserialized, then this method
     * will throw that exception.
     *
     * @throws ro_exc if ro_exc is not null
     * @throws AssertionError if ro_exc is not null and
     *        ro_exc is not instanceof IOException, RuntimeException,
     *        ClassNotFoundException or Error
     */
    private void readObject(ObjectInputStream in) 
        throws IOException, ClassNotFoundException 
    {
        logger = Logger.getLogger("org.apache.river.qa.harness.test");
        logger.entering(getClass().getName(),"readObject");
        writeObjectException = (Throwable) in.readObject();
        readObjectException = (Throwable) in.readObject();
        if (readObjectException != null) {
            if (readObjectException instanceof IOException) {
                throw (IOException) readObjectException;
            } else if (readObjectException instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) readObjectException;
            } else if (readObjectException instanceof RuntimeException) {
                throw (RuntimeException) readObjectException;
            } else if (readObjectException instanceof Error) {
                throw (Error) readObjectException;
            } else {
                throw new AssertionError();
            }
        }
    }
}
