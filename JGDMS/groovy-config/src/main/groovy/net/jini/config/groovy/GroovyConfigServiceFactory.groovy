/*
 * Copyright 2019 HD.Design.
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

package net.jini.config.groovy


import org.osgi.annotation.bundle.Capability
import org.osgi.annotation.bundle.Requirement
import net.jini.config.Configuration
import net.jini.config.ConfigurationException

/**
 *
 * @author Peter Firmstone.
 */
@Requirement(
	namespace="osgi.extender",
	filter="(osgi.extender=osgi.serviceloader.registrar)")
@Capability(
	namespace="osgi.serviceloader",
	name="net.jini.config.ConfigurationServiceFactory")
class GroovyConfigService implements net.jini.config.ConfigurationServiceFactory {
	
    public Configuration getInstance(String[] options, ClassLoader cl) 
            throws ConfigurationException 
    {
        return new GroovyConfig(options, cl);
    }
}

