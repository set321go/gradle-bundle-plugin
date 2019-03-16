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
