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
package au.net.zeus.jgdms.service.support.smartfixture;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * A top-level, package-mate fixture standing in for a SMART service's real
 * public API interface, used by {@code CreateProxyDefaultTest} to exercise
 * {@code AbstractJiniService}'s default {@code createProxy(Object, Uuid)}
 * against the naming convention it resolves the generated proxy class by: the
 * fixture "generated" class must be a TOP-LEVEL class named
 * {@code Constrainable<simple-name>Proxy} in this same package.
 */
public interface DummyServiceApi extends Remote {
    String ping() throws RemoteException;
}
