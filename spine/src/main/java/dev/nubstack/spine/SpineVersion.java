package dev.nubstack.spine;

/**
 * Build identity for the shared library.
 */
public final class SpineVersion {

    private SpineVersion() {
    }

    /**
     * Returns the library artifact version string.
     *
     * @param artifactVersion version from the build, never blank
     * @return trimmed version string
     */
    public static String require(String artifactVersion) {
        if (artifactVersion == null || artifactVersion.isBlank()) {
            throw new IllegalArgumentException("artifactVersion must not be blank");
        }
        return artifactVersion.trim();
    }
}
