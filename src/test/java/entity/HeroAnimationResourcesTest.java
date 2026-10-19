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

    @Test
    void everyPlayableCharacterShipsDeathFrames() {
        // Hero.loadSprite() plays standard/hurt as the death animation, and Player loads the
        // same folder for its death frames. Only "up" is authored, so every facing reuses it.
        assertDeathAnimation("/player/vince/standard/hurt/up");
        assertDeathAnimation("/player/triss/standard/hurt/up");
        assertDeathAnimation("/player/sword_animations/standard/hurt/up");
    }

    private void assertDeathAnimation(String basePath) {
        for (int frame = 1; frame <= 6; frame++) {
            String resource = basePath + "/" + frame + ".png";
            assertNotNull(getClass().getResource(resource), "Missing death frame " + resource);
        }
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
