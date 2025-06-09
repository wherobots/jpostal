package com.mapzen.jpostal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class Config {
    private static boolean libsLoaded = false;

    private final String dataDir;
    private final String libraryFile;

    private Config(final String dataDir, final String libraryFile) {
        this.dataDir = dataDir;
        this.libraryFile = libraryFile;
    }

    public String getDataDir() {
        return dataDir;
    }

    public String getLibraryFile() {
        return libraryFile;
    }

    void loadLibrary() {
        if (this.libraryFile != null) {
            System.load(this.libraryFile);
        } else {
            try {
                loadLibsFromJar();
            } catch (UnsatisfiedLinkError ex) {
                System.loadLibrary("jpostal");
            }
        }
        libsLoaded = true;
    }

    static IllegalArgumentException mismatchException(final Config current, final Config requested) {
        return new IllegalArgumentException(String.format("Config mismatch: initialized instance uses [%s], but requested [%s]", current, requested));
    }

    @Override
    public String toString() {
        return "Config{" + "dataDir=" + dataDir + ",libraryFile=" + libraryFile + '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Config other)) {
            return false;
        } else {
            return Objects.equals(this.dataDir, other.dataDir) &&
                    Objects.equals(this.libraryFile, other.libraryFile);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String dataDir;
        private String libraryFile;

        private Builder() {}

        public Config build() {
            return new Config(dataDir, libraryFile);
        }

        public Builder dataDir(final String dataDir) {
            this.dataDir = dataDir;
            return this;
        }

        public Builder libraryFile(final String libraryFile) {
            this.libraryFile = libraryFile;
            return this;
        }
    }

    public static synchronized void loadLibraryFromJar(String libraryName) {
        if (libsLoaded) {
            return;
        }

        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        String nativeLibOSName;
        String nativeLibArchName;
        String nativeLibFileName;
        String fullPathInJar;

        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            nativeLibOSName = "linux";
            nativeLibFileName = "lib" + libraryName + ".so";
        } else if (osName.contains("freebsd")) {
            nativeLibOSName = "freebsd";
            nativeLibFileName = "lib" + libraryName + ".so";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            nativeLibOSName = "darwin";
            nativeLibFileName = "lib" + libraryName + ".dylib";
        } else if (osName.contains("win")) {
            nativeLibOSName = "windows";
            nativeLibFileName = libraryName + ".dll";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + osName);
        }

        if (osArch.contains("arm64") || osArch.contains("aarch64")) {
            nativeLibArchName = "arm64";
        } else if (osArch.contains("x64") || osArch.contains("amd64") || osArch.contains("x86_64")) {
            nativeLibArchName = "x64";
        } else if (osArch.contains("32") || osArch.contains("86")) {
            nativeLibArchName = "x86";
        } else {
            throw new UnsupportedOperationException("Unsupported architecture: " + osArch);
        }

        fullPathInJar = "/" + nativeLibOSName + "-" + nativeLibArchName + "/" + nativeLibFileName;

        try (InputStream in = Config.class.getResourceAsStream(fullPathInJar)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Native library " + fullPathInJar + " not found in JAR");
            }

            File tempFile = File.createTempFile("lib", nativeLibFileName.substring(nativeLibFileName.lastIndexOf('.')));
            tempFile.deleteOnExit();
            Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.load(tempFile.getAbsolutePath());
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to load native library " + libraryName + ": " + e.getMessage());
        }
    }

    private static synchronized void loadLibsFromJar() {
        if (libsLoaded) {
            return;
        }
        // both should be present in the JAR
        loadLibraryFromJar("postal");
        loadLibraryFromJar("jpostal");
        libsLoaded = true;
    }
}
