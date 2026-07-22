import json

# Load the map JSON
with open("res/maps/mapA/map.json", "r") as f:
    data = json.load(f)

map_width = data["mapWidth"]
map_height = data["mapHeight"]
world_size = 50

# Create 50x50 grid
grid = [[0] * world_size for _ in range(world_size)]

# In Tiled JSON, layers are ordered bottom-to-top (first = bottom)
# Process in reverse order so top layers are written last (on top)
for layer in reversed(data["layers"]):
    for tile in layer["tiles"]:
        id = tile["id"]
        x = tile["x"]
        y = tile["y"]
        if 0 <= x < world_size and 0 <= y < world_size:
            grid[x][y] = id

# Write to mapA.txt
with open("res/maps/mapA.txt", "w") as f:
    for row in range(world_size):
        line = " ".join(str(grid[col][row]) for col in range(world_size))
        f.write(line + "\n")

print("mapA.txt created successfully!")
