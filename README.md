# ✦ M.I.R.A. (Minimalist Instant Radial Assistant)

> **100% Offline, OS-Adaptive Radial File Converter & Power-Tool Daemon for macOS and Windows.**  
> Summon at your cursor with `Cmd + E` (macOS) or `Ctrl + E` (Windows) — just like `Cmd + C` / `Cmd + V`.

---

## ⚡ Why M.I.R.A.?

Converting a PDF to DOCX, merging multiple PDFs, turning a CSV into JSON, or compressing a HEIC image shouldn't require uploading private files to slow, ad-filled cloud websites.

**M.I.R.A.** runs silently as a zero-clutter background daemon (no Dock icon, no open windows). Highlight one or more files in **macOS Finder** or **Windows Explorer**, press **`Cmd + E`** (`Ctrl + E` on Windows), and a **Chameleon Frosted Glass** radial menu materializes directly under your cursor.

---

## ✨ Key Features

- **🎨 Chameleon OS Theme & Accent Sync:** Automatically detects macOS Dark/Light mode + System Accent colors (`AppleAccentColor`) and Windows 10/11 DWM Registry Accent colors (`HKCU\Software\Microsoft\Windows\DWM\AccentColor`), blending into any user's custom desktop theme.
- **🌀 Live 60-FPS "Thinking Orb" Feedback:** When converting heavy documents or multi-page PDFs, the center medallion morphs into a dual counter-rotating energy orb so you always know conversion is in progress.
- **📦 Multi-File Batch Mode & PDF Merger:** Highlight multiple PDFs to unlock `MERGE PDFS`, or highlight multiple images to unlock `COMBINE PDF`, `BATCH WEBP/PNG/JPG`, and `ZIP ARCHIVE`.
- **🔒 100% Offline Engine:** Powered locally by Apache PDFBox, Apache POI, TwelveMonkeys ImageIO, and macOS `sips` / `textutil`. Zero internet connection required.
- **⌨️ Raycast-Speed Keyboard Navigation:** Press `1`–`7` to trigger any slice instantly, `Spacebar` to flip between **CONVERT** and **TOOLS**, or `Tab` to cycle through 4 built-in luxury themes (*Auto OS Sync*, *Obsidian & Champagne Gold*, *Frosted Pearl*, *Midnight Emerald*).

---

## 🔄 Supported Offline Conversions & Tools

| Input Format | Convert To | Power Tools (`Spacebar`) |
| :--- | :--- | :--- |
| **Spreadsheets** (`.xlsx`, `.xls`, `.csv`, `.tsv`) | `JSON`, `CSV`, `XLSX`, `HTML`, `PDF`, `TXT` | `SORT A-Z`, `DEDUPE`, `SUMMARY`, `COPY TXT` |
| **PDF Documents** (`.pdf`) | `DOCX`, `TXT`, `HTML`, `JPG`, `PNG` | `MERGE PDFS` (Batch), `SPLIT PAGES`, `ALL PAGES PNG`, `COPY TXT` |
| **Images** (`.png`, `.jpg`, `.webp`, `.heic`, `.tiff`, `.bmp`) | `PNG`, `JPG`, `WEBP`, `HEIC`, `TIFF`, `BMP`, `PDF` | `ADD BG` (Studio Frame), `COMPRESS`, `GRAYSCALE`, `STRIP EXIF` |
| **Word & Text** (`.docx`, `.rtf`, `.txt`, `.md`, `.html`) | `PDF` (Word-Wrapped), `DOCX`, `TXT`, `HTML`, `RTF` | `SUMMARY`, `UPPERCASE`, `CLEAN TXT`, `COPY TXT` |
| **Presentations** (`.pptx`, `.ppt`) | `PDF`, `PNG`, `TXT`, `HTML` | `EXTRACT TXT`, `SLIDE 1 PNG`, `COPY TXT` |
| **JSON Data** (`.json`) | `CSV`, `XLSX`, `HTML`, `PDF`, `TXT` | Automatic tabular key-value flattening |

---

## 🚀 Installation & Usage

### Option 1: Native Installer (Recommended)
1. Download **`MIRA-2.5.0.dmg`** (macOS) or **`MIRA-2.5.0.exe`** (Windows) from the [Releases](../../releases) page.
2. On macOS, grant **Accessibility** permission in `System Settings -> Privacy & Security -> Accessibility` so M.I.R.A. can listen for `Cmd + E`.
3. Click any file in Finder/Explorer and press **`Cmd + E`** (`Ctrl + E` on Windows).

### Option 2: Build from Source
```bash
git clone [https://github.com/YOUR_USERNAME/radial-drop-tool.git](https://github.com/YOUR_USERNAME/radial-drop-tool.git)
cd radial-drop-tool
mvn clean javafx:run

https://github.com/user-attachments/assets/511b649e-e105-43aa-a27e-a9395ad57a7b


https://github.com/user-attachments/assets/30f2b8df-4427-4d1e-a45e-f37fc0e5358e




