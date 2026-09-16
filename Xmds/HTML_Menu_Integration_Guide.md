# Integrating HTML Menus into a Java 2D RPG

## Goal

Use Java2D for gameplay and HTML/CSS/JavaScript for menus.

## Architecture

``` text
Java Engine
├── Rendering
├── Physics
├── AI
├── Combat
└── UI Manager
      └── Browser Overlay (JCEF)
```

## Use HTML For

-   Main Menu
-   Pause Menu
-   Inventory
-   Quest Log
-   Character Screen
-   Settings
-   Skill Tree

## Do Not Use HTML For

-   Tile rendering
-   Entities
-   Combat
-   Physics

## Recommended Structure

``` text
src/
 ui/
   BrowserOverlay.java
   UIManager.java
   html/
     index.html
     inventory.html
     quests.html
     settings.html
```

## Java ↔ JavaScript

``` javascript
window.game.resumeGame();
window.game.saveGame();
```

``` java
public void resumeGame(){}
public void saveGame(){}
```

## Implementation Steps

1.  Integrate JCEF.
2.  Create BrowserOverlay.
3.  Create UIManager.
4.  Build HTML menus.
5.  Connect JavaScript to Java.
6.  Pause gameplay while menus are open.
7.  Add animations and responsive CSS.

## Future

-   Drag-and-drop inventory
-   Interactive map
-   Crafting
-   Achievements
-   Localization
