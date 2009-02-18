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
 *  GridBagParsedConstraints
 *
 *  a helper class to simplify the creation and initialization of
 *  <code>GridBagConstraints</code> objects
 *
 *  The constructor accepts a string representation of the constraints.
 *  For example a call which
 *  looks like:
 *
 *  new GridBagParsedConstraints("w=1 h=1 a=n in=10,5,10,5")
 *
 *  will create a GridBagConstraints object with constraints set to:
 *	gridwidth = 1
 *	gridheight = 1
 *	anchor = NORTH
 *	insets = 10,5,10,5
 *
 * all other constraints will take default values
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
 *</pre>
 */

package com.sun.jini.test.impl.end2end.e2etest;

import java.awt.GridBagConstraints;
import java.util.StringTokenizer;
import java.util.NoSuchElementException;
import java.awt.Insets;

class GridBagParsedConstraints extends GridBagConstraints {

    /**
     * constructs a <code>GridBagParsedConstraints<code> object initialized
     * with constraints derived from the given <code>constraintString</code>
     *
     * @param the constraint string
     *
     * @throws an IllegalArgumentException if a parsing error occurs
     */

    GridBagParsedConstraints(String constraintString) {
	StringTokenizer stok = new StringTokenizer(constraintString);

	try {
	    while (stok.hasMoreTokens()) {
		StringTokenizer thisTok =
			new StringTokenizer(stok.nextToken(),"=");
		String constraint = thisTok.nextToken();
		String constraintValue = thisTok.nextToken();
		if (constraint.equals("x")) {
		    if (constraintValue.equals("rel")) {
			gridx = GridBagConstraints.RELATIVE;
		    } else {
			gridx = Integer.parseInt(constraintValue);
		    }
		} else if (constraint.equals("y")) {
		    if (constraintValue.equals("rel")) {
			gridy = GridBagConstraints.RELATIVE;
		    } else {
			gridy = Integer.parseInt(constraintValue);
		    }
		} else if (constraint.equals("w")) {
		    if (constraintValue.equals("rel")) {
			gridwidth = GridBagConstraints.RELATIVE;
		    } else if (constraintValue.equals("rem")) {
			gridwidth = GridBagConstraints.REMAINDER;
		    } else {
			gridwidth = Integer.parseInt(constraintValue);
		    }
		} else if (constraint.equals("h")) {
		    if (constraintValue.equals("rel")) {
			gridheight = GridBagConstraints.RELATIVE;
		    } else if (constraintValue.equals("rem")) {
			gridheight = GridBagConstraints.REMAINDER;
		    } else {
			gridheight = Integer.parseInt(constraintValue);
		    }
		} else if (constraint.equals("f")) {
		    if (constraintValue.equals("none")) {
			fill = GridBagConstraints.NONE;
		    } else if (constraintValue.equals("ver")) {
			fill = GridBagConstraints.VERTICAL;
		    } else if (constraintValue.equals("hor")) {
			fill = GridBagConstraints.HORIZONTAL;
		    } else if (constraintValue.equals("both")) {
			fill = GridBagConstraints.BOTH;
		    } else throw new IllegalArgumentException("Parse error: "
						     + constraintString);
		} else if (constraint.equals("ix")) {
		    ipadx = Integer.parseInt(constraintValue);
		} else if (constraint.equals("iy")) {
		    ipady = Integer.parseInt(constraintValue);
		} else if (constraint.equals("in")) {
		    StringTokenizer inVals =
			    new StringTokenizer(constraintValue,",");
		    insets = new Insets(Integer.parseInt(inVals.nextToken()),
					Integer.parseInt(inVals.nextToken()),
					Integer.parseInt(inVals.nextToken()),
					Integer.parseInt(inVals.nextToken()));
		} else if (constraint.equals("a")) {
		    if (constraintValue.equals("n")) {
			anchor = GridBagConstraints.NORTH;
		    } else if (constraintValue.equals("s")) {
			anchor = GridBagConstraints.SOUTH;
		    } else if (constraintValue.equals("e")) {
			anchor = GridBagConstraints.EAST;
		    } else if (constraintValue.equals("w")) {
			anchor = GridBagConstraints.WEST;
		    } else if (constraintValue.equals("ne")) {
			anchor = GridBagConstraints.NORTHEAST;
		    } else if (constraintValue.equals("nw")) {
			anchor = GridBagConstraints.NORTHWEST;
		    } else if (constraintValue.equals("se")) {
			anchor = GridBagConstraints.SOUTHEAST;
		    } else if (constraintValue.equals("sw")) {
			anchor = GridBagConstraints.SOUTHWEST;
		    } else if (constraintValue.equals("c")) {
			anchor = GridBagConstraints.CENTER;
		    } else throw new Error("Parse error: " + constraintString);
		} else if (constraint.equals("wx")) {
		    Float f = new Float(constraintValue);
		    weightx = f.floatValue();
		} else if (constraint.equals("wy")) {
		    Float f = new Float(constraintValue);
		    weighty = f.floatValue();
		} else throw new Error("Parse error: " + constraintString);
	    }
	} catch (NumberFormatException e) {
	  throw new IllegalArgumentException("Parse error: "
					    + constraintString);
	}
	catch (NoSuchElementException e) {
	  throw new IllegalArgumentException("Parse error: "
					    + constraintString);
	}
    }
}
