package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EntitySpriteScaleTest {

    @Test
    void supportsUniformAndIndependentSpriteScaling() {
        Entity entity = new Entity();

        entity.setSpriteDrawScale(1.5f);
        assertEquals(72, entity.scaleSpriteDrawWidth(48));
        assertEquals(72, entity.scaleSpriteDrawHeight(48));

        entity.setSpriteDrawScale(0.5f, 2.0f);
        assertEquals(24, entity.scaleSpriteDrawWidth(48));
        assertEquals(96, entity.scaleSpriteDrawHeight(48));
    }

    @Test
    void rejectsInvalidSpriteScales() {
        Entity entity = new Entity();

        assertThrows(IllegalArgumentException.class, () -> entity.setSpriteDrawScale(0f));
        assertThrows(IllegalArgumentException.class, () -> entity.setSpriteDrawScale(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> entity.setSpriteDrawScale(1f, Float.POSITIVE_INFINITY));
    }
}
