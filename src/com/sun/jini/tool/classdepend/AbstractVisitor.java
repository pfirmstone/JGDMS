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
package com.sun.jini.tool.classdepend;

import org.objectweb.asm.commons.EmptyVisitor;
import org.objectweb.asm.signature.SignatureVisitor;

/**
 * An abstract implementation of the ASM visitor interfaces, including {@link
 * SignatureVisitor}.
 */
abstract class AbstractVisitor extends EmptyVisitor
    implements SignatureVisitor
{
    public void visitFormalTypeParameter(String name) { }

    public SignatureVisitor visitClassBound() { return this; }

    public SignatureVisitor visitInterfaceBound() { return this; }

    public SignatureVisitor visitSuperclass() { return this; }

    public SignatureVisitor visitInterface() { return this; }

    public SignatureVisitor visitParameterType() { return this; }

    public SignatureVisitor visitReturnType() { return this; }

    public SignatureVisitor visitExceptionType() { return this; }

    public void visitBaseType(char descriptor) { }

    public void visitTypeVariable(String name) { }

    public SignatureVisitor visitArrayType() { return this; }

    public void visitClassType(String name) { }

    public void visitInnerClassType(String name) { }

    public void visitTypeArgument() { }

    public SignatureVisitor visitTypeArgument(char wildcard) { return this; }
}
