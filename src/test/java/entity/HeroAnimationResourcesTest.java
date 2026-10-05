package entity;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class HeroAnimationResourcesTest {
    private static final String[] DIRECTIONS = {"up", "down", "left", "right"};

    @Test
    void warriorHasCompleteWalkAndSlashAnimations() {
        assertAnimation("/player/vince/standard/walk", 9);
        assertAnimation("/player/vince/standard/slash", 6);
    }

    @Test
    void mageHasCompleteWalkAndSpellcastAnimations() {
        assertAnimation("/player/triss/standard/walk", 9);
        assertAnimation("/player/triss/standard/spellcast", 7);
    }

    @Test
    void adventurerCompanionHasCompleteWalkAndSlashAnimations() {
        assertAnimation("/player/sword_animations/standard/walk", 9);
        assertAnimation("/player/sword_animations/standard/slash", 6);
    }

    private void assertAnimation(String basePath, int frameCount) {
        for (String direction : DIRECTIONS) {
            for (int frame = 1; frame <= frameCount; frame++) {
                String resource = basePath + "/" + direction + "/" + frame + ".png";
                assertNotNull(getClass().getResource(resource), "Missing animation frame " + resource);
            }
        }
    }
}
