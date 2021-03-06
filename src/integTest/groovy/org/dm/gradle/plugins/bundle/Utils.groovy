package org.dm.gradle.plugins.bundle

import java.util.zip.ZipEntry
import java.util.zip.ZipFile


static void createSources(File projectRoot) {
    getJavaSrc(projectRoot).mkdirs()
    getResourceDir(projectRoot).mkdirs()
}

static File getResourceDir(File projectRoot) {
    new File(projectRoot, 'src/main/resources')
}

static File getJavaSrc(File projectRoot) {
    new File(projectRoot, 'src/main/java/org/foo/bar')
}

static ZipFile getJarFile(File projectRoot, String version) {
    if (version?.trim()) {
        new ZipFile(new File(projectRoot, "build/libs/${projectRoot.name}-${version}.jar"))
    } else {
        new ZipFile(new File(projectRoot, "build/libs/${projectRoot.name}.jar"))
    }

}

static String getFileContentFromJar(ZipFile jar, String file) {
    jar.withCloseable {
        it.getInputStream(new ZipEntry(file)).text
    }
}

static File copyFile(File projectRoot, String filename, String filePath) {
    def file = new File(projectRoot, filename)
    file << new File(filePath).text
    file
}

static Properties loadTestProps() {
    def pluginPropsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("jar-test.properties")
    if (pluginPropsStream == null) {
        throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
    }

    Properties props = new Properties()
    props.load(pluginPropsStream)
    props
}

static File copyAndReplaceBuildFile(File projectRoot, Properties properties) {
    String jarVersion = properties.get("version")

    def source = new File('src/integTest/resources/build.test')
    def dest = new File(projectRoot, 'build.gradle')
    dest << source.text.replaceAll('PLUGIN_VERSION', "${jarVersion}")
    dest
}
