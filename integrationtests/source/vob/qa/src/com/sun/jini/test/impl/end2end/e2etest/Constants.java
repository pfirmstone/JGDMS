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

package com.sun.jini.test.impl.end2end.e2etest;

/**
 * Constants common to many classes in the test package.<p>
 *
 * Some of these constants control the output of the log file. The
 * property <code>end2end.logArgs</code> is used to specify the output
 * to be generated, for instance -Dend2end.logArgs=failures,calls would
 * cause all remote method calls and returns to be logged, as well as any
 * failures which are encountered.<p>
 *
 * Some constants define the properties of the proxy.
 */

public interface Constants {
    /** print all output - generally not a good idea */
    static final String ALL                 = "all";

    /**
     * used internally to generate output unconditionally,
     * i.e. for unexpected exceptions or other unusual events. It is
     * unnecessary to specify 'always' in <code>end2end.logArgs</code>,
     * as it is unconditionally prepended to this property string.
     */
    static final String ALWAYS              = "always";

    /** wrap test results with delimeter strings for readibility */
    static final String BOUNDARIES          = "boundaries";

    /** print call entry/return for test method calls */
    static final String CALLS               = "calls";

    /** print the authenticated client subject */
    static final String CLIENTSUBJECT	    = "clientSubject";

    /** print the value of the client constraints obtained from the proxy */
    static final String CLIENTCONSTRAINTS   = "clientConstraints";

    /** print the value of the combined constraints */
    static final String COMBINEDCONSTRAINTS = "combinedConstraints";

    /** print debug lines - not interesting for production test runs */
    static final String DEBUG               = "debug";

    /** print count of endpoints found in service proxy */
    static final String ENDPOINTCOUNT       = "endpointCount";

    /** print test failure messages - this should always be set */
    static final String FAILURES            = "failures";

    /** print summary of wrapper and method call counts */
    static final String FINALSTATISTICS     = "finalStatistics";

    /** print misc info - rarely used and not much output generated */
    static final String INFO                = "info";

    /** print internal test calls - mostly for debug */
    static final String INTERNALCALLS       = "internalCalls";

    /** print free memory before and after calls */
    static final String MEMORY		    = "memory";

    /** print call parameters - not currently used */
    static final String PARAMS              = "params";

    /** print the value of the client constraints applied proxy */
    static final String PROXYCONSTRAINTS    = "proxyConstraints";

    /** print call return values - not currently used */
    static final String RETURN              = "return";

    /** print stats on every method call */
    static final String RUNNINGSTATISTICS   = "runningStatistics";

    /** print the server constraints */
    static final String SERVERCONSTRAINTS   = "serverConstraints";

    /** print the cryptosuite being used */
    static final String SUITE               = "suite";

    /** the table of legal log flags */
    static final String[] legalLogFlags = {
    					   ALL,
    					   ALWAYS,
    					   BOUNDARIES,
    					   CALLS,
					   CLIENTSUBJECT,
					   CLIENTCONSTRAINTS,
    					   COMBINEDCONSTRAINTS,
    					   DEBUG,
    					   ENDPOINTCOUNT,
    					   FAILURES,
    					   FINALSTATISTICS,
    					   INFO,
    					   INTERNALCALLS,
					   MEMORY,
    					   PARAMS,
    					   PROXYCONSTRAINTS,
    					   RETURN,
    					   RUNNINGSTATISTICS,
    					   SERVERCONSTRAINTS,
    					   SUITE
					  } ;

    /** The proxy is exported with marshalling set to as-is */
    public final static int AS_IS = 1 << 0;

    /** The proxy is exported with marshalling set to client */
    public final static int CLIENT = 1 << 1;

    /** The proxy is exported with marshalling set to nobody */
    public final static int NOBODY = 1 << 2;

    /** The proxy is exported with marshalling set to client-priv */
    public final static int CLIENT_PRIV = 1 << 3;
}
