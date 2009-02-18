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
 * @summary Test basic IiopExporter functionality for "unconnected" exports
 * 	    (i.e., exports which do not connect to an ORB).  Port 50340 must be
 * 	    available for tnameserv to run on in order for this test to
 * 	    function properly.
 * @library ../../../../testlibrary
 * @build TestLibrary
 * @build UnconnectedExport Echo EchoImpl _Echo_Stub _EchoImpl_Tie
 * @run main/othervm -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory -Djava.naming.provider.url=iiop://localhost:50340 UnconnectedExport
 */

import java.io.File;
import javax.naming.InitialContext;
import net.jini.iiop.IiopExporter;

public class UnconnectedExport {
    public static void main(String[] args) throws Exception {
	final int REPS = 100;
	final int TNAMESERV_PORT = 50340;
	
	String tnameservPath = TestLibrary.getProperty("java.home", "") +
	    File.separator + "bin" + File.separator + "tnameserv";
	Process tnameserv = Runtime.getRuntime().exec(
	    tnameservPath + " -ORBInitialPort " + TNAMESERV_PORT);
	try {
	    // tnameserv writes to stdout once started
	    tnameserv.getInputStream().read();
	    try { Thread.sleep(1000); } catch (InterruptedException ex) {}

	    InitialContext context = new InitialContext();
	    IiopExporter exporter = new IiopExporter();
	    EchoImpl impl = new EchoImpl();
	    context.bind("echo", exporter.export(impl));
	    Echo stub = (Echo) context.lookup("echo");

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
	} finally {
	    tnameserv.destroy();
	}
    }
}
