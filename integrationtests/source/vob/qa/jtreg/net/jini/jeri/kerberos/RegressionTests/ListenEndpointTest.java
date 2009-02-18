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
import java.rmi.Remote;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.kerberos.KerberosServerEndpoint;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;

public class ListenEndpointTest {
    private static final String HOST = "localhost";
    private static final int PORT = 0;
    private static final String loginEntry = "testServer";
    public static void main(String[] args) throws Exception {
	LoginContext loginContext = new LoginContext(loginEntry);
	loginContext.login();
	Subject s = loginContext.getSubject();
	ServerEndpoint se1 = KerberosServerEndpoint.getInstance(s, null, null,
								PORT);
	ServerEndpoint se2 = KerberosServerEndpoint.getInstance(s, null, HOST,
								PORT);
	InvocationLayerFactory ilf = new BasicILFactory();
	Exporter e1 = new BasicJeriExporter(se1, ilf, false, false);
	Exporter e2 = new BasicJeriExporter(se2, ilf, false, false);
	e1.export(new Remote() { });
	e2.export(new Remote() { });
    }
}

