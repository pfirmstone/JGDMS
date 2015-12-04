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
package org.apache.river.test.impl.outrigger.matching;

// imports
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import java.lang.reflect.*;
import java.rmi.MarshalledObject;
import net.jini.core.transaction.TransactionException;
import java.rmi.RemoteException;
import net.jini.core.entry.UnusableEntryException;


/**
 * Abstract class defining the interface to strategies (Gamma et. al.)
 * for pulling information out of a JavaSpace.  This could represent a
 * take or a read, with or without a transaction.
 *
 * Because this is indented for use in a test framework entries pulled
 * out of the JavaSpace will be check against the template
 *
 * @author John McClain
 */
public abstract class SpaceQuery {

    /**
     * Method clients call to make the query, they must provide the
     * <code>JavaSpace</code> and a template.  Will return null if no
     * entry was recoved from the space.
     */
    public Entry query(JavaSpace space, Entry tmpl)
            throws UnusableEntryException, TransactionException,
            InterruptedException, RemoteException {
        Entry e = doQuery(space, tmpl);

        if (e == null || validEntry(tmpl, e)) {
            return e;
        } else {

            // ACK!
            throw new RuntimeException("SpaceQuery:query:Got an Invalid Entry"
                    + " from a query!");
        }
    }

    /**
     * Returns true if entry matches tmpl.
     */
    protected boolean validEntry(Entry tmpl, Entry entry) {

        // A null template matches any entry
        if (tmpl == null) {
            return true;
        }

        try {
            final Class tmplClass = tmpl.getClass();
            final Class entryClass = entry.getClass();

            // Entry must be an instance of tmpl
            if (!tmplClass.isInstance(entry)) {
                return false;
            }
            final Field[] tmplFields = tmplClass.getFields();
            final Field[] entryFields = entryClass.getFields();

            /*
             * For each non-null, non-static field in the template
             * check to see if it byte equals the corresponding field
             * in entry.
             */
            for (int i = 0; i < tmplFields.length; i++) {
                final Field tmplField = tmplFields[i];
                final Object tmplFieldVal = tmplField.get(tmpl);

                if (tmplFieldVal == null) {
                    continue;
                }

                if (Modifier.isStatic(tmplField.getModifiers())) {
                    continue;
                }

                // Find the corresponding fields in entry
                Field entryField = null;

                for (int j = 0; j < entryFields.length; j++) {
                    if (entryFields[j].getName().equals(tmplField.getName()) &&
                        entryFields[j].getType().equals(tmplField.getType())) {
                            entryField = entryFields[j];
                            break;
                    }
                }

                // This should never happen
                if (entryField == null) {
                    return false;
                }

                // Equality check of entryField.get(entry) and tmplFieldVal
                final MarshalledObject mEntryFieldVal = new
                        MarshalledObject(entryField.get(entry));
                final MarshalledObject mTmplFieldVal = new
                        MarshalledObject(tmplFieldVal);

                if (!mEntryFieldVal.equals(mTmplFieldVal)) {
                    return false;
                }
            }
        } catch (java.io.IOException e) {

            /*
             * If we are here we could no serialize one of the
             * fields...this can not be a legal template/entry
             */
            return false;
        } catch (IllegalAccessException e) {

            /*
             * If we are here we could not get the value from one of
             * the fields, they are all supposeted to be public and
             * have a no arg constructer, again this can not be a
             * legal template/entry
             */
            return false;
        }
        return true;
    }

    /**
     * Method to be filled in by sub-classes that performs actuall
     * query of the JavaSpace.
     */
    protected abstract Entry doQuery(JavaSpace space, Entry tmpl)
            throws UnusableEntryException, TransactionException,
            InterruptedException, RemoteException;
}
