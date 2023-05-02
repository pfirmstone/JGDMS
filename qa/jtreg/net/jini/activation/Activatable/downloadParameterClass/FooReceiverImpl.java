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
import java.io.*;
import java.rmi.*;
import net.jini.activation.*;
import net.jini.activation.arg.*;
import java.rmi.server.*;
import net.jini.activation.ActivationExporter;
import net.jini.export.Exporter;
import net.jini.jeri.*;

public class FooReceiverImpl
    implements DownloadParameterClass.FooReceiver, Serializable
{

    private ActivationLibrary.ExportHelper helper;

    public FooReceiverImpl(ActivationID id, MarshalledObject mobj)
	throws ActivationException, RemoteException
    {
	helper = new ActivationLibrary.ExportHelper(mobj, this, id);
	helper.export();
    }

    private Object writeReplace() {
	return helper.getStub();
    }

    public void receiveFoo(Object obj) {
	/*
	 * Cast argument to the type "Foo" as resolved from this activatble
	 * object's class; a ClassCastException will be thrown if the argument
	 * implements a type "Foo" loaded from a different class loader.
	 */
	Foo foo = (Foo) obj;
    }
}
