# Loading Tiled (.tmx) Maps into a Java2D/Swing Game

## Important correction first
The libGDX snippet (`TmxMapLoader`, `OrthogonalTiledMapRenderer`) does not apply here — `GamePanel` extends `JPanel` and renders with `Graphics2D`, which is a plain Java2D/Swing project, not libGDX. libGDX has its own render loop and asset system that can't be mixed into a Swing app. This guide instead shows how to parse Tiled's `.tmx` XML format directly in plain Java and feed the tile data into your existing `tileManager`/`GamePanel` structure — no new framework required.

## How TMX files are structured
A `.tmx` file is XML. The part you care about is the `<data>` block inside a `<layer>` element — it's a comma-separated grid of tile IDs (one number per tile, row by row):
```xml
<map version="1.10" tiledversion="1.10.2" orientation="orthogonal" width="20" height="15" tilewidth="16" tileheight="16">
  <tileset firstgid="1" name="tiles" tilewidth="16" tileheight="16" source="tileset.tsx"/>
  <layer id="1" name="Ground" width="20" height="15">
    <data encoding="csv">
      1,1,1,2,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
      1,1,2,2,2,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
      ...
    </data>
  </layer>
</map>
```
This maps directly onto what your `tileM.getMapTileNum()[col][row]` already expects — a 2D grid of tile numbers. The `firstgid` on each tileset tells you the starting tile ID for that tileset's images, which matters if you have multiple tilesets in one map.

## Implementation plan

### 1. Add a TMX parser class
Create `tile/TmxMapLoader.java` (not to be confused with libGDX's class of the same name — this is your own plain-Java version):

```java
package tile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class TmxMapLoader {

    public static class TmxMapData {
        public int width;
        public int height;
        public int tileWidth;
        public int tileHeight;
        public int[][] tileGrid; // [col][row], matching your existing getMapTileNum() layout
    }

    public static TmxMapData load(String resourcePath) throws Exception {
        InputStream is = TmxMapLoader.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new java.io.IOException("TMX file not found: " + resourcePath);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(is);
        doc.getDocumentElement().normalize();

        Element mapElement = doc.getDocumentElement();
        TmxMapData data = new TmxMapData();
        data.width = Integer.parseInt(mapElement.getAttribute("width"));
        data.height = Integer.parseInt(mapElement.getAttribute("height"));
        data.tileWidth = Integer.parseInt(mapElement.getAttribute("tilewidth"));
        data.tileHeight = Integer.parseInt(mapElement.getAttribute("tileheight"));

        NodeList layers = doc.getElementsByTagName("layer");
        if (layers.getLength() == 0) {
            throw new IllegalStateException("No <layer> found in TMX file: " + resourcePath);
        }

        // Assumes the first layer is your ground/collision layer.
        // If you use multiple layers (ground + decoration + collision), loop over `layers` instead.
        Element firstLayer = (Element) layers.item(0);
        NodeList dataNodes = firstLayer.getElementsByTagName("data");
        Element dataElement = (Element) dataNodes.item(0);
        String csv = dataElement.getTextContent().trim();

        String[] rawValues = csv.split(",");
        data.tileGrid = new int[data.width][data.height];

        int index = 0;
        for (int row = 0; row < data.height; row++) {
            for (int col = 0; col < data.width; col++) {
                String token = rawValues[index].trim();
                data.tileGrid[col][row] = token.isEmpty() ? 0 : Integer.parseInt(token);
                index++;
            }
        }

        return data;
    }
}
```

### 2. Wire it into your existing `tileManager`
I don't have your `tileManager.java`, so I can't point to exact lines — but based on `GamePanel` calling `tileM.loadMap(mapFile)` and `tileM.getMapTileNum()[col][row]`, your `tileManager` almost certainly already has:
- a method that loads a map file and populates a `mapTileNum[][]` array
- a `getMapTileNum()` getter returning that array

Add a new loading path alongside your existing one (likely a `.txt`-based loader currently):
```java
public void loadTmxMap(String tmxResourcePath) {
    try {
        TmxMapLoader.TmxMapData data = TmxMapLoader.load(tmxResourcePath);
        this.mapTileNum = data.tileGrid; // adjust field name to match your actual field
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```
Then in `GamePanel.loadMap()`:
```java
public void loadMap(String filename) {
    if (filename.endsWith(".tmx")) {
        tileM.loadTmxMap(filename);
    } else {
        tileM.loadMap(filename); // your existing .txt loader, unchanged
    }
}
```
This lets you keep your current `.txt` maps working while adding `.tmx` support side-by-side — no need to migrate everything at once.

### 3. Rendering tile images
Your current renderer (in `tileManager.draw()`, presumably) already maps a `tileNum` integer to a specific tile image — that logic doesn't need to change, since the TMX loader produces the same `int[][]` shape your existing code expects. The only new consideration: Tiled tile IDs are **1-indexed with an offset from `firstgid`**, so if you add a second tileset later, you'll need to check which tileset a given ID belongs to by comparing against each tileset's `firstgid`. For a single-tileset map (the common case), your existing tile-number-to-image logic should work unchanged.

### 4. Getting the actual tileset image
Tiled tilesets (`.tsx` files, or embedded directly in the `.tmx`) reference a source PNG — you'll load that once and slice it into individual tile images the same way you likely already do for your existing tile spritesheet, using the `tilewidth`/`tileheight` from the TMX file to compute slice dimensions.

## What you'll need to test
1. Export a small test map from Sprite Fusion or Tiled as `.tmx`.
2. Place it in your resources folder alongside your existing map files.
3. Call `gp.loadMap("testmap.tmx")` and confirm `tileM.getMapTileNum()` returns a grid matching what you see in the Tiled editor.
4. Confirm collision (`isTileBlocked()`) still works — this depends on whatever tile-number-to-blocked mapping your `tileManager` already uses, which shouldn't need to change since the grid shape is identical to your `.txt` format.

## If you want multi-layer support later
Real Tiled maps often have separate layers for ground, decoration, and collision. The parser above only reads the first `<layer>` — extending it to loop over all `<layer>` elements and return a `Map<String, int[][]>` keyed by layer name is a natural next step once single-layer loading is confirmed working.