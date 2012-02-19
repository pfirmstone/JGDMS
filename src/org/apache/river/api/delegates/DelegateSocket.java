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

package org.apache.river.api.delegates;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketPermission;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.security.Guard;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 *
 * @author peter
 */
class DelegateSocket extends Socket{
    
    private final static String connect = "CONNECT";
    
    private volatile Guard g; // Only ever write while holding lock on s.
    private final Socket s; // Synchronize on s, not "this", since caller could obtain it for dos.
    
    DelegateSocket(Socket socket, Guard guard){
        g = guard;
        s = socket;
    }
    
    // Only overridded in case superclass reimplemented.
    public void connect(SocketAddress endpoint) throws IOException {
        s.connect(endpoint, 0); 
    }

    public void connect(final SocketAddress endpoint, final int timeout) throws IOException {
        g.checkGuard(this);
        Guard ng = DelegatePermission.get(new SocketPermission(endpoint.toString(), connect));
        ng.checkGuard(this); // Make sure this will succeed.
        // caller probably does not have SocketPermission.
        try {
            AccessController.doPrivileged( new PrivilegedExceptionAction(){
                public Object run() throws IOException{
                    s.connect(endpoint, timeout);
                    return null;
                }
            });
            synchronized (s) {
                g = ng;
            }
        } catch (PrivilegedActionException ex) {
            Exception e = ex.getException();
            if ( e instanceof IOException) throw (IOException)e;
            throw new IOException("ConnectionFailed: " + endpoint.toString(), e);
        }
    }

    public void bind(SocketAddress bindpoint) throws IOException {
        if (isClosed()) throw new SocketException("Socket is closed");
	if (isBound())throw new SocketException("Already bound");
        s.bind(bindpoint);
    }

    public InetAddress getInetAddress() {
	return s.getInetAddress();
    }

    public InetAddress getLocalAddress() {
	return s.getLocalAddress();
    }
    
    public int getPort() {
	return s.getPort();
    }

    public int getLocalPort() {
	return s.getLocalPort();
    }
    
    public SocketAddress getRemoteSocketAddress() {
	return s.getRemoteSocketAddress();
    }

    public SocketAddress getLocalSocketAddress() {
	return s.getLocalSocketAddress();
    }
    
    public SocketChannel getChannel() {
        SocketChannel ch = s.getChannel();
        return new DelegateSocketChannel(ch, g);
    }

    
    public InputStream getInputStream() throws IOException {
        InputStream in = s.getInputStream();
	return new DelegateInputStream(in, g); // Not atomic can it be attacked?
    }

    public OutputStream getOutputStream() throws IOException {
	OutputStream out = s.getOutputStream();
        return new DelegateOutputStream(out, g);
    }
   
    public void setTcpNoDelay(boolean on) throws SocketException {
	g.checkGuard(this);
        s.getTcpNoDelay();
    }

    public boolean getTcpNoDelay() throws SocketException {
	return s.getTcpNoDelay();
    }
    
    public void setSoLinger(boolean on, int linger) throws SocketException {
	g.checkGuard(this);
        s.setSoLinger(on, linger);
    }

    public int getSoLinger() throws SocketException {
	return s.getSoLinger();
    }

    public void sendUrgentData (int data) throws IOException  {
        g.checkGuard(this);
        s.sendUrgentData(data);
    }

    public void setOOBInline(boolean on) throws SocketException {
	g.checkGuard(this);
        s.setOOBInline(on);
    }

    public boolean getOOBInline() throws SocketException {
	return s.getOOBInline();
    }

    public void setSoTimeout(int timeout) throws SocketException {
        g.checkGuard(this);
        s.setSoTimeout(timeout);
    }

    public int getSoTimeout() throws SocketException {
        return s.getSoTimeout();
    }

    public void setSendBufferSize(int size) throws SocketException{
        g.checkGuard(this);
        s.setSendBufferSize(size);
    }

    public int getSendBufferSize() throws SocketException {
        return s.getSendBufferSize();
    }

    public void setReceiveBufferSize(int size) throws SocketException {
        g.checkGuard(this);
        s.setReceiveBufferSize(size);
    }

    public int getReceiveBufferSize() throws SocketException {
        return s.getReceiveBufferSize();
    }
    
    public void setKeepAlive(boolean on) throws SocketException {
	g.checkGuard(this);
        s.setKeepAlive(on);
    }
   
    public boolean getKeepAlive() throws SocketException {
	return s.getKeepAlive();
    }

    public void setTrafficClass(int tc) throws SocketException {
	g.checkGuard(this);
        s.setTrafficClass(tc);
    }

    public int getTrafficClass() throws SocketException {
        return s.getTrafficClass();
    }

    public void setReuseAddress(boolean on) throws SocketException {
	g.checkGuard(this);
        s.setReuseAddress(on);
    }

    public boolean getReuseAddress() throws SocketException {
	return s.getReuseAddress();
    }

    
    public void close() throws IOException {
        g.checkGuard(this);
        s.close();
    }

    public void shutdownInput() throws IOException
    {
	g.checkGuard(this);
        s.shutdownInput();
    }
    
    public void shutdownOutput() throws IOException
    {
	g.checkGuard(this);
        s.shutdownOutput();
    }

    public String toString() {
	return s.toString();
    }

    public boolean isConnected() {
	return s.isConnected();
    }

    public boolean isBound() {
	return s.isBound();
    }

    public boolean isClosed() {
        return s.isClosed();
    }

    public boolean isInputShutdown() {
        return s.isInputShutdown();
    }

    public boolean isOutputShutdown() {
        return s.isOutputShutdown();
    }

    public void setPerformancePreferences(int connectionTime,
                                          int latency,
                                          int bandwidth)
    {
	g.checkGuard(this);
        s.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

}
