#!/usr/bin/env python3
import json

# Read the JSON map
with open('res/maps/forest/map.json', 'r') as f:
    data = json.load(f)

map_width = data['mapWidth']
map_height = data['mapHeight']

# Game world size
world_width = 800
world_height = 310

# Create grid filled with 0 (empty/grass)
grid = [[0 for _ in range(world_width)] for _ in range(world_height)]

# Process all layers IN REVERSE ORDER (Tiled JSON has layers bottom-to-top,
# so we process top-to-bottom so top layers end up on top)
for layer in reversed(data['layers']):
    for tile in layer['tiles']:
        tile_id = int(tile['id'])
        x = tile['x']
        y = tile['y']
        if 0 <= x < world_width and 0 <= y < world_height:
            grid[y][x] = tile_id

# Write to forest.txt
with open('res/maps/forest.txt', 'w') as f:
    for row in grid:
        f.write(' '.join(str(val) for val in row) + '\n')

print("forest.txt created successfully!")
print(f"World size: {world_width}x{world_height}")
print(f"Map size: {map_width}x{map_height}")