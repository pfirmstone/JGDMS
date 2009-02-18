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
package com.sun.jini.test.spec.security.proxytrust.util;

import java.util.logging.Level;

// java.rmi
import java.rmi.Remote;
import java.rmi.server.ExportException;

// net.jini
import net.jini.export.Exporter;


/**
 * Base for all used exporter classes. Counts number of export/unexport methods
 * invocations.
 */
public class BaseExporter implements Exporter {

    // number of 'export' method invocations
    protected int expNum;

    // number of 'unexport' method invocations
    protected int unexpNum;

    /**
     * Default constructor.
     */
    public BaseExporter() {
        expNum = 0;
        unexpNum = 0;
    }

    /**
     * Exporter's method. Increase number of this method invocations by one.
     *
     * @return null
     */
    public Remote export(Remote impl) throws ExportException {
        ++expNum;
        return null;
    }

    /**
     * Exporter's method. Increase number of this method invocations by one.
     *
     * @return false
     */
    public boolean unexport(boolean force) {
        ++unexpNum;
        return false;
    }

    /**
     * Reset number of 'export' and 'unexport' methods invocations to zero.
     */
    public void resetCounters() {
        expNum = 0;
        unexpNum = 0;
    }

    /**
     * Returns number of 'export' method invocations.
     *
     * @return number of 'export' method invocations
     */
    public int getExpNum() {
        return expNum;
    }

    /**
     * Returns number of 'unexport' method invocations.
     *
     * @return number of 'unexport' method invocations
     */
    public int getUnexpNum() {
        return unexpNum;
    }
}
