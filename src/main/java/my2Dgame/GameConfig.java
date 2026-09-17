package my2Dgame;

/**
 * Runtime configuration for the web (CheerpJ/browser) build.
 *
 * Set WEB_BUILD = true for the browser build and false for the desktop build.
 * This flag disables desktop-only features (JInput controllers, filesystem
 * persistence, fullscreen windowing) that CheerpJ does not support.
 */
public final class GameConfig {

    private GameConfig() {
    }

    public static final boolean WEB_BUILD = true;
}