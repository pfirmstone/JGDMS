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
package com.sun.jini.test.impl.outrigger.transaction;

// imports
import net.jini.entry.AbstractEntry;


/**
 * Simple entry class.
 * This class will be used in all tests in this package.
 *
 * @author H. Fukuda
 */
public class SimpleEntry extends AbstractEntry {

    /** Field #1: <code>String</code> */
    public String string;

    /** Field #2: <code>Integer</code> */
    public Integer stage;

    /** Field #3: <code>Integer</code> */
    public Integer id;

    /**
     * Default constructor.
     * All fields are set to <code>null</code>.
     */
    public SimpleEntry() {
        string = null;
        stage = null;
        id = null;
    }

    public SimpleEntry(String string, int stage, int id) {
        this.string = string;
        this.stage = new Integer(stage);
        this.id = new Integer(id);
    }

    public String toString() {
        return "string=" + string + ", stage=" + stage + ", id=" + id;
    }
}
