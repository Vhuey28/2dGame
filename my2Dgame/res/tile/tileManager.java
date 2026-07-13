package tile;

import java.awt.Graphics2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import my2Dgame.GamePanel;

public class tileManager {
	GamePanel gp;
	Tile[] tile;
	int mapTileNum[][];
	BufferedImage tileSheet;

	public tileManager(GamePanel gp) {
		this.gp = gp;
		
		tile = new Tile[256];
		mapTileNum = new int[gp.maxWorldCol][gp.maxWorldRow];
		getTileImage();
		loadMap("map1.txt");
	}

	public void loadMap(String filename) {
		try {
			InputStream is = loadMapResource("/res/maps/" + filename, "res/maps/" + filename);
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			String line;
			int row = 0;
			while (row < gp.maxWorldRow && (line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}

				String[] tokens = line.split("[\\s,]+");
				for (int col = 0; col < gp.maxWorldCol && col < tokens.length; col++) {
					try {
						mapTileNum[col][row] = Integer.parseInt(tokens[col]);
					} catch (NumberFormatException nfe) {
						mapTileNum[col][row] = 0;
					}
				}
				row++;
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int[][] getMapTileNum() {
		return mapTileNum;
	}
	
	public void getTileImage() {
		try {
			tile[0] = new Tile();
			tile[0].image = ImageIO.read(loadTileResource("/tiles/greenFloor.png"));

			tile[1] = new Tile();
			tile[1].image = ImageIO.read(loadTileResource("/tiles/wall.png"));

			tile[2] = new Tile();
			tile[2].image = ImageIO.read(loadTileResource("/tiles/water.png"));

		} catch (IOException e) {
			e.printStackTrace();
		}
	}/* 
	public void getTileImage() {

    try {

        // Load ONE spritesheet
        tileSheet = ImageIO.read(loadTileResource("/res/tile/Pixel Crawler - Free Pack/Environment/Tilesets/Floors_Tiles.png"));

        int spriteSize = 16; // Original tile size inside the PNG

        int columns = tileSheet.getWidth() / spriteSize;
        int rows = tileSheet.getHeight() / spriteSize;

        int index = 0;

        for (int y = 0; y < rows; y++) {

            for (int x = 0; x < columns; x++) {

                tile[index] = new Tile();

                tile[index].image = tileSheet.getSubimage(
                        x * spriteSize,
                        y * spriteSize,
                        spriteSize,
                        spriteSize);

                index++;
            }
        }

        System.out.println("Loaded " + index + " tiles.");

    } catch (Exception e) {
        e.printStackTrace();
    }
}*/

	private InputStream loadTileResource(String path) throws IOException {
		InputStream is = getClass().getResourceAsStream(path);
		if (is != null) {
			return is;
		}

		String[] fallbackPaths = {
			"src" + path,
			"my2Dgame/src" + path,
			"res" + path,
			"my2Dgame/res" + path
		};

		for (String fallbackPath : fallbackPaths) {
			File fallback = new File(fallbackPath);
			if (fallback.exists()) {
				return new FileInputStream(fallback);
			}
		}

		throw new IOException("Tile resource not found: " + path);
	}
	
	public void loadMap() {
		try {
			InputStream is = loadMapResource("/res/maps/map1.txt", "res/maps/map1.txt");
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			
			String line;
			int row = 0;
			while (row < gp.maxWorldRow && (line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}

				String[] tokens = line.split("[\\s,]+");
				for (int col = 0; col < gp.maxWorldCol && col < tokens.length; col++) {
					try {
						mapTileNum[col][row] = Integer.parseInt(tokens[col]);
					} catch (NumberFormatException nfe) {
						mapTileNum[col][row] = 0;
					}
				}
				row++;
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private InputStream loadMapResource(String classpathPath, String fallbackPath) throws IOException {
		InputStream is = getClass().getResourceAsStream(classpathPath);
		if (is != null) {
			return is;
		}

		String[] fallbackPaths = {
			fallbackPath,
			"my2Dgame/" + fallbackPath,
			"src/" + fallbackPath,
			"my2Dgame/src/" + fallbackPath
		};

		for (String candidate : fallbackPaths) {
			File fallback = new File(candidate);
			if (fallback.exists()) {
				return new FileInputStream(fallback);
			}
		}

		throw new IOException("Map resource not found: " + classpathPath + " or fallback paths");
	}

	public boolean isBlocked(int worldX, int worldY) {
		int col = worldX / gp.tileSize;
		int row = worldY / gp.tileSize;

		if (col < 0 || col >= gp.maxWorldCol || row < 0 || row >= gp.maxWorldRow) {
			return true;
		}

		int tileNum = mapTileNum[col][row];
		return tileNum == 1 || tileNum == 2;
	}

	public void draw(Graphics2D g2, int cameraX, int cameraY) {
//		g2.drawImage(tile[0].image, 0, 0, gp.tileSize, gp.tileSize, null);
//		g2.drawImage(tile[1].image, 100, 0, gp.tileSize, gp.tileSize, null);
//		g2.drawImage(tile[2].image, 200, 0, gp.tileSize, gp.tileSize, null);
		
		for (int row = 0; row < gp.maxWorldRow; row++) {
			for (int col = 0; col < gp.maxWorldCol; col++) {
				int tileNum = mapTileNum[col][row];
				int worldX = col * gp.tileSize;
				int worldY = row * gp.tileSize;
				int screenX = worldX - cameraX;
				int screenY = worldY - cameraY;

				if (screenX + gp.tileSize > 0 && screenX < gp.screenWidth &&
					screenY + gp.tileSize > 0 && screenY < gp.screenHeight) {
					g2.drawImage(tile[tileNum].image, screenX, screenY, gp.tileSize, gp.tileSize, null);
				}
			}
		}
	}

}

