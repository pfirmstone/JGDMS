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

import net.jini.io.MarshalOutputStream;

import java.io.OutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * MarshalOutputStream subclass used by tests to write a custom
 * codebase annotation to the stream.
 */
public class FakeMarshalOutputStream extends MarshalOutputStream {

    Logger logger;
    private String codebase;

    public FakeMarshalOutputStream(OutputStream out, Collection context,
        String codebase) throws IOException
    {
        super(out,context);
        logger = Logger.getLogger("com.sun.jini.qa.harness.test");
        logger.entering(getClass().getName(),"constructor");
        this.codebase = codebase;
    }

    protected void writeAnnotation(String annotation) throws IOException {
        logger.entering(getClass().getName(),"writeAnnotation",annotation);
        writeObject(codebase);
    }

}
