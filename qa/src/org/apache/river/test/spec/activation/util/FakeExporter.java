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
package org.apache.river.test.spec.activation.util;
import java.rmi.server.ExportException;
import net.jini.export.Exporter;
import java.util.logging.Logger;
import java.util.logging.Level;


/**
 * A fake implementation of the <code>Exporter</code>
 * class.
 */
public class FakeExporter implements Exporter {

    private Logger logger;

    /**
     * Constructor with logger as a parameter
     */
    public FakeExporter(Logger logger) {
        this.logger = logger;
    }


    /**
     * No exporting really, return the parameter
     */
    public java.rmi.Remote export(java.rmi.Remote impl)
    	throws ExportException
    {
        return impl;
    }

    /**
     * No unexporting really, return false
     */
    public boolean unexport(boolean force)
    {
        return true;
    }
}
