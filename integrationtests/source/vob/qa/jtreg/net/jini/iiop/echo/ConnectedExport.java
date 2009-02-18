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
 * @summary Test basic IiopExporter functionality for "connected" exports
 * 	    (i.e., exports with a specified ORB to which to connect).
 * @build ConnectedExport Echo EchoImpl _Echo_Stub _EchoImpl_Tie
 * @run main/othervm ConnectedExport
 */

import net.jini.iiop.IiopExporter;
import org.omg.CORBA.ORB;

public class ConnectedExport {
    public static void main(String[] args) throws Exception {
	final int REPS = 100;
	ORB orb = ORB.init(new String[0], null);
	IiopExporter exporter = new IiopExporter(orb);
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
	orb.destroy();
    }
}
