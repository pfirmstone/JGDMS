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
package org.apache.river.tool;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.Principal;

/**
 * Builds a {@link Principal} whose class has the name a policy gives, when the lint cannot make
 * one from that class: the class is not on the lint's classpath (a SPIFFE or JWT principal of
 * another module) or has no constructor taking a name.
 *
 * <p>A policy provider matches a grant's {@code principal} clause on the principal's class name
 * and its name, so a class of the same name stands in.  It is a minimal class file (a name field,
 * a constructor taking the name, {@code getName}) defined in a loader of its own, so it never
 * shadows the real class for anything else.  A class in {@code java.} cannot be defined that way
 * and gets no stand-in.
 */
final class StandInPrincipal {

    private StandInPrincipal() {}

    /** Loader of the generated classes; one per stand-in. */
    private static final class Loader extends ClassLoader {
        Loader() {
            super(StandInPrincipal.class.getClassLoader());
        }

        Class<?> define(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    /**
     * @return a principal of a class named {@code className} with name {@code name}; {@code null}
     *         if no such class can be defined
     */
    static Principal create(String className, String name) {
        if (className == null || className.startsWith("java.") || className.length() == 0) return null;
        try {
            Class<?> c = new Loader().define(className, classFile(className.replace('.', '/')));
            return (Principal) c.getConstructor(String.class).newInstance(name);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError | IOException e) {
            return null;
        }
    }

    private static byte[] classFile(String internal) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        o.writeInt(0xCAFEBABE);
        o.writeShort(0);
        o.writeShort(52);
        o.writeShort(19); // constant pool count = 18 entries + 1
        utf8(o, internal);                     // 1
        o.writeByte(7); o.writeShort(1);       // 2 Class this
        utf8(o, "java/lang/Object");           // 3
        o.writeByte(7); o.writeShort(3);       // 4 Class Object
        utf8(o, "java/security/Principal");    // 5
        o.writeByte(7); o.writeShort(5);       // 6 Class Principal
        utf8(o, "name");                       // 7
        utf8(o, "Ljava/lang/String;");         // 8
        utf8(o, "<init>");                     // 9
        utf8(o, "(Ljava/lang/String;)V");      // 10
        utf8(o, "Code");                       // 11
        utf8(o, "getName");                    // 12
        utf8(o, "()Ljava/lang/String;");       // 13
        utf8(o, "()V");                        // 14
        o.writeByte(12); o.writeShort(9); o.writeShort(14);   // 15 NameAndType <init>:()V
        o.writeByte(10); o.writeShort(4); o.writeShort(15);   // 16 Methodref Object.<init>
        o.writeByte(12); o.writeShort(7); o.writeShort(8);    // 17 NameAndType name:String
        o.writeByte(9); o.writeShort(2); o.writeShort(17);    // 18 Fieldref this.name
        o.writeShort(0x0021);                  // public super
        o.writeShort(2);                       // this
        o.writeShort(4);                       // super
        o.writeShort(1);                       // one interface
        o.writeShort(6);
        o.writeShort(1);                       // one field
        o.writeShort(0x0002); o.writeShort(7); o.writeShort(8); o.writeShort(0);
        o.writeShort(2);                       // two methods
        // <init>(String)
        o.writeShort(0x0001); o.writeShort(9); o.writeShort(10); o.writeShort(1);
        o.writeShort(11); o.writeInt(22);
        o.writeShort(2); o.writeShort(2); o.writeInt(10);
        o.writeByte(0x2a);                                   // aload_0
        o.writeByte(0xb7); o.writeShort(16);                 // invokespecial Object.<init>
        o.writeByte(0x2a); o.writeByte(0x2b);                // aload_0 aload_1
        o.writeByte(0xb5); o.writeShort(18);                 // putfield name
        o.writeByte(0xb1);                                   // return
        o.writeShort(0); o.writeShort(0);
        // getName()
        o.writeShort(0x0001); o.writeShort(12); o.writeShort(13); o.writeShort(1);
        o.writeShort(11); o.writeInt(17);
        o.writeShort(1); o.writeShort(1); o.writeInt(5);
        o.writeByte(0x2a);                                   // aload_0
        o.writeByte(0xb4); o.writeShort(18);                 // getfield name
        o.writeByte(0xb0);                                   // areturn
        o.writeShort(0); o.writeShort(0);
        o.writeShort(0);                       // class attributes
        o.flush();
        return bo.toByteArray();
    }

    private static void utf8(DataOutputStream o, String s) throws IOException {
        o.writeByte(1);
        o.writeUTF(s);
    }
}
