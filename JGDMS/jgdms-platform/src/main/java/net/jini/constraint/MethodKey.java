/*
 * Copyright 2021 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.constraint;

import java.util.Arrays;
import java.util.Objects;

/**
 * Order is based on natural ordering that ensures preceding keys don't
 * match all methods of later orderings.
 */
class MethodKey implements Comparable<MethodKey> {

    final String name;
    final String[] parameters;
    private final int hashCode;
    private final int parametersHashcode;

    MethodKey(String name, String[] parameters) {
        this.name = name;
        this.parameters = parameters;
        this.parametersHashcode = Arrays.hashCode(parameters);
        int hash = 7;
        hash = 97 * hash + (this.name != null ? this.name.hashCode() : 0);
        hash = 97 * hash + parametersHashcode;
        this.hashCode = hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodKey)) return false;
        MethodKey that = (MethodKey) o;
        if (this.hashCode != that.hashCode) return false; // prevents NPE when name is null
        if (!Objects.equals(this.name, that.name)) return false;
        return Arrays.equals(this.parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public int compareTo(MethodKey o) {
        if (hashCode == o.hashCode) return 0; // Optmisation.
        if (name == null && o.name == null) {// can only be one default.
            return compareParameters(this, o);
        }
        // default is always last.
        if (name != null && o.name == null) return -1;
        if (name == null && o.name != null) return 1;
        if (name.equals(o.name)){
            return compareParameters(this, o);
        } else if (name.charAt(0) == '*') {
            String nameSubstring = name.substring(1);
            if (o.name.charAt(0) == '*') { // Both wild cards, does one match the other?  If not, order alphabetically.
                String oNameSubstring = o.name.substring(1);
                // Lets see if the shortest one matches the other.
                if ( nameSubstring.length() < oNameSubstring.length()){
                    // name can match more, so put it last.
                    if (oNameSubstring.contains(nameSubstring)) return 1;
                } else if (nameSubstring.length() > oNameSubstring.length()){
                    // oName can match more, so put name first.
                    if (nameSubstring.contains(oNameSubstring)) return -1;
                } 
                // Strings are not equal, order alphabetically.
                return nameSubstring.compareTo(oNameSubstring);
            } else if (o.name.charAt(o.name.length()-1) == '*'){ // Wild card but trailing, does one contain the other?
                String oNameSubstring = o.name.substring(0, o.name.length() - 1);
                // Lets see if the shortest one matches the other.
                if ( nameSubstring.length() < oNameSubstring.length()){
                    // name can match more, so put it last.
                    if (oNameSubstring.contains(nameSubstring)) return 1;
                } else if (nameSubstring.length() > oNameSubstring.length()){
                    // oName can match more, so put name first.
                    if (nameSubstring.contains(oNameSubstring)) return -1;
                } 
                // Strings are not equal, order alphabetically.
                return nameSubstring.compareTo(oNameSubstring);
            } else { // Normal string comes first.
                return 1;
            }
        } else if (name.charAt(name.length()-1) == '*'){
            String nameSubstring = name.substring(0, name.length()-1);
            if (o.name.charAt(0) == '*') { // Both wild cards, does one match the other?  If no, order alphabetically.
                String oNameSubstring = o.name.substring(1);
                // Lets see if the shortest one matches the other.
                if ( nameSubstring.length() < oNameSubstring.length()){
                    // name can match more, so put it last.
                    if (oNameSubstring.contains(nameSubstring)) return 1;
                } else if (nameSubstring.length() > oNameSubstring.length()){
                    // oName can match more, so put name first.
                    if (nameSubstring.contains(oNameSubstring)) return -1;
                } 
                // Strings are not equal, order alphabetically.
                return nameSubstring.compareTo(oNameSubstring);
            } else if (o.name.charAt(o.name.length()-1) == '*'){ // Wild card but trailing, does one contain the other?
                String oNameSubstring = o.name.substring(0, o.name.length() - 1);
                // Lets see if the shortest one matches the other.
                if ( nameSubstring.length() < oNameSubstring.length()){
                    // name can match more, so put it last.
                    if (oNameSubstring.contains(nameSubstring)) return 1;
                } else if (nameSubstring.length() > oNameSubstring.length()){
                    // oName can match more, so put name first.
                    if (nameSubstring.contains(oNameSubstring)) return -1;
                } 
                // Strings are not equal, order alphabetically.
                return nameSubstring.compareTo(oNameSubstring);
            } else { // Normal string comes first.
                return 1;
            }
        } else if (o.name.charAt(0) == '*' || o.name.charAt(o.name.length()-1) == '*'){ // We know that name is not a wild card.
            // Name is normal string, so it goes first.
            return -1;
        } else { // No wild cards, compare method name strings, then parameters.
            return name.compareTo(o.name);
        }
    }
    
    private int compareParameters(MethodKey key1, MethodKey key2){
        if (key1.parametersHashcode == key2.parametersHashcode) return 0;  // Hashcode is 0 for null.
        if (key2.parameters == null && key1.parameters != null) return -1;
        else if (key1.parameters == null) return 1;
        int n = key1.parameters.length - key2.parameters.length;
        if (n < 0) return -1;
        if (n > 0) return 1;
        n = key1.parametersHashcode - key2.parametersHashcode;
        if (n < 0) return -1;
        if (n > 0) return 1;
        if (Arrays.equals(key1.parameters, key2.parameters)) return 0;
        return -1; 
    }
    
    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(name).append("( ");
        if (parameters != null){
            for (int i = 0, l = parameters.length; i < l; i++){
                sb.append(parameters[i]).append(" ");
            }
        }
        sb.append(") ");
        return sb.toString();
    }
    
}
