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
package org.apache.river.qa.harness;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Valid;

/** 
 * A string processing class which performs pre-defined substitutions on an
 * input string. The string is scanned for special tokens, and any such token
 * found is replaced with a value obtained by searching
 * the configuration object. The substitutions are as follows:
 * <ul>
 *   <li> If the configuration parameter foo=aaaa.bbbb.cccc
 *        exists, then all occurrences of either $foo or ${foo} in the
 *        input string will be replaced with the value aaaa.bbbb.cccc
 *   <li> Any value contained in single quotes in the input string is taken
 *        literally. For example, if test.dollar='$', then the value of the
 *        property test.dollar is $.
 *   <li> Substitutions are made within the string for values contained in
 *        double quotes, but no substitutions are made for values contained
 *        in single quotes.
 *   <li> After all '$' substitutions are made, a final scan for the token
 *        &lt;gethost&gt; is made. Every occurance of this token in the string is
 *        replaced with the network host name of the system hosting the VM.
 * </ul>
 * Note that the <code>resolve</code> method is called by the 
 * <code>getParameterString</code> method of the config object. Since
 * this class indirectly calls the same method to resolve parameter values,
 * parameter resolution is done recursively. No attempt is made to
 * detect or recover from recursion loops.
 */
@AtomicSerial
public class Resolver implements Serializable {
    
    public static SerialForm [] serialForm(){
        return new SerialForm[]{
            new SerialForm("config", QAConfig.class),
            new SerialForm("tokenMap", Map.class)
        };
    }
    
    public static void serialize(PutArg arg, Resolver r) throws IOException{
        arg.put("config", r.config);
        arg.put("tokenMap", r.tokenMap);
        arg.writeArgs();
    }

    private final static Logger logger = Logger.getLogger("org.apache.river.qa.harness");
    private final QAConfig config;
    private final Map<String, String> tokenMap;

    /** 
     * Construct an instance of a <code>Resolver</code>.
     *
     * @param config the configuration object provided by the test suite.
     */
    Resolver(QAConfig config) {
	this.tokenMap = new HashMap<String, String>();
        this.config = config;
    }
    
    Resolver(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg.get("config", null, QAConfig.class),
	     Valid.copyMap(
		arg.get("tokenMap", null, Map.class),
		new HashMap<String, String>(),
		String.class,
		String.class
	     )
	);
    }
    
    private Resolver(QAConfig config, Map<String,String> tokenMap){
	this.config = config;
	this.tokenMap = tokenMap;
    }

    void setToken(String token, String value) {
	if (!token.startsWith(">")) {
	    token = "<" + token + ">";
	}
	logger.log(Level.FINEST, "setting token " + token + " to " + value);
	tokenMap.put(token, value);
    }

    /** 
     * Resolves a value in the input string by splitting it into words and
     * performing pre-defined substitutions on it. White-space separates
     * words except inside quoted strings. XXX only tabs really separate words
     * <p>
     * `<code>$<em>name</em></code>' and `<code>${<em>name</em>}</code>' are 
     * replaced by the result of calling 
     * <code>config.getStringConfigVal(name)</code>.
     * <p>
     * `<code>$/</code>' is replaced by the platform-specific file separator;
     * `<code>$:</code>' is replaced by the platform-specific path separator;
     * <p>
     * `<code>$$</code>' is replaced by a single `$'.
     * <p>
     * No substitutions are performed inside single-quoted strings;
     * $ substitutions are performed in double-quoted strings.  
     * <p>
     * After all '$' substitutions are performed, all occurances of
     * tokens are evaluated and replaced with their values
     * <p>
     * If a value cannot be resolved, it is discarded.
     * 
     * @param s <code>String</code> containing the value to be resolved
     *
     * @return <code>String</code> containing the result after the 
     *         appropriate substitution(s) are made
     *
     * @throws TestException when there is a problem resolving the value
     *                       of the input argument
     */
    String resolve(String s) throws TestException {
        if (s == null)  return s;
        StringBuffer current = new StringBuffer(64);
        char term = 0; // 0   => top level
                       // ' ' => inside word
        char c = 0;
        iLoop:
        for (int i = 0;i < s.length(); i++) {
            c = s.charAt(i);
            switch (c) {
                case '#':
                    /* # at top level introduces comment to end of line and
                     * terminates command (if found); otherwise, it goes into
                     * the current word
                     */
                    if (term == 0 || term ==' ') {
                        break iLoop;
                    } else {
                        current.append(c);
                    }
                    break;
                
                case '\'':
                case '\"':
                    /* string quotes at top level begin/end a matched pair;
                     * otherwise they are part of it
                     */
                    if (term == 0 || term == ' ') {
                        term = c;          // start matched pair
                    } else if (term == c) {
                        term = ' ';        // end matched pair
                    } else {
                        current.append(c); // put character in string
                    }
                    break;
                case '$':
                    /* dollar introduces a name to be substituted, provided
                     * it does not appear in single quotes.
                     *
                     * Special values: $/ is File.separatorChar
                     *                 $: is File.pathSeparatorChar
                     *                 $$ is $
                     */
                    if (term != '\'') {
                        StringBuffer buf = new StringBuffer();
                        String name = null;
                        try {
                            c = s.charAt(++i);
                            switch (c) {
                                case '/':
                                    current.append(File.separatorChar);
                                    continue iLoop;
                                case ':':
                                    current.append(File.pathSeparatorChar);
                                    continue iLoop;
                                case '$':
                                    current.append('$');
                                    continue iLoop;
                                case '{':
                                    c = s.charAt(++i);
                                    while (c != '}') {
                                        buf.append(c);
                                        c = s.charAt(++i);
                                    }
				    name = config.getStringConfigVal(
							     buf.toString(),
							     null);
                                    break;
                                default:
                                if (isNameChar(c)) {
                                    while(    i < s.length()
                                           && isNameChar(s.charAt(i)) )
                                    {
                                        buf.append(s.charAt(i++));
                                    }//end loop
                                    i--;
                                } else {
                                    throw new TestException
                                           ("bad $ expression: `$" + c + "'");
                                }
				name = config.getStringConfigVal(buf.toString(),
								 null);
                            }//end switch
                            /* only start a new word if there is something to
                             * substitute
                             */
                            if (name != null) {
                                if (term == 0)  term = ' ';
                                current.append(name);
                            }
                        } catch (IndexOutOfBoundsException e) {
                            //e.printStackTrace();
                            throw new TestException("bad $ expression");
                        }
                    } else {
                        current.append(c);
                    }//endif
                    break;
                case ' ':
                    /* space is skipped if not in a word; otherwise it goes
                     * into the current word
                     */
                    if (term != 0) current.append(c);
                    break;
                case '\t':
                    /* tab is skipped if not in a word; if in a word and term
                     * is space, then terminate it; otherwise it goes into 
                     * the current word
                     */
                    if (term != 0) {
                        if (term == ' ') {
                            term = 0;
                        } else {
                            current.append(c);
                        }
                    }
                    break;
                default:
                    /* other characters start a word if needed, then go into
                     * the word
                     */
                    if (term == 0)  term = ' ';
                    current.append(c);
                    break;
                }//end switch
        }//end loop(i)
        /* the end has been reached; if a word has been started, finish it */
        return handleTokens(current.toString());
    }//end resolve

    /**
     * Resolves a reference in a string. White-space separates words except
     * inside quoted strings. The given string <code>s</code> is searched for
     * all occurances of `<code>$<em>key</em></code>' and
     * `<code>${<em>key</em>}</code>'. Any matches are replaced with
     * <code>replacement</code>. All other '$' escapes are ignored. No
     * search of the configuration is performed.
     * <p>
     * No substitutions are performed inside single-quoted strings;
     * substitutions are performed in double-quoted strings.  
     *
     * @param s the input string, which may be null
     * @param ref the reference to resolve
     * @param replacement the string to replace <code>ref</code> with
     *
     * @throws IllegalArgumentException if <code>ref</code> or <code>replacement</code>
     *                                  are null
     */
    String resolveReference(String s, String ref, String replacement) 
	throws TestException 
    {
        if (s == null) {
	    return s;
	}
	if (ref == null) {
	    throw new IllegalArgumentException("ref is null");
	}
	if (replacement == null) {
	    throw new IllegalArgumentException("replacement is null");
	}
        StringBuffer current = new StringBuffer(64);
        char term = 0; // 0   => top level
                       // ' ' => inside word
        char c = 0;
        iLoop:
        for (int i = 0;i < s.length(); i++) {
            c = s.charAt(i);
            switch (c) {
	        case '#':
                    /* # at top level introduces comment to end of line and
                     * terminates command (if found); otherwise, it goes into
                     * the current word
                     */
                    if (term == 0 || term ==' ') {
                        break iLoop;
                    } else {
                        current.append(c);
                    }
                    break;
                
                case '\'':
                case '\"':
                    /* string quotes at top level begin/end a matched pair;
                     * otherwise they are part of it
                     */
                    if (term == 0 || term == ' ') {
                        term = c;          // start matched pair
                    } else if (term == c) {
                        term = ' ';        // end matched pair
                    } else {
                        current.append(c); // put character in string
                    }
                    break;
                case '$':
                    /* dollar introduces a name to be substituted, provided
                     * it does not appear in single quotes.
                     */
                    if (term != '\'') {
                        StringBuffer buf = new StringBuffer();
                        String name = null;
                        try {
                            c = s.charAt(++i);
                            switch (c) {
                                case '/':
                                    current.append(File.separatorChar);
                                    continue iLoop;
                                case ':':
                                    current.append(File.pathSeparatorChar);
                                    continue iLoop;
                                case '$':
                                    current.append('$');
                                    continue iLoop;
                                case '{':
                                    c = s.charAt(++i);
                                    while (c != '}') {
                                        buf.append(c);
                                        c = s.charAt(++i);
                                    }
				    if (buf.toString().equals(ref)) {
					name = replacement;
				    } else {
					name = "${" + buf.toString() + "}";
				    }
                                    break;
                                default:
				    if (isNameChar(c)) {
					while(i < s.length()
					      && isNameChar(s.charAt(i)) )
					{
					    buf.append(s.charAt(i++));
					}//end loop
					i--; // because incremented again by for-loop
				    }
				    if (buf.toString().equals(ref)) {
					name = replacement;
				    } else {
					name = "$" + buf.toString();
				    }
			    }//end switch
			    if (term == 0)  term = ' '; //XXX not sure about this
			    current.append(name);
			} catch (IndexOutOfBoundsException e) {
			    throw new TestException("bad $ expression");
			}
		    } else {
                        current.append(c);
                    }//endif
                    break;
	        case ' ':
		    /* space is skipped if not in a word; otherwise it goes
		     * into the current word
		     */
		    if (term != 0) current.append(c);
		    break;
	        case '\t':
                    /* tab is skipped if not in a word; if in a word and term
                     * is space, then terminate it; otherwise it goes into 
                     * the current word
                     */
                    if (term != 0) {
                        if (term == ' ') {
                            term = 0;
                        } else {
                            current.append(c);
                        }
                    }
                    break;
                default:
                    /* other characters start a word if needed, then go into
                     * the word
                     */
                    if (term == 0)  term = ' ';
                    current.append(c);
                    break;
                }//end switch
        }//end loop(i)
        /* the end has been reached; if a word has been started, finish it */
    return current.toString();
    }//end resolve

    /* Determines if the given character is one of the characters associated
     * with the dollar sign ($) substitution symbol.
     * 
     * @param c <code>char</code> containing the character to analyze.
     *
     * @return <code>true</code> if the given character is one of the
     *         characters associated with the dollar sign ($) substitution 
     *         symbol; <code>false</code> otherwise.
     */
    private boolean isNameChar(char c) {
        return (   Character.isUpperCase(c) 
                || Character.isLowerCase(c) 
                || Character.isDigit(c)
                || (c == '_') 
                || (c == '.') );
    }//end isNameChar

    /**
     * Search <code>str</code> for each occurrence of the substring
     * '<gethost>' and replace each discovered occurrence with the
     * actual name of the local host machine.
     *
     * @param str <code>String</code> to search for occurances of the
     *            substring '<gethost>'. This argument may be <code>null</code>.
     *
     * @return    a new <code>String</code>, identical to the input
     *            <code>String</code> except that each occurrence of the
     *            substring '<gethost>' is replaced with the name of the
     *            local host.
     */
    private String handleTokens(String str) throws TestException {
	if (str == null) {
	    return null;
	}
	Iterator it = tokenMap.keySet().iterator();
	while (it.hasNext()) {
	    String token = (String) it.next();
	    while (true) {
		String newString = handleToken(str, token, (String) tokenMap.get(token));
		if (newString == str) { // object equality intentional
		    break;
		}
		str = newString;
	    }
	}
	while (true) {
	    String newString = handleFileToken(str);
	    if (newString == str) {
		break;
	    }
	    str = newString;
	}
	while (true) {
	    String newString = handleURLToken(str);
	    if (newString == str) {
		break;
	    }
	    str = newString;
	}
	return str;
    }

    /**
     * Search str for the substring token, and if found, replace token
     * with value.
     */
    private String handleToken(String str, String token, String value){
	String newString = str;
        if(str != null) {
	    int begIndx = str.indexOf(token);
	    if (begIndx >= 0) {
		int endIndx = begIndx + token.length();
		String subStr0 = str.substring(0, begIndx);
		String subStr1 = str.substring(endIndx, str.length());
		newString = subStr0 + value + subStr1;
	    }
	}
	return newString;
    }

    /**
     * Search str for a token of the form '<file:foo>' where foo is a path.
     * If the token is found, convert foo into a File by calling
     * config.getComponentFile and replace the original token with the toString
     * value of the File.
     *
     * Throws a TestException if a token is found but the file reference
     * could not be found, or if there is a formatting error in the token
     */
    private String handleFileToken(String str) throws TestException {
	String newString = str;
        if(str != null) {
	    int begIndx = str.indexOf("<file:");
	    if (begIndx >= 0) {
		int endIndx = str.indexOf('>', begIndx);
		if (endIndx < 0) {
		    throw new TestException("No terminator for '<file:': "
					    + str);
		}
		String subStr0 = str.substring(0, begIndx);
		String subStr1 = str.substring(endIndx + 1, str.length());
		String fileName = str.substring(begIndx + "<file:".length(),
						endIndx).trim();
		if (fileName.startsWith("/")) {
		    fileName = fileName.substring(1);
		    logger.log(Level.FINER, 
			       "WARNING: file ref is absolute: " + str);
		}
		File file = config.getComponentFile(fileName, null);
		if (file == null) {
		    throw new TestException("file " + fileName + " not found from: " + str);
		}
		newString = subStr0 + file + subStr1;
	    }
	}
	return newString;
    }

    /**
     * Search str for a token of the form '<url:foo>' where foo is a path.
     * If the token is found, convert foo into a URL by calling
     * config.getComponentURL and replace the original token with the toString
     * value of the URL.
     *
     * Throws a TestException if a token is found but the file reference
     * could not be found, or if there is a formatting error in the token
     */
    private String handleURLToken(String str) throws TestException {
	String newString = str;
        if(str != null) {
	    int begIndx = str.indexOf("<url:");
	    if (begIndx >= 0) {
		int endIndx = str.indexOf('>', begIndx);
		if (endIndx < 0) {
		    throw new TestException("No terminator for '<url:': "
					    + str);
		}
		String subStr0 = str.substring(0, begIndx);
		String subStr1 = str.substring(endIndx + 1, str.length());
		String fileName = str.substring(begIndx + "<url:".length(),
						endIndx).trim();
		if (fileName.startsWith("/")) {
		    fileName = fileName.substring(1);
		    logger.log(Level.FINER, 
			       "WARNING: file ref is absolute: " + str);
		}
		String urlString = config.getComponentURL(fileName, null).toString();
		newString = subStr0 + urlString + subStr1;
	    }
	}
	return newString;
    }
}
