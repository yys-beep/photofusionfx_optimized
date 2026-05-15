# PhotoFusion FX

PhotoFusion FX is a JavaFX course-project implementation for photo repository management, non-destructive layer-based image editing, object extraction, reusable asset management, mosaic generation, video montage rendering, and media sharing.

## Major Features

- Import images individually or from a folder into a managed local library.
- Browse the repository with thumbnails, search, favourites, metadata annotation, and a heart marker for annotated images.
- Edit images non-destructively: brightness, contrast, saturation, grayscale, scenario tint/color combination, border, scale, rotation, translation, and auto enhancement are applied to previews/exports only.
- Add separate image/text/shape layers over images. Text layers are draggable, movable, restylable, and support font family, font size, fill color, outline/stroke color, stroke width, and opacity.
- Paste clipboard image/files as layers and add reusable extracted objects from the asset library.
- Export a composite image, save it into the library, or export a resized copy without modifying the original source file.
- Select objects using magic-wand color similarity, brush add/erase, or lasso selection mask.
- Extract selected objects as transparent PNG, outline them, tint/enhance them, copy them as actual PNG files plus image clipboard data, save them to the photo library, or store them in the asset library.
- Asset Library tab stores reusable extracted objects and imported media under `~/.photofusionfx/asset-library/`.
- Generate mosaics using average-color tile matching.
- Create real MP4 video montages from selected photos.
- Add draggable video text/image/shape layers. These layers are rendered into every output video frame using a 1280×720 reference overlay coordinate system.
- Share selected images/videos by email as real SMTP/MIME attachments.
- WhatsApp helper copies the actual selected `File` object to the system clipboard and opens WhatsApp with a caption. Paste in WhatsApp to attach the real file instead of a path string.

## Tech Stack

- Java 21
- JavaFX
- SQLite (local metadata persistence)
- JCodec (video encoding)
- Jakarta Mail / Eclipse Angus (SMTP email)

## Project Structure

```text
src/main/java/com/photofusionfx/
├── MainApp.java
├── AppContext.java
├── model/
│   ├── AssetItem.java
│   ├── EditParameters.java
│   ├── LayerType.java
│   ├── ProjectLayer.java
│   └── ...
├── service/
│   ├── AssetLibraryService.java
│   ├── LayerRenderService.java
│   ├── ObjectExtractionService.java
│   └── ...
├── ui/
│   ├── AssetLibraryPane.java
│   ├── EditorPane.java
│   ├── ExtractorPane.java
│   ├── VideoPane.java
│   └── ...
└── util/
```

## Run Instructions

### 1) Prerequisites

- JDK 21 installed
- Maven installed and available in PATH

### 2) Start the application

```bash
mvn clean javafx:run
```

### 3) Build a jar/package

```bash
mvn clean package
```

## Data Locations

The application stores data under:

```text
~/.photofusionfx/
```

Important subfolders/files:

```text
photofusionfx.db                 SQLite metadata database
library/                         managed image repository
exports/                         exported images and videos
asset-library/                   reusable objects/assets
asset-library/extracted-objects/ extracted transparent PNG objects
clipboard-staging/               temporary staged clipboard PNG files
projects/                        reserved project workspace folder
mail.properties                  saved SMTP settings
```

## Notes on Non-Destructive Editing

The original image/video source files are not overwritten. Adjustments, text, shapes, extracted objects, and video graphics are represented as separate layers or generated outputs. A file changes only when the user explicitly exports, saves to library, or saves to asset library.

## WhatsApp Sharing Limitation

Desktop Java cannot silently attach a local file to WhatsApp Web through a stable public API. This project avoids the previous path-string behavior by placing the actual selected file object on the OS clipboard and opening WhatsApp with a caption. The user then pastes in the WhatsApp chat to attach the real file.
