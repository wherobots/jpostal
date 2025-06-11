package com.mapzen.jpostal;

final class LibPostal {
    private final Config config;

    private LibPostal(final Config config) {
        if (config == null) {
            throw new NullPointerException("Config must not be null");
        }

        config.loadLibrary();

        final String dataDir = config.getDataDir();
        if (dataDir == null) {
            setup();
        } else {
            setupDataDir(dataDir);
        }

        this.config = config;
    }

    Config getConfig() {
        return config;
    }

    private static native void setup();
    private static native void setupDataDir(final String dataDir);
    private static native void teardown();

    private volatile static LibPostal instance = null;

    /**
     * Returns the singleton instance, creating it if necessary.
     */
    static LibPostal getInstance() {
        if (instance == null) {
            synchronized(LibPostal.class) {
                if (instance == null) {
                    instance = new LibPostal(Config.builder().build());
                }
            }
        }
        return instance;
    }

    /**
     * Returns the singleton instance with a specific config, creating it if necessary.
     * Throws if the config does not match the existing instance.
     */
    static LibPostal getInstance(final Config config) {
       if (instance == null) {
            synchronized(LibPostal.class) {
                if (instance == null ) {
                    instance = new LibPostal(config);
                }
            }
        } else if (!instance.config.equals(config)) {
           throw Config.mismatchException(instance.config, config);
       }
       return instance;
    }

    /**
     * Closes the singleton instance, releasing native resources and allowing re-initialization.
     */
    public static void close() {
        synchronized (LibPostal.class) {
            // Close dependent singletons first
            AddressParser._close();
            AddressExpander._close();
            if (instance != null) {
                teardown();
                instance = null;
            }
        }
    }

    @Override
    protected void finalize() {
        teardown();
    }
}
