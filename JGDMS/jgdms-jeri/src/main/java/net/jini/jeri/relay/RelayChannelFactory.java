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

package net.jini.jeri.relay;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * Opens connections to the local relay process for {@link RelayEndpoint}.
 *
 * <p>A factory is installed once per JVM with {@link
 * RelayChannels#setFactory RelayChannels.setFactory}.  Each call to {@link
 * #openChannel} must return a new, connected, blocking channel; the caller
 * owns it and closes it.  One channel carries exactly one request.
 *
 * @see RelayChannels
 * @since 4.0
 */
public interface RelayChannelFactory {

    /**
     * Returns a new channel connected to the relay.
     *
     * @return a connected channel in blocking mode
     * @throws IOException if the relay cannot be reached
     */
    SocketChannel openChannel() throws IOException;
}
