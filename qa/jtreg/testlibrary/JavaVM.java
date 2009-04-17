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
/**
 * 
 */

import java.io.*;
import java.util.Arrays;
import java.util.Properties;

/**
 * RMI regression test utility class that uses Runtime.exec to spawn a
 * java process that will run a named java class.  
 */
public class JavaVM {

    // need to 
    protected Process vm = null;

    private String classname = "";
    private String args = "";
    private String options = "";
    private OutputStream outputStream = System.out;
    private OutputStream errorStream = System.err;
    private String policyFileName = null;
    private String preamble;
    
    static void mesg(Object mesg) {
	System.err.println("JAVAVM: " + mesg.toString());
    }

    /** string name of the program execd by JavaVM */
    public static String javaProgram = "java";

    static {
	try {
	    javaProgram = TestLibrary.getProperty("java.home", "") + 
		File.separator + "bin" + File.separator + javaProgram;
	} catch (SecurityException se) {
	}
    }

    public JavaVM(String classname) {
	this(classname, "", "", System.out, System.err, "#");	
    }
    public JavaVM(String classname, 
		  String options, String args) {
	this(classname, options, args, System.out, System.err, "#");
    }
    public JavaVM(String classname, 
		  String options, String args, String preamble) {
	this(classname, options, args, System.out, System.err, preamble);
    }

    public JavaVM(String classname, 
		  String options, String args,
		  OutputStream out, OutputStream err)
    {
	this(classname, options, args, out, err, null);
    }

    public JavaVM(String classname, 
		  String options, String args,
		  OutputStream out, OutputStream err, String preamble)
    {
	this.classname = classname;
	this.options = options;
	this.args = args;
	this.outputStream = out;
	this.errorStream = err;
	this.preamble = preamble + ": ";
    }    

    public void addOptions(String[] opts) {
	String newOpts = "";
	for (int i = 0 ; i < opts.length ; i ++) {
	    newOpts += " " + opts[i];
	}
	newOpts += " ";
	options = newOpts + options;
    }
    public void addArguments(String[] arguments) {
	String newArgs = "";
	for (int i = 0 ; i < arguments.length ; i ++) {
	    newArgs += " " + arguments[i];
	}
	newArgs += " ";
	args = newArgs + args;
    }

    public void setPolicyFile(String policyFileName) {
	this.policyFileName = policyFileName;
    }
		 		     
    protected String getCodeCoverageOptions() {
        return TestLibrary.getExtraProperty("jcov.options","");
    }

    /**
     * Exec the VM as specified in this object's constructor.
     */
    public void start() throws IOException {

	if (vm != null) return;

	/*
	 * If specified, add option for policy file
	 */
	if (policyFileName != null) {
	    String option = "-Djava.security.policy=" + policyFileName;
	    addOptions(new String[] { option });
	}
	
	addOptions(new String[] { getCodeCoverageOptions() });

	String javaCommand = JavaVM.javaProgram + 
	    " " + options + " " + classname + " " + args;

	mesg("command = " + javaCommand);
	System.err.println("");

	/* REMIND: this is a temporary workaround so that multi-component
	 * codebases get parsed correctly
	 */

	java.util.StringTokenizer tokens = 
	    new java.util.StringTokenizer(javaCommand);
	java.util.ArrayList parsedCommand = 
	    new java.util.ArrayList();
	while (tokens.hasMoreTokens()) {
	    String nextToken = tokens.nextToken();
	    if (nextToken.startsWith("-Djava.rmi.server.codebase=\"")) {
		while (tokens.hasMoreTokens()) {
		    nextToken += " " + tokens.nextToken();
		    if (nextToken.endsWith("\"")) {
			break;
		    }
		}
		int index = nextToken.indexOf('\"');
		// remove leading and ending quote characters
		nextToken = nextToken.substring(0, index) +
		    nextToken.substring(index+1, nextToken.length()-1);
	    }
	    parsedCommand.add(nextToken);
	}

	String[] execCommand =
	    (String[]) parsedCommand.toArray(new String[parsedCommand.size()]);
	vm = Runtime.getRuntime().exec(execCommand);
	System.err.println("JavaVM: parsed command: " + Arrays.asList(execCommand));
	
	/* output from the execed process may optionally be captured. */
	StreamPipe.plugTogether(vm.getInputStream(), this.outputStream, preamble);
	StreamPipe.plugTogether(vm.getErrorStream(), this.errorStream, preamble);
	
	try {
	    Thread.sleep(2000);
	} catch (Exception ignore) {
	}
	
	mesg("finished starting vm.");
    }
    
    public void destroy()
    {
	if (vm != null) {
	    vm.destroy();
	}
	vm = null;
    }
    
    protected Process getVM() {
	return vm;
    }
}
