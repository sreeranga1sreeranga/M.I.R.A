#!/usr/bin/env bash
set -e

WIKI_DIR="${HOME}/Documents/M.I.R.A.wiki"
REPO_URL="https://github.com/sreeranga1sreeranga/M.I.R.A.wiki.git"

echo "✦ Initializing M.I.R.A. Multi-Page GitHub Wiki..."

rm -rf "${WIKI_DIR}"
git clone "${REPO_URL}" "${WIKI_DIR}"
cd "${WIKI_DIR}"

# ==============================================================================
# 1. _Sidebar.md (Persistent Left Navigation Menu)
# ==============================================================================
cat << 'EOF' > _Sidebar.md
### ✦ M.I.R.A. v2.5 Docs
* [[🏠 Home|Home]]
* [[🚀 Installation & OS Setup|Installation-and-Setup]]
* [[🔄 Conversion Matrix & Tools|Supported-Formats-and-Tools]]
* [[🎨 Themes & Speed Shortcuts|Keyboard-Shortcuts-and-Themes]]
* [[⚙️ Architecture & Engine|Architecture-and-Offline-Engine]]

---
**Quick Links**
* [📦 GitHub Repository](https://github.com/sreeranga1sreeranga/M.I.R.A)
* [🐛 Report an Issue](https://github.com/sreeranga1sreeranga/M.I.R.A/issues)
* [💬 Discussions](https://github.com/sreeranga1sreeranga/M.I.R.A/discussions)
EOF

# ==============================================================================
# 2. Home.md
# ==============================================================================
cat << 'EOF' > Home.md
# ✦ Welcome to the M.I.R.A. Wiki

**M.I.R.A. (Minimalist Instant Radial Assistant)** is a 100% offline, OS-adaptive radial file converter and batch utility daemon for **macOS** and **Windows**.

Instead of opening heavy desktop applications or uploading sensitive personal documents to third-party cloud converters, M.I.R.A. runs silently in your background and materializes directly at your mouse pointer when you trigger **`Cmd + E`** (macOS) or **`Ctrl + E`** (Windows).

---

## 🌟 Core Highlights

| Feature | Description |
| :--- | :--- |
| **Silent Background Daemon** | Runs with `apple.awt.UIElement=true` on macOS and as a System Tray utility on Windows—zero Dock icons, taskbar clutter, or permanent windows. |
| **Chameleon Frosted Glass UI** | Captures the live desktop region behind your cursor (`Robot` + `BoxBlur`) and dynamically matches your OS Dark/Light mode and system accent color. |
| **60-FPS Thinking Orb** | Dual counter-rotating energy arcs inside the center medallion provide real-time visual feedback during heavy background file operations. |
| **Multi-File Batch & Merge** | Highlight multiple PDFs, images, or documents in Finder/Explorer to unlock `MERGE PDFS`, `COMBINE PDF`, `BATCH WEBP/PNG/JPG`, and `ZIP ARCHIVE`. |
| **Spacebar Power Tools** | Tap `Spacebar` or click the center hub to switch from standard format conversion to smart contextual utilities (`SORT A-Z`, `DEDUPE`, `SUMMARY`, `BEAUTIFY`, `ADD BG`). |
| **100% Offline Privacy** | Powered locally by Apache PDFBox, Apache POI, TwelveMonkeys ImageIO, and native macOS `sips` / `textutil` bridges. Zero cloud dependencies. |

---

## 📚 Documentation Sections

1. **[[Installation & OS Permissions|Installation-and-Setup]]** — How to install packages, configure macOS Accessibility, and build from source.
2. **[[Supported Formats & Power Tools|Supported-Formats-and-Tools]]** — Complete conversion matrix, batch actions, and `Spacebar` tool actions.
3. **[[Themes & Keyboard Shortcuts|Keyboard-Shortcuts-and-Themes]]** — Hotkeys (`1`–`7`, `Spacebar`, `Tab`, `Alt+Click`) and the 4 luxury themes.
4. **[[Architecture & Offline Engine|Architecture-and-Offline-Engine]]** — Deep-dive into threading, rendering pipeline, and headless CLI architecture.
EOF

# ==============================================================================
# 3. Installation-and-Setup.md
# ==============================================================================
cat << 'EOF' > Installation-and-Setup.md
# 🚀 Installation & OS Setup Guide

M.I.R.A. can be installed via standalone native installers (bundled with embedded runtime) or built directly from source.

---

## 🍎 macOS Installation (`.dmg`)

1. Download **`MIRA-2.5.0.dmg`** from the [GitHub Releases](https://github.com/sreeranga1sreeranga/M.I.R.A/releases) page.
2. Double-click the `.dmg` and drag **MIRA.app** into your **Applications** folder.
3. Launch **MIRA.app**. It will appear silently in your top Menu Bar with a circular status glyph.
4. **Grant Accessibility Permission (Required for Global `Cmd + E` Shortcut):**
   * Open **System Settings** → **Privacy & Security** → **Accessibility**.
   * Toggle the switch next to **MIRA** to **ON** (or your Terminal/IDE if running from source).
5. **Grant Screen Recording / Automation (Optional for Backdrop Blur & Finder Sync):**
   * On your first `Cmd + E` press, macOS may request Automation permission for Finder. Click **Allow** so M.I.R.A. can read selected file paths.

---

## 🪟 Windows 10 / 11 Installation (`.exe`)

1. Download **`MIRA-2.5.0.exe`** from the [GitHub Releases](https://github.com/sreeranga1sreeranga/M.I.R.A/releases) page.
2. Run the installer. M.I.R.A. will launch silently into your **Windows System Tray** (bottom-right taskbar notification area).
3. Select any file in **Windows File Explorer** and press **`Ctrl + E`** (or **`Shift + E`**) to summon the wheel.

---

## 🛠️ Building from Source (Developers)

### Prerequisites
* **Java Development Kit (JDK) 21+**
* **Apache Maven 3.8+**

### 1. Clone & Run in Dev Mode
```bash
git clone https://github.com/sreeranga1sreeranga/M.I.R.A.git
cd M.I.R.A
mvn clean javafx:run
```

### 2. Build Standalone Shaded Jar
```bash
mvn clean package -DskipTests
```
The self-contained executable jar will be placed in `target/mira-1.0.0-shaded.jar`.

### 3. Headless CLI / Sidecar Mode
M.I.R.A. includes a high-speed headless engine that bypasses the graphical interface completely—ideal for scripts, CI pipelines, or desktop shells like Tauri and Electron:
```bash
java -jar target/mira-1.0.0-shaded.jar "/path/to/dataset.xlsx" "JSON"
java -jar target/mira-1.0.0-shaded.jar "/path/to/document.pdf" "TXT"
java -jar target/mira-1.0.0-shaded.jar "/path/to/photo.png" "WEBP"
```
EOF

# ==============================================================================
# 4. Supported-Formats-and-Tools.md
# ==============================================================================
cat << 'EOF' > Supported-Formats-and-Tools.md
# 🔄 Supported Formats Matrix & Power Tools

M.I.R.A. dynamically detects the active file type and populates the radial segments with appropriate conversion targets or contextual utility tools.

---

## 📂 Single File Conversion Matrix

| Category | Source Extensions | Convert Targets (Default Mode) | Power Tools (`Spacebar` Mode) |
| :--- | :--- | :--- | :--- |
| **Spreadsheets** | `.xlsx`, `.xls`, `.csv`, `.tsv` | `JSON`, `CSV`, `XLSX`, `HTML`, `PDF`, `TXT` | `SORT A-Z`, `DEDUPE`, `SUMMARY`, `COPY TXT` |
| **Documents & Web** | `.docx`, `.doc`, `.rtf`, `.txt`, `.md`, `.html`, `.htm`, `.mhtml` | `PDF`, `DOCX`, `TXT`, `HTML`, `RTF` | `SUMMARY`, `UPPERCASE`, `CLEAN TXT`, `COPY TXT` |
| **Presentations** | `.pptx`, `.ppt` | `PDF`, `PNG`, `TXT`, `HTML` | `EXTRACT TXT`, `SLIDE 1 PNG`, `COPY TXT` |
| **PDF Documents** | `.pdf` | `DOCX`, `TXT`, `HTML`, `JPG`, `PNG` | `SPLIT PAGES`, `SUMMARY`, `ALL PAGES PNG`, `COPY TXT` |
| **Structured Data** | `.json` | `CSV`, `XLSX`, `HTML`, `PDF`, `TXT` | `SUMMARY`, `BEAUTIFY`, `MINIFY`, `COPY TXT` |
| **Raster Images** | `.png`, `.jpg`, `.jpeg`, `.webp`, `.heic`, `.tiff`, `.bmp` | `PNG`, `JPG`, `WEBP`, `HEIC`, `TIFF`, `BMP`, `PDF` | `ADD BG`, `COMPRESS`, `GRAYSCALE`, `STRIP EXIF` |

---

## 🛠️ Power Tools Breakdown

* **`ADD BG` (Studio Frame)**: Opens a glossy framing studio with interactive padding and border-radius sliders, rendering gradient cards around screenshots.
* **`SORT A-Z`**: Sorts spreadsheet records alphabetically by first column without modifying headers.
* **`DEDUPE`**: Removes duplicate rows while preserving column sequence.
* **`SUMMARY`**: Computes record counts, word counts, character statistics, and preview snippets.
* **`BEAUTIFY` / `MINIFY`**: Formats JSON with 2-space indentation or compacts it into a single line.
* **`SPLIT PAGES`**: Bursts multi-page PDF documents into individual page PDFs.
* **`COMPRESS`**: Reduces image file size by applying 50% explicit JPEG compression.
* **`COPY TXT`**: Extracts document/table text straight into system clipboard without writing a file.

---

## 📦 Multi-File Batch Operations

When you select multiple files simultaneously in Finder or Explorer, M.I.R.A. switches to **Batch Mode**:

| Selection Type | Available Radial Actions | Result |
| :--- | :--- | :--- |
| **Multiple PDFs** | `MERGE PDFS`, `BATCH TXT`, `BATCH PNG`, `ZIP ARCHIVE` | Combines all PDFs into `Merged_<timestamp>.pdf` or batch converts each page. |
| **Multiple Images** | `COMBINE PDF`, `BATCH PNG`, `BATCH JPG`, `BATCH WEBP`, `STRIP EXIF`, `ZIP ARCHIVE` | Merges images into a single presentation PDF or batch converts formats. |
| **Mixed / Other Files** | `ZIP ARCHIVE`, `BATCH PDF`, `BATCH TXT` | Compresses all items into a single `.zip` archive or batch converts text documents. |
EOF

# ==============================================================================
# 5. Keyboard-Shortcuts-and-Themes.md
# ==============================================================================
cat << 'EOF' > Keyboard-Shortcuts-and-Themes.md
# 🎨 Themes & Speed Shortcuts

M.I.R.A. was engineered for zero-latency productivity. You can trigger, navigate, and execute complete file operations without touching your mouse.

---

## ⚡ Complete Shortcut Map

| Input | Target Action | Description |
| :--- | :--- | :--- |
| **`Cmd + E`** | macOS Summon | Global summon at mouse position from Finder or Desktop. |
| **`Ctrl + E`** | Windows Summon | Global summon at mouse position from File Explorer. |
| **`1` – `7`** | Quick Trigger | Instantly executes the corresponding radial slice clockwise from 12 o'clock. |
| **`Spacebar`** | Mode Flip | Toggles between **Convert Mode** and **Power Tools Mode**. |
| **`Tab`** | Luxury Themes | Cycles through the 4 visual luxury themes instantly. |
| **`Arrow Keys`** | Radial Navigation | Move focus between radial segments with `Left`/`Up` and `Right`/`Down`. |
| **`Enter`** | Execute Slice | Triggers the currently focused radial slice. |
| **`Alt` + Click / Enter** | Direct Clipboard Mode | Converts and copies text output straight to clipboard, skipping disk creation. |
| **`Escape`** | Dismiss | Closes the radial menu immediately. |

---

## 🎨 4 Luxury Theme Palettes

Tap **`Tab`** while the radial menu is open, or use the System Tray menu to toggle between themes:

### 1. 🪟 Auto Chameleon OS Sync (Default)
* Detects system Dark Mode vs. Light Mode in real-time.
* Extracts native OS accent color (macOS `AppleAccentColor` or Windows Personalization registry) and applies it to rim highlights and glows.

### 2. 👑 Obsidian & Champagne Gold
* Ultra-dark obsidian glass base (`rgba(14, 18, 28, 0.94)`).
* Champagne gold metallic rim accents (`#D4AF37`) with warm ember glow.

### 3. ❄️ Frosted Pearl Alabaster
* Translucent titanium daylight alabaster base (`rgba(252, 254, 255, 0.93)`).
* Cyan sky glass accents (`#0284C7`) optimized for high-contrast daylight workflows.

### 4. 🌲 Midnight Emerald
* Deep botanical forest jade glass base (`rgba(8, 22, 19, 0.94)`).
* Emerald mint neon highlights (`#10B981`) with soft radioactive glow.
EOF

# ==============================================================================
# 6. Architecture-and-Offline-Engine.md
# ==============================================================================
cat << 'EOF' > Architecture-and-Offline-Engine.md
# ⚙️ Architecture & Engine Deep-Dive

M.I.R.A. is built as a lightweight, low-footprint background daemon designed to stay responsive with minimal RAM usage.

---

## 🏗️ Architectural Topology

```text
┌────────────────────────────────────────────────────────┐
│                   Operating System                     │
│  macOS Finder (AppleScript) / Windows File Explorer    │
└───────────────────────────┬────────────────────────────┘
                            │ Cmd+E / Ctrl+E (JNativeHook)
┌───────────────────────────▼────────────────────────────┐
│                    M.I.R.A. Daemon                     │
│  ┌──────────────────────┐    ┌──────────────────────┐  │
│  │ Chameleon Glass UI   │    │ Thinking Orb         │  │
│  │ Robot Backdrop Blur  │    │ 60-FPS Energy Arcs   │  │
│  └──────────┬───────────┘    └──────────┬───────────┘  │
│             │                           │              │
│             └─────────────┬─────────────┘              │
│                           ▼                            │
│           CompletableFuture Async Task Pool            │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│              100% Offline Engine Hub                   │
│  ┌──────────────────────┐    ┌──────────────────────┐  │
│  │ Apache PDFBox 3.x    │    │ Apache POI 5.2.5     │  │
│  │ PDF Merge, Burst, Text│   │ XLSX, DOCX, PPTX     │  │
│  ├──────────────────────┤    ├──────────────────────┤  │
│  │ TwelveMonkeys ImageIO│    │ Native OS Bridges    │  │
│  │ Lossless WebP & Core │    │ macOS sips, textutil │  │
│  └──────────────────────┘    └──────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

---

## 🔬 Core Components

### 1. Chameleon Live Frosted Backdrop
Before displaying the window, M.I.R.A. reads the mouse pointer via `MouseInfo.getPointerInfo()` and utilizes `javafx.scene.robot.Robot` to grab the exact 310×310 px bitmap under the window:
```java
Robot robot = new Robot();
blurredBackdropSnapshot = robot.getScreenCapture(null, new Rectangle2D(targetX, targetY, SIZE, SIZE));
```
The snapshot is mapped to an `ImageView` with a hardware-accelerated `BoxBlur(22, 22, 3)` and clipped to the exact outer and inner radius of the radial ring.

### 2. 60-FPS Thinking Orb Engine
During intensive conversions, an `AnimationTimer` renders dual concentric counter-rotating arcs inside the hub:
* **Outer Ring**: Rotates clockwise at `240 deg/sec` with linear gradient glow.
* **Inner Ring**: Rotates counter-clockwise at `380 deg/sec`.
* The thread safely transfers back to JavaFX UI thread via `Platform.runLater()` upon task completion.

### 3. File Interception Pipeline
* **macOS**: An AppleScript command requests `selection as alias list` from the frontmost Finder instance, returning POSIX paths delimited by `linefeed`.
* **Windows**: A PowerShell script inspects `Shell.Application` COM objects to capture selected items in the active File Explorer window.

### 4. Headless Sidecar Protocol
`Launcher.java` implements argument routing:
```java
if (args.length >= 2) {
    File targetFile = new File(args[0]);
    String action = args[1].toUpperCase();
    new RadialConverterApp().runHeadlessConversion(targetFile, action);
    System.exit(0);
}
```
This architecture allows Tauri v2, Electron, or terminal workflows to invoke M.I.R.A. as an ultra-fast offline microservice without opening JavaFX graphical stages.
EOF

# ==============================================================================
# Commit & Push
# ==============================================================================
git add _Sidebar.md Home.md Installation-and-Setup.md Supported-Formats-and-Tools.md Keyboard-Shortcuts-and-Themes.md Architecture-and-Offline-Engine.md
git commit -m "Initialize M.I.R.A. Multi-Page Wiki Suite with Custom Sidebar"
git push origin master || git push origin main

echo "✅ M.I.R.A. GitHub Wiki successfully published!"
