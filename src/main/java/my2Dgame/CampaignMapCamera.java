package my2Dgame;

/**
 * Pure coordinate/camera math for the strategic campaign map.
 *
 * <p>The simulated world uses abstract map units (roughly a 1,000 x 600 plane
 * in the generated campaign). This class converts between those units and the
 * fixed logical screen used by {@code GamePanel}. It deliberately does not
 * depend on AWT, so the transform and the token de-overlap fan can be unit
 * tested headlessly.</p>
 */
public final class CampaignMapCamera {
    /** World coordinate shown at the centre of the viewport. */
    public double centerX;
    /** World coordinate shown at the centre of the viewport. */
    public double centerY;
    /** Logical screen pixels per world unit. */
    public double zoom = 1.0;

    public void fit(double minX, double maxX, double minY, double maxY,
            int viewportWidth, int viewportHeight, int padding) {
        centerX = (minX + maxX) / 2.0;
        centerY = (minY + maxY) / 2.0;
        double worldWidth = Math.max(1.0, maxX - minX);
        double worldHeight = Math.max(1.0, maxY - minY);
        int usableWidth = Math.max(1, viewportWidth - padding * 2);
        int usableHeight = Math.max(1, viewportHeight - padding * 2);
        zoom = Math.max(0.05, Math.min(usableWidth / worldWidth, usableHeight / worldHeight));
    }

    public int worldToScreenX(double worldX, int viewportWidth) {
        return (int) Math.round(viewportWidth / 2.0 + (worldX - centerX) * zoom);
    }

    public int worldToScreenY(double worldY, int viewportHeight) {
        return (int) Math.round(viewportHeight / 2.0 + (worldY - centerY) * zoom);
    }

    public double screenToWorldX(int screenX, int viewportWidth) {
        return centerX + (screenX - viewportWidth / 2.0) / zoom;
    }

    public double screenToWorldY(int screenY, int viewportHeight) {
        return centerY + (screenY - viewportHeight / 2.0) / zoom;
    }

    /** Dragging right moves the visible world right, like grabbing the map. */
    public void panByScreenDelta(int deltaX, int deltaY) {
        centerX -= deltaX / zoom;
        centerY -= deltaY / zoom;
    }

    /** Zooms while keeping the world point under the cursor stationary. */
    public void zoomAt(int screenX, int screenY, int viewportWidth, int viewportHeight,
            double factor, double minZoom, double maxZoom) {
        double beforeX = screenToWorldX(screenX, viewportWidth);
        double beforeY = screenToWorldY(screenY, viewportHeight);
        zoom = Math.max(minZoom, Math.min(maxZoom, zoom * factor));
        centerX = beforeX - (screenX - viewportWidth / 2.0) / zoom;
        centerY = beforeY - (screenY - viewportHeight / 2.0) / zoom;
    }

    /**
     * Keeps at least part of the playable world in view. When the viewport is
     * larger than the world on an axis, that axis is simply re-centred.
     */
    public void clampTo(double minX, double maxX, double minY, double maxY,
            int viewportWidth, int viewportHeight, double outsideMarginWorldUnits) {
        double halfVisibleX = viewportWidth / (2.0 * zoom);
        double halfVisibleY = viewportHeight / (2.0 * zoom);
        double allowedMinX = minX - outsideMarginWorldUnits + halfVisibleX;
        double allowedMaxX = maxX + outsideMarginWorldUnits - halfVisibleX;
        double allowedMinY = minY - outsideMarginWorldUnits + halfVisibleY;
        double allowedMaxY = maxY + outsideMarginWorldUnits - halfVisibleY;
        centerX = allowedMinX <= allowedMaxX
                ? Math.max(allowedMinX, Math.min(allowedMaxX, centerX))
                : (minX + maxX) / 2.0;
        centerY = allowedMinY <= allowedMaxY
                ? Math.max(allowedMinY, Math.min(allowedMaxY, centerY))
                : (minY + maxY) / 2.0;
    }

    /**
     * Deterministic screen-space fan used when several moving tokens occupy
     * the same world point (most often armies stationed in a settlement).
     * Slot 0 stays on the simulated point; the rest form a compact diamond /
     * spiral around it, so no token ever completely hides another.
     */
    public static int[] fanOutOffset(int slot, int tokenCount, int spacing) {
        if (slot <= 0 || tokenCount <= 1) return new int[] {0, 0};
        int[][] directions = {
                {1, 0}, {0, 1}, {-1, 0}, {0, -1},
                {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
                {2, 0}, {0, 2}, {-2, 0}, {0, -2},
                {2, 1}, {-2, 1}, {2, -1}, {-2, -1},
                {1, 2}, {-1, 2}, {1, -2}, {-1, -2}
        };
        int[] direction = directions[(slot - 1) % directions.length];
        int ring = 1 + (slot - 1) / directions.length;
        return new int[] {direction[0] * spacing * ring, direction[1] * spacing * ring};
    }
}
