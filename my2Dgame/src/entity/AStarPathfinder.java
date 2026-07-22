package entity;

import java.awt.Point;
import java.util.*;

import my2Dgame.GamePanel;

/**
 * Grid-based A* pathfinder. Operates in TILE coordinates internally but
 * returns a path as a list of WORLD-PIXEL waypoints (tile centers), so
 * callers can move toward each Point directly without extra conversion.
 *
 * Usage:
 *   List<Point> path = AStarPathfinder.findPath(gp, (int)x, (int)y, (int)targetX, (int)targetY);
 *
 * Cost note: this is a full grid search, not cheap at 800x310 tiles if run
 * naively. maxSearchRadius below caps how far it will search from the start
 * tile, which both bounds cost and prevents units from computing enormous
 * paths across the whole map. Recompute paths periodically (e.g. every 30-60
 * ticks), not every frame.
 */
public class AStarPathfinder {

    private static final int MAX_SEARCH_RADIUS_TILES = 40; // tiles in each direction from the start

    private static class Node {
        int col, row;
        int gCost, hCost;
        Node parent;
        int fCost() { return gCost + hCost; }
        Node(int col, int row) { this.col = col; this.row = row; }
    }

    public static List<Point> findPath(GamePanel gp, int startWorldX, int startWorldY,
                                        int targetWorldX, int targetWorldY) {
        int tileSize = gp.tileSize;
        int startCol = startWorldX / tileSize;
        int startRow = startWorldY / tileSize;
        int targetCol = targetWorldX / tileSize;
        int targetRow = targetWorldY / tileSize;

        // If target is unreasonably far, don't search the whole map — caller
        // should just move directly / re-evaluate target instead.
        if (Math.abs(targetCol - startCol) > MAX_SEARCH_RADIUS_TILES ||
            Math.abs(targetRow - startRow) > MAX_SEARCH_RADIUS_TILES) {
            return null;
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingInt(Node::fCost));
        Set<Long> closedSet = new HashSet<>();
        Map<Long, Node> allNodes = new HashMap<>();

        Node startNode = new Node(startCol, startRow);
        startNode.gCost = 0;
        startNode.hCost = heuristic(startCol, startRow, targetCol, targetRow);
        openSet.add(startNode);
        allNodes.put(key(startCol, startRow), startNode);

        int[][] neighbors8 = {
            {1,0},{-1,0},{0,1},{0,-1},
            {1,1},{1,-1},{-1,1},{-1,-1}
        };

        int iterations = 0;
        int maxIterations = 4000; // hard safety cap regardless of radius

        while (!openSet.isEmpty() && iterations++ < maxIterations) {
            Node current = openSet.poll();
            long ck = key(current.col, current.row);
            if (closedSet.contains(ck)) continue;
            closedSet.add(ck);

            if (current.col == targetCol && current.row == targetRow) {
                return reconstructPath(current, tileSize);
            }

            for (int[] dir : neighbors8) {
                int nCol = current.col + dir[0];
                int nRow = current.row + dir[1];
                long nk = key(nCol, nRow);
                if (closedSet.contains(nk)) continue;

                int worldX = nCol * tileSize;
                int worldY = nRow * tileSize;
                if (gp.isTileBlocked(worldX, worldY) ||
                    gp.isTileBlocked(worldX + tileSize - 1, worldY + tileSize - 1)) {
                    continue;
                }
                // Prevent cutting diagonally through a blocked corner
                if (dir[0] != 0 && dir[1] != 0) {
                    boolean cornerA = gp.isTileBlocked(current.col * tileSize + dir[0] * tileSize, current.row * tileSize);
                    boolean cornerB = gp.isTileBlocked(current.col * tileSize, current.row * tileSize + dir[1] * tileSize);
                    if (cornerA && cornerB) continue;
                }

                int moveCost = (dir[0] != 0 && dir[1] != 0) ? 14 : 10; // roughly sqrt(2) vs 1, x10 for int math
                int tentativeG = current.gCost + moveCost;

                Node neighbor = allNodes.get(nk);
                if (neighbor == null) {
                    neighbor = new Node(nCol, nRow);
                    neighbor.gCost = tentativeG;
                    neighbor.hCost = heuristic(nCol, nRow, targetCol, targetRow);
                    neighbor.parent = current;
                    allNodes.put(nk, neighbor);
                    openSet.add(neighbor);
                } else if (tentativeG < neighbor.gCost) {
                    neighbor.gCost = tentativeG;
                    neighbor.parent = current;
                    openSet.add(neighbor); // re-add with updated priority (stale copies filtered by closedSet)
                }
            }
        }

        return null; // no path found within search bounds
    }

    private static int heuristic(int c1, int r1, int c2, int r2) {
        // Octile distance, scaled x10 to match integer move costs above
        int dx = Math.abs(c1 - c2);
        int dy = Math.abs(c1 - c2 == 0 ? r1 - r2 : r1 - r2);
        dx = Math.abs(c1 - c2);
        dy = Math.abs(r1 - r2);
        return 10 * (dx + dy) - 6 * Math.min(dx, dy);
    }

    private static long key(int col, int row) {
        return ((long) col << 32) | (row & 0xffffffffL);
    }

    private static List<Point> reconstructPath(Node endNode, int tileSize) {
        LinkedList<Point> path = new LinkedList<>();
        Node current = endNode;
        while (current != null) {
            int worldX = current.col * tileSize + tileSize / 2;
            int worldY = current.row * tileSize + tileSize / 2;
            path.addFirst(new Point(worldX, worldY));
            current = current.parent;
        }
        // Drop the very first waypoint (current tile) so movement starts toward the next tile
        if (!path.isEmpty()) path.removeFirst();
        return path;
    }
}