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

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import net.jini.core.constraint.InvocationConstraints;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import java.security.Principal;

import javax.security.auth.Subject;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import com.sun.jini.test.impl.end2end.jssewrapper.Bridge;

/**
 * A user interface for the entire test.
 */
class GUI extends JFrame implements Constants {

    /** The test coordinator for this run */
    private TestCoordinator coordinator;

    /**
     * Construct the GUI, which consists of two panels:
     *
     *  a panel containing parameters global to the test
     *
     *  a tabbed panel containing per-client test parameters
     */
    GUI(TestCoordinator coordinator) {
    super("End-to-End Secure RMI Test");
    this.coordinator = coordinator;
    getContentPane().setLayout(new FlowLayout());
        JComponent globalPanel = new GlobalPanel();
        globalPanel.setBorder(new TitledBorder("Global Parameters"));
    getContentPane().add(globalPanel);
    Collection tests = coordinator.getTestClients();
        int counter = 1;
        JTabbedPane testTabs = new JTabbedPane();
        getContentPane().add(testTabs);
    for (Iterator it = tests.iterator(); it.hasNext(); ) {
        TestClient client = (TestClient) it.next();
            JComponent clientPanel = new TestPanel(client);
        testTabs.addTab("Client " + counter, clientPanel);
            counter++;
    }
    }

    /**
     * A panel which displays global test parameters
     */
    private class GlobalPanel extends GridBagJPanel implements Runnable {

    /** the thread which updates this display panel */
    private Thread updateThread;

    /** the label displaying the number of client threads */
    private JLabel threadCountLabel = new JLabel();

    /** the label displaying the amount of free memory */
        private JLabel freeLabel = new JLabel();

    /** the label displaying the total memory */
        private JLabel totalLabel = new JLabel();

    /** a progress bar displaying the percentage of free memory */
        private JProgressBar memoryBar = new JProgressBar();

        /** a label displaying the total number of failures */
        private JLabel totalFailuresLabel = new JLabel("0");

    /**
     * Construct the panel and start a thread which updates the
     * values of the global parameters
     */
    GlobalPanel() {

        add(new JLabel("Thread Count: "), "a=e");
        add(threadCountLabel, "a=w w=rem");
        threadCountLabel.setText(
        Integer.toString(coordinator.getThreadCount()));

        add(new JLabel("Free Memory: "),"a=e");
        add(memoryBar,"w=rem");

        add(new JLabel("Free memory: "), "a=e");
        add(freeLabel, "a=w w=rem");
        freeLabel.setForeground(Color.black);

        add(new JLabel("Total memory: "), "a=e");
        add(totalLabel, "a=w w=rem");
        totalLabel.setForeground(Color.black);

        add(new JLabel("Total number of failures detected: "), "a=e");
        add(totalFailuresLabel, "a=w w=rem");
        totalFailuresLabel.setForeground(Color.black);

        updateThread = new Thread(this, "Global Display thread");
        updateThread.setDaemon(true);
        updateThread.start();
    }

    /**
     * The run method for the background thread, which updates
     * memory usage displays and failure counts
     */
    public void run() {
            boolean done = false;

            /*
         * note that this thread can be terminated by sending it
         * an interrupt signal. In the current implementation, this
         * is never done.
         */
        while (!done) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
            Runtime runtime = Runtime.getRuntime();
            memoryBar.setStringPainted(true);
            int total = (int) runtime.totalMemory();
            memoryBar.setMaximum(total);
            totalLabel.setText(Integer.toString(total));
            int free = (int) runtime.freeMemory();
            memoryBar.setValue(free);
            freeLabel.setText(Integer.toString(free));
            Collection tests = coordinator.getTestClients();
            int totalFailures = 0;
            Iterator it = tests.iterator();
            while (it.hasNext()) {
                TestClient client = (TestClient) it.next();
                totalFailures += client.getFailureCount();
            }
            totalFailuresLabel.setText(
                    Integer.toString(totalFailures));
            }
        });
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            done = true;
        }
        }
    }
    }

    /**
     * A panel which displays parameters specific to a client
     */
    private class TestPanel extends GridBagJPanel implements UserInterface {

    /** progress bar to display the progress of the test */
    private JProgressBar testProgressBar = new JProgressBar();

        /** upper limit of <code>testProgressBar</code> */
    private int max;

    /** label to display name of method being called */
    private JLabel testNameLabel = new JLabel();

    /** label to display the ciphersuite being used in a call */
    private JLabel testSuiteLabel = new JLabel();

    /** text field to display applied client contextual constraints */
    private JTextField clientContextConstraintsLabel = new JTextField(40);

    /** text field to display applied client proxy constraints */
    private JTextField clientProxyConstraintsLabel = new JTextField(40);

    /** text field to display observed combined constraints */
    private JTextField combinedConstraintsLabel = new JTextField(40);

    /** text field to display applied server constraints */
    private JTextField serverConstraintsLabel = new JTextField(40);

    /** text field to display authenticated client subject */
    private JTextField clientSubjectLabel = new JTextField(40);

    /** text field to display authenticated server subject */
    private JTextField serverSubjectLabel = new JTextField(40);

    /** label to display number of client endpoints */
    private JLabel endpointCountLabel = new JLabel();

    /** label to display free memory before method call */
    private JLabel freePreMemoryLabel = new JLabel();

    /** label to display free memory after method call */
    private JLabel freePostMemoryLabel = new JLabel();

    /** label to display the failure count */
    private JLabel failureCountLabel = new JLabel("0");

    /** text field to display call return status */
    private JTextField statusLabel = new JTextField(40);

    /** toggle button to request pause after method call complete */
        private JToggleButton pauseButton = new JToggleButton("Pause");

    /** toggle button to request forced failure of the remote call */
        private JToggleButton forceFailButton =
        new JToggleButton("Force Failure");

    /** toggle button to request pause after test failure detected */
        private JToggleButton pauseFailureButton =
        new JToggleButton("Pause on Failure");

    /** button to continue after a pause XXX misnamed? */
    private JButton goButton = new JButton("Execute Test");

    /** client associated with this panel */
    private TestClient client;

    /** failure display dialog associated with this panel */
        private FailureDialog failureDialog;

    /**
     * Construct the display panel for the given client
     *
     * @param client the client being displayed
     */
    TestPanel(TestClient client) {
        this.client = client;
            client.registerUserInterface(this);

        testProgressBar.setStringPainted(true);
        add(new JLabel("Test Progress: "),"a=e");
            testProgressBar.setString("");
        add(testProgressBar, "a=w");
            add(new JPanel(), "f=hor wx=1 w=rem"); // fixes testProgressBar layout problem

        add(new JLabel("Test Name: "), "a=e");
        add(testNameLabel, "a=w w=rem");
            testNameLabel.setForeground(Color.black);

        add(new JLabel("Test Suite: "), "a=e");
        add(testSuiteLabel, "a=w w=rem");
            testSuiteLabel.setForeground(Color.black);

        add(new JLabel("Client contextual constraints: "), "a=e");
        add(clientContextConstraintsLabel, "a=w w=rem f=hor wx=1");
            clientContextConstraintsLabel.setEditable(false);

        add(new JLabel("Client proxy constraints: "), "a=e");
        add(clientProxyConstraintsLabel, "a=w w=rem f=hor wx=1");
            clientProxyConstraintsLabel.setEditable(false);

        add(new JLabel("Observed combined constraints: "), "a=e");
        add(combinedConstraintsLabel, "a=w w=rem f=hor wx=1");
            combinedConstraintsLabel.setEditable(false);

        add(new JLabel("Server constraints: "), "a=e");
        add(serverConstraintsLabel, "a=w w=rem f=hor wx=1");
            serverConstraintsLabel.setEditable(false);

        add(new JLabel("Client subject: "), "a=e");
        add(clientSubjectLabel, "a=w w=rem f=hor wx=1");
            clientSubjectLabel.setEditable(false);

        add(new JLabel("Server subject: "), "a=e");
        add(serverSubjectLabel, "a=w w=rem f=hor wx=1");
            serverSubjectLabel.setEditable(false);

        add(new JLabel("Number of client endpoints: "), "a=e");
        add(endpointCountLabel, "a=w w=rem");
            endpointCountLabel.setForeground(Color.black);

        add(new JLabel("Free Memory before Remote call: "), "a=e");
        add(freePreMemoryLabel, "a=w w=rem");
            freePreMemoryLabel.setForeground(Color.black);

        add(new JLabel("Free Memory after Remote call: "), "a=e");
        add(freePostMemoryLabel, "a=w w=rem");
            freePostMemoryLabel.setForeground(Color.black);

        add(new JLabel("Number of failures detected: "), "a=e");
        add(failureCountLabel, "a=w w=rem");
            failureCountLabel.setForeground(Color.black);

        add(new JLabel("Call status: "), "a=e");
        add(statusLabel, "a=w w=rem f=hor wx=1");
            statusLabel.setEditable(false);

        JPanel bPanel = new JPanel();
        bPanel.setLayout(new GridLayout(1, 4, 10, 10));
        bPanel.add(forceFailButton);
        bPanel.add(pauseButton);
            bPanel.add(pauseFailureButton);
        bPanel.add(goButton);
        goButton.addActionListener(new GoButtonHandler());
        add(bPanel, "w=rem in=10,0,10,0");
    }

    /* inherit javadoc */
    public void setTestCount(final int testNum, final int totalTests) {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            if (max != totalTests) {
            testProgressBar.setMaximum(totalTests);
            max = totalTests;
            }
            testProgressBar.setValue(testNum);
            testProgressBar.setString(Integer.toString(testNum)
                        + " of "
                        + Integer.toString(totalTests));
        }
        });
    }

    /* inherit javadoc */
    public void setTestSuite(final CipherSuite suite) {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
                    String name = (suite == null) ? ""
                                                  : suite.toString();
            testSuiteLabel.setText(name);
        }
        });
    }

    /* inherit javadoc */
    public void setTestName(final String testName) {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            testNameLabel.setText(testName);
        }
        });
    }

    /* inherit javadoc */
    public void setClientContextualConstraints(
                    final InvocationConstraints constraints)
    {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            clientContextConstraintsLabel.setText(
                constraints == null ? ""
                            : constraints.toString());
        }
        });
    }

    /* inherit javadoc */
    public void
               setClientProxyConstraints(final InvocationConstraints constraints)
        {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            clientProxyConstraintsLabel.setText(
                constraints == null ? ""
                            : constraints.toString());
        }
        });
    }

    /* inherit javadoc */
    public void setServerConstraints(final InvocationConstraints constraints)
        {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            serverConstraintsLabel.setText(
                constraints == null ? ""
                            : constraints.toString());
        }
        });
    }

    /* inherit javadoc */
    public void
               setCombinedConstraints(final InvocationConstraints constraints)
        {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            combinedConstraintsLabel.setText(
                constraints == null ? ""
                            : constraints.toString());
        }
        });
    }

        /**
     * Returns a <code>String</code> containing the comma separated names
     * of all of the <code>Principals</code> in the <code>Subject</code>
     *
     * @param subject the <code>Subject</code> who's
     * <code>Principals</code> are to be returned
     * @return the <code>String</code> of <code>Principal</code> names
     */
    private String getPrincipalString(Subject subject) {
        StringBuffer sb = new StringBuffer();
        if (subject != null) {
        Set principals = subject.getPrincipals();
        Iterator it = principals.iterator();
        while (it.hasNext()) {
            Principal p = (Principal) it.next();
            sb.append(p.getName());
            if (it.hasNext()) {
            sb.append("; ");
            }
        }
        }
        return sb.toString();
    }

    /* inherit javadoc */
    public void setClientSubject(final Subject subject) {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            clientSubjectLabel.setText(getPrincipalString(subject));
        }
        });
    }

    /* inherit javadoc */
    public void setServerSubject(final Subject subject) {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            serverSubjectLabel.setText(getPrincipalString(subject));
        }
        });
    }

    /* inherit javadoc */
    public void setEndpointCount(final int count) {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            endpointCountLabel.setText(Integer.toString(count));
        }
        });
    }

    /* inherit javadoc */
    public void setFailureCount(final int count) {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            failureCountLabel.setText(Integer.toString(count));
        }
        });
    }

    /* inherit javadoc */
    public void setPreCallFreeMemory(final long memory) {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            freePreMemoryLabel.setText(Long.toString(memory));
        }
        });
    }

    /* inherit javadoc */
    public void setPostCallFreeMemory(final long memory) {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            freePostMemoryLabel.setText(Long.toString(memory));
        }
        });
    }

    /* inherit javadoc */
    public void setCallStatus(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            statusLabel.setText(message);
        }
        });
    }

    /* inherit javadoc */
        public void setCallInProgress() {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            statusLabel.setForeground(Color.red);
        }
        });
        }

    /* inherit javadoc */
        public void setCallComplete() {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            statusLabel.setForeground(Color.black);
        }
        });
        }

    /* inherit javadoc */
        public boolean stopAfterCall() {
        return pauseButton.getModel().isSelected();
        }

    /* inherit javadoc */
        public boolean stopAfterFailure() {
        return pauseFailureButton.getModel().isSelected();
        }

    /* inherit javadoc */
    public boolean forceFailure() {
        return forceFailButton.getModel().isSelected();
    }

    /* inherit javadoc */
        public void showFailure(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            if (failureDialog == null) {
            failureDialog = new FailureDialog();
            failureDialog.pack();
            }
            failureDialog.setMessage(message);
            failureDialog.setVisible(true);
        }
        });
        }

    /**
     * ActionListener tied to the 'execute' button.
     */
    private class GoButtonHandler implements ActionListener {

        /**
         * handle a button push by calling the
         * <code>TestClient.executeTests</code> method
         *
         * @param e the ActionEvent for the button
         */
        public void actionPerformed(ActionEvent e) {
        client.executeTests();
        }
    }
    }

    /**
     * A dialog to be displayed when a failure is detected. This
     * dialog is meant to be cached, so a message setter is provided
     */
    private class FailureDialog extends JDialog {

    /** text area to display the error message text */
    private JTextArea messageArea = new JTextArea(30,60);

    /**
     * Construct the failure dialog
     */
    FailureDialog() {
        super();
        setTitle("RMI Test Failure");
        GridBagJPanel panel = new GridBagJPanel();
        getContentPane().add(panel);
        panel.add(new JLabel("Test Failure Detected"),
                 "w=rem in=10,10,10,10");
        JComponent text;
        text = new JScrollPane(messageArea,
                       JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                   JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        panel.add(text, "w=rem f=both wx=1 wy=1");
    }

    /**
     * set the message to be displayed by the dialog.
     *
     * @param message the message to display
     */
    void setMessage(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            messageArea.setText(message);
        }
        });
    }
    }
}
