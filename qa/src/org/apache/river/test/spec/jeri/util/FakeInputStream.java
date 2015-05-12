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

import java.io.InputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * A fake implementation of the <code>InputStream</code> contract.
 *
 * <p>The read method return values or throws are configurable.
 */
public class FakeInputStream extends InputStream {

    Logger logger;
    private Throwable readException;
    private int readReturn;

    /**
     * Constructs a FakeInputStream.
     *
     * @param read_exc the exception that <code>read</code> should throw;
     *        must be instanceof IOException, RuntimeException, or Error
     * @param read_ret if read_exc is null, the value that <code>read</code>
     *        should return
     */
    public FakeInputStream(Throwable read_exc, int read_ret) {
        logger = Logger.getLogger("org.apache.river.qa.harness.test");
        logger.entering(getClass().getName(),"constructor");
        readException = read_exc;
        readReturn = read_ret;
    }

    /**
     * Implementation of abstract method.
     * 
     * @return read_ret if read_exc is null
     * @throws read_exc if read_exc is not null
     * @throws AssertionError if read_exc is not null and
     *        read_exc is not instanceof IOException, RuntimeException or Error
     */
    public int read() throws IOException {
        logger.entering(getClass().getName(),"read");
        if (readException != null) {
            if (readException instanceof IOException) {
                throw (IOException) readException;
            } else if (readException instanceof RuntimeException) {
                throw (RuntimeException) readException;
            } else if (readException instanceof Error) {
                throw (Error) readException;
            } else {
                throw new AssertionError();
            }
        }
        return readReturn;
    }
}

