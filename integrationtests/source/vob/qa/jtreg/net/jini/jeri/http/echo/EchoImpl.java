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
 * @summary Echo implementation for testing basic HTTP endpoint functionality.
 * @build Echo EchoImpl
 * @run main/othervm EchoImpl
 * @run main/othervm -Dhttp.proxyHost=jiniproxy -Dhttp.proxyPort=8001 EchoImpl
 */

import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.http.HttpServerEndpoint;

public class EchoImpl implements Echo {
    int numEchoes = 0;

    public int echo(int val) throws java.rmi.RemoteException { 
	numEchoes++; 
	return val;
    }
    
    public static void main(String[] args) throws Exception {
	testExport(new BasicJeriExporter(HttpServerEndpoint.getInstance(0),
					 new BasicILFactory()));
	
	CountedSocketFactory csf = new CountedSocketFactory();
	CountedServerSocketFactory cssf = new CountedServerSocketFactory();
	testExport(
	    new BasicJeriExporter(
		HttpServerEndpoint.getInstance(null, 0, csf, cssf),
		new BasicILFactory()));
	if (csf.clientSocketsCreated == 0 ||
	    cssf.serverSocketsCreated == 0 ||
	    cssf.serverSocketsAccepted == 0)
	{
	    throw new Error();
	}
    }

    static void testExport(BasicJeriExporter exporter) throws Exception {
	final int REPS = 100;
	EchoImpl impl = new EchoImpl();
	Echo stub = (Echo) exporter.export(impl);
	for (int i = 0; i < REPS; i++) {
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
	if (impl.numEchoes != REPS) {
	    throw new Error();
	}
    }
}
