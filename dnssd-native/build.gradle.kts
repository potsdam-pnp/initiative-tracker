plugins {
    `cpp-library`
}

library {
   binaries.configureEach {
       compileTask.get().compilerArgs.add("-I/usr/local/Cellar/openjdk/21.0.2/libexec/openjdk.jdk/Contents/Home/include")
       compileTask.get().compilerArgs.add("-I/usr/local/Cellar/openjdk/21.0.2/libexec/openjdk.jdk/Contents/Home/include/darwin")
   }
}
