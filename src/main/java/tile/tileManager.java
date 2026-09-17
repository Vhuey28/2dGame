package tile;

import java.awt.Graphics2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.util.ArrayList;
import java.util.List;

import my2Dgame.GamePanel;

public class tileManager {
	GamePanel gp;
	Tile[] tile;
	int mapTileNum[][];
	BufferedImage tileSheet;
	public int currentMapWidth = 0;
	public int currentMapHeight = 0;

	// Multi-layer support for TMX maps
	private List<int[][]> mapLayers = new ArrayList<>();
	private boolean isTmxMap = false;
	private java.util.Set<Integer> blockedDecorationTiles = new java.util.HashSet<>(java.util.Arrays.asList(
    /* fill in with the tile numbers you found via the debug key, e.g. */ 12, 13, 27
	));

	public tileManager(GamePanel gp) {
		this.gp = gp;

		tile = new Tile[2048];
		mapTileNum = new int[gp.maxWorldCol][gp.maxWorldRow];
		loadMap("map1.txt");
	}

	private void clearTileImages() {
		for (int i = 0; i < tile.length; i++) {
			tile[i] = null;
		}
	}

	public void loadMap(String filename) {
		// Reset layers
		mapLayers.clear();
		isTmxMap = false;

		// Fully reset the tile grid so no data from a previous map can bleed through
		for (int col = 0; col < gp.maxWorldCol; col++) {
			for (int row = 0; row < gp.maxWorldRow; row++) {
				mapTileNum[col][row] = 0;
			}
		}


		// Check if it's a TMX file
		if (filename.toLowerCase().endsWith(".tmx")) {
			loadTmxMap(filename);
			isTmxMap = true;
		} else {
			// Load appropriate tiles for this map
			loadTileImages(filename);

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
	}

	private void loadTmxMap(String filename) {
		clearTileImages();
		try {
			// Load the TMX file content
			String tmxContent = loadTmxResource("/res/maps/" + filename, "res/maps/" + filename);

			// Parse XML
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(new InputSource(new StringReader(tmxContent)));

			Element mapElem = doc.getDocumentElement();

			int mapWidth = Integer.parseInt(mapElem.getAttribute("width"));
			int mapHeight = Integer.parseInt(mapElem.getAttribute("height"));
			int tileWidth = Integer.parseInt(mapElem.getAttribute("tilewidth"));
			int tileHeight = Integer.parseInt(mapElem.getAttribute("tileheight"));
			currentMapWidth = mapWidth;   // <-- add this
			currentMapHeight = mapHeight; // <-- add this

			// Parse tilesets to find the image source
			NodeList tilesetNodes = mapElem.getElementsByTagName("tileset");
			String tilesetImagePath = null;
			int tilesetFirstGid = 1;
			int tilesetColumns = 0;
			int tilesetTileWidth = tileWidth;
			int tilesetTileHeight = tileHeight;
			int tilesetSpacing = 0;
			int tilesetMargin = 0;

			for (int i = 0; i < tilesetNodes.getLength(); i++) {
				Element tsElem = (Element) tilesetNodes.item(i);
				tilesetFirstGid = Integer.parseInt(tsElem.getAttribute("firstgid"));
				tilesetColumns = tsElem.hasAttribute("columns") ? Integer.parseInt(tsElem.getAttribute("columns")) : 0;
				tilesetTileWidth = Integer.parseInt(tsElem.getAttribute("tilewidth"));
				tilesetTileHeight = Integer.parseInt(tsElem.getAttribute("tileheight"));
				tilesetSpacing = tsElem.hasAttribute("spacing") ? Integer.parseInt(tsElem.getAttribute("spacing")) : 0;
				tilesetMargin = tsElem.hasAttribute("margin") ? Integer.parseInt(tsElem.getAttribute("margin")) : 0;

				NodeList imageNodes = tsElem.getElementsByTagName("image");
				if (imageNodes.getLength() > 0) {
					Element imgElem = (Element) imageNodes.item(0);
					tilesetImagePath = imgElem.getAttribute("source");
				}
			}

			// Load the tileset image
			if (tilesetImagePath != null) {
				// Resolve path relative to TMX file
				String basePath = "";
				int lastSlash = filename.lastIndexOf('/');
				if (lastSlash >= 0) {
					basePath = filename.substring(0, lastSlash + 1);
				}
				loadTmxTileset(basePath + tilesetImagePath, tilesetTileWidth, tilesetTileHeight, tilesetColumns, tilesetSpacing, tilesetMargin);
			} else {
				// Fallback to default tiles
				loadDefaultTiles();
			}

			// Parse layer data (CSV format) - store all visible layers
			NodeList layerNodes = mapElem.getElementsByTagName("layer");
			boolean firstLayer = true;
			for (int i = 0; i < layerNodes.getLength(); i++) {
				Element layerElem = (Element) layerNodes.item(i);

				// Check if layer is visible
				boolean visible = !layerElem.hasAttribute("visible") || !"0".equals(layerElem.getAttribute("visible"));
				if (!visible) continue;

				NodeList dataNodes = layerElem.getElementsByTagName("data");
				if (dataNodes.getLength() > 0) {
					Element dataElem = (Element) dataNodes.item(0);
					String encoding = dataElem.getAttribute("encoding");
					String textContent = dataElem.getTextContent().trim();

					if ("csv".equals(encoding)) {
						// Create a new layer array
						int[][] layerData = new int[gp.maxWorldCol][gp.maxWorldRow];
						parseCsvLayerDataToArray(textContent, mapWidth, mapHeight, tilesetFirstGid, layerData);
						mapLayers.add(layerData);

						// Use first visible layer for collision/ground map
						if (firstLayer) {
							mapTileNum = layerData;
							firstLayer = false;
						}
					}
				}
			}

			System.out.println("Loaded TMX map: " + filename + " (" + mapWidth + "x" + mapHeight + ") with " + mapLayers.size() + " layers");

		} catch (Exception e) {
			e.printStackTrace();
			// Fallback to default tiles
			loadDefaultTiles();
		}
	}

	private String loadTmxResource(String classpathPath, String fallbackPath) throws IOException {
		InputStream is = getClass().getResourceAsStream(classpathPath);
		if (is != null) {
			return new String(is.readAllBytes());
		}

		// Classpath-only — no desktop filesystem fallbacks (web build)
		throw new IOException("TMX resource not found on classpath: " + classpathPath);
	}

	private void loadTmxTileset(String path, int tileWidth, int tileHeight, int columns, int spacing, int margin) {
		try {
			// Try to load the tileset image with fallback paths
			BufferedImage sheet = null;
			IOException lastException = null;

			// Try the given path first
			try {
				sheet = ImageIO.read(loadTileResource(path));
			} catch (IOException e) {
				lastException = e;
			}

			// If that fails, try common forest paths
			if (sheet == null) {
				String[] forestPaths = {
					"maps/forest/spritesheet.png",
					"maps/spritesheet.png",
					"/tiles/forest_spritesheet.png"
				};
				for (String p : forestPaths) {
					try {
						sheet = ImageIO.read(loadTileResource(p));
						if (sheet != null) {
							System.out.println("Loaded TMX tilesheet from fallback path: " + p);
							break;
						}
					} catch (IOException e) {
						lastException = e;
					}
				}
			}

			if (sheet == null) {
				throw lastException != null ? lastException : new IOException("Could not load tilesheet from any path");
			}

			tileSheet = sheet;
			if (columns == 0) {
				columns = tileSheet.getWidth() / tileWidth;
			}
			int rows = tileSheet.getHeight() / tileHeight;
			int index = 0;
			for (int y = 0; y < rows; y++) {
				for (int x = 0; x < columns; x++) {
					if (index < tile.length) {
						tile[index] = new Tile();
						tile[index].image = tileSheet.getSubimage(
							x * (tileWidth + spacing) + margin,
							y * (tileHeight + spacing) + margin,
							tileWidth, tileHeight);
						index++;
					}
				}
			}
			System.out.println("Loaded " + index + " tiles from TMX tilesheet");
		} catch (IOException e) {
			e.printStackTrace();
			loadDefaultTiles();
		}
	}

	private void parseCsvLayerData(String csv, int mapWidth, int mapHeight, int firstGid) {
		String[] tokens = csv.split("[\\s,]+");
		int expectedCount = mapWidth * mapHeight;
		int idx = 0;
		for (String token : tokens) {
			if (token.isEmpty()) continue;
			if (idx >= expectedCount) break;
			try {
				int gid = Integer.parseInt(token);
				// Convert GID to local tile index (subtract firstGid, 0 stays 0)
				int tileIndex = (gid == 0) ? 0 : (gid - firstGid + 1);
				int col = idx % mapWidth;
				int row = idx / mapWidth;
				if (col < gp.maxWorldCol && row < gp.maxWorldRow) {
					mapTileNum[col][row] = tileIndex;
				}
				idx++;
			} catch (NumberFormatException e) {
				// Skip invalid tokens
				idx++;
			}
		}
	}

	// Parses CSV layer data into a provided array (for multi-layer support)
	private void parseCsvLayerDataToArray(String csv, int mapWidth, int mapHeight, int firstGid, int[][] targetArray) {
		String[] tokens = csv.split("[\\s,]+");
		int expectedCount = mapWidth * mapHeight;
		int idx = 0;
		for (String token : tokens) {
			if (token.isEmpty()) continue;
			if (idx >= expectedCount) break;
			try {
				int gid = Integer.parseInt(token);
				// Convert GID to local tile index (subtract firstGid, 0 stays 0)
				int tileIndex = (gid == 0) ? 0 : (gid - firstGid);
				int col = idx % mapWidth;
				int row = idx / mapWidth;
				if (col < gp.maxWorldCol && row < gp.maxWorldRow) {
					targetArray[col][row] = tileIndex;
				}
				idx++;
			} catch (NumberFormatException e) {
				// Skip invalid tokens
				idx++;
			}
		}
	}

	public int getDecorationTileNum(int col, int row) {
		if (mapLayers.size() < 2) return 0;
		int[][] decoLayer = mapLayers.get(1);
		if (col < 0 || col >= gp.maxWorldCol || row < 0 || row >= gp.maxWorldRow) return 0;
		return decoLayer[col][row];
	}

	private void loadTileImages(String filename) {
		clearTileImages();
		if ("mapA.txt".equals(filename)) {
			// Load mapA spritesheet (128x32 = 8 cols x 2 rows = 16 tiles of 16x16)
			loadSpriteSheet("maps/mapA/spritesheet.png", 16);
		} else if ("home.txt".equals(filename)) {
			// Load home map spritesheet (128x3632 = 8 cols x 227 rows = 1816 tiles)
			loadSpriteSheet("maps/home/spritesheet.png", 16);
		} else if ("forest.txt".equals(filename) || "forest.tmx".equals(filename)) {
			// Load forest map spritesheet (128x96 = 8 cols x 6 rows = 48 tiles of 16x16)
			loadSpriteSheet("maps/forest/spritesheet.png", 16);
		} else {
			// Default tiles for map1 and others
			loadDefaultTiles();
		}
	}

	private void loadSpriteSheet(String path, int tileSize) {
		try {
			tileSheet = ImageIO.read(loadTileResource(path));
			int columns = tileSheet.getWidth() / tileSize;
			int rows = tileSheet.getHeight() / tileSize;
			int index = 0;
			for (int y = 0; y < rows; y++) {
				for (int x = 0; x < columns; x++) {
					if (index < tile.length) {
						tile[index] = new Tile();
						tile[index].image = tileSheet.getSubimage(
							x * tileSize, y * tileSize, tileSize, tileSize);
						index++;
					}
				}
			}
			System.out.println("Loaded " + index + " tiles from spritesheet: " + path);
		} catch (IOException e) {
			e.printStackTrace();
			// Fallback to default tiles
			loadDefaultTiles();
		}
	}

	private void loadDefaultTiles() {
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

		// Classpath-only — no desktop filesystem fallbacks (web build)
		throw new IOException("Map resource not found on classpath: " + classpathPath);
	}

	public boolean isBlocked(int worldX, int worldY) {
		 	int col = worldX / gp.tileSize;
			int row = worldY / gp.tileSize;

			if (col < 0 || col >= gp.maxWorldCol || row < 0 || row >= gp.maxWorldRow) {
				return true;
			}

			int tileNum = mapTileNum[col][row];
			if (tileNum == 1 || tileNum == 2) return true;

			// Check decoration layer (trees/rocks) for blocking tiles
			if (mapLayers.size() >= 2) {
				int decoTile = mapLayers.get(1)[col][row];
				if (blockedDecorationTiles.contains(decoTile)) return true;
			}

			return false;
	}
	/* 
	public void draw(Graphics2D g2, int cameraX, int cameraY) {
	//		g2.drawImage(tile[0].image, 0, 0, gp.tileSize, gp.tileSize, null);
	//		g2.drawImage(tile[1].image, 100, 0, gp.tileSize, gp.tileSize, null);
	//		g2.drawImage(tile[2].image, 200, 0, gp.tileSize, gp.tileSize, null);

		// For TMX maps, draw all layers in order
		if (isTmxMap && !mapLayers.isEmpty()) {
			for (int[][] layer : mapLayers) {
				drawLayer(g2, cameraX, cameraY, layer);
			}
		} else {
			// Single layer (old .txt format)
			drawLayer(g2, cameraX, cameraY, mapTileNum);
		}
	}*/
	public void draw(Graphics2D g2, int cameraX, int cameraY) {
		if (isTmxMap && !mapLayers.isEmpty()) {
			for (int[][] layer : mapLayers) {
				drawLayer(g2, cameraX, cameraY, layer, true);  // skipZero = true, TMX decoration layers can be legitimately empty
			}
		} else {
			drawLayer(g2, cameraX, cameraY, mapTileNum, false); // skipZero = false, tile 0 is real grass here
		}
	}

// Renders only decoration-layer tiles whose row is at or above the given world Y —
// i.e., tiles that should appear BEHIND something standing at worldY.
// Call this before drawing entities.
public void drawDecorationBehind(Graphics2D g2, int cameraX, int cameraY, float beforeWorldY) {
    if (mapLayers.size() < 2) return;
    int[][] decoLayer = mapLayers.get(1);
    for (int row = 0; row < gp.maxWorldRow; row++) {
        int tileWorldY = row * gp.tileSize;
        if (tileWorldY + gp.tileSize > beforeWorldY) continue; // this row draws in the "front" pass instead
        drawDecorationRow(g2, cameraX, cameraY, decoLayer, row);
    }
}

public void drawDecorationFront(Graphics2D g2, int cameraX, int cameraY, float afterWorldY) {
    if (mapLayers.size() < 2) return;
    int[][] decoLayer = mapLayers.get(1);
    for (int row = 0; row < gp.maxWorldRow; row++) {
        int tileWorldY = row * gp.tileSize;
        if (tileWorldY + gp.tileSize <= afterWorldY) continue;
        drawDecorationRow(g2, cameraX, cameraY, decoLayer, row);
    }
}

private void drawDecorationRow(Graphics2D g2, int cameraX, int cameraY, int[][] layerData, int row) {
    for (int col = 0; col < gp.maxWorldCol; col++) {
        int tileNum = layerData[col][row];
        if (tileNum == 0) continue;
        int screenX = col * gp.tileSize - cameraX;
        int screenY = row * gp.tileSize - cameraY;
        if (screenX + gp.tileSize > 0 && screenX < gp.screenWidth &&
            screenY + gp.tileSize > 0 && screenY < gp.screenHeight) {
            if (tileNum < tile.length && tile[tileNum] != null && tile[tileNum].image != null) {
                g2.drawImage(tile[tileNum].image, screenX, screenY, gp.tileSize, gp.tileSize, null);
            }
        }
    }
}

	private void drawLayer(Graphics2D g2, int cameraX, int cameraY, int[][] layerData, boolean skipZero) {
		for (int row = 0; row < gp.maxWorldRow; row++) {
			for (int col = 0; col < gp.maxWorldCol; col++) {
				int tileNum = layerData[col][row];
				if (skipZero && tileNum == 0) continue;

				int worldX = col * gp.tileSize;
				int worldY = row * gp.tileSize;
				int screenX = worldX - cameraX;
				int screenY = worldY - cameraY;

				if (screenX + gp.tileSize > 0 && screenX < gp.screenWidth &&
					screenY + gp.tileSize > 0 && screenY < gp.screenHeight) {
					if (tileNum < tile.length && tile[tileNum] != null && tile[tileNum].image != null) {
						g2.drawImage(tile[tileNum].image, screenX, screenY, gp.tileSize, gp.tileSize, null);
					}
				}
			}
		}
	}

}

