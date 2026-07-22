package tile;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import java.util.*;

public class TmxMapLoader {

    public static class TmxMap {
        public int width;
        public int height;
        public int tileWidth;
        public int tileHeight;
        public List<TmxLayer> layers = new ArrayList<>();
        public List<TmxTileset> tilesets = new ArrayList<>();
        public Properties properties = new Properties();
    }

    public static class TmxLayer {
        public String name;
        public int width;
        public int height;
        public int[] data; // flattened row-major: data[row * width + col]
        public Properties properties = new Properties();
        public boolean visible = true;
        public float opacity = 1.0f;
    }

    public static class TmxTileset {
        public int firstGid;
        public String name;
        public int tileWidth;
        public int tileHeight;
        public int spacing;
        public int margin;
        public int columns;
        public String imageSource;
        public int imageWidth;
        public int imageHeight;
        public BufferedImage image;
        public Properties tileProperties = new Properties(); // key: "tileId.propertyName"
    }

    public static class Properties extends HashMap<String, String> {
        public boolean getBoolean(String key, boolean defaultValue) {
            String v = get(key);
            return v != null ? Boolean.parseBoolean(v) : defaultValue;
        }
        public int getInt(String key, int defaultValue) {
            String v = get(key);
            return v != null ? Integer.parseInt(v) : defaultValue;
        }
        public float getFloat(String key, float defaultValue) {
            String v = get(key);
            return v != null ? Float.parseFloat(v) : defaultValue;
        }
    }

    public static TmxMap load(String filename) throws Exception {
        // Try classpath first, then filesystem
        InputStream is = TmxMapLoader.class.getResourceAsStream("/res/maps/" + filename);
        if (is == null) {
            File file = new File("res/maps/" + filename);
            if (file.exists()) {
                is = new FileInputStream(file);
            } else {
                file = new File("my2Dgame/res/maps/" + filename);
                if (file.exists()) {
                    is = new FileInputStream(file);
                } else {
                    throw new FileNotFoundException("TMX file not found: " + filename);
                }
            }
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(is);
        is.close();

        Element mapElem = doc.getDocumentElement();
        TmxMap map = new TmxMap();

        map.width = Integer.parseInt(mapElem.getAttribute("width"));
        map.height = Integer.parseInt(mapElem.getAttribute("height"));
        map.tileWidth = Integer.parseInt(mapElem.getAttribute("tilewidth"));
        map.tileHeight = Integer.parseInt(mapElem.getAttribute("tileheight"));

        // Parse map properties
        NodeList propNodes = mapElem.getElementsByTagName("property");
        for (int i = 0; i < propNodes.getLength(); i++) {
            Element prop = (Element) propNodes.item(i);
            map.properties.put(prop.getAttribute("name"), prop.getAttribute("value"));
        }

        // Parse tilesets
        NodeList tilesetNodes = mapElem.getElementsByTagName("tileset");
        for (int i = 0; i < tilesetNodes.getLength(); i++) {
            Element tsElem = (Element) tilesetNodes.item(i);
            TmxTileset ts = parseTileset(tsElem, mapElem.getAttribute("tiledversion"));
            map.tilesets.add(ts);
        }

        // Parse layers
        NodeList layerNodes = mapElem.getElementsByTagName("layer");
        for (int i = 0; i < layerNodes.getLength(); i++) {
            Element layerElem = (Element) layerNodes.item(i);
            TmxLayer layer = parseLayer(layerElem);
            map.layers.add(layer);
        }

        // Load tileset images
        for (TmxTileset ts : map.tilesets) {
            if (ts.imageSource != null) {
                loadTilesetImage(ts, filename);
            }
        }

        return map;
    }

    private static TmxTileset parseTileset(Element tsElem, String tiledVersion) {
        TmxTileset ts = new TmxTileset();
        ts.firstGid = Integer.parseInt(tsElem.getAttribute("firstgid"));
        ts.name = tsElem.getAttribute("name");
        ts.tileWidth = Integer.parseInt(tsElem.getAttribute("tilewidth"));
        ts.tileHeight = Integer.parseInt(tsElem.getAttribute("tileheight"));
        ts.spacing = tsElem.hasAttribute("spacing") ? Integer.parseInt(tsElem.getAttribute("spacing")) : 0;
        ts.margin = tsElem.hasAttribute("margin") ? Integer.parseInt(tsElem.getAttribute("margin")) : 0;
        ts.columns = tsElem.hasAttribute("columns") ? Integer.parseInt(tsElem.getAttribute("columns")) : 0;

        // Parse tileset properties
        NodeList tsProps = tsElem.getElementsByTagName("property");
        for (int i = 0; i < tsProps.getLength(); i++) {
            Element prop = (Element) tsProps.item(i);
            ts.tileProperties.put(prop.getAttribute("name"), prop.getAttribute("value"));
        }

        // Parse image
        NodeList imageNodes = tsElem.getElementsByTagName("image");
        if (imageNodes.getLength() > 0) {
            Element imgElem = (Element) imageNodes.item(0);
            ts.imageSource = imgElem.getAttribute("source");
            ts.imageWidth = imgElem.hasAttribute("width") ? Integer.parseInt(imgElem.getAttribute("width")) : 0;
            ts.imageHeight = imgElem.hasAttribute("height") ? Integer.parseInt(imgElem.getAttribute("height")) : 0;
        }

        // Parse individual tile properties (for collision, etc.)
        NodeList tileNodes = tsElem.getElementsByTagName("tile");
        for (int i = 0; i < tileNodes.getLength(); i++) {
            Element tileElem = (Element) tileNodes.item(i);
            int tileId = Integer.parseInt(tileElem.getAttribute("id"));
            NodeList tileProps = tileElem.getElementsByTagName("property");
            for (int j = 0; j < tileProps.getLength(); j++) {
                Element prop = (Element) tileProps.item(j);
                ts.tileProperties.put(tileId + "." + prop.getAttribute("name"), prop.getAttribute("value"));
            }
        }

        return ts;
    }

    private static TmxLayer parseLayer(Element layerElem) {
        TmxLayer layer = new TmxLayer();
        layer.name = layerElem.getAttribute("name");
        layer.width = Integer.parseInt(layerElem.getAttribute("width"));
        layer.height = Integer.parseInt(layerElem.getAttribute("height"));
        layer.visible = !layerElem.hasAttribute("visible") || !"0".equals(layerElem.getAttribute("visible"));
        layer.opacity = layerElem.hasAttribute("opacity") ? Float.parseFloat(layerElem.getAttribute("opacity")) : 1.0f;

        // Parse layer properties
        NodeList propNodes = layerElem.getElementsByTagName("property");
        for (int i = 0; i < propNodes.getLength(); i++) {
            Element prop = (Element) propNodes.item(i);
            layer.properties.put(prop.getAttribute("name"), prop.getAttribute("value"));
        }

        // Parse data
        NodeList dataNodes = layerElem.getElementsByTagName("data");
        if (dataNodes.getLength() > 0) {
            Element dataElem = (Element) dataNodes.item(0);
            String encoding = dataElem.getAttribute("encoding");
            String compression = dataElem.getAttribute("compression");
            String textContent = dataElem.getTextContent().trim();

            if ("csv".equals(encoding)) {
                layer.data = parseCsvData(textContent, layer.width * layer.height);
            } else if ("base64".equals(encoding)) {
                // For simplicity, only CSV is implemented here
                // Base64 + gzip/zlib would need additional parsing
                throw new UnsupportedOperationException("Base64 encoding not supported in this simple loader");
            } else {
                // XML format (list of <tile> elements)
                layer.data = parseXmlTileData(dataElem, layer.width * layer.height);
            }
        }

        return layer;
    }

    private static int[] parseCsvData(String csv, int expectedSize) {
        String[] tokens = csv.split("[\\s,]+");
        int[] data = new int[expectedSize];
        int idx = 0;
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (idx >= expectedSize) break;
            try {
                data[idx++] = Integer.parseInt(token);
            } catch (NumberFormatException e) {
                data[idx++] = 0;
            }
        }
        return data;
    }

    private static int[] parseXmlTileData(Element dataElem, int expectedSize) {
        int[] data = new int[expectedSize];
        NodeList tileNodes = dataElem.getElementsByTagName("tile");
        int idx = 0;
        for (int i = 0; i < tileNodes.getLength() && idx < expectedSize; i++) {
            Element tileElem = (Element) tileNodes.item(i);
            data[idx++] = Integer.parseInt(tileElem.getAttribute("gid"));
        }
        return data;
    }

    private static void loadTilesetImage(TmxTileset ts, String tmxFilename) throws IOException {
        // Try to load the tileset image relative to the TMX file location
        String basePath = tmxFilename.substring(0, tmxFilename.lastIndexOf('/') + 1);
        String imagePath = basePath + ts.imageSource;

        // Try classpath first
        InputStream is = TmxMapLoader.class.getResourceAsStream("/res/maps/" + imagePath);
        if (is == null) {
            // Try filesystem
            File file = new File("res/maps/" + imagePath);
            if (file.exists()) {
                is = new FileInputStream(file);
            } else {
                file = new File("my2Dgame/res/maps/" + imagePath);
                if (file.exists()) {
                    is = new FileInputStream(file);
                }
            }
        }

        if (is != null) {
            ts.image = ImageIO.read(is);
            is.close();
        } else {
            throw new IOException("Could not load tileset image: " + imagePath);
        }
    }

    /**
     * Gets the tile image from a tileset for a given global tile ID (GID).
     * Returns null if the GID is 0 (empty) or not found in any tileset.
     */
    public static BufferedImage getTileImage(TmxMap map, int gid) {
        if (gid == 0) return null;

        // Find the tileset that contains this GID
        TmxTileset tileset = null;
        for (int i = map.tilesets.size() - 1; i >= 0; i--) {
            TmxTileset ts = map.tilesets.get(i);
            if (gid >= ts.firstGid) {
                tileset = ts;
                break;
            }
        }

        if (tileset == null || tileset.image == null) return null;

        int localId = gid - tileset.firstGid;
        int columns = tileset.columns > 0 ? tileset.columns :
            tileset.image.getWidth() / tileset.tileWidth;

        int tileX = (localId % columns) * (tileset.tileWidth + tileset.spacing) + tileset.margin;
        int tileY = (localId / columns) * (tileset.tileHeight + tileset.spacing) + tileset.margin;

        if (tileX + tileset.tileWidth <= tileset.image.getWidth() &&
            tileY + tileset.tileHeight <= tileset.image.getHeight()) {
            return tileset.image.getSubimage(tileX, tileY, tileset.tileWidth, tileset.tileHeight);
        }

        return null;
    }

    /**
     * Checks if a tile has a specific property (e.g., "collider" = "true")
     */
    public static boolean hasTileProperty(TmxMap map, int gid, String propertyName) {
        if (gid == 0) return false;

        for (int i = map.tilesets.size() - 1; i >= 0; i--) {
            TmxTileset ts = map.tilesets.get(i);
            if (gid >= ts.firstGid) {
                int localId = gid - ts.firstGid;
                String key = localId + "." + propertyName;
                String value = ts.tileProperties.get(key);
                if (value != null) {
                    return Boolean.parseBoolean(value);
                }
                // Also check tileset-level properties
                value = ts.tileProperties.get(propertyName);
                if (value != null) {
                    return Boolean.parseBoolean(value);
                }
                break;
            }
        }
        return false;
    }
}