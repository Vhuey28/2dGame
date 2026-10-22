package my2Dgame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

class CampaignTextLayoutTest {

    @Test
    void fitUsesPixelWidthsAndIsNullAndTinySlotSafe() {
        FontMetrics metrics = metrics();
        String fitted = CampaignTextLayout.fit(metrics,
                "A very long settlement and ruler name that cannot fit in this slot", 92);

        assertTrue(metrics.stringWidth(fitted) <= 92);
        assertTrue(fitted.endsWith("..."));
        assertEquals("", CampaignTextLayout.fit(metrics, null, 100));
        assertEquals("", CampaignTextLayout.fit(metrics, "text", 0));
        assertTrue(metrics.stringWidth(CampaignTextLayout.fit(metrics, "text", 2)) <= 2);
        assertEquals("ab...", CampaignTextLayout.fit(null, "abcdefghij", 30));
    }

    @Test
    void wrapRespectsPixelWidthMaximumLinesAndEllipsizesTheRemainder() {
        FontMetrics metrics = metrics();
        List<String> lines = CampaignTextLayout.wrap(metrics,
                "food jobs taxes crime sanitation war loyalty migration contracts chronicle", 74, 2);

        assertEquals(2, lines.size());
        for (String line : lines) {
            assertTrue(metrics.stringWidth(line) <= 74, line);
            assertFalse(line.isBlank());
        }
        assertTrue(lines.get(1).endsWith("..."));
    }

    @Test
    void wrapSplitsSingleLongWordsAndKeepsExplicitLineBreaks() {
        FontMetrics metrics = metrics();
        List<String> split = CampaignTextLayout.wrap(metrics,
                "Antidisestablishmentarianism", 48, 3);
        assertFalse(split.isEmpty());
        assertTrue(split.size() <= 3);
        for (String line : split) assertTrue(metrics.stringWidth(line) <= 48, line);

        List<String> explicit = CampaignTextLayout.wrap(metrics, "first line\nsecond line", 240, 4);
        assertEquals(List.of("first line", "second line"), explicit);
        assertTrue(CampaignTextLayout.wrap(metrics, "   ", 100, 3).isEmpty());
        assertTrue(CampaignTextLayout.wrap(metrics, null, 100, 3).isEmpty());
    }

    @Test
    void clampKeepsCardsInsideTheSafeCanvas() {
        Rectangle bounds = new Rectangle(10, 20, 120, 90);
        Rectangle placed = CampaignTextLayout.clamp(
                new Rectangle(-100, 500, 400, 300), bounds, 4);

        assertEquals(14, placed.x);
        assertEquals(24, placed.y);
        assertEquals(112, placed.width);
        assertEquals(82, placed.height);
        assertTrue(bounds.contains(placed));
    }

    @Test
    void leastOverlappingChoiceAvoidsObstaclesAndBounds() {
        Rectangle bounds = new Rectangle(0, 0, 220, 110);
        Rectangle obstacle = new Rectangle(12, 12, 60, 40);
        Rectangle placed = CampaignTextLayout.chooseLeastOverlapping(
                List.of(new Rectangle(12, 12, 60, 40), new Rectangle(120, 12, 60, 40)),
                List.of(obstacle), bounds);

        assertFalse(placed.intersects(obstacle));
        assertTrue(bounds.contains(placed));
        assertEquals(120, placed.x);
    }

    @Test
    void centeredXCentersMeasuredTextInsideItsSlot() {
        FontMetrics metrics = metrics();
        String text = "Party";
        int x = CampaignTextLayout.centeredX(metrics, text, 100, 80);
        int expected = 100 + (80 - metrics.stringWidth(text)) / 2;
        assertEquals(expected, x);
        assertTrue(x >= 100 && x + metrics.stringWidth(text) <= 180);
    }

    private static FontMetrics metrics() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setFont(new Font("SansSerif", Font.PLAIN, 12));
            return graphics.getFontMetrics();
        } finally {
            graphics.dispose();
        }
    }
}
