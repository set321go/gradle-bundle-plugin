package org.dm.gradle.plugins.bundle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static org.dm.gradle.plugins.bundle.Utils.copyAndReplaceBuildFile
import static org.dm.gradle.plugins.bundle.Utils.copyFile
import static org.dm.gradle.plugins.bundle.Utils.createSources
import static org.dm.gradle.plugins.bundle.Utils.getFileContentFromJar
import static org.dm.gradle.plugins.bundle.Utils.getJarFile
import static org.dm.gradle.plugins.bundle.Utils.getResourceDir

class BundlePluginTestKitSpec extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
    @Shared List<String> gradleVersions
    File buildFile
    String version = ''

    def setupSpec() {
        gradleVersions = ['3.2.1']
    }

    def setup() {
        createSources(testProjectDir.root)
        buildFile = copyAndReplaceBuildFile(testProjectDir.root)
        copyFile(testProjectDir.root, 'src/main/java/org/foo/bar/TestActivator.java', 'src/integTest/resources/org/foo/bar/TestActivator.java')
        testProjectDir.newFile('src/main/java/org/foo/bar/More.java') << 'package org.foo.bar;\n class More {}'
    }

    @Unroll
    def "Jar task is executed while build"() {
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('build')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        result.task(":build").outcome == TaskOutcome.SUCCESS
        manifestContains 'Bundle-Activator: org.foo.bar.TestActivator'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Includes project output class files by default"() {
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        jarContains 'org/foo/bar/TestActivator.class'
        jarContains 'org/foo/bar/More.class'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Includes project resources by default"() {
        setup:
        def resources = new File(getResourceDir(testProjectDir.root),'org/foo/bar')
        resources.mkdirs()
        def resource = new File(resources, 'dummy.txt')
        resource.write 'abc'

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        jarContains 'org/foo/bar/dummy.txt'
        where:
        gradleVersion << gradleVersions

    }

    @Unroll
    def "Includes project sources if instructed"() {
        setup:
        buildFile.append '\nbundle { instructions << ["-sources": true] }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        jarContains 'OSGI-OPT/src/org/foo/bar/TestActivator.java'
        jarContains 'OSGI-OPT/src/org/foo/bar/More.java'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Uses bundle instructions"() {
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Bundle-Activator: org.foo.bar.TestActivator'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Uses project version as 'Bundle-Version' by default"() {
        setup:
        version = '1.0.2'
        buildFile.append "\nversion = \"$version\""
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        and:
        manifestContains "Bundle-Version: $version"
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Overwrites project version using 'Bundle-Version' instruction"() {
        setup:
        version = '1.0.2'
        buildFile.append '\nversion = "1.0.2"\nbundle { instructions << ["Bundle-Version": "5.0"] }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()
        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        and:
        manifestContains "Bundle-Version: 5.0"
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Uses jar manifest values"() {
        setup:
        buildFile.append '\njar { manifest { attributes("Built-By": "abc") } }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Built-By: abc'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Overwrites jar manifest values"() {
        setup:
        buildFile.append '\njar { manifest { attributes("Built-By": "abc") } }\nbundle { instructions << ["Built-By": "xyz"] }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Built-By: xyz'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Uses baseName and extension defined in jar task"() {
        setup:
        buildFile.append'\njar { baseName = "xyz"\nextension = "baz" }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        new File(testProjectDir.root,'build/libs/xyz.baz').exists()
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Ignores unknown attributes"() {
        setup:
        buildFile.append '\nbundle { instructions << ["junk": "xyz"] }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Supports old OSGI plugin instruction format"() {
        setup:
        buildFile.append '\nbundle { instruction "Built-By", "ab", "c"\ninstruction "Built-By", "x", "y", "z" }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        manifestContains 'Built-By: ab,c,x,y,z'
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Displays builder classpath"() {
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar', '-d')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        result.output =~ /The Builder is about to generate a jar using classpath: \[.+\]/
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Displays errors"() {
        setup:
        buildFile.append '\nbundle { instructions << ["Bundle-Activator": "org.foo.bar.NotExistingActivator"] }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        result.output =~ /Bundle-Activator not found/
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "Can trace bnd build process"() {
        setup:
        buildFile.append '\nbundle { trace = true }'
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('jar', '-i')
                .build()

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        result.output =~ /(?m)begin DSAnnotations$/
        where:
        gradleVersion << gradleVersions
    }

    @Unroll
    def "jar task actions contain only a bundle generator action"() {
        setup:
        buildFile.append "task actionscheck { doLast { println jar.actions.size() + \" \" + jar.actions[0].@action.getClass().getSimpleName() } }"
        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withArguments('actionscheck')
                .build()

        then:
        result.task(":actionscheck").outcome == TaskOutcome.SUCCESS
        result.output =~ /1 BundleGenerator/
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
