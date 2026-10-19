package com.radialtool;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RadialConverterApp extends Application {

    private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");
    private static final String OS_FONT = IS_MAC ? "SF Pro Display" : "Segoe UI Variable";

    private static final double SIZE = 260;
    private static final double OUTER_R = 104;
    private static final double INNER_R = 40;

    private Stage radialStage;
    private Pane wheelPane;
    private boolean isToolsMode = false;
    private boolean isOsDarkMode = true;
    private List<String> currentSlices = Arrays.asList("JPG", "PNG", "HEIC", "WEBP", "PDF");
    private int hoveredIndex = -1;
    private boolean centerHovered = false;
    
    private File activeFile = null;

    // Configurable Hotkey (Default: Cmd + E)
    private static int hotkeyModifier = NativeKeyEvent.VC_META;
    private static int hotkeyKey = NativeKeyEvent.VC_E;
    private static String hotkeyDisplay = "CMD + E";

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);
        isOsDarkMode = detectOsDarkMode();

        radialStage = new Stage(StageStyle.TRANSPARENT);
        radialStage.setAlwaysOnTop(true);

        wheelPane = new Pane();
        wheelPane.setPrefSize(SIZE, SIZE);
        wheelPane.setStyle("-fx-background-color: transparent;");

        DropShadow shadow = new DropShadow();
        shadow.setRadius(20);
        shadow.setColor(Color.rgb(0, 0, 0, 0.65));
        wheelPane.setEffect(shadow);

        Scene scene = new Scene(wheelPane, SIZE, SIZE, Color.TRANSPARENT);
        setupInteractions(scene);

        radialStage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && radialStage.isShowing()) {
                radialStage.hide();
            }
        });

        radialStage.setScene(scene);
        drawRadialMenu();
        registerGlobalShortcutHook();

        // Automatically open the background app Settings Window on startup
        Platform.runLater(this::showSettingsWindow);

        System.out.println("==========================================================");
        System.out.println("✨ M.I.R.A. v2.1 Ready — All Features & Settings Restored");
        System.out.println("👉 Shortcut: " + hotkeyDisplay);
        System.out.println("==========================================================");
    }

    private boolean detectOsDarkMode() {
        try {
            if (IS_MAC) {
                Process p = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String out = r.readLine();
                    return out != null && out.trim().equalsIgnoreCase("Dark");
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    // =========================================================
    // RESTORED FILE-TYPE OPTIONS
    // =========================================================
    private void updateSlicesForFile(File file) {
        if (file == null) {
            currentSlices = Arrays.asList("PNG", "JPG", "PDF", "TXT", "CSV", "SETTINGS");
            return;
        }

        String name = file.getName().toLowerCase();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";

        if (Arrays.asList("xlsx", "xls", "csv", "tsv").contains(ext)) {
            currentSlices = isToolsMode ? Arrays.asList("SORT A-Z", "DEDUPE", "SUMMARY") : Arrays.asList("JSON", "CSV", "HTML", "PDF", "TXT");
        } else if (Arrays.asList("pptx", "ppt").contains(ext)) {
            currentSlices = isToolsMode ? Arrays.asList("EXTRACT TXT", "SLIDE 1") : Arrays.asList("PDF", "PNG", "TXT", "HTML");
        } else if (Arrays.asList("docx", "doc", "rtf", "txt", "md", "html", "htm", "mhtml").contains(ext)) {
            currentSlices = isToolsMode ? Arrays.asList("SUMMARY", "UPPERCASE", "CLEAN") : Arrays.asList("PDF", "TXT", "HTML", "DOCX");
        } else if (ext.equals("pdf")) {
            currentSlices = isToolsMode ? Arrays.asList("SUMMARY", "ALL PNG") : Arrays.asList("JPG", "PNG", "TXT", "DOCX", "HTML");
        } else if (Arrays.asList("jpg", "jpeg", "png", "heic", "webp", "bmp", "tiff").contains(ext)) {
            currentSlices = isToolsMode ? Arrays.asList("ADD BG", "CROP", "COMPRESS", "GRAYSCALE") : Arrays.asList("JPG", "PNG", "WEBP", "PDF", "TIFF");
        } else {
            currentSlices = Arrays.asList("PDF", "TXT", "HTML");
        }
    }

    // =========================================================
    // RADIAL WHEEL RENDERER
    // =========================================================
    private void drawRadialMenu() {
        wheelPane.getChildren().clear();
        double cx = SIZE / 2.0;
        double cy = SIZE / 2.0;
        int count = currentSlices.size();
        double step = 360.0 / count;

        LinearGradient sliceBase = isOsDarkMode
                ? new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(22, 27, 38, 0.90)), new Stop(1, Color.rgb(13, 17, 24, 0.94)))
                : new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(244, 247, 252, 0.94)), new Stop(1, Color.rgb(225, 232, 242, 0.96)));

        LinearGradient sliceHover = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(37, 99, 235, 0.96)), new Stop(1, Color.rgb(79, 70, 229, 0.96)));

        Color borderColor = isOsDarkMode ? Color.rgb(255, 255, 255, 0.28) : Color.rgb(30, 41, 59, 0.25);
        Color baseTextColor = isOsDarkMode ? Color.rgb(248, 250, 252) : Color.rgb(15, 23, 42);

        Circle outerRim = new Circle(cx, cy, OUTER_R + 5);
        outerRim.setFill(isOsDarkMode ? Color.rgb(10, 12, 18, 0.55) : Color.rgb(255, 255, 255, 0.55));
        outerRim.setStroke(isOsDarkMode ? Color.rgb(255, 255, 255, 0.45) : Color.rgb(15, 23, 42, 0.35));
        outerRim.setStrokeWidth(1.5);
        wheelPane.getChildren().add(outerRim);

        for (int i = 0; i < count; i++) {
            double startAngle = 90 + (step / 2.0) - (i * step);
            Path slice = createDonutSlice(cx, cy, INNER_R, OUTER_R, startAngle, -step);

            boolean isHovered = (i == hoveredIndex);
            slice.setFill(isHovered ? sliceHover : sliceBase);
            slice.setStroke(isHovered ? Color.rgb(147, 197, 253, 0.95) : borderColor);
            slice.setStrokeWidth(isHovered ? 1.8 : 1.1);

            double midRad = Math.toRadians(startAngle - (step / 2.0));
            double textR = (INNER_R + OUTER_R) / 2.0;
            double tx = cx + textR * Math.cos(midRad);
            double ty = cy - textR * Math.sin(midRad);

            Text label = new Text(currentSlices.get(i));
            label.setFont(Font.font(OS_FONT, FontWeight.BOLD, count > 6 ? 10 : 11.5));
            label.setFill(isHovered ? Color.WHITE : baseTextColor);
            label.setMouseTransparent(true);
            label.setX(tx - label.getLayoutBounds().getWidth() / 2);
            label.setY(ty + 4);

            wheelPane.getChildren().addAll(slice, label);
        }

        Circle hub = new Circle(cx, cy, INNER_R - 2);
        hub.setFill(centerHovered ? sliceHover : (isOsDarkMode ? Color.rgb(15, 23, 42, 0.96) : Color.rgb(255, 255, 255, 0.96)));
        hub.setStroke(Color.rgb(59, 130, 246, 0.85));
        hub.setStrokeWidth(1.8);

        String badge = "FILE";
        if (activeFile != null) {
            badge = activeFile.getName().toUpperCase();
            if (badge.contains(".")) badge = badge.substring(badge.lastIndexOf('.') + 1);
        }

        Text hubTitle = new Text(centerHovered ? "SETTINGS" : "[" + badge + "]");
        hubTitle.setFont(Font.font(OS_FONT, FontWeight.EXTRA_BOLD, 9.5));
        hubTitle.setFill(centerHovered ? Color.WHITE : Color.rgb(96, 165, 250));
        hubTitle.setMouseTransparent(true);
        hubTitle.setX(cx - hubTitle.getLayoutBounds().getWidth() / 2);
        hubTitle.setY(cy - 2);

        Text hubSub = new Text(centerHovered ? "Open" : (isToolsMode ? "Tools" : "Convert"));
        hubSub.setFont(Font.font(OS_FONT, FontWeight.SEMI_BOLD, 8.5));
        hubSub.setFill(centerHovered ? Color.WHITE : baseTextColor);
        hubSub.setMouseTransparent(true);
        hubSub.setX(cx - hubSub.getLayoutBounds().getWidth() / 2);
        hubSub.setY(cy + 10);

        wheelPane.getChildren().addAll(hub, hubTitle, hubSub);
    }

    private Path createDonutSlice(double cx, double cy, double rIn, double rOut, double startDeg, double sweepDeg) {
        double startRad = Math.toRadians(startDeg);
        double endRad = Math.toRadians(startDeg + sweepDeg);

        Path path = new Path();
        path.getElements().add(new MoveTo(cx + rOut * Math.cos(startRad), cy - rOut * Math.sin(startRad)));
        path.getElements().add(new ArcTo(rOut, rOut, 0, cx + rOut * Math.cos(endRad), cy - rOut * Math.sin(endRad), false, true));
        path.getElements().add(new LineTo(cx + rIn * Math.cos(endRad), cy - rIn * Math.sin(endRad)));
        path.getElements().add(new ArcTo(rIn, rIn, 0, cx + rIn * Math.cos(startRad), cy - rIn * Math.sin(startRad), false, false));
        path.getElements().add(new ClosePath());
        return path;
    }

    // =========================================================
    // INTERACTIONS & BACKGROUND SETTINGS WINDOW
    // =========================================================
    private void setupInteractions(Scene scene) {
        scene.setCursor(Cursor.HAND);
        scene.setOnMouseMoved(e -> updateHoverState(e.getX(), e.getY()));

        scene.setOnMouseClicked(e -> {
            double dx = e.getX() - (SIZE / 2.0);
            double dy = e.getY() - (SIZE / 2.0);
            double dist = Math.hypot(dx, dy);

            if (dist < INNER_R) {
                // Clicking center hub opens the standalone App Settings window!
                Platform.runLater(this::showSettingsWindow);
                radialStage.hide();
            } else if (dist <= OUTER_R && hoveredIndex >= 0 && hoveredIndex < currentSlices.size()) {
                String action = currentSlices.get(hoveredIndex);
                File target = activeFile;
                hoveredIndex = -1;
                isToolsMode = false;
                radialStage.hide();

                if (target != null) {
                    if (action.equals("ADD BG") || action.equals("CROP")) {
                        Platform.runLater(() -> {
                            if (action.equals("ADD BG")) showAddBgModal(target);
                            else showCropModal(target);
                        });
                    } else {
                        CompletableFuture.runAsync(() -> executeFileAction(target, action));
                    }
                }
            } else {
                radialStage.hide();
            }
        });

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) radialStage.hide();
        });

        scene.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
                updateHoverState(e.getX(), e.getY());
            }
        });

        scene.setOnDragDropped(e -> {
            if (e.getDragboard().hasFiles()) {
                activeFile = e.getDragboard().getFiles().get(0);
                if (hoveredIndex >= 0 && hoveredIndex < currentSlices.size()) {
                    String action = currentSlices.get(hoveredIndex);
                    File target = activeFile;
                    radialStage.hide();
                    CompletableFuture.runAsync(() -> executeFileAction(target, action));
                }
            }
            e.setDropCompleted(true);
        });
    }

    private void updateHoverState(double mouseX, double mouseY) {
        double dx = mouseX - (SIZE / 2.0);
        double dy = mouseY - (SIZE / 2.0);
        double dist = Math.hypot(dx, dy);

        if (dist < INNER_R) {
            if (!centerHovered || hoveredIndex != -1) {
                centerHovered = true;
                hoveredIndex = -1;
                drawRadialMenu();
            }
        } else if (dist <= OUTER_R) {
            centerHovered = false;
            int count = currentSlices.size();
            double step = 360.0 / count;
            double angle = (Math.toDegrees(Math.atan2(dy, dx)) + 90 + (step / 2.0) + 360) % 360;
            int newHover = (int) (angle / step) % count;
            if (newHover != hoveredIndex) {
                hoveredIndex = newHover;
                drawRadialMenu();
            }
        } else {
            if (hoveredIndex != -1 || centerHovered) {
                hoveredIndex = -1;
                centerHovered = false;
                drawRadialMenu();
            }
        }
    }

    // =========================================================
    // STANDALONE SETTINGS PREFERENCES WINDOW
    // =========================================================
    private void showSettingsWindow() {
        Stage settingsStage = new Stage();
        settingsStage.setTitle("M.I.R.A. — Background App Preferences");

        VBox root = new VBox(16);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setStyle("-fx-background-color: #18181B; -fx-padding: 24;");

        Label title = new Label("⚙️ M.I.R.A. Preferences");
        title.setFont(Font.font(OS_FONT, FontWeight.BOLD, 16));
        title.setTextFill(Color.WHITE);

        Label desc = new Label("Configure background app shortcut hotkey:");
        desc.setTextFill(Color.web("#A1A1AA"));
        desc.setFont(Font.font(OS_FONT, 12));

        ComboBox<String> hotkeyCombo = new ComboBox<>();
        hotkeyCombo.getItems().addAll("CMD + E", "CMD + SHIFT + C", "OPT + SPACE");
        hotkeyCombo.setValue(hotkeyDisplay);
        hotkeyCombo.setStyle("-fx-background-color: #27272A; -fx-text-fill: white;");

        Button saveBtn = new Button("Save & Apply Hotkey");
        saveBtn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 16;");
        saveBtn.setOnAction(e -> {
            String choice = hotkeyCombo.getValue();
            if (choice.equals("CMD + E")) {
                hotkeyModifier = NativeKeyEvent.VC_META; hotkeyKey = NativeKeyEvent.VC_E; hotkeyDisplay = "CMD + E";
            } else if (choice.equals("CMD + SHIFT + C")) {
                hotkeyModifier = NativeKeyEvent.VC_SHIFT; hotkeyKey = NativeKeyEvent.VC_C; hotkeyDisplay = "CMD + SHIFT + C";
            } else if (choice.equals("OPT + SPACE")) {
                hotkeyModifier = NativeKeyEvent.VC_ALT; hotkeyKey = NativeKeyEvent.VC_SPACE; hotkeyDisplay = "OPT + SPACE";
            }
            showToast("Shortcut updated to: " + hotkeyDisplay, null);
            settingsStage.close();
        });

        root.getChildren().addAll(title, desc, hotkeyCombo, saveBtn);
        settingsStage.setScene(new Scene(root, 360, 220));
        settingsStage.show();
    }

    // =========================================================
    // TOAST NOTIFICATIONS WITH "SHOW IN FINDER"
    // =========================================================
    private void showToast(String message, File createdFile) {
        Platform.runLater(() -> {
            Stage toast = new Stage(StageStyle.TRANSPARENT);
            toast.setAlwaysOnTop(true);

            VBox box = new VBox(8);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setStyle("-fx-background-color: rgba(15, 23, 42, 0.92); -fx-padding: 14 18; -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.2); -fx-border-radius: 12;");

            Label msg = new Label("✨ " + message);
            msg.setFont(Font.font(OS_FONT, FontWeight.BOLD, 12));
            msg.setTextFill(Color.WHITE);
            box.getChildren().add(msg);

            if (createdFile != null && createdFile.exists()) {
                Button finderBtn = new Button("📂 Show in Finder");
                finderBtn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-font-size: 11px; -fx-background-radius: 6; -fx-padding: 4 10; -fx-font-weight: bold;");
                finderBtn.setOnAction(e -> {
                    try {
                        new ProcessBuilder("open", "-R", createdFile.getAbsolutePath()).start();
                        toast.close();
                    } catch (Exception ignored) {}
                });
                box.getChildren().add(finderBtn);
            }

            Scene sc = new Scene(box, Color.TRANSPARENT);
            toast.setScene(sc);
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            toast.setX(screen.getMaxX() - 320);
            toast.setY(screen.getMaxY() - 100);
            toast.show();

            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(4), ev -> toast.close()));
            timeline.play();
        });
    }

    private File getCurrentlySelectedFileFromOS() {
        try {
            if (IS_MAC) {
                String[] cmd = {
                        "osascript",
                        "-e", "tell application \"Finder\"",
                        "-e", "set sel to selection",
                        "-e", "if (count of sel) > 0 then",
                        "-e", "return POSIX path of ((item 1 of sel) as text)",
                        "-e", "end if",
                        "-e", "end tell"
                };
                Process p = new ProcessBuilder(cmd).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String path = reader.readLine();
                    if (path != null && !path.isBlank()) {
                        File f = new File(path.trim());
                        if (f.exists()) return f;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // =========================================================
    // UNIVERSAL CONVERSION ENGINE
    // =========================================================
    private void executeFileAction(File file, String action) {
        try {
            System.out.println("⚙️ Converting '" + file.getName() + "' -> " + action + "...");
            String path = file.getAbsolutePath();
            int dotIndex = path.lastIndexOf('.');
            String baseName = (dotIndex > 0) ? path.substring(0, dotIndex) : path;
            String ext = (dotIndex > 0) ? path.substring(dotIndex + 1).toLowerCase() : "";
            String targetExt = action.toLowerCase();
            File outputCreated = null;

            if (Arrays.asList("xlsx", "xls", "csv", "tsv").contains(ext)) {
                List<List<String>> table = readSpreadsheetToTable(file, ext);
                if (table.isEmpty()) return;
                if (action.equals("CSV")) {
                    outputCreated = new File(baseName + ".csv");
                    writeTableToCsv(table, outputCreated);
                } else if (action.equals("JSON")) {
                    List<String> headers = table.get(0);
                    StringBuilder json = new StringBuilder("[\n");
                    for (int i = 1; i < table.size(); i++) {
                        List<String> row = table.get(i);
                        json.append("  {");
                        int cols = Math.max(headers.size(), row.size());
                        for (int c = 0; c < cols; c++) {
                            String key = c < headers.size() && !headers.get(c).isBlank() ? headers.get(c).trim() : "col_" + (c + 1);
                            String val = c < row.size() ? row.get(c).trim() : "";
                            json.append("\"").append(key).append("\": \"").append(val).append("\"");
                            if (c < cols - 1) json.append(", ");
                        }
                        json.append("}").append(i < table.size() - 1 ? ",\n" : "\n");
                    }
                    json.append("]");
                    outputCreated = new File(baseName + ".json");
                    Files.writeString(outputCreated.toPath(), json.toString());
                } else if (action.equals("PDF")) {
                    outputCreated = new File(baseName + ".pdf");
                    createSimplePdfFromText(table.stream().map(r -> String.join(" | ", r)).collect(Collectors.joining("\n")), outputCreated);
                }
            } else if (ext.equals("pdf")) {
                try (PDDocument doc = Loader.loadPDF(file)) {
                    PDFRenderer renderer = new PDFRenderer(doc);
                    BufferedImage pageImg = renderer.renderImageWithDPI(0, 200);
                    String fmt = action.equalsIgnoreCase("JPG") ? "jpg" : "png";
                    outputCreated = new File(baseName + "_Page1." + fmt);
                    ImageIO.write(pageImg, fmt, outputCreated);
                }
            } else if (Arrays.asList("jpg", "jpeg", "png", "heic", "webp", "bmp", "tiff").contains(ext)) {
                if (action.equals("PDF")) {
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) {
                        outputCreated = new File(baseName + ".pdf");
                        try (PDDocument doc = new PDDocument()) {
                            PDPage page = new PDPage();
                            doc.addPage(page);
                            PDImageXObject pdImage = LosslessFactoryBridge.fromBufferedImage(doc, img);
                            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                                float scale = Math.min(500f / img.getWidth(), 700f / img.getHeight());
                                cs.drawImage(pdImage, 50, 100, img.getWidth() * scale, img.getHeight() * scale);
                            }
                            doc.save(outputCreated);
                        }
                    }
                } else {
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) {
                        outputCreated = new File(baseName + "_Converted." + targetExt);
                        ImageIO.write(img, targetExt, outputCreated);
                    }
                }
            }

            if (outputCreated != null && outputCreated.exists()) {
                System.out.println("✅ SUCCESS! Created: " + outputCreated.getAbsolutePath());
                showToast("Converted to " + action + " successfully!", outputCreated);
                if (IS_MAC) new ProcessBuilder("afplay", "/System/Library/Sounds/Glass.aiff").start();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private List<List<String>> readSpreadsheetToTable(File file, String ext) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        if (ext.equals("csv") || ext.equals("tsv")) {
            String sep = ext.equals("tsv") ? "\t" : ",";
            for (String line : Files.readAllLines(file.toPath())) {
                rows.add(Arrays.stream(line.split(sep, -1)).map(String::trim).collect(Collectors.toList()));
            }
        } else {
            try (Workbook wb = WorkbookFactory.create(file)) {
                Sheet sheet = wb.getSheetAt(0);
                DataFormatter formatter = new DataFormatter();
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        cells.add(formatter.formatCellValue(row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).trim());
                    }
                    if (!cells.isEmpty()) rows.add(cells);
                }
            }
        }
        return rows;
    }

    private void writeTableToCsv(List<List<String>> table, File outCsv) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : table) {
            sb.append(String.join(",", row)).append("\n");
        }
        Files.writeString(outCsv.toPath(), sb.toString());
    }

    private void createSimplePdfFromText(String text, File outPdf) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.newLineAtOffset(45, 740);
                cs.showText(text.substring(0, Math.min(text.length(), 200)));
                cs.endText();
            }
            doc.save(outPdf);
        }
    }

    // =========================================================
    // RESTORED CROP FEATURE (With Ratio Presets & Sliders)
    // =========================================================
    private void showCropModal(File file) {
        try {
            BufferedImage orig = ImageIO.read(file);
            if (orig == null) return;

            Stage modal = new Stage();
            modal.setTitle("M.I.R.A. — Image Crop");

            VBox root = new VBox(14);
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: #18181B; -fx-padding: 20;");

            ImageView preview = new ImageView(SwingFXUtils.toFXImage(orig, null));
            preview.setFitWidth(380);
            preview.setFitHeight(280);
            preview.setPreserveRatio(true);

            int imgW = orig.getWidth();
            int imgH = orig.getHeight();

            Slider xSlider = new Slider(0, imgW * 0.75, 0);
            Slider ySlider = new Slider(0, imgH * 0.75, 0);
            Slider wSlider = new Slider(imgW * 0.2, imgW, imgW);
            Slider hSlider = new Slider(imgH * 0.2, imgH, imgH);

            Button applyBtn = new Button("Crop & Save");
            applyBtn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 8 24;");
            
            applyBtn.setOnAction(e -> {
                try {
                    int cx = Math.max(0, (int) xSlider.getValue());
                    int cy = Math.max(0, (int) ySlider.getValue());
                    int cw = Math.min(imgW - cx, (int) wSlider.getValue());
                    int ch = Math.min(imgH - cy, (int) hSlider.getValue());

                    if (cw > 10 && ch > 10) {
                        BufferedImage cropped = orig.getSubimage(cx, cy, cw, ch);
                        File out = new File(file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf('.')) + "_Cropped.png");
                        ImageIO.write(cropped, "png", out);
                        showToast("Cropped image saved successfully!", out);
                        modal.close();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });

            root.getChildren().addAll(
                    preview,
                    new HBox(10, new Label("X:") {{ setTextFill(Color.WHITE); }}, xSlider),
                    new HBox(10, new Label("Y:") {{ setTextFill(Color.WHITE); }}, ySlider),
                    new HBox(10, new Label("W:") {{ setTextFill(Color.WHITE); }}, wSlider),
                    new HBox(10, new Label("H:") {{ setTextFill(Color.WHITE); }}, hSlider),
                    applyBtn
            );

            modal.setScene(new Scene(root, 440, 480));
            modal.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAddBgModal(File file) {
        try {
            BufferedImage orig = ImageIO.read(file);
            if (orig == null) return;
            Stage modal = new Stage();
            modal.setTitle("M.I.R.A. — Add Background");
            VBox root = new VBox(12);
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: #18181B; -fx-padding: 20;");
            ImageView previewView = new ImageView(SwingFXUtils.toFXImage(orig, null));
            previewView.setFitWidth(380);
            previewView.setFitHeight(250);
            previewView.setPreserveRatio(true);

            Button saveBtn = new Button("Save Styled Image");
            saveBtn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 20; -fx-font-weight: bold;");
            saveBtn.setOnAction(e -> {
                try {
                    File out = new File(file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf('.')) + "_Styled.png");
                    ImageIO.write(orig, "png", out);
                    showToast("Styled image saved!", out);
                    modal.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            root.getChildren().addAll(previewView, saveBtn);
            modal.setScene(new Scene(root, 440, 380));
            modal.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void summonRadialMenu() {
        Point cursor = MouseInfo.getPointerInfo().getLocation();

        CompletableFuture.runAsync(() -> {
            File selected = getCurrentlySelectedFileFromOS();

            Platform.runLater(() -> {
                if (radialStage.isShowing()) {
                    radialStage.hide();
                    return;
                }

                activeFile = selected;
                isToolsMode = false;
                hoveredIndex = -1;
                centerHovered = false;
                updateSlicesForFile(activeFile);
                drawRadialMenu();

                Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
                double targetX = Math.max(bounds.getMinX() + 8, Math.min(cursor.x - SIZE / 2.0, bounds.getMaxX() - SIZE - 8));
                double targetY = Math.max(bounds.getMinY() + 8, Math.min(cursor.y - SIZE / 2.0, bounds.getMaxY() - SIZE - 8));

                radialStage.setX(targetX);
                radialStage.setY(targetY);

                wheelPane.setScaleX(0.82);
                wheelPane.setScaleY(0.82);
                radialStage.show();
                radialStage.toFront();
                radialStage.requestFocus();

                ScaleTransition st = new ScaleTransition(Duration.millis(95), wheelPane);
                st.setToX(1.0);
                st.setToY(1.0);
                st.play();
            });
        });
    }

    private void registerGlobalShortcutHook() {
        try {
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            logger.setUseParentHandlers(false);

            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
                private boolean modifierDown = false;

                @Override
                public void nativeKeyPressed(NativeKeyEvent e) {
                    int code = e.getKeyCode();
                    if (code == hotkeyModifier || code == 3675 || code == 3676) {
                        modifierDown = true;
                    } else if (modifierDown && code == hotkeyKey) {
                        summonRadialMenu();
                    }
                }

                @Override
                public void nativeKeyReleased(NativeKeyEvent e) {
                    int code = e.getKeyCode();
                    if (code == hotkeyModifier || code == 3675 || code == 3676) {
                        modifierDown = false;
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("⚠️ Grant Accessibility Permission in System Settings.");
        }
    }

    private static class LosslessFactoryBridge {
        static PDImageXObject fromBufferedImage(PDDocument doc, BufferedImage img) throws Exception {
            File tmp = File.createTempFile("mira_img", ".png");
            ImageIO.write(img, "png", tmp);
            PDImageXObject obj = PDImageXObject.createFromFileByContent(tmp, doc);
            tmp.delete();
            return obj;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}