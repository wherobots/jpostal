package com.mapzen.jpostal;

import com.mapzen.jpostal.ExpanderOptions;

import java.nio.charset.StandardCharsets;

public class AddressExpander {

    private static native synchronized void setup();
    private static native synchronized void setupDataDir(String dataDir);
    private static native synchronized byte[][] libpostalExpand(byte[] address, ExpanderOptions options);
    private static native synchronized void teardown();

    private volatile static AddressExpander instance = null;

    private final LibPostal libPostal;

    public static AddressExpander getInstanceDataDir(String dataDir) {
        return getInstanceConfig(Config.builder().dataDir(dataDir).build());
    }

    public static AddressExpander getInstanceConfig(Config config) {
        if (instance == null) {
            synchronized(AddressExpander.class) {
                if (instance == null) {
                    instance = new AddressExpander(LibPostal.getInstance(config));
                }
            }
        } else if (!instance.libPostal.getConfig().equals(config)) {
            throw Config.mismatchException(instance.libPostal.getConfig(), config);
        }
        return instance;
    }

    public static AddressExpander getInstance() {
        return getInstanceDataDir(null);
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    public String[] expandAddress(String address) {
        return expandAddressWithOptions(address, new ExpanderOptions.Builder().build());
    }

    public String[] expandAddressWithOptions(String address, ExpanderOptions options) {
        if (address == null) {
            throw new NullPointerException("String address must not be null");
        }
        if (options == null) {
            throw new NullPointerException("ExpanderOptions options must not be null");
        }

        byte[][] expansionBytes = libpostalExpand(address.getBytes(), options);
        String[] expansions = new String[expansionBytes.length];
        for (int i = 0; i < expansionBytes.length; i++) {
            expansions[i] = new String(expansionBytes[i], StandardCharsets.UTF_8);
        }
        return expansions;
    }

    AddressExpander(final LibPostal libPostal) {
        if (libPostal == null) {
            throw new NullPointerException("LibPostal must not be null");
        }

        this.libPostal = libPostal;

        final String dataDir = libPostal.getConfig().getDataDir();
        synchronized (this.libPostal) {
            if (dataDir == null) {
                setup();
            } else {
                setupDataDir(dataDir);
            }
        }
    } 

    @Override
    protected void finalize() {
        synchronized (libPostal) {
            teardown();
        }
    }

    /**
     * Closes the singleton instances of LibPostal, AddressParser, and AddressExpander, releasing native resources and allowing re-initialization.
     * This is not thread-safe. Use with caution.
     */
    public static void close() {
        LibPostal.close();
    }

    public static void _close() {
        if (instance != null) {
            synchronized (AddressExpander.class) {
                if (instance != null) {
                    teardown();
                    instance = null;
                }
            }
        }
    }
}
