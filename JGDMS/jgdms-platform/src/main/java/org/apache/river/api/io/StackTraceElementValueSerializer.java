/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.river.api.io;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * The DER serializer for a {@link StackTraceElement} that is a value in its own right (a field, an element
 * of a {@code StackTraceElement[]}), as opposed to a frame inside an exception.
 *
 * <p>It carries the same four fields as {@link StackTraceElementSerializer} (declaringClass, methodName,
 * fileName, lineNumber) but implements {@link Resolve}, so a decoder hands the caller the
 * {@code StackTraceElement}. {@link StackTraceElementSerializer} cannot: {@link DerThrowableForm} declares its
 * {@code stack} field as {@code StackTraceElementSerializer[]}, which a resolved element would no longer fit.
 * The class loader name and module name and version of a frame are not carried (as for frames in an
 * exception), so a decoded frame is not {@code equals} to one that had them.
 *
 * <p>The value is inert: a decode rebuilds it from strings and an int, loading no class and running no code.
 */
@Serializer(replaceObType = StackTraceElement.class)
@AtomicSerial
class StackTraceElementValueSerializer implements Resolve {

    private static final String DECLARING_CLASS = "declaringClass";
    private static final String METHOD_NAME = "methodName";
    private static final String FILE_NAME = "fileName";
    private static final String LINE_NUMBER = "lineNumber";

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm(DECLARING_CLASS, String.class),
            new SerialForm(METHOD_NAME, String.class),
            new SerialForm(FILE_NAME, String.class),
            new SerialForm(LINE_NUMBER, Integer.TYPE)
        };
    }

    public static void serialize(PutArg arg, StackTraceElementValueSerializer s) throws IOException {
        arg.put(DECLARING_CLASS, s.element.getClassName());
        arg.put(METHOD_NAME, s.element.getMethodName());
        arg.put(FILE_NAME, s.element.getFileName());
        arg.put(LINE_NUMBER, s.element.getLineNumber());
        arg.writeArgs();
    }

    private final StackTraceElement element;

    StackTraceElementValueSerializer(StackTraceElement element) {
        this.element = element;
    }

    StackTraceElementValueSerializer(GetArg arg) throws IOException, ClassNotFoundException {
        this(build(arg.get(DECLARING_CLASS, null, String.class),
                arg.get(METHOD_NAME, null, String.class),
                arg.get(FILE_NAME, null, String.class),
                arg.get(LINE_NUMBER, 0)));
    }

    private static StackTraceElement build(String declaringClass, String methodName, String fileName,
                                           int lineNumber) throws InvalidObjectException {
        if (declaringClass == null) {
            throw new InvalidObjectException("declaring class cannot be null");
        }
        if (methodName == null) {
            throw new InvalidObjectException("method name cannot be null");
        }
        return new StackTraceElement(declaringClass, methodName, fileName, lineNumber);
    }

    @Override
    public Object readResolve() throws ObjectStreamException {
        return element;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StackTraceElementValueSerializer s && s.element.equals(element);
    }

    @Override
    public int hashCode() {
        return element.hashCode();
    }
}
