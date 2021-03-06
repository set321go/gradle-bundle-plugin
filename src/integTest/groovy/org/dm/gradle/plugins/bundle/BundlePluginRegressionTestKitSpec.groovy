package org.dm.gradle.plugins.bundle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static org.dm.gradle.plugins.bundle.Utils.copyAndReplaceBuildFile
import static org.dm.gradle.plugins.bundle.Utils.copyFile
import static org.dm.gradle.plugins.bundle.Utils.createSources
import static org.dm.gradle.plugins.bundle.Utils.getFileContentFromJar
import static org.dm.gradle.plugins.bundle.Utils.getJarFile
import static org.dm.gradle.plugins.bundle.Utils.getResourceDir
import static org.dm.gradle.plugins.bundle.Utils.loadTestProps

class BundlePluginRegressionTestKitSpec extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
    @Shared List<String> gradleVersions
    @Shared Properties testProps
    File buildFile
    String version = ''

    def setupSpec() {
        testProps = loadTestProps()
        gradleVersions = testProps.getProperty('gradleVersions').split(',')
    }

    def setup() {
        createSources(testProjectDir.root)
        buildFile = copyAndReplaceBuildFile(testProjectDir.root, testProps)
        copyFile(testProjectDir.root, 'src/main/java/org/foo/bar/TestActivator.java', 'src/integTest/resources/org/foo/bar/TestActivator.java')
        testProjectDir.newFile('src/main/java/org/foo/bar/More.java') << 'package org.foo.bar;\n class More {}'
    }

    @Unroll
    @Issue(1)
    def "Saves manifest under build/tmp [#gradleVersion]"() {
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        def jarFile = getJarFile(testProjectDir.root, version)
        def manifest = getFileContentFromJar(jarFile, 'META-INF/MANIFEST.MF')
        new File(testProjectDir.root,'build/tmp/jar/MANIFEST.MF').text == manifest.replaceAll('(?m)^Bnd-LastModified: \\d+$\r\n', '')
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(1)
    def "Does not re-execute 'jar' when manifest has not been changed [#gradleVersion]"() {
        setup:
        def build = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        when:
        def noChange = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        build.task(":jar").outcome == TaskOutcome.SUCCESS
        noChange.task(":jar").outcome == TaskOutcome.UP_TO_DATE
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(1)
    def "Re-executes 'jar' when manifest has been changed [#gradleVersion]"() {
        setup:
        def build = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        buildFile.append '\nbundle { instructions << ["Built-By": "xyz"] }'
        when:
        def afterChange = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        build.task(":jar").outcome == TaskOutcome.SUCCESS
        afterChange.task(":jar").outcome == TaskOutcome.SUCCESS
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(8)
    def "Uses instructions (Private-Package) from an included file [#gradleVersion]"() {
        setup:
        testProjectDir.newFile('bnd.bnd') << 'Private-Package: org.springframework.*'
        buildFile.append """
            dependencies { compile "org.springframework:spring-instrument:4.0.6.RELEASE" }
            bundle { instruction "-include", "${testProjectDir.root}/bnd.bnd" }"""
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Private-Package: org.springframework.instrument,org.foo.bar'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(9)
    def "Supports Include-Resource header [#gradleVersion]"() {
        setup:
        def resource = 'test-resource.txt'
        testProjectDir.newFile(resource) << 'this resource should be included'
        buildFile.append "\nbundle { instruction 'Include-Resource', '${testProjectDir.root}/${resource}' }"
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        jarContains resource
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(9)
    def "Supports -includeresource directive [#gradleVersion]"() {
        setup:
        def resource = 'test-resource.txt'
        testProjectDir.newFile(resource) << 'this resource should be included'
        buildFile.append "\nbundle { instruction '-includeresource', '${testProjectDir.root}/${resource}' }"
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        jarContains resource
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(13)
    def "Supports -dsannotations directive [#gradleVersion]"() {
        setup:
        copyFile(testProjectDir.root, 'src/main/java/org/foo/bar/TestComponent.java', 'src/integTest/resources/org/foo/bar/TestComponent.java')
        buildFile.append """
            dependencies { compile 'org.osgi:org.osgi.compendium:5.0.0' }
            bundle { instructions << ["-dsannotations": "*"] }"""
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Service-Component: OSGI-INF/org.foo.bar.TestComponent.xml'
        jarContains 'OSGI-INF/org.foo.bar.TestComponent.xml'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(22)
    def "-include instruction expects baseDir to be correct [#gradleVersion]"() {
        setup:
        copyFile(testProjectDir.root, 'bnd.bnd', 'src/integTest/resources/bnd.bnd')
        buildFile.append '\nbundle { instructions << ["-include": "bnd.bnd"] }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Bundle-Description: Bundle Description Test'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(24)
    def "bndlib can read system properties [#gradleVersion]"() {
        setup:
        copyFile(testProjectDir.root, 'bnd.bnd', 'src/integTest/resources/bnd.bnd')
        copyFile(testProjectDir.root, 'include.txt', 'src/integTest/resources/include.txt')
        copyFile(testProjectDir.root, 'gradle.properties', 'src/integTest/resources/gradle.properties')
        buildFile.append '\nbundle { instructions << ["-include": "bnd.bnd"] }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        jarContains 'include.txt'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(32)
    def "Supports blueprint.xml [#gradleVersion]"() {
        setup:
        def blueprintFileLocation = new File(getResourceDir(testProjectDir.root), "OSGI-INF/blueprint/")
        blueprintFileLocation.mkdirs()
        copyFile(testProjectDir.root, 'src/main/resources/OSGI-INF/blueprint/blueprint.xml', 'src/integTest/resources/OSGI-INF/blueprint/blueprint.xml')
        buildFile.append """
            dependencies { compile 'org.apache.camel:camel-core:2.15.2' }
            bundle { instructions << ['-plugin': 'aQute.lib.spring.SpringXMLType'] }"""
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Import-Package:.*org.apache.camel.*'
        jarContains 'OSGI-INF/blueprint/blueprint.xml'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(33)
    def "Gradle properties are passed to bndlib by default [#gradleVersion]"() {
        setup:
        copyFile(testProjectDir.root, 'gradle.properties', 'src/integTest/resources/gradle.properties')
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Qux: baz'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(33)
    def "Gradle properties are not passed to bndlib when passProjectProperties is false [#gradleVersion]"() {
        setup:
        copyFile(testProjectDir.root, 'gradle.properties', 'src/integTest/resources/gradle.properties')
        buildFile.append '\nbundle { passProjectProperties = false }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        !manifestContains('Qux: baz')
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(38)
    def "Supports excludeDependencies [#gradleVersion]"() {
        setup:
        copyFile(testProjectDir.root, 'src/main/java/org/foo/bar/AClass.java', 'src/integTest/resources/org/foo/bar/AClass.java')
        buildFile.append """
            dependencies { compile 'com.google.guava:guava:18.0' }
            bundle { exclude module: 'guava' }"""
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        // com.google.common.hash is expected to have no version
        manifestContains 'Import-Package: com.google.common.hash,org.osgi.framework;version=.*'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(41)
    def "Produces an error when osgi plugin is applied [#gradleVersion]"() {
        setup:
        buildFile.append "apply plugin: 'osgi'"
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .buildAndFail()

        then:
        result.output =~ /gradle-bundle-plugin is not compatible with osgi plugin/
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(41)
    def "Handles non-string keys and values in bundle instructions [#gradleVersion]"() {
        setup:
        buildFile.append '\next {bar = \'bar\'}\ndef foo = "Foo"\nbundle { instructions << ["$foo": 123.5, \'Abc\': "$foo-${bar}"] }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Foo: 123.5'
        manifestContains 'Abc: Foo-bar'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(43)
    def "Transitive dependencies are not passed to bndlibs by default [#gradleVersion]"() {
        setup:
        copyFile(testProjectDir.root, 'src/main/java/org/foo/bar/TClass.java', 'src/integTest/resources/org/foo/bar/TClass.java')
        buildFile.append '\ndependencies { compile "com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.6.4" }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Import-Package: com.fasterxml.jackson.databind,org.osgi.framework.*'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(43)
    def "Transitive dependencies are passed to bndlibs when includeTransitiveDependencies is true [#gradleVersion]"() {
        setup:
        copyFile(testProjectDir.root, 'src/main/java/org/foo/bar/TClass.java', 'src/integTest/resources/org/foo/bar/TClass.java')
        buildFile.append '''
                dependencies { compile "com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.6.4" }
                bundle { includeTransitiveDependencies = true }'''
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Import-Package: com.fasterxml.jackson.databind;version=.*'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(53)
    def "Handles bundle instructions with null values [#gradleVersion]"() {
        setup:
        buildFile.append 'bundle { instructions << ["Foo": null] }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Foo: null'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(56)
    def "Supports compileOnly [#gradleVersion]"() {
        setup:
        copyFile(testProjectDir.root, 'src/main/java/org/foo/bar/AClass.java', 'src/integTest/resources/org/foo/bar/AClass.java')
        buildFile.append 'dependencies { compileOnly "com.google.guava:guava:18.0" }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Import-Package: com.google.common.hash;version=.*'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(56)
    def "Supports compileOnly excludeDependencies [#gradleVersion]"() {
        setup:
        copyFile(testProjectDir.root, 'src/main/java/org/foo/bar/AClass.java', 'src/integTest/resources/org/foo/bar/AClass.java')
        buildFile.append """
            dependencies { compileOnly 'com.google.guava:guava:18.0' }
            bundle { exclude module: 'guava' }"""
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        // com.google.common.hash is expected to have no version
        manifestContains 'Import-Package: com.google.common.hash,org.osgi.framework;version=.*'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(59)
    def "Produces an error when the build has errors [#gradleVersion]"() {
        setup:
        buildFile.append '\nbundle { failOnError = true\ninstruction "-plugin", "org.example.foo.Bar" }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .buildAndFail()

        then:
        result.output =~ 'Build (has errors|failed with an exception)'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    @Issue(68)
    def "Honours all jar task inputs [#gradleVersion]"() {
        setup:
        def nestedRes = new File(getResourceDir(testProjectDir.root), "/nested/")
        nestedRes.mkdirs()
        copyFile(testProjectDir.root, 'src/main/resources/nested/resource.txt', 'src/integTest/resources/res/nested/resource.txt')
        buildFile.append '\njar { from "res" }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        jarContains 'nested/resource.txt'
        where:
        gradleVersion << gradleVersions
    }

    private boolean manifestContains(String line) {
        def jarFile = getJarFile(testProjectDir.root, version)
        def manifest = getFileContentFromJar(jarFile, 'META-INF/MANIFEST.MF')

        manifest =~ "(?m)^$line\$"
    }

    private boolean jarContains(String entry) {
        getJarFile(testProjectDir.root, version).withCloseable {
            it.getEntry(entry) != null
        }
    }
}
