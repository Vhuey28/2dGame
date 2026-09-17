# Web Compatibility Implementation Plan for Chronicle Conquest

## Purpose

This document describes the changes needed to make the Java 2D game **Chronicle Conquest** run more reliably in a browser through **CheerpJ** and GitHub Pages.

The current desktop version uses Java Swing/AWT, JInput, filesystem-based settings, and several desktop-oriented resource fallbacks. Those assumptions need to be isolated or disabled for the web build.

---

# 1. Target Deployment Structure

Keep the Java source and resources in the normal Maven structure.

Recommended repository layout:

```text
2dGame/
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   │       └── res/
│   │           ├── maps/
│   │           ├── player/
│   │           ├── tiles/
│   │           └── effects/
│   └── test/
├── web/
│   └── index.html
├── pom.xml
└── .github/
    └── workflows/
        └── deploy.yml
```

The final GitHub Pages deployment directory should contain:

```text
site/
├── index.html
└── my2Dgame-1.0.0.jar
```

The resources do **not** need to be copied individually into `web/`. Maven should package `src/main/resources` into the JAR.

---

# 2. Fix the GitHub Actions Build

## Current problem

The workflow builds a fresh Maven JAR with:

```bash
mvn -B clean package
```

but then copies the JAR from:

```text
web/my2Dgame-1.0.0.jar
```

This can deploy an older JAR instead of the JAR that was just built.

## Recommended workflow

After Maven finishes, copy the newly created JAR:

```bash
mkdir -p site
cp target/my2Dgame-1.0.0.jar site/
cp web/index.html site/
```

Do not depend on a manually copied JAR inside `web/`.

## Optional improvement

Use a Maven command that explicitly verifies the build:

```bash
mvn -B clean package
test -f target/my2Dgame-1.0.0.jar
```

Then copy the verified artifact to `site/`.

---

# 3. Fix `index.html`

The browser should load the JAR from the same directory as `index.html`.

Use:

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>Chronicle Conquest</title>
    <script src="https://cjrtnc.leaningtech.com/4.3/loader.js"></script>
</head>
<body style="margin:0; overflow:hidden;">
<script>
(async () => {
    await cheerpjInit();

    // Match the Java game's logical resolution.
    cheerpjCreateDisplay(768, 576);

    await cheerpjRunJar("./my2Dgame-1.0.0.jar");
})();
</script>
</body>
</html>
```

The important part is:

```javascript
"./my2Dgame-1.0.0.jar"
```

Do not use:

```javascript
"/my2Dgame-1.0.0.jar"
```

because the leading `/` points to the domain root rather than the GitHub Pages project directory.

---

# 4. Add a Web/Browser Runtime Flag

The cleanest approach is to make desktop-specific features optional.

Create a small runtime utility such as:

```java
package my2Dgame;

public final class RuntimeMode {

    private RuntimeMode() {
    }

    public static boolean isWeb() {
        return Boolean.getBoolean("chronicle.web");
    }
}
```

However, if CheerpJ does not provide a convenient Java system property automatically, use a command-line or application configuration mechanism instead.

An even simpler approach for a dedicated web build is to create:

```java
public static final boolean WEB_BUILD = true;
```

in a configuration class.

For example:

```java
package my2Dgame;

public final class GameConfig {

    private GameConfig() {
    }

    public static final boolean WEB_BUILD = true;
}
```

For the desktop build, this can be changed to `false`.

A separate web profile in Maven is preferable if the project eventually needs both desktop and web builds.

---

# 5. Disable JInput for the Web Version

## Why this matters

The current controller code initializes JInput during game-panel construction.

The problematic pattern is approximately:

```java
BindingManager bindings = new BindingManager();
ControllerHandler controllerH = new ControllerHandler(bindings);
```

and the controller handler immediately searches for controllers:

```java
ControllerEnvironment.getDefaultEnvironment()
    .getControllers();
```

JInput is designed around desktop Java/native controller access. A browser should not depend on that native controller layer.

## Recommended approach

Make the controller handler optional.

For example:

```java
BindingManager bindings = new BindingManager();

ControllerHandler controllerH = null;

if (!GameConfig.WEB_BUILD) {
    controllerH = new ControllerHandler(bindings);
}
```

Then make every controller-dependent call null-safe.

Instead of:

```java
controllerH.update();
```

use:

```java
if (controllerH != null) {
    controllerH.update();
}
```

Likewise for controller button/axis checks.

## Better long-term design

Create an input abstraction:

```java
public interface GameInput {
    void update();
    boolean isActionPressed(String action);
}
```

Then implement:

```text
KeyboardMouseInput
DesktopControllerInput
```

The web build can use keyboard/mouse input without loading JInput.

This is preferable if the project will continue to support both desktop and browser versions.

---

# 6. Avoid JInput Initialization Entirely in Web Mode

Do not merely catch an exception around:

```java
ControllerEnvironment.getDefaultEnvironment()
```

For example, avoid relying only on:

```java
try {
    ...
} catch (Exception e) {
    ...
}
```

The better solution is to prevent the JInput class from being initialized in the first place.

Use:

```java
if (!GameConfig.WEB_BUILD) {
    controllerH = new ControllerHandler(bindings);
}
```

This prevents the browser version from depending on JInput.

---

# 7. Handle Keybinding Persistence Without Desktop Files

## Current problem

The game stores configuration using a path similar to:

```java
System.getProperty("user.home")
    + "/.chronicle_conquest/keybinds.properties";
```

and uses:

```java
FileInputStream
FileOutputStream
```

This assumes a normal desktop filesystem.

## Web solution

For the first web-compatible version, use default key bindings without saving them to disk.

Example:

```java
public void load() {
    if (GameConfig.WEB_BUILD) {
        return;
    }

    // Existing desktop file-loading code
}
```

and:

```java
public void save() {
    if (GameConfig.WEB_BUILD) {
        return;
    }

    // Existing desktop file-saving code
}
```

The game will still run with default controls.

## Better future solution

For persistent browser settings, use browser storage such as `localStorage`.

That requires JavaScript/browser integration and should be implemented separately from the initial compatibility fix.

Do not introduce browser storage until the game can start and play successfully.

---

# 8. Make Resource Loading Classpath-Only

The project already has the correct Maven resource directory:

```text
src/main/resources/res/
```

Resources such as maps and images should be loaded from the classpath.

Preferred pattern:

```java
InputStream input =
    getClass().getResourceAsStream("/res/maps/" + filename);
```

Check for `null`:

```java
InputStream input =
    getClass().getResourceAsStream("/res/maps/" + filename);

if (input == null) {
    throw new IOException(
        "Could not find map resource: " + filename
    );
}
```

## Avoid desktop-only fallbacks

The current code contains filesystem fallback paths such as:

```text
res/maps/...
src/res/maps/...
my2Dgame/res/maps/...
my2Dgame/src/res/maps/...
```

These are useful during local development but should not be necessary for the packaged JAR.

For the web version, prefer:

```java
getResourceAsStream(...)
```

over:

```java
new File(...)
```

or:

```java
Files.readAllBytes(...)
```

---

# 9. Verify Resource Paths Inside the JAR

After building:

```bash
mvn clean package
```

inspect the JAR:

```bash
jar tf target/my2Dgame-1.0.0.jar
```

You should see entries similar to:

```text
my2Dgame/Main.class
res/maps/map1.txt
res/maps/forest.tmx
res/player/...
res/tiles/...
res/effects/...
```

If these resources are missing, fix the Maven/resource configuration before troubleshooting CheerpJ.

---

# 10. Avoid Desktop Fullscreen in the Web Build

The current `Main` class uses desktop graphics APIs such as:

```java
GraphicsEnvironment
GraphicsDevice
setFullScreenWindow(...)
```

These should not control the browser display.

For the web build, skip fullscreen logic.

Instead of:

```java
if (fullScreen) {
    device.setFullScreenWindow(frame);
}
```

use:

```java
if (!GameConfig.WEB_BUILD && fullScreen) {
    device.setFullScreenWindow(frame);
}
```

The browser page should control the visible display size.

---

# 11. Match the Game Resolution

The Java game uses:

```text
768 × 576
```

The CheerpJ page should therefore use:

```javascript
cheerpjCreateDisplay(768, 576);
```

rather than:

```javascript
cheerpjCreateDisplay(800, 600);
```

This does not necessarily cause the JAR startup error, but matching the logical resolution avoids scaling and input-coordinate problems.

---

# 12. Keep Swing/AWT Changes Minimal

Do not rewrite the game into JavaScript.

The objective is to make the existing Java game compatible with the Java runtime available in the browser.

Keep:

- game loop
- entities
- combat
- maps
- tiles
- sprites
- animation
- game logic
- Java rendering

Focus changes on:

- JInput
- filesystem access
- fullscreen/window management
- resource loading
- deployment

---

# 13. Add Browser-Safe Error Reporting

Wrap the startup code temporarily:

```java
public static void main(String[] args) {

    try {
        // Existing startup code
    } catch (Throwable e) {
        e.printStackTrace();
        throw e;
    }
}
```

This is useful during browser testing because the console can reveal whether the failure is:

```text
NoClassDefFoundError
ExceptionInInitializerError
FileNotFoundException
NullPointerException
AWTException
JInput/controller initialization failure
```

Do not leave excessive debugging output in the final release.

---

# 14. Recommended Implementation Order

Do the changes in this order.

### Step 1 — Fix deployment

Make GitHub Actions build:

```bash
mvn clean package
```

and deploy:

```text
target/my2Dgame-1.0.0.jar
```

rather than the old JAR.

### Step 2 — Fix `index.html`

Use:

```javascript
cheerpjRunJar("./my2Dgame-1.0.0.jar");
```

and:

```javascript
cheerpjCreateDisplay(768, 576);
```

### Step 3 — Disable JInput

Prevent:

```java
ControllerEnvironment.getDefaultEnvironment()
```

from executing in the web build.

### Step 4 — Disable filesystem keybinding persistence

Do not attempt to write:

```text
~/.chronicle_conquest/keybinds.properties
```

from the browser build.

### Step 5 — Make resource loading classpath-only

Use:

```java
getResourceAsStream(...)
```

for maps, images, and other packaged resources.

### Step 6 — Disable desktop fullscreen

Do not call:

```java
setFullScreenWindow(...)
```

in the browser build.

### Step 7 — Rebuild

Run:

```bash
mvn clean package
```

### Step 8 — Inspect the JAR

Run:

```bash
jar tf target/my2Dgame-1.0.0.jar
```

Verify the resources exist.

### Step 9 — Deploy

Push the changes to GitHub and allow the Pages workflow to finish.

### Step 10 — Test

Open:

```text
https://vhuey28.github.io/2dGame/
```

Open the browser developer console and check for the first Java exception.

The **first exception** is more useful than the later cascading errors.

---

# 15. Expected Result

The desired architecture is:

```text
                    GitHub Pages
                         |
                         v
                  +--------------+
                  | index.html   |
                  +--------------+
                         |
                         v
                      CheerpJ
                         |
                         v
                my2Dgame-1.0.0.jar
                         |
          +--------------+--------------+
          |              |              |
          v              v              v
       Java code     Game resources   Game logic
                         |
                         v
                  /res/... inside JAR
```

The browser version should not depend on:

```text
Desktop JInput native controllers
Desktop filesystem
Desktop fullscreen
IDE working directory
src/ directory at runtime
```

---

# 16. Important Diagnostic Rule

If the JAR itself successfully loads but the game crashes afterward, **do not move the JAR or `index.html` again**.

At that point inspect the first Java exception.

For example:

```text
JInput error
    -> disable JInput

FileNotFoundException
    -> fix filesystem access

Resource not found
    -> fix getResourceAsStream path

AWT/Graphics error
    -> remove desktop-only graphics behavior

NoClassDefFoundError
    -> inspect Maven dependencies/shading

NullPointerException
    -> inspect the specific startup class
```

This separates deployment problems from Java compatibility problems.

---

# 17. Final Architecture

The long-term goal should be:

```text
Desktop build
    |
    +-- Keyboard/mouse
    +-- JInput controllers
    +-- Desktop filesystem
    +-- Optional fullscreen


Web build
    |
    +-- Keyboard/mouse
    +-- Browser-compatible input
    +-- Classpath resources
    +-- No desktop filesystem dependency
    +-- Browser-controlled display
```

The game itself remains largely shared between the two builds.

This approach avoids maintaining two completely separate games while removing the desktop assumptions that prevent the browser version from starting.
