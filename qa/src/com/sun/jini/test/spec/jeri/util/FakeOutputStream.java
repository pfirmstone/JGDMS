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
package com.sun.jini.test.spec.jeri.util;

import java.io.OutputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * A fake implementation of the <code>OutputStream</code> contract.
 *
 * <p>The write method exception behavior is configurable.
 */
public class FakeOutputStream extends OutputStream {

    Logger logger;
    private Throwable writeException;

    /**
     * Constructs a FakeOutputStream.
     *
     * @param write_exc the exception that <code>write</code> should throw;
     *        must be instanceof IOException, RuntimeException, or Error
     */
    public FakeOutputStream(Throwable write_exc) {
        logger = Logger.getLogger("com.sun.jini.qa.harness.test");
        logger.entering(getClass().getName(),"constructor");
        writeException = write_exc;
    }

    /**
     * Implementation of abstract method.
     * 
     * @throws write_exc if write_exc is not null
     * @throws AssertionError if write_exc is null or
     *        write_exc is not instanceof IOException, RuntimeException or Error
     */
    public void write(int b) throws IOException {
        logger.entering(getClass().getName(),"write");
        if (writeException != null) {
            if (writeException instanceof IOException) {
                throw (IOException) writeException;
            } else if (writeException instanceof RuntimeException) {
                throw (RuntimeException) writeException;
            } else if (writeException instanceof Error) {
                throw (Error) writeException;
            } else {
                throw new AssertionError();
            }
        }
        throw new AssertionError();
    }
}

