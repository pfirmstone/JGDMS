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

/**
* @author Alexey V. Varlamov
* @since 3.0.0
*/

package org.apache.river.api.security;

import org.apache.river.impl.Messages;
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import org.apache.river.api.security.PolicyUtils.ExpansionFailedException;


/**
 * This is a basic high-level tokenizer of policy files. It takes in a stream,
 * analyzes data read from it and returns a set of structured tokens. <br>
 * This implementation recognizes text files, consisting of clauses with the
 * following syntax:
 * 
 * <pre>
 * 
 *     keystore &quot;some_keystore_url&quot;, &quot;keystore_type&quot;;
 *  
 * </pre>
 * <pre>
 * 
 *     grant [SignedBy &quot;signer_names&quot;] [, CodeBase &quot;URL&quot;]
 *      [, Principal [principal_class_name] &quot;principal_name&quot;]
 *      [, Principal [principal_class_name] &quot;principal_name&quot;] ... {
 *      permission permission_class_name [ &quot;target_name&quot; ] [, &quot;action&quot;] 
 *      [, SignedBy &quot;signer_names&quot;];
 *      permission ...
 *      };
 *  
 * </pre>
 * 
 * <br>
 * Keywords are case-insensitive in contrast to quoted string literals.
 * Comma-separation rule is quite forgiving, most commas may be just omitted.
 * Whitespaces, line- and block comments are ignored. Symbol-level tokenization
 * is delegated to java.io.StreamTokenizer. <br>
 * <br>
 * This implementation is effectively thread-safe, as it has no field references
 * to data being processed (that is, passes all the data as method parameters).
 * 
 * @see org.apache.river.api.security.DefaultPolicyParser
 */
class DefaultPolicyScanner {
    
    /**
     * Specific exception class to signal policy file syntax error.
     * 
     */
    public static class InvalidFormatException extends Exception {

        /**
         * @serial
         */
        private static final long serialVersionUID = 5789786270390222184L;

        /** 
         * Constructor with detailed message parameter. 
         */
        public InvalidFormatException(String arg0) {
            super(arg0);
        }
	
	public InvalidFormatException(String message, Exception cause){
	    super(message, cause);
	}
    }

    /**
     * Configures passed tokenizer accordingly to supported syntax.
     */
    StreamTokenizer configure(StreamTokenizer st) {
        st.slashSlashComments(true);
        st.slashStarComments(true);
	st.wordChars('_', '_');
	st.wordChars('$', '$');
	return st;
    }

    /**
     * Performs the main parsing loop. Starts with creating and configuring a
     * StreamTokenizer instance; then tries to recognize <i>keystore </i> or
     * <i>grant </i> keyword. When found, invokes read method corresponding to
     * the clause and collects result to the passed collection.
     * 
     * @param r
     *            policy stream reader
     * @param grantEntries
     *            a collection to accumulate parsed GrantEntries
     * @param keystoreEntries
     *            a collection to accumulate parsed KeystoreEntries
     * @throws IOException
     *             if stream reading failed
     * @throws InvalidFormatException
     *             if unexpected or unknown token encountered
     */
    void scanStream(Reader r, Collection<GrantEntry> grantEntries,
            List<KeystoreEntry> keystoreEntries) throws IOException,
            InvalidFormatException {
        StreamTokenizer st = configure(new StreamTokenizer(r));
        //main parsing loop
        parsing: while (true) {
            switch (st.nextToken()) {
            case StreamTokenizer.TT_EOF: //we've done the job
                break parsing;

            case StreamTokenizer.TT_WORD:
                if (Util.equalsIgnoreCase("keystore", st.sval)) { //$NON-NLS-1$
                    keystoreEntries.add(readKeystoreEntry(st));
                } else if (Util.equalsIgnoreCase("grant", st.sval)) { //$NON-NLS-1$
                    grantEntries.add(readGrantEntry(st));
                } else {
                    handleUnexpectedToken(st, Messages.getString("security.89")); //$NON-NLS-1$
                }
                break;

            case ';': //just delimiter of entries
                break;

            default:
                handleUnexpectedToken(st);
                break;
            }
        }
    }

    /**
     * Tries to read <i>keystore </i> clause fields. The expected syntax is
     * 
     * <pre>
     * 
     *     &quot;some_keystore_url&quot;[, &quot;keystore_type&quot;];
     *  
     * </pre>
     * 
     * @return successfully parsed KeystoreEntry
     * @throws IOException
     *             if stream reading failed
     * @throws InvalidFormatException
     *             if unexpected or unknown token encountered
     */
    KeystoreEntry readKeystoreEntry(StreamTokenizer st)
            throws IOException, InvalidFormatException {
        String url = null, type = null;
        if (st.nextToken() == '"') {
            url = st.sval;
            if ((st.nextToken() == '"')
                    || ((st.ttype == ',') && (st.nextToken() == '"'))) {
                type = st.sval;
            } else { // handle token in the main loop
                st.pushBack();
            }
        } else {
            handleUnexpectedToken(st, Messages.getString("security.8A")); //$NON-NLS-1$
        }
        return new KeystoreEntry(url, type);
    }

    /**
     * Tries to read <i>grant </i> clause. <br>
     * First, it reads <i>codebase </i>, <i>signedby </i>, <i>principal </i>
     * entries till the '{' (opening curly brace) symbol. Then it calls
     * readPermissionEntries() method to read the permissions of this clause.
     * <br>
     * Principal entries (if any) are read by invoking readPrincipalEntry()
     * method, obtained PrincipalEntries are accumulated. <br>
     * The expected syntax is
     * 
     * <pre>
     * 
     *     [ [codebase &quot;url&quot;] | [signedby &quot;name1,...,nameN&quot;] | 
     *          principal ...] ]* { ... }
     *  
     * </pre>
     * 
     * @return successfully parsed GrantEntry
     * @throws IOException
     *             if stream reading failed
     * @throws InvalidFormatException
     *             if unexpected or unknown token encountered
     */
    GrantEntry readGrantEntry(StreamTokenizer st) throws IOException,
            InvalidFormatException {
        String signer = null, codebase = null;
        Collection<PrincipalEntry> principals = new ArrayList<PrincipalEntry>();
        Collection<PermissionEntry> permissions = null;
        
        parsing: while (true) {
            switch (st.nextToken()) {

            case StreamTokenizer.TT_WORD:
                if (Util.equalsIgnoreCase("signedby", st.sval)) { //$NON-NLS-1$
                    if (st.nextToken() == '"') {
                        signer = st.sval;
                    } else {
                        handleUnexpectedToken(st, Messages.getString("security.8B")); //$NON-NLS-1$
                    }
                } else if (Util.equalsIgnoreCase("codebase", st.sval)) { //$NON-NLS-1$
                    if (st.nextToken() == '"') {
                        codebase = st.sval;
                    } else {
                        handleUnexpectedToken(st, Messages.getString("security.8C")); //$NON-NLS-1$
                    }
                } else if (Util.equalsIgnoreCase("principal", st.sval)) { //$NON-NLS-1$
                    principals.add(readPrincipalEntry(st));
                } else {
                    handleUnexpectedToken(st);
                }
                break;

            case ',': //just delimiter of entries
                break;

            case '{':
                permissions = readPermissionEntries(st);
                break parsing;

            default: // handle token in the main loop
                st.pushBack();
                break parsing;
            }
        }

        return new GrantEntry(signer, codebase, principals, permissions);
    }

    /**
     * Tries to read <i>Principal </i> entry fields. The expected syntax is
     * 
     * <pre>
     * 
     *     [ principal_class_name ] &quot;principal_name&quot;
     *  
     * </pre>
     * 
     * Both class and name may be wildcards, wildcard names should not
     * surrounded by quotes.
     * 
     * @return successfully parsed PrincipalEntry
     * @throws IOException
     *             if stream reading failed
     * @throws InvalidFormatException
     *             if unexpected or unknown token encountered
     */
    PrincipalEntry readPrincipalEntry(StreamTokenizer st)
            throws IOException, InvalidFormatException {
        String classname = null, name = null;
        if (st.nextToken() == StreamTokenizer.TT_WORD) {
            classname = st.sval;
            st.nextToken();
        } else if (st.ttype == '*') {
            classname = PrincipalEntry.WILDCARD;
            st.nextToken();
        }
        if (st.ttype == '"') {
            name = st.sval;
        } else if (st.ttype == '*') {
            name = PrincipalEntry.WILDCARD;
        } else {
            handleUnexpectedToken(st, Messages.getString("security.8D")); //$NON-NLS-1$
        }
        return new PrincipalEntry(classname, name);
    }

    /**
     * Tries to read a list of <i>permission </i> entries. The expected syntax
     * is
     * 
     * <pre>
     * 
     *     permission permission_class_name
     *          [ &quot;target_name&quot; ] [, &quot;action_list&quot;]
     *          [, signedby &quot;name1,name2,...&quot;];
     *  
     * </pre>
     * 
     * List is terminated by '}' (closing curly brace) symbol.
     * 
     * @return collection of successfully parsed PermissionEntries
     * @throws IOException
     *             if stream reading failed
     * @throws InvalidFormatException
     *             if unexpected or unknown token encountered
     */
    Collection<PermissionEntry> readPermissionEntries(
            StreamTokenizer st) throws IOException, InvalidFormatException {
        Collection<PermissionEntry> permissions = new HashSet<PermissionEntry>();
        parsing: while (true) {
            switch (st.nextToken()) {

            case StreamTokenizer.TT_WORD:
                if (Util.equalsIgnoreCase("permission", st.sval)) { //$NON-NLS-1$
                    String klass = null, name = null, actions = null, signers = null;
                    
                    if (st.nextToken() == StreamTokenizer.TT_WORD) {
                        klass = st.sval;
                        if (st.nextToken() == '"') {
                            name = st.sval;
                            st.nextToken();
                        }
                        if (st.ttype == ',') {
                            st.nextToken();
                        }
                        if (st.ttype == '"') {
                            actions = st.sval;
                            if (st.nextToken() == ',') {
                                st.nextToken();
                            }
                        }
                        if (st.ttype == StreamTokenizer.TT_WORD
                                && Util.equalsIgnoreCase("signedby", st.sval)) { //$NON-NLS-1$
                            if (st.nextToken() == '"') {
                                signers = st.sval;
                            } else {
                                handleUnexpectedToken(st);
                            }
                        } else { // handle token in the next iteration
                            st.pushBack();
                        }
                        PermissionEntry pe = new PermissionEntry(klass, name, actions, signers);
                        permissions.add(pe);
                        continue parsing;
                    }
                }
                handleUnexpectedToken(st, Messages.getString("security.8E")); //$NON-NLS-1$
                break;

            case ';': //just delimiter of entries
                break;

            case '}': //end of list
                break parsing;

            default: // invalid token
                handleUnexpectedToken(st);
                break;
            }
        }

        return permissions;
    }
    
    /**
     * Formats a detailed description of tokenizer status: current token,
     * current line number, etc.
     */
    String composeStatus(StreamTokenizer st) {
        return st.toString();
    }

    /**
     * Throws InvalidFormatException with detailed diagnostics.
     * 
     * @param st
     *            a tokenizer holding the erroneous token
     * @param message
     *            a user-friendly comment, probably explaining expected syntax.
     *            Should not be <code>null</code>- use the overloaded
     *            single-parameter method instead.
     */
    final void handleUnexpectedToken(StreamTokenizer st,
            String message) throws InvalidFormatException {
        throw new InvalidFormatException(Messages.getString("security.8F", //$NON-NLS-1$
                composeStatus(st), message));
    }

    /**
     * Throws InvalidFormatException with error status: which token is
     * unexpected on which line.
     * 
     * @param st
     *            a tokenizer holding the erroneous token
     */
    final void handleUnexpectedToken(StreamTokenizer st)
            throws InvalidFormatException {
        throw new InvalidFormatException(Messages.getString("security.90", //$NON-NLS-1$
                composeStatus(st)));
    }


    
 
    
 


    /**
     * Compound token representing <i>keystore </i> clause. See policy format
     * {@link org.apache.river.api.security.ConcurrentPolicyFile description}for details.
     * 
     * @see org.apache.river.api.security.DefaultPolicyParser
     * @see org.apache.river.api.security.DefaultPolicyScanner
     */
    static class KeystoreEntry {

        /**
         * The URL part of keystore clause.
         */
        private final String url;

        /**
         * The typename part of keystore clause.
         */
        private final String type;
        
        KeystoreEntry(String url, String type){
            this.url= url;
            this.type= type;
        }
        
        public String toString(){
            String newline = "\n";
            int l = getUrl() == null? 0 : getUrl().length();
            l = l + (getType() == null? 0 : getType().length());
            l = l + 4;
            StringBuffer sb = new StringBuffer(l);
            if ( getUrl() != null ) sb.append(getUrl()).append(newline);
            if ( getType() != null ) sb.append(getType()).append(newline);
            return sb.toString();
        }

        /**
         * @return the url
         */
        String getUrl() {
            return url;
        }

        /**
         * @return the type
         */
        String getType() {
            return type;
        }
    }

    /**
     * Compound token representing <i>grant </i> clause. See policy format
     * {@link org.apache.river.api.security.ConcurrentPolicyFile description}for details.
     * 
     * @see org.apache.river.api.security.DefaultPolicyParser
     * @see org.apache.river.api.security.DefaultPolicyScanner
     */
    static class GrantEntry {

        /**
         * The signers part of grant clause. This is a comma-separated list of
         * certificate aliases.
         */
        private final String signers;

        /**
         * The codebase part of grant clause. This is an URL from which code
         * originates.  Comma separate list allowed?
         */
        private final String codebase;

        /**
         * Collection of PrincipalEntries of grant clause.
         */
        private final Collection<PrincipalEntry> principals;

        /**
         * Collection of PermissionEntries of grant clause.
         */
        private final Collection<PermissionEntry> permissions;
        
        GrantEntry(String signers, String codebase, 
                    Collection<PrincipalEntry> pe,
                    Collection<PermissionEntry> perms){
            this.signers = signers;
            this.codebase = codebase;
            this.principals = pe;
            this.permissions = perms;
        }
        
        public String toString(){
            String newline = "\n";
            StringBuilder sb = new StringBuilder(400);
            if (signers != null ) sb.append(signers).append(newline);
            if (codebase != null ) sb.append(codebase).append(newline);
            if (principals != null ) sb.append(principals).append(newline);
            if (permissions != null ) sb.append(permissions).append(newline);
            return sb.toString();
        }

        /**
         * @return the signers
         */
        String getSigners() {
            return signers;
        }

        /**
         * @return the codebase
         */
        String getCodebase(Properties system) {
            if (system == null) return codebase;
            try {
                return PolicyUtils.expand(codebase, system);
            } catch (ExpansionFailedException ex) {
//                Logger.getLogger(DefaultPolicyScanner.class.getName()).log(Level.SEVERE, null, ex);
                ex.printStackTrace(System.err);
                return codebase;
            }
        }

        /**
         * @return the principals
         */
        Collection<PrincipalEntry> getPrincipals(Properties system) {
            return principals;
        }

        /**
         * @return the permissions
         */
        Collection<PermissionEntry> getPermissions() {
            return permissions;
        }

    }

    /**
     * Compound token representing <i>principal </i> entry of a <i>grant </i>
     * clause. See policy format
     * {@link org.apache.river.api.security.ConcurrentPolicyFile description}for details.
     * 
     * @see org.apache.river.api.security.DefaultPolicyParser
     * @see org.apache.river.api.security.DefaultPolicyScanner
     */
    static class PrincipalEntry {

        /**
         * Wildcard value denotes any class and/or any name.
         * Must be asterisk, for proper general expansion and 
         * PrivateCredentialsPermission wildcarding
         */
        public static final String WILDCARD = "*"; //$NON-NLS-1$
        
        /**
         * The classname part of principal clause.
         */
        private final String klass;

        /**
         * The name part of principal clause.
         */
        private final String name;
        
        PrincipalEntry(String classname, String name){
            klass = classname;
            this.name = name;
        }
        
        public String toString(){
            String newline = "\n";
            StringBuilder sb = new StringBuilder(100);
            if ( getKlass() != null ) sb.append(getKlass()).append(newline);
            if ( getName() != null ) sb.append(getName()).append(newline);
            return sb.toString();
        }

        /**
         * @return the klass
         */
        String getKlass() {
            return klass;
        }

        /**
         * @return the name
         */
        String getName() {
            return name;
        }
    }

    /**
     * Compound token representing <i>permission </i> entry of a <i>grant </i>
     * clause. See policy format
     * {@link org.apache.river.api.security.ConcurrentPolicyFile description}for details.
     * 
     * @see org.apache.river.api.security.DefaultPolicyParser
     * @see org.apache.river.api.security.DefaultPolicyScanner
     */
    static class PermissionEntry {

        /**
         * The classname part of permission clause.
         */
        private final String klass;

        /**
         * The name part of permission clause.
         */
        private final String name;

        /**
         * The actions part of permission clause.
         */
        private final String actions;

        /**
         * The signers part of permission clause. This is a comma-separated list
         * of certificate aliases.
         */
        private final String signers;
        
        PermissionEntry(String klass, String name, String actions, String signers){
            if (klass == null) throw new NullPointerException();
            this.klass= klass;
            this.name= name == null ? "" : name;
            this.actions= actions == null ? "" : actions;
            this.signers= signers;
        }
        
        public String toString(){
            String endline = "\n";
            int l = getKlass() == null ? 0 : getKlass().length();
            l = l + (getName() == null? 0 : getName().length());
            l = l + (getActions() == null? 0 : getActions().length());
            l = l + (getSigners() == null? 0 : getSigners().length());
            l = l + 8;
            StringBuffer sb = new StringBuffer(l);
            if ( getKlass() != null ) sb.append(getKlass()).append(endline);
            if ( getName() != null ) sb.append(getName()).append(endline);
            if ( getActions() != null ) sb.append(getActions()).append(endline);
            if ( getSigners() != null ) sb.append(getSigners()).append(endline);
            return sb.toString();
        }

        /**
         * @return the klass
         */
        String getKlass() {
            return klass;
        }

        /**
         * @return the name
         */
        String getName() {
            return name;
        }

        /**
         * @return the actions
         */
        String getActions() {
            return actions;
        }

        /**
         * @return the signers
         */
        String getSigners() {
            return signers;
        }
    }
}
