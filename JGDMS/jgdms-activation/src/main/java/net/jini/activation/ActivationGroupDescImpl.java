/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.jini.activation;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Properties;
import net.jini.activation.arg.ActivationGroupDesc;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

@AtomicSerial
public final class ActivationGroupDescImpl implements Serializable, ActivationGroupDesc {
    private static final long serialVersionUID = -4936225423168276595L;
    
    public static SerialForm [] serialForm(){
        return new SerialForm[]{
            new SerialForm("className", String.class),
            new SerialForm("location", String.class),
            new SerialForm("data", String[].class),
            new SerialForm("env", CommandEnvironmentImpl.class),
            new SerialForm("props", Properties.class)
        };
    }
    
    public static void serialize(PutArg arg, ActivationGroupDescImpl desc) throws IOException{
        arg.put("className", desc.className);
        arg.put("location", desc.location);
        arg.put("data", desc.data);
        arg.put("env", desc.env);
        arg.put("props", desc.props.clone());
        arg.writeArgs();
    }

    /**
     * The group's fully package qualified class name.
     */
    private final String className;

    /**
     * The location from where to load the group's class.
     */
    private final String location;

    /**
     * The group's initialization data.
     */
    private final String[] data;

    /**
     * The controlling options for executing the VM in another process.
     */
    private final CommandEnvironment env;

    /**
     * A properties map which will override those set by default in the
     * subprocess environment
     */
    private Properties props;
    
    public ActivationGroupDescImpl(GetArg arg) throws IOException, ClassNotFoundException{
        this(
            arg.get("className", null, String.class),
            arg.get("lcoation", null, String.class),
            arg.get("data", null, String[].class),
            arg.get("props", null, Properties.class),
            arg.get("env", null, CommandEnvironment.class)
        );
    }

    public ActivationGroupDescImpl(Properties props, CommandEnvironment env) {
        this(null, null, null, props, env);
    }

    public ActivationGroupDescImpl(String className, String codebase, String[] data,
            Properties props, CommandEnvironment env) {
        super();
        this.className = className;
        this.location = codebase;
        this.data = data == null ? null : data.clone();
        this.props = props;
        this.env = env;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public String[] getData() {
        return data == null? null : data.clone();
    }

    @Override
    public CommandEnvironment getCommandEnvironment() {
        return env;
    }

    @Override
    public Properties getPropertyOverrides() {
        return props == null ? null : (Properties) props.clone();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((className == null) ? 0 : className.hashCode());
        result = prime * result + ((data == null) ? 0 : Arrays.hashCode(data));
        result = prime * result + ((env == null) ? 0 : env.hashCode());
        result = prime * result + ((location == null) ? 0 : location.hashCode());
        result = prime * result + ((props == null) ? 0 : props.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ActivationGroupDescImpl)) {
            return false;
        }
        final ActivationGroupDescImpl that = (ActivationGroupDescImpl) obj;
        if (!(className == null ? that.className == null : className.equals(that.className))) {
            return false;
        }
        if (!(data == null ? that.data == null : Arrays.equals(data, that.data))) {
            return false;
        }
        if (!(env == null ? that.env == null : env.equals(that.env))) {
            return false;
        }
        if (!(location == null ? that.location == null : location.equals(that.location))) {
            return false;
        }
        return (props == null ? that.props == null : props.equals(that.props));
    }

    @AtomicSerial
    public static final class CommandEnvironmentImpl implements Serializable, ActivationGroupDesc.CommandEnvironment {
        private static final long serialVersionUID = 6165754737887770191L;
        
        public static SerialForm[] serialForm(){
            return new SerialForm[]{
                new SerialForm("command", String.class),
                new SerialForm("options", String[].class)
            };
        }
        
        public void serialize(PutArg arg, CommandEnvironmentImpl c) throws IOException{
            arg.put("command", c.command);
            arg.put("options", c.options == null? null : c.options.clone());
            arg.writeArgs();
        }

        private final String command;

        private final String options[];

        public CommandEnvironmentImpl(String command, String[] options) {
            super();
            this.command = command;
            if (options == null) {
                this.options = null;
            } else {
                this.options = new String[options.length];
                System.arraycopy(options, 0, this.options, 0, options.length);
            }
        }
        
        public CommandEnvironmentImpl(GetArg arg) throws IOException, ClassNotFoundException{
            this(arg.get("command", null, String.class),
                 arg.get("options", null, String[].class));
        }

        public String[] getCommandOptions() {
            if (options == null) {
                return new String[0];
            }
            return options.clone();
        }

        public String getCommandPath() {
            return this.command;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((command == null) ? 0 : command.hashCode());
            result = prime * result + Arrays.hashCode(options);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof CommandEnvironmentImpl)) {
                return false;
            }
            final CommandEnvironmentImpl that = (CommandEnvironmentImpl) obj;
            if (!(command == null ? that.command == null : command.equals(that.command))) {
                return false;
            }
            return Arrays.equals(options, that.options);
        }
    }
}
