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
/*  */

/**
 *<pre>
 *  GridBagJPanel
 *
 *  class to simplify using the GridBagLayout manager and make the
 *  application code more readable
 *
 *  extends JPanel by adding a method, add, which accepts a
 *  string representation of the constraints. For example a call which
 *  looks like:
 *
 *  fooPanel.add(new Canvas(),"w=1 h=1 a=n in=10,5,10,5")
 *
 *  will add a canvas to the panel fooPanel, with:
 *	gridwidth = 1
 *	gridheight = 1
 *	anchor = NORTH
 *	insets = 10,5,10,5
 *
 * all other parameters will take default values
 *
 * magic string tokens:
 *  w = gridwidth, takes an int or one of the tokens rem (REMAINDER)
 *      or rel (RELATIVE)
 *  h = gridheight, ditto
 *  x = gridx, takes an int or the token rel
 *  y = gridy, ditto
 *  f = fill, takes one of the tokens none hor (HORIZONTAL) ver (VERTICAL) both
 *  a = anchor, takes one of n s e w ne nw se sw c for the compass point
 *  ix= ipadx, takes an int
 *  iy= ipady, takes an int
 *  in= insets, takes four ints separated by commas
 *  wx= weightx, takes an int or a float
 *  wy= weighty, ditto
 *
 * a parse error generates an IllegalArgumentException. This makes
 * error detection simple during development, without cluttering up the
 * code with try/catch blocks to catch exceptions which would never occur
 * in production
 *
 * the constructor automatically creates a GridBagLayout manager for the
 * GridBagPanel, so it is unnecessary to use setLayout to add one.
 * </pre>
 */
package org.apache.river.test.impl.end2end.e2etest;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.util.*;
import javax.swing.*;
import java.awt.*;

public class GridBagJPanel extends JPanel {

    private GridBagLayout layout;

    /**
     * constructs a <code>GridBagJPanel</code>
     */
    public GridBagJPanel() {
        layout = new GridBagLayout();
        setLayout(layout);
    }

    /**
     * add a component to the panel, using the given
     * <code>constraintString</code> to
     * specify the constraints for the component.
     *
     * @param component the component to add
     * @param constraintString the string specifying the constraint to apply
     *
     * @throws IllegalArgumentException if the
     * <code>constraintsString</code> cannot be parsed
     */

    public void add(Component component, String constraintString) {
        GridBagConstraints gbc = new GridBagParsedConstraints(constraintString);
        layout.setConstraints(component,gbc);
        add(component);
    }
}
