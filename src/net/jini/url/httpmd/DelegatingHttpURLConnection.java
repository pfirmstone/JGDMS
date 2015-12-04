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

package net.jini.url.httpmd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.security.Permission;
import java.util.Map;

/**
 * An HTTP URL connection that delegates all operations defined by
 * HttpURLConnection and URLConnection.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
class DelegatingHttpURLConnection extends HttpURLConnection {
    
    /** The HTTP URL connection to which operations should be delegated. */
    HttpURLConnection delegateConnection;

    /**
     * Creates an HttpURLConnection for the specified URL that delegates all
     * operations to the value of the delegateConnection field, which should be
     * set separately.
     */
    DelegatingHttpURLConnection(URL url) {
	super(url);
    }

    /* -- HttpURLConnection methods -- */

    public void setInstanceFollowRedirects(boolean followRedirects) {
	delegateConnection.setInstanceFollowRedirects(followRedirects);
    }

    public boolean getInstanceFollowRedirects() {
	return delegateConnection.getInstanceFollowRedirects();
    }

    public void setRequestMethod(String method) throws ProtocolException {
	delegateConnection.setRequestMethod(method);
    }

    public String getRequestMethod() {
	return delegateConnection.getRequestMethod();
    }
    
    public int getResponseCode() throws IOException {
	return delegateConnection.getResponseCode();
    }

    public String getResponseMessage() throws IOException {
	return delegateConnection.getResponseMessage();
    }

    public void disconnect() {
	delegateConnection.disconnect();
    }

    public boolean usingProxy() {
	return delegateConnection.usingProxy();
    }

    public InputStream getErrorStream() {
	return delegateConnection.getErrorStream();
    }

    /* -- URLConnection methods -- */

    public void connect() throws IOException {
	delegateConnection.connect();
    }

    public URL getURL() {
	return delegateConnection.getURL();
    }

    public int getContentLength() {
	return delegateConnection.getContentLength();
    }

    public String getContentType() {
	return delegateConnection.getContentType();
    }

    public String getContentEncoding() {
	return delegateConnection.getContentEncoding();
    }

    public long getExpiration() {
	return delegateConnection.getExpiration();
    }

    public long getDate() {
	return delegateConnection.getDate();
    }

    public long getLastModified() {
	return delegateConnection.getLastModified();
    }

    public String getHeaderField(String name) {
	return delegateConnection.getHeaderField(name);
    }

    public Map getHeaderFields() {
	return delegateConnection.getHeaderFields();
    }

    public int getHeaderFieldInt(String name, int Default) {
	return delegateConnection.getHeaderFieldInt(name, Default);
    }

    public long getHeaderFieldDate(String name, long Default) {
	return delegateConnection.getHeaderFieldDate(name, Default);
    }

    public String getHeaderFieldKey(int n) {
	return delegateConnection.getHeaderFieldKey(n);
    }

    public String getHeaderField(int n) {
	return delegateConnection.getHeaderField(n);
    }

    public Object getContent() throws IOException {
	return delegateConnection.getContent();
    }

    public Object getContent(Class[] classes) throws IOException {
	return delegateConnection.getContent(classes);
    }

    public Permission getPermission() throws IOException {
	return delegateConnection.getPermission();
    }

    public InputStream getInputStream() throws IOException {
	return delegateConnection.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
	return delegateConnection.getOutputStream();
    }

    public void setDoInput(boolean doinput) {
	delegateConnection.setDoInput(doinput);
    }

    public boolean getDoInput() {
	return delegateConnection.getDoInput();
    }

    public void setDoOutput(boolean dooutput) {
	delegateConnection.setDoOutput(dooutput);
    }

    public boolean getDoOutput() {
	return delegateConnection.getDoOutput();
    }

    public void setAllowUserInteraction(boolean allowuserinteraction) {
	delegateConnection.setAllowUserInteraction(allowuserinteraction);
    }

    public boolean getAllowUserInteraction() {
	return delegateConnection.getAllowUserInteraction();
    }

    public void setUseCaches(boolean usecaches) {
	delegateConnection.setUseCaches(usecaches);
    }

    public boolean getUseCaches() {
	return delegateConnection.getUseCaches();
    }

    public void setIfModifiedSince(long ifmodifiedsince) {
	delegateConnection.setIfModifiedSince(ifmodifiedsince);
    }

    public long getIfModifiedSince() {
	return delegateConnection.getIfModifiedSince();
    }

    public boolean getDefaultUseCaches() {
	return delegateConnection.getDefaultUseCaches();
    }

    public void setDefaultUseCaches(boolean defaultusecaches) {
	delegateConnection.setDefaultUseCaches(defaultusecaches);
    }

    public void setRequestProperty(String key, String value) {
	delegateConnection.setRequestProperty(key, value);
    }

    public void addRequestProperty(String key, String value) {
	delegateConnection.addRequestProperty(key, value);
    }

    public String getRequestProperty(String key) {
	return delegateConnection.getRequestProperty(key);
    }

    public Map getRequestProperties() {
	return delegateConnection.getRequestProperties();
    }
}
    
