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
package com.sun.jini.test.spec.io.util;

import java.io.IOException;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * A serializable object that throws a specified exception
 * in it's readObject method.
 */
public class FakeObject implements Serializable {

    private Throwable readObjectException;

    public FakeObject(Throwable ro_exc) {
        readObjectException = ro_exc;
    }

    public boolean equals(Object obj) {
        if (obj instanceof FakeObject) {
            FakeObject other = (FakeObject)obj;
            if (readObjectException == null) {
                return other.readObjectException == null;
            } else {
                return readObjectException.equals(other.readObjectException);
            }
        }
	return false;
    }

    public int hashCode() {
        return 31;
    }

    private void readObject(ObjectInputStream in) 
        throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
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
