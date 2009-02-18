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
 * @summary Verify that IiopExporter will not export an object if its Tie class
 * 	    is unavailable.
 * @clean _Echo_Stub _EchoImpl_Tie
 * @build NonIiopExport Echo EchoImpl
 * @run main/othervm NonIiopExport
 */

import java.rmi.server.ExportException;
import net.jini.iiop.IiopExporter;
import org.omg.CORBA.ORB;

public class NonIiopExport {
    public static void main(String[] args) throws Exception {
	ORB orb = ORB.init(new String[0], null);
	EchoImpl impl = new EchoImpl();
	try {
	    new IiopExporter().export(impl);
	    throw new Error();
	} catch (ExportException ex) {
	}
	try {
	    new IiopExporter(orb).export(impl);
	    throw new Error();
	} catch (ExportException ex) {
	}
	orb.destroy();
    }
}
