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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Defines a ClassLoader that supplies resources from files specified as a
 * Map which maps resource names to file names.
 */
public class FileClassLoader extends ClassLoader {
    private final Map resourceFiles;

    public FileClassLoader(Map resourceFiles) {
	this.resourceFiles = resourceFiles;
    }

    public FileClassLoader(Map resourceFiles, ClassLoader parent) {
	super(parent);
	this.resourceFiles = resourceFiles;
    }

    public String toString() {
	return "FileClassLoader{ " + resourceFiles + ", " + getParent() + " }";
    }

    protected Class findClass(String name) throws ClassNotFoundException {
	String resource = name.replace('.', '/').concat(".class");
	String file = (String) resourceFiles.get(resource);
	if (file == null) {
	    throw new ClassNotFoundException(name);
	}
	InputStream in = null;
	try {
	    in = new BufferedInputStream(new FileInputStream(file));
	    byte[] bytes = readToEOF(in);
	    return defineClass(name, bytes, 0, bytes.length);
	} catch (IOException e) {
	    throw new ClassNotFoundException(name, e);
	} finally {
	    if (in != null) {
		try {
		    in.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    private static byte[] readToEOF(InputStream in) throws IOException {
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	byte[] b = new byte[1024];
	int len;
	while ((len = in.read(b)) != -1) {
	    baos.write(b, 0, len);
	}
	return baos.toByteArray();
    }

    protected URL findResource(String name) {
	String file = (String) resourceFiles.get(name);
	try {
	    if (file != null) {
		return new URL("file:" + file);
	    }
	} catch (MalformedURLException e) {
	}
	return null;
    }

    protected Enumeration findResources(final String name) {
	return new Enumeration() {
	    private URL url = findResource(name);
	    public boolean hasMoreElements() { return url != null; }
	    public Object nextElement() {
		if (url == null) {
		    throw new NoSuchElementException();
		}
		URL result = url;
		url = null;
		return result;
	    }
	};
    }
}
