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
/* @test 
 * @summary Test non-activatable exports using JrmpExporter.
 * @build TestExport TestExport_Stub CountedSocketFactory Echo 
 * @run main/othervm TestExport
 */

import java.rmi.RemoteException;
import net.jini.jrmp.JrmpExporter;

public class TestExport implements Echo {
    
    public int echo(int val) throws RemoteException { 
	return val;
    }
    
    public void shutdown() throws Exception {
    }
    
    public static void main(String[] args) throws Exception {
	testExport(new JrmpExporter());
	
	CountedSocketFactory csf = new CountedSocketFactory();
	testExport(new JrmpExporter(0, csf, csf));
	if (csf.clientSocketsCreated == 0 ||
	    csf.serverSocketsCreated == 0 ||
	    csf.serverSocketsAccepted == 0)
	{
	    throw new Error();
	}
    }
    
    static void testExport(JrmpExporter exporter) throws Exception {
	TestExport impl = new TestExport();
	Echo stub = (Echo) exporter.export(impl);
	for (int i = 0; i < 100; i++) {
	    if (stub.echo(i) != i) {
		throw new Error();
	    }
	}
	if (!exporter.unexport(true)) {
	    throw new Error();
	}
	try {
	    stub.echo(0);
	    throw new Error();
	} catch (Exception ex) {
	}
    }
}
