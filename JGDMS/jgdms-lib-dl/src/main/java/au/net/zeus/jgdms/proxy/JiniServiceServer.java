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
package au.net.zeus.jgdms.proxy;

import java.rmi.Remote;
import net.jini.admin.JoinAdmin;
import org.apache.river.admin.DestroyAdmin;

/**
 * Remote wire-protocol interface that all {@code AbstractJiniService} server
 * stubs expose for administrative purposes.
 *
 * <p>Any service that extends {@code AbstractJiniService} implements this
 * interface, so its exported stub automatically carries these administration
 * methods.  The client-side {@link AbstractJiniServiceAdminProxy} receives the
 * stub typed as {@code JiniServiceServer} and delegates all
 * {@link JoinAdmin} and {@link DestroyAdmin} calls to it.
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 * @since 3.1.1
 * @see AbstractJiniServiceAdminProxy
 */
public interface JiniServiceServer extends JoinAdmin, DestroyAdmin, Remote {
}
