package com.mapzen.jpostal;

import java.io.*;
import java.util.Objects;

public final class Config {
    private static boolean libLoaded = false;

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
            loadLibraryFromJar("jpostal");
        }
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
        if (libLoaded) {
            return;
        }

        String osName = System.getProperty("os.name").toLowerCase();
        String nativeLibFileName;
        String fullPathInJar;
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            nativeLibFileName = "lib" + libraryName + ".so";
        } else if (osName.contains("mac")) {
            nativeLibFileName = "lib" + libraryName + ".dylib";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + osName);
        }
        fullPathInJar = "/" + nativeLibFileName;

        try (InputStream in = Config.class.getResourceAsStream(fullPathInJar)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Native library " + fullPathInJar + " not found in JAR.");
            }

            File tempFile = File.createTempFile("lib", nativeLibFileName.substring(nativeLibFileName.lastIndexOf('.')));
            tempFile.deleteOnExit();

            try (OutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            System.load(tempFile.getAbsolutePath());
            libLoaded = true;
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to load native library " + libraryName + ": " + e.getMessage());
        }
    }
}
