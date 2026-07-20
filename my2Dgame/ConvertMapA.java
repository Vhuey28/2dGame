import java.io.*;
import java.util.*;
import org.json.*;

public class ConvertMapA {
    public static void main(String[] args) {
        try {
            String json = new String(new FileInputStream("res/maps/mapA/map.json").readAllBytes());
            JSONObject obj = new JSONObject(json);
            int mapWidth = obj.getInt("mapWidth");
            int mapHeight = obj.getInt("mapHeight");

            // Use actual map dimensions from JSON
            int[][] grid = new int[mapHeight][mapWidth];
            // Fill with 0 (empty) - Java initializes to 0 by default, but explicit for clarity
            for (int y = 0; y < mapHeight; y++) {
                for (int x = 0; x < mapWidth; x++) {
                    grid[y][x] = 0;
                }
            }

            JSONArray layers = obj.getJSONArray("layers");
            for (int l = 0; l < layers.length(); l++) {
                JSONObject layer = layers.getJSONObject(l);
                JSONArray tiles = layer.getJSONArray("tiles");
                for (int i = 0; i < tiles.length(); i++) {
                    JSONObject tile = tiles.getJSONObject(i);
                    int id = tile.getInt("id");  // handles string "15" -> int 15
                    int x = tile.getInt("x");
                    int y = tile.getInt("y");
                    if (x >= 0 && x < mapWidth && y >= 0 && y < mapHeight) {
                        // Java 2D arrays are [row][col] = [y][x]
                        grid[y][x] = id;
                    }
                }
            }

            // Write to mapA.txt (mapHeight rows, mapWidth cols)
            PrintWriter pw = new PrintWriter("res/maps/mapA.txt");
            for (int y = 0; y < mapHeight; y++) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < mapWidth; x++) {
                    sb.append(grid[y][x]);
                    if (x < mapWidth - 1) sb.append(" ");
                }
                pw.println(sb.toString());
            }
            pw.close();
            System.out.println("mapA.txt created successfully! (" + mapWidth + "x" + mapHeight + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
