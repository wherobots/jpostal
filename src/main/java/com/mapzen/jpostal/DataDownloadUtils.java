package com.mapzen.jpostal;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public class DataDownloadUtils {

    private static final String LIBPOSTAL_DATA_FILE="libpostal_data.tar.gz";
    private static final String LIBPOSTAL_PARSER_FILE="parser.tar.gz";
    private static final String LIBPOSTAL_LANG_CLASS_FILE="language_classifier.tar.gz";
    private static final String[] LIBPOST_ARCHIVES = {LIBPOSTAL_DATA_FILE, LIBPOSTAL_PARSER_FILE, LIBPOSTAL_LANG_CLASS_FILE};

    public static Boolean isDataDirPopulated(String dataDir) {
        Path dataPath = Paths.get(dataDir);
        return Files.exists(dataPath.resolve("transliteration"))
                && Files.exists(dataPath.resolve("numex"))
                && Files.exists(dataPath.resolve("address_parser"))
                && Files.exists(dataPath.resolve("address_expansions"))
                && Files.exists(dataPath.resolve("language_classifier"));
    }

    // Derived from https://github.com/openvenues/libpostal/blob/7855e6a243c9dbab652ca0e099cf050ce035dcc9/src/libpostal_data.in
    public static synchronized void populateDataDir(String dst, Boolean senzing) {
        if (dst == null || dst.isEmpty()) {
            throw new IllegalArgumentException("Data directory must not be null or empty");
        }

        String originUrl;
        String versionUrlParam;
        if (senzing) {
            originUrl = "https://public-read-libpostal-data.s3.amazonaws.com";
            versionUrlParam = "/v1.1.0/";
        } else {
            originUrl = "https://github.com/openvenues/libpostal/releases/download";
            versionUrlParam = "/v1.0.0/"; // for some reason, v1.1.0 doesn't exist in the GitHub releases
        }

        Path dataDir = Paths.get(dst);
        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create data directory", e);
        }

        for (String archive : LIBPOST_ARCHIVES) {
            Path archivePath = dataDir.resolve(archive);
            String archiveUrl = originUrl + versionUrlParam + archive;
            try {
                downloadFile(archiveUrl, archivePath);
                extractTarGz(archivePath, dataDir);
                Files.deleteIfExists(archivePath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to download or extract " + archive, e);
            }
        }
    }

    private static void downloadFile(String urlStr, Path dest) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "jpostal-java");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void extractTarGz(Path tarGzPath, Path destDir) throws IOException {
        try (InputStream fi = Files.newInputStream(tarGzPath);
             GZIPInputStream gzi = new GZIPInputStream(fi);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzi)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                Path entryPath = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    if (!Files.exists(entryPath.getParent())) {
                        Files.createDirectories(entryPath.getParent());
                    }
                    try (OutputStream out = Files.newOutputStream(entryPath)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = tarIn.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    public static void populateDataDir(String dst) {
        populateDataDir(dst, false);
    }
}
