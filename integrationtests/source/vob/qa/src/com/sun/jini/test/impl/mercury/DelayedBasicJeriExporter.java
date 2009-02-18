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
package com.sun.jini.test.impl.mercury;

import java.rmi.Remote;
import java.rmi.server.ExportException;
import net.jini.id.*;
import net.jini.jeri.*;
import net.jini.export.Exporter;

public class DelayedBasicJeriExporter implements Exporter {
    private final BasicJeriExporter exporter;
    private final long wait; 

    public DelayedBasicJeriExporter(
        ServerEndpoint se, InvocationLayerFactory ilf, 
        boolean enableDGC, boolean keepAlive, Uuid id, long delay) 
    {
        exporter = new BasicJeriExporter(se, ilf, enableDGC, keepAlive, id);

	wait = (delay <= 0)?60000L:delay;
    }

    public Remote export(Remote impl) throws ExportException {
	Remote o = exporter.export(impl); 
	try { 
	    Thread.sleep(wait);
	} catch (Exception e) {
	}
        return o;
    }
    public boolean unexport(boolean force) {
        return exporter.unexport(force);
    }

}
