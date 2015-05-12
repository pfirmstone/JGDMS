/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.river.test.spec.javaspace.conformance;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

/**
 *
 * @author peter
 */
public abstract class AbstractTransactionReadTest extends AbstractReadTestBase {

    public AbstractTransactionReadTest() {
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
