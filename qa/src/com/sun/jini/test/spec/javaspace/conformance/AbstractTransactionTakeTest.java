/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.jini.test.spec.javaspace.conformance;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

/**
 *
 * @author peter
 */
public abstract class AbstractTransactionTakeTest extends AbstractTakeTestBase {

    public AbstractTransactionTakeTest() {
    }

    /**
     * Sets up the testing environment.
     *
     * @param config QAConfig from the runner for construct.
     */
    public Test construct(QAConfig config) throws Exception {
        // mandatory call to parent
        super.construct(config);
        // get an instance of Transaction Manager
        mgr = getTxnManager();
        return this;
    }
    
}
