package my2Dgame;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CampaignMapCameraTest {

    @Test
    void fitCentresWorldAndRoundTripIsStable() {
        CampaignMapCamera camera = new CampaignMapCamera();
        camera.fit(0, 1000, 0, 600, 768, 576, 40);

        assertEquals(500.0, camera.centerX, 0.001);
        assertEquals(300.0, camera.centerY, 0.001);
        int sx = camera.worldToScreenX(250, 768);
        int sy = camera.worldToScreenY(450, 576);
        assertEquals(250.0, camera.screenToWorldX(sx, 768), 1.5);
        assertEquals(450.0, camera.screenToWorldY(sy, 576), 1.5);
    }

    @Test
    void zoomKeepsCursorWorldPointFixedAndPanMovesOppositeDrag() {
        CampaignMapCamera camera = new CampaignMapCamera();
        camera.fit(0, 1000, 0, 600, 768, 576, 40);
        double anchoredX = camera.screenToWorldX(200, 768);
        double anchoredY = camera.screenToWorldY(180, 576);

        camera.zoomAt(200, 180, 768, 576, 1.5, 0.2, 4.0);
        assertEquals(anchoredX, camera.screenToWorldX(200, 768), 0.001);
        assertEquals(anchoredY, camera.screenToWorldY(180, 576), 0.001);

        double centerBefore = camera.centerX;
        camera.panByScreenDelta(20, 0);
        assertTrue(camera.centerX < centerBefore, "grabbing and dragging right reveals the left side");
    }

    @Test
    void fanOutKeepsFirstTokenOnTheSimulatedPointAndSeparatesOthers() {
        assertArrayEquals(new int[] {0, 0}, CampaignMapCamera.fanOutOffset(0, 5, 17));
        int[] firstMoved = CampaignMapCamera.fanOutOffset(1, 5, 17);
        int[] secondMoved = CampaignMapCamera.fanOutOffset(2, 5, 17);
        assertNotEquals(0, Math.abs(firstMoved[0]) + Math.abs(firstMoved[1]));
        assertNotEquals(0, Math.abs(secondMoved[0]) + Math.abs(secondMoved[1]));
        assertTrue(firstMoved[0] != secondMoved[0] || firstMoved[1] != secondMoved[1]);
        assertArrayEquals(new int[] {0, 0}, CampaignMapCamera.fanOutOffset(3, 1, 17));
    }
}
