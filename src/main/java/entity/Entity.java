package entity;

import java.awt.image.BufferedImage;
import my2Dgame.GamePanel;

public class Entity {

	GamePanel gp;

	public float x, y;
	public float speed;

	// Set only for combatants projected from the persistent strategic simulation.
	// Tactical code reports these IDs back through BattleResult instead of mutating world objects.
	public Long sourcePersonId;
	public Long sourceRegimentId;
	public Long sourceArmyId;

	public BufferedImage up1,up2,down1,down2,left1,left2,right1,right2;
	// Diagonal sprites (optional - if not provided, will use nearest cardinal)
	public BufferedImage upLeft1, upLeft2, upRight1, upRight2, downLeft1, downLeft2, downRight1, downRight2;
	public String direction;

	public int spriteCounter = 0;
	public int spriteNum = 1;

	// For smooth movement - track velocity
	public float velocityX = 0;
	public float velocityY = 0;

	// Runtime PNG render scaling. This changes only the displayed sprite size;
	// collision bounds and the source image files remain unchanged.
	private float spriteDrawWidthScale = 1.0f;
	private float spriteDrawHeightScale = 1.0f;

	/** Sets one uniform render scale for this entity's PNG animation frames. */
	public void setSpriteDrawScale(float scale) {
		setSpriteDrawScale(scale, scale);
	}

	/**
	 * Sets independent width and height render scales for PNG animation frames.
	 * For example, setSpriteDrawScale(1.1f, 0.9f) makes a sprite wider and shorter.
	 */
	public void setSpriteDrawScale(float widthScale, float heightScale) {
		if (!isValidSpriteScale(widthScale) || !isValidSpriteScale(heightScale)) {
			throw new IllegalArgumentException("Sprite draw scales must be finite and greater than zero");
		}
		spriteDrawWidthScale = widthScale;
		spriteDrawHeightScale = heightScale;
	}

	protected int scaleSpriteDrawWidth(int baseWidth) {
		return Math.max(1, Math.round(baseWidth * spriteDrawWidthScale));
	}

	protected int scaleSpriteDrawHeight(int baseHeight) {
		return Math.max(1, Math.round(baseHeight * spriteDrawHeightScale));
	}

	private boolean isValidSpriteScale(float scale) {
		return scale > 0f && !Float.isNaN(scale) && !Float.isInfinite(scale);
	}

}
