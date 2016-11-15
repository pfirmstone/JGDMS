package org.apache.river.tool.classdepend;

/***
 * ASM examples: examples showing how ASM can be used
 * Copyright (c) 2000-2005 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

/**
 * 
 * 
 */
abstract class AbstractDependencyVisitor extends ClassVisitor {

    AbstractDependencyVisitor() {
        super(Opcodes.ASM5);
    }

    abstract protected void addName(String name);

    /* -- ClassVisitor -- */

    @Override
    public void visit(int version, int access, String name, String signature,
		      String superName, String[] interfaces)
    {
	if (signature == null) {
	    addNameInternal(superName, false);
	    addNames(interfaces);
	} else {
	    addSignature(signature);
	}
        super.visit(version, access, name, signature, superName, interfaces);
    }
    
    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
	addDesc(desc);
	AnnotationVisitor ann = super.visitAnnotation(desc, visible);
        if (ann != null) return new AnnotationVisit(Opcodes.ASM5, ann);
        return null;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc,
				   String signature, Object value)
    {
	if (signature == null) {
	    addDesc(desc);
	} else {
	    addTypeSignature(signature);
	}
	if (value instanceof Type) {
            addType((Type) value);
        }
	return super.visitField(access, name, desc, signature, value);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
				     String signature, String[] exceptions)
    {
        if (signature == null) {
            addMethodDesc(desc);
        } else {
            addSignature(signature);
        }
        addNames(exceptions);
        return new MethodVisit(Opcodes.ASM5, super.visitMethod(api, desc, desc, desc, exceptions));
    }

    @Override
    public void visitInnerClass(String name, String outerName,
				String innerName, int access)
    {
	/* XXX: Do we need to consider inner classes?
         * Yes the old ClassDep tool includes them */
        addNameInternal(outerName, false);
	addNameInternal(name, false);
        super.visitInnerClass(name, outerName, innerName, access);
    }

   /* -- Utilities -- */

    private  void addNameInternal(String name, boolean transientField) {
        if (name != null) {
	    addName(name.replace('/', '.'));
	}
    }

    private void addNames(String[] names) {
	if (names != null) {
            int l = names.length;
	    for (int i = 0; i < l; i++) {
                String name = names[i];
		addNameInternal(name, false);
	    }
	}
    }

    private void addDesc(String desc) {
        addType(Type.getType(desc));
    }

    private void addMethodDesc(String desc) {
        addType(Type.getReturnType(desc));
        Type [] type = Type.getArgumentTypes(desc);
        int l = type.length;
	for (int i = 0; i < l; i++) {            
            addType(type[i]);
	}
    }

    private void addType(Type t) {
        switch (t.getSort()) {
            case Type.ARRAY:
                addType(t.getElementType());
                break;
            case Type.OBJECT:
                addNameInternal(t.getClassName(), false);
                break;
        }
    }

    private void addSignature(String signature) {
	new SignatureReader(signature).accept(new SignatureVisit(Opcodes.ASM5));
    }

    private void addTypeSignature(String signature) {
	new SignatureReader(signature).acceptType(new SignatureVisit(Opcodes.ASM5));
    }
    
    /**
     * Annotations
     */
    private class AnnotationVisit extends AnnotationVisitor {

        public AnnotationVisit(int i, AnnotationVisitor av) {
            super(i, av);
        }
        
        @Override
        public void visit(String name, Object value) {
            if (value instanceof Type) {
                addType((Type) value);
            }
            super.visit(name, value);
        }

        @Override
        public void visitEnum(String name, String desc, String value) {
            addDesc(desc);
            super.visitEnum(name,desc,value);
        }
        
        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
            addDesc(desc);
            AnnotationVisitor ann = super.visitAnnotation(name, desc);
            if (ann != null) return new AnnotationVisit( Opcodes.ASM5, ann);
            return null;
        }
        
    }
    
    /**
     * MethodVisit delegates to encapsulated MethodVisitor as well as
     * recording dependencies.
     */
    private class MethodVisit extends MethodVisitor {

        public MethodVisit(int i, MethodVisitor mv) {
            super(i, mv);
        }
        
        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter,
                                                  String desc,
                                                  boolean visible)
        {
            addDesc(desc);
            AnnotationVisitor ann = super.visitParameterAnnotation(parameter, desc, visible);
            if (ann != null) return new AnnotationVisit(Opcodes.ASM5, ann );
            return null;
        }
        @Override
        public void visitTypeInsn(int opcode, String desc) {
            if (desc.charAt(0) == '[') {
                addDesc(desc);
            } else {
                addNameInternal(desc, false);
            }
            super.visitTypeInsn(opcode, desc);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name,
                                   String desc)
        {
            addNameInternal(owner, false);
            addDesc(desc);
            super.visitFieldInsn(opcode, owner, name, desc);
        }

        String pattern = "^\\[{0,2}L{0,1}(\\w+[/.]{1}[\\w$\\d/.]+);{0,1}$";
        Pattern arrayOfObjects = Pattern.compile(pattern);
        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String desc, boolean itf)
        {
            /* This filters out Generic's and primitive owners.
             *
             * Also when the owner is an array, containing Objects and
             * the method name is clone(), (I think it's got something to do
             * with cloning array's, this must be a new java 5 language feature
             * I tested 1.4 code without this ever occurring)      
             * we can't get the Object's type
             * using Type.getType(owner) due to the nature of 
             * the ASM Core API requiring bytecode be read sequentially.
             * This only occurs with clone() which returns java.lang.Object
             */
            Matcher match = arrayOfObjects.matcher(owner);
            while (match.find()){
                String object = match.group(1);
                addNameInternal(object, false);
            } 
            addMethodDesc(desc);
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        @Override
        public void visitLdcInsn(Object cst) {
            if (cst instanceof Type) {
                addType((Type) cst);
            }
            super.visitLdcInsn(cst);
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            addDesc(desc);
            super.visitMultiANewArrayInsn(desc,dims);
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature,
                                       Label start, Label end, int index)
        {
            if (signature != null) {
                addTypeSignature(signature);
            }
            super.visitLocalVariable(name, desc, signature, start, end, index);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler,
                                       String type)
        {
            addNameInternal(type, false);
            super.visitTryCatchBlock(start, end, handler, type);
        }
    }
    
    /**
     * Signatures
     */
    private class SignatureVisit extends SignatureVisitor {

        public SignatureVisit(int i) {
            super(i);
        }
        
        @Override
        public void visitTypeVariable(String name) {
            /* XXX: Need to do something? */
            //System.out.println(name);
            super.visitTypeVariable(name);
        }
        
        @Override
        public void visitClassType(String name) { 
            addNameInternal(name, false);
            super.visitClassType(name);
        }
        
        @Override
        public void visitInnerClassType(String name) {
            // This is not a fully qualified class name, ignore.
            super.visitInnerClassType(name);
        }
        
    }
}
