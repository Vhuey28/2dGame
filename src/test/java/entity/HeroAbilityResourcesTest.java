package entity;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class HeroAbilityResourcesTest {
    @Test
    void requestedWarriorEffectsArePackaged() {
        assertResources(
                "/effects/fire_effect_sliced_tiles/tiles/group2/r11_c07.png",
                "/effects/fire_effect_sliced_tiles/tiles/group3/r05_c10.png",
                "/effects/fire_effect_sliced_tiles/tiles/group3/r05_c11.png",
                "/effects/fire_effect_sliced_tiles/tiles/group3/r05_c12.png",
                "/effects/fire_effect_sliced_tiles/tiles/group3/r06_c12.png",
                "/effects/fire_effect_sliced_tiles/tiles/group3/r06_c11.png",
                "/effects/fire_effect_sliced_tiles/tiles/group3/r06_c10.png");
    }

    @Test
    void requestedMageEffectsArePackaged() {
        assertResources(
                "/effects/purple_effect_sliced_tiles/tiles/group4/r04_c14.png",
                "/effects/purple_effect_sliced_tiles/tiles/group4/r04_c15.png",
                "/effects/purple_effect_sliced_tiles/tiles/group4/r04_c16.png",
                "/effects/purple_effect_sliced_tiles/tiles/group4/r04_c17.png",
                "/effects/purple_effect_sliced_tiles/tiles/group4/r08_c14.png",
                "/effects/purple_effect_sliced_tiles/tiles/group4/r08_c15.png",
                "/effects/purple_effect_sliced_tiles/tiles/group5/r09_c19.png",
                "/effects/purple_effect_sliced_tiles/tiles/group5/r09_c20.png",
                "/effects/purple_effect_sliced_tiles/tiles/group5/r09_c21.png",
                "/effects/purple_effect_sliced_tiles/tiles/group5/r09_c22.png");
    }

    private void assertResources(String... paths) {
        for (String path : paths) {
            assertNotNull(getClass().getResource(path), "Missing ability effect " + path);
        }
    }
}
