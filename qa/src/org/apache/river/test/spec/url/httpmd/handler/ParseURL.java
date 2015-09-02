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
package org.apache.river.test.spec.url.httpmd.handler;

import java.util.logging.Level;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
// org.apache.river.qa.harness
import org.apache.river.qa.harness.QAConfig; // base class for QAConfig
import org.apache.river.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.Vector;

// davis packages
import net.jini.url.httpmd.Handler;

// java.net
import java.net.URL;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of {@link Handler#parseURL(URL,String,int,int)} method
 *   and syntax of HTTPMD URL.
 *   {@link Handler#parseURL(URL,String,int,int)} method parses the string representation
 *   of an HTTPMD URL and is invoked while HTTPMD URL object is being created.
 *
 *   HTTPMD URLs have a syntax similar to that of HTTP URLs, but include a
 *   message digest as the last parameter stored in the last segment of the
 *   path. The parameter is introduced by the ';' character, and includes the
 *   name of the message digest algorithm, a '=', the message digest, and an
 *   optional comment introduced by the ',' character. In addition, a comment
 *   by itself may be specified in a relative HTTPMD URL. Comments are ignored
 *   when using equals to compare HTTPMD URLs. The comment specified in the
 *   context URL is ignored when parsing a relative HTTPMD URL. Adding a
 *   comment to an HTTPMD URL is useful in cases where the URL is required to
 *   have a particular suffix, for example the ".jar" file extension.
 *   A comment-only relative HTTPMD URL is useful when specifying the URL of
 *   the containing document from within the contents of the document, where
 *   the message digest cannot be specified because it is not yet known.
 *
 *   The message digest algorithm is case-insensitive, and may include ASCII
 *   letters and numbers, as well as the following characters:
 *
 *    - _ . ~ * ' ( ) : @ & + $ ,
 *
 *
 *   The value specifies the name of the MessageDigest algorithm to use. For
 *   the URL syntax to be valid, the value must be the name of a MessageDigest
 *   algorithm as determined by calling MessageDigest.getInstance(String).
 *
 *   The message digest is represented as a positive hexadecimal integer,
 *   using digits, and the letters 'a' through 'f', in either lowercase or
 *   uppercase.
 *
 *   The characters following the ',' comment character may include ASCII
 *   letters and numbers, as well as the following characters:
 *
 *    - _ . ~ * ' ( ) : @ & = + $ ,
 *
 *
 * Test Cases:
 *   This test tries to create various HTTPMD URL objects using the following
 *   constructors:
 *     {@link URL#URL(String)}
 *     {@link URL#URL(URL,String)}
 *   {@link Handler#parseURL(URL,String,int,int)} method
 *   is invoked while HTTPMD URL object is being created.
 *   The cases:
 *     - Bad absolute syntax
 *       (expected result: java.net.MalformedURLException.class)
 *       ParseURLBadAbs1:  httpmd://foo:20/bar/baz?q#r
 *       ParseURLBadAbs2:  httpmd://foo/bar/baz;?q#r
 *       ParseURLBadAbs3:  httpmd:/bar/baz;md5?q#r
 *       ParseURLBadAbs4:  httpmd:baz;md5=?q#r
 *       ParseURLBadAbs5:  httpmd:baz;md5=abxd#r
 *       ParseURLBadAbs6:  httpmd:baz;ugh=abcd
 *       ParseURLBadAbs7:  httpmd:baz;=
 *       ParseURLBadAbs8:  httpmd:baz;=abcd
 *       ParseURLBadAbs9:  httpmd:baz;md5=abcd;ugh=1234
 *       ParseURLBadAbs10: httpmd:baz?;md5=abcd
 *       ParseURLBadAbs11: httpmd:baz?q#;md5=abcd
 *       ParseURLBadAbs12: httpmd:baz?q;md5=abcd,!
 *       ParseURLBadAbs13: httpmd:,
 *       ParseURLBadAbs14: httpmd:,comment
 *       ParseURLBadAbs15: httpmd://foo:20;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$
 *
 *     - Good absolute syntax
 *       ParseURLGoodAbs1:  HTTPMD://FOO:20/bar/baz;p1=v1;MD5=ABCD?q#r
 *               expected:  httpmd://FOO:20/bar/baz;p1=v1;MD5=ABCD?q#r
 *       ParseURLGoodAbs2:  httpmd://foo:20/bar/baz;sha=1234?q
 *               expected:  httpmd://foo:20/bar/baz;sha=1234?q
 *       ParseURLGoodAbs3:  httpmd://foo:20/bar/baz;md5=1234
 *               expected:  httpmd://foo:20/bar/baz;md5=1234
 *       ParseURLGoodAbs4:  httpmd://foo/bar/baz;md5=1234
 *               expected:  httpmd://foo/bar/baz;md5=1234
 *       ParseURLGoodAbs5:  httpmd:/bar/baz;md5=1234
 *               expected:  httpmd:/bar/baz;md5=1234
 *       ParseURLGoodAbs6:  httpmd:/bar/baz;md5=1234,?q#r
 *               expected:  httpmd:/bar/baz;md5=1234,?q#r
 *       ParseURLGoodAbs7:  httpmd:/bar/baz;md5=1234,Hello7-_.~*'():@&=+$,#r
 *               expected:  httpmd:/bar/baz;md5=1234,Hello7-_.~*'():@&=+$,#r
 *       ParseURLGoodAbs8:  httpmd:/bar/baz;md5=1234,x*?q
 *               expected:  httpmd:/bar/baz;md5=1234,x*?q
 *       ParseURLGoodAbs9:  httpmd:/bar,baz;md5=1234,c1
 *               expected:  httpmd:/bar,baz;md5=1234,c1
 *       ParseURLGoodAbs10: httpmd:baz;sha-1=99f6837808c0a79398bf69d83cfb1b82d20cf0cf
 *                expected: httpmd:baz;sha-1=99f6837808c0a79398bf69d83cfb1b82d20cf0cf
 *       ParseURLGoodAbs11: httpmd:;md5=1234
 *                expected: httpmd:;md5=1234
 *       ParseURLGoodAbs12: httpmd://foo:20/bar/baz;sha=1234#r
 *                expected: httpmd://foo:20/bar/baz;sha=1234#r
 *       ParseURLGoodAbs13: httpmd://foo:20/file.jar;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$
 *                expected: httpmd://foo:20/file.jar;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$
 *
 *     - Bad relative syntax
 *       (expected result: java.net.MalformedURLException.class)
 *       context:   httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2
 *       ParseURLBadRel1:   //foo/bar/baz;?q#r
 *       ParseURLBadRel2:   /bar/baz;sha?q#r
 *       ParseURLBadRel3:   /bar/baz;=?q#r
 *       ParseURLBadRel4:   baz;sha=?q#r
 *       ParseURLBadRel5:   baz;sha=abxd#r
 *       ParseURLBadRel6:   baz;ugh=abcd
 *       ParseURLBadRel7:   baz;sha=abcd;ugh=1234
 *       ParseURLBadRel8:   baz?;sha=abcd
 *       ParseURLBadRel9:   baz?q#;sha=abcd
 *       ParseURLBadRel10:  baz
 *       ParseURLBadRel11:  baz,comment
 *       ParseURLBadRel12:  !bad-comment
 *       ParseURLBadRel13:  baz;sha=abcd,{?q#r
 *       ParseURLBadRel14:  c1?q1#r1
 *       ParseURLBadRel15:  c1?q1
 *       ParseURLBadRel16:  c1#r1
 *       ParseURLBadRel17:  c1
 *       ParseURLBadRel18:  ?q1#r1
 *
 *     - Good relative syntax
 *       ParseURLGoodRel1
 *                 context: httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c1?q2#r2
 *                 rel url: httpmd://foo:30/bar/baz;sha=1234,c2?q#r
 *                expected: httpmd://foo:30/bar/baz;sha=1234,c2?q#r
 *       ParseURLGoodRel2
 *                 context: httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c1?q2#r2
 *                 rel url: httpmd://foo:30/bar/baz;a=b,c;sha=1234?q
 *                expected: httpmd://foo:30/bar/baz;a=b,c;sha=1234?q
 *       ParseURLGoodRel3
 *                 context: httpmd://alpha:20/beta/gamma;p2=v1,2;md5=abcd?q2#r2
 *                 rel url: //foo:30/bar/baz;sha=1234,c2#r
 *                expected: httpmd://foo:30/bar/baz;sha=1234,c2#r
 *       ParseURLGoodRel4
 *                 context: httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2
 *                 rel url: //foo:30/bar/baz;sha=1234
 *                expected: httpmd://foo:30/bar/baz;sha=1234
 *       ParseURLGoodRel5
 *                 context: httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2
 *                 rel url: /bar/baz;sha=1234?q#r
 *                expected: httpmd://alpha:20/bar/baz;sha=1234?q#r
 *       ParseURLGoodRel6
 *                 context: httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2
 *                 rel url: baz;sha=1234?q#r
 *                expected: httpmd://alpha:20/beta/baz;sha=1234?q#r
 *       ParseURLGoodRel7
 *                 context: httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2
 *                 rel url: baz;sha=1234?q#r
 *                expected: httpmd://alpha:20/beta/baz;sha=1234?q#r
 *       ParseURLGoodRel8
 *                 context: httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2
 *                 rel url: ;sha=1234?q#r
 *                expected: httpmd://alpha:20/beta/;sha=1234?q#r
 *       ParseURLGoodRel9
 *                 context: httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
 *                 rel url: ,c1?q1#r1
 *                expected: httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c1?q1#r1
 *       ParseURLGoodRel10
 *                 context: httpmd://alpha:-1/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
 *                 rel url: ,c1?q1
 *                expected: httpmd://alpha:-1/beta/gamma;p2=v2;md5=abcd,c1?q1
 *       ParseURLGoodRel11
 *                 context: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
 *                 rel url: ,c1#r1
 *                expected: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c1#r1
 *       ParseURLGoodRel12
 *                 context: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
 *                 rel url: ,c1
 *                expected: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c1
 *       ParseURLGoodRel13
 *                 context: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r2
 *                 rel url: ,
 *                expected: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,
 *       ParseURLGoodRel14
 *                 context: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2
 *                 rel url: ,?q1#r1
 *                expected: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,?q1#r1
 *       ParseURLGoodRel15
 *                 context: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2
 *                 rel url: #r1
 *                expected: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r1
 *       ParseURLGoodRel16
 *                 context: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2
 *                 rel url: ,#r1
 *                expected: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,#r1
 *       ParseURLGoodRel17
 *                 context: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2
 *                 rel url: gamma;p2=v2;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$
 *                expected: httpmd://alpha/beta/gamma;p2=v2;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$
 *       ParseURLGoodRel18
 *                 context: httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2
 *                 rel url: file.jar;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$
 *                expected: httpmd://alpha/beta/file.jar;md5=abcdefABCDEF0123456789,abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~*'():@&=+$
 *
 *
 * Infrastructure:
 *     - ParseURL.TestItem
 *         auxiliary class that describes a Test Case
 *     - ParseURL
 *         performs actions
 *
 * Actions:
 *   Jini Harness does the following before running the test:
 *     - setting java.protocol.handler.pkgs property to
 *       net.jini.url to enable HTTPMD URL objects creating
 *       ({@link Handler} is used as HTTPMD Protocol handler)
 *   Test performs the following steps:
 *     - for each case do the following:
 *        - trying to create HTTPMD URL object,
 *        - comparing created URL object or thrown Exception with the expected result.
 *
 * </pre>
 */
public class ParseURL extends QATestEnvironment implements Test {
    QAConfig config;

    /**
     * All Test Cases (each element describes a Test Case).
     */
    protected Vector items = new Vector();

    /**
     * Test Cases names
     * The value is specified by ParseURL.testCases test property.
     */
    protected String[] testCases;

    /**
     * Getting Test Class name.
     *
     * @return Test Class name
     */
    public String getTestClassName() {
        return this.getClass().getName();
    }

    /**
     * Getting Test Cases names.
     *
     * @return Test Cases names
     */
    public String[] getTestCaseNames() {
        String cName =
                getTestClassName().substring(getTestClassName().lastIndexOf(".")
                + 1);
        String tests = config.getStringConfigVal(cName + ".testCases", null);
        return tests.split(" ");
    }

    /**
     * <pre>
     * This method performs all preparations.
     * Test parameters:
     *    ParseURL.testCases            - Test Cases names
     *    &lt;TestCaseName&gt;.Context  - the context in which to parse the specification
     *    &lt;TestCaseName&gt;.Spec     - the String to parse as a HTTPMD URL
     *    &lt;TestCaseName&gt;.Expected - expected result (Exception or HTTPMD URL in the
     *                                    String representation)
     * </pre>
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /* Getting test parameters and creating TestItem objects */
        String[] tc_names = getTestCaseNames();

        for (int i = 0; i < tc_names.length; i++) {
            items.add(i, new TestItem(tc_names[i]));
        }
        return this;
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        boolean returnedVal = true;

        for (int i = 0; i < items.size(); i++) {
            boolean retVal = testCase((TestItem) items.get(i));

            if (retVal != true) {
                returnedVal = retVal;
            }
        }

        if (returnedVal != true) {
            throw new TestException(
                    "" + " test failed");
        }
        return;
    }

    /**
     * Test Case actions.
     *
     * @param ti TestItem object that descibes a Test Case
     * @return result of the Test Case (true (if the returned
     *         value is equal to the expected one) or false)
     */
    public boolean testCase(TestItem ti) {

        /* Test Case Name */
        String t_name = ti.getTestCaseName();
        logger.log(Level.FINE, "\n=============== Test Case name: " + t_name);

        /* Test Case Parameters */
        String cxt = ti.getContext(); // Context in which to parse
        String spec = ti.getSpec(); // Spec to parse as HTTPMD URL
        String exp = ti.getExpected(); // Expected result
        logger.log(Level.FINE, "\tContext  : " + cxt);
        logger.log(Level.FINE, "\tSpec     : " + spec);
        logger.log(Level.FINE, "\tExpected : " + exp);

        /*
         * Creating context URL object from the String representation if
         * context isn't null
         */
        URL contextURL = null;

        if (cxt != null) {
            try {
                contextURL = new URL(cxt);
            } catch (Exception e) {
                logger.log(Level.FINE,
                        t_name + " test failed:\n" + "Exception while Context URL creating: "
                        + e);
                return false;
            }
        }

        /*
         * Trying to create a HTTPMD URL object by parsing the given spec
         * within a specified context.
         */
        URL httpmdURL;

        try {
            httpmdURL = new URL(contextURL, spec);

            /* If Exception is expected */
            if (exp.endsWith(".class")) {
                Class expClass = Class.forName(exp.substring(0,
                        exp.lastIndexOf(".class")));
                logger.log(Level.FINE,
                        t_name + " test failed:\n" + "Expected result: "
                        + expClass.getName() + "\n" + "Returned result: "
                        + httpmdURL);
                return false;
            }

            /*
             * If no Exception is expected.
             * Comparing created HTTPMD URL object with the expected one.
             */
            if (!httpmdURL.toString().equals(exp)) {
                logger.log(Level.FINE,
                        t_name + " test failed:\n" + "Expected result: " + exp
                        + "\nReturned result: " + httpmdURL.toString());
                return false;
            }
            logger.log(Level.FINE, t_name + " test case passed");
            return true;
        } catch (Exception e) {
            Class excClass = e.getClass();
            Class expClass;

            /* If no Exception is expected */
            if (!exp.endsWith(".class")) {
                logger.log(Level.FINE,
                        t_name + " test failed:" + "\nExpected result: " + exp
                        + "\nReturned result: " + excClass.getName()
                        + "\n                 " + e);
                return false;
            }

            /* If Exception is expected */
            try {
                expClass = Class.forName(exp.substring(0,
                        exp.lastIndexOf(".class")));
            } catch (ClassNotFoundException ee) {
                logger.log(Level.FINE,
                        t_name + " test failed:\n" + "Exception while Class object creating: "
                        + ee);
                return false;
            }

            if (excClass.getName() != expClass.getName()) {
                logger.log(Level.FINE,
                        t_name + " test failed:\n" + "Expected result: "
                        + expClass.getName() + "\n" + "Returned result: "
                        + excClass.getName() + "\n                 " + e);
                return false;
            }
            logger.log(Level.FINE, t_name + " test case passed");
            return true;
        }
    }


    /**
     * Auxiliary class that describes a Test Case.
     */
    protected class TestItem {

        /**
         * The Test Case name.
         */
        protected String testCaseName;

        /**
         * The context in which to parse the specification as a String
         * (null if spec represents an absolute HTTPMD URL).
         * The value is specified by &lt;TestCaseName&gt;.Context test property.
         */
        protected String context;

        /**
         * The String to parse as a HTTPMD URL.
         * The value is specified by &lt;TestCaseName&gt;.Spec test property.
         */
        protected String spec;

        /**
         * Expected result (Exception or HTTPMD URL in the String representation).
         * The value is specified by &lt;TestCaseName&gt;.Expected test property.
         */
        protected String expected;

        /**
         * Creating TestItem object (Constructor)
         *
         * @param tcname Test Case name
         * @throws MalformedURLException if URL object can't be created from
         *                               the String representation
         */
        public TestItem(String tcname) {
            testCaseName = tcname;
            context =
                    replaceQuote(replacePound(replaceDollar(config.getStringConfigVal
                    (tcname + ".Context", null))));
            spec =
                    replaceQuote(replacePound(replaceDollar(config.getStringConfigVal
                    (tcname + ".Spec", null))));
            expected =
                    replaceQuote(replacePound(replaceDollar(config.getStringConfigVal
                    (tcname + ".Expected", null))));
        }

        /**
         * Getting Test Case name of this TestItem object.
         *
         * @return Test Case name of this TestItem object
         */
        public String getTestCaseName() {
            return testCaseName;
        }

        /**
         * Getting the context in which to parse the specification.
         *
         * @return the context in which to parse the specification
         */
        public String getContext() {
            return context;
        }

        /**
         * Getting the String to parse as a HTTPMD URL.
         *
         * @return the String to parse as a HTTPMD URL
         */
        public String getSpec() {
            return spec;
        }

        /**
         * Getting the expected result (Exception or HTTPMD URL in the
         * String representation).
         *
         * @return the expected result
         */
        public String getExpected() {
            return expected;
        }

        /**
         * Replacing &lt;DollarSign&gt; with $ sign.
         *
         * @param from string
         *
         * @return String object with &lt;DollarSign&gt; replaced with $ sign
         */
        public String replaceDollar(String from) {
            final String at = "<DollarSign>";
            final Character dollar = new Character('$');

            if (from == null) {
                return from;
            }
            StringBuffer res = new StringBuffer(from);

            while (true) {
                int ind = res.indexOf(at);

                if (ind == -1) {
                    break;
                }
                res = res.replace(ind, ind + at.length(), dollar.toString());
            }
            return res.toString();
        }

        /**
         * Replacing &lt;PoundSign&gt; with # sign.
         *
         * @param from string
         *
         * @return String object with &lt;PoundSign&gt; replaced with # sign
         */
        public String replacePound(String from) {
            final String at = "<PoundSign>";
            final Character pound = new Character('#');

            if (from == null) {
                return from;
            }

            if (!(from.matches(".*" + at + ".*"))) {
                return from;
            }
            return from.replaceAll(at, pound.toString());
        }

        /**
         * Replacing &lt;QuoteSign&gt; with ' sign.
         *
         * @param from string
         *
         * @return String object with &lt;QuoteSign&gt; replaced with ' sign
         */
        public String replaceQuote(String from) {
            final String at = "<QuoteSign>";
            final Character quote = new Character('\'');

            if (from == null) {
                return from;
            }

            if (!(from.matches(".*" + at + ".*"))) {
                return from;
            }
            return from.replaceAll(at, quote.toString());
        }
    }
}
