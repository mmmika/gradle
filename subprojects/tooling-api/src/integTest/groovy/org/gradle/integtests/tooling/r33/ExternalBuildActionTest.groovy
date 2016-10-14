/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r33

import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ OperatingSystem.current().isWindows() })
class ExternalBuildActionTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider testFolder = new TestNameTestDirectoryProvider()

    def setup() {
    }

    def "Can execute build action loaded with custom class loader"() {
        setup:
        // compile the build action
        File source = testFolder.file('extSrc', 'CustomBuildAction.java') << """
            import org.gradle.tooling.*;

            public class CustomBuildAction implements BuildAction<String> {
                @Override
                public String execute(BuildController controller) {
                    return "Action finished";
                 }
            }
        """
        compile(source, System.getProperty('java.class.path'))
        File classFile = testFolder.file('extSrc', 'CustomBuildAction.class')

        // load the buildAction
        ClassLoader classLoader = new ExternalBuildActionClassloader(classFile);
        Class<?> cls = classLoader.loadClass("CustomBuildAction");
        BuildAction action = cls.newInstance();

        // open project connection
        File project = testFolder.createDir("project")
        GradleConnector connector = GradleConnector.newConnector();
        ProjectConnection connection = connector.forProjectDirectory(project).connect();

        when:
        BuildActionExecuter<?> executer = connection.action(action);
        String result = executer.run();

        then:
        result == 'Action finished'

        cleanup:
        connection.close()
    }

    private def compile(File file, String classpath) {
        Process p = Runtime.runtime.exec(['javac', file.absolutePath, '-cp', classpath] as String[])
        p.waitFor()
    }

    static class ExternalBuildActionClassloader extends ClassLoader {

        File classFile
        String className

        ExternalBuildActionClassloader(File classFile) {
            this.classFile = classFile
            this.className = classFile.name.replace(".class", "")
        }

        @Override
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (className == name) {
                byte[] raw = classFile.bytes
                return defineClass(name, raw, 0, raw.length);
            }

            def cls = findSystemClass(name);
            if (resolve && cls != null) {
                resolveClass(cls);
            }
            if (cls == null) {
                throw new ClassNotFoundException(name);
            }
            return cls;
        }
    }

}
