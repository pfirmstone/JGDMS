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
package au.net.zeus.jgdms.api.hello;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote service interface for the Hello World example service.
 *
 * <p>This interface demonstrates the minimal contract that a JGDMS service
 * must expose to clients.  It extends {@link Remote} so that JERI can
 * generate a remote stub for it, and every method declares
 * {@link RemoteException} to communicate transport failures to callers.
 *
 * <h2>Usage</h2>
 * Clients discover an implementation of this interface via
 * {@link net.jini.lookup.ServiceDiscoveryManager}:
 * <pre>{@code
 * ServiceTemplate template = new ServiceTemplate(
 *         null, new Class[]{ HelloService.class }, null);
 * ServiceItem[] items = sdm.lookup(template, 1, null);
 * HelloService svc = (HelloService) items[0].service;
 * System.out.println(svc.sayHello("World"));
 * }</pre>
 *
 * <h2>Security</h2>
 * When exported over an SSL/TLS endpoint the service requires callers to
 * satisfy the constraints declared in the server-side configuration
 * ({@code hello-world-service.config}):
 * <ul>
 *   <li>{@link net.jini.core.constraint.Integrity#YES} — all messages are
 *       integrity-protected</li>
 *   <li>{@link net.jini.core.constraint.ClientAuthentication#YES} — callers
 *       must present a valid X.509 certificate</li>
 * </ul>
 *
 * @see au.net.zeus.jgdms.hello.HelloWorldServiceImpl
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public interface HelloService extends Remote {

    /**
     * Returns a greeting message for the given name.
     *
     * @param name the name to greet; must not be {@code null}
     * @return a non-null greeting string, e.g. {@code "Hello, World!"}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws RemoteException      if a communication failure occurs
     */
    String sayHello(String name) throws RemoteException;
}
