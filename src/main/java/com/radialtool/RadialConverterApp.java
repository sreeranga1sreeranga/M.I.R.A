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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D.Double;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RadialConverterApp extends Application {

    private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");
    private static final String OS_FONT = IS_MAC ? "SF Pro Display" : "Segoe UI Variable";

    // Minimalist Luxury Proportions
    private static final double SIZE = 290;
    private static final double OUTER_R = 114;
    private static final double INNER_R = 46;
    private static final double GAP_DEGREES = 3.2; // Architectural gap between slices

    private Stage radialStage;
    private Pane wheelPane;
    private boolean isToolsMode = false;
    private List<String> currentSlices = Arrays.asList("PNG", "JPG", "WEBP", "PDF");
    private int hoveredIndex = -1;
    private boolean centerHovered = false;
    private File activeFile = null;

    // 3 Luxury Market Themes: 0 = Obsidian & Champagne Gold, 1 = Titanium Frost, 2 = Midnight Emerald
    private int luxuryThemeIndex = 0;
    private static final String[] THEME_NAMES = {"OBSIDIAN GOLD", "TITANIUM FROST", "MIDNIGHT EMERALD"};

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);

        radialStage = new Stage(StageStyle.TRANSPARENT);
        radialStage.setAlwaysOnTop(true);

        wheelPane = new Pane();
        wheelPane.setPrefSize(SIZE, SIZE);
        wheelPane.setStyle("-fx-background-color: transparent;");

        DropShadow ambientShadow = new DropShadow();
        ambientShadow.setRadius(28);
        ambientShadow.setColor(Color.rgb(0, 0, 0, 0.72));
        wheelPane.setEffect(ambientShadow);

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

        System.out.println("==========================================================");
        System.out.println("✨ M.I.R.A. Luxury Edition — 100% Offline Conversion Engine");
        System.out.println("👉 Select ANY file and press COMMAND + E (Mac) / SHIFT + E (Win)");
        System.out.println("🎨 Press TAB while the wheel is open to switch Luxury Themes!");
        System.out.println("==========================================================");
    }

    // =========================================================
    // 1. STRICT OFFLINE CONVERSION MAP (Crop Removed!)
    // =========================================================
    private void updateSlicesForFile(File file) {
        if (file == null) {
            currentSlices = Arrays.asList("PICK FILE");
            return;
        }

        String name = file.getName().toLowerCase();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";

        // 1. SPREADSHEETS (.xlsx, .xls, .csv, .tsv)
        if (Arrays.asList("xlsx", "xls", "csv", "tsv").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("SORT A-Z", "DEDUPE", "SUMMARY");
            } else {
                List<String> opts = Arrays.asList("JSON", "CSV", "XLSX", "HTML", "PDF", "TXT");
                String filter = ext.equals("xls") ? "XLSX" : ext.toUpperCase();
                currentSlices = opts.stream().filter(s -> !s.equalsIgnoreCase(filter)).toList();
            }
            return;
        }

        // 2. IMAGES (.jpg, .jpeg, .png, .heic, .webp, .bmp, .tiff) -> NO CROP!
        if (Arrays.asList("jpg", "jpeg", "png", "heic", "webp", "bmp", "tiff").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("ADD BG", "COMPRESS", "GRAYSCALE");
            } else {
                List<String> allImg = IS_MAC
                        ? Arrays.asList("PNG", "JPG", "WEBP", "HEIC", "TIFF", "BMP", "PDF")
                        : Arrays.asList("PNG", "JPG", "WEBP", "TIFF", "BMP", "PDF");
                String filterExt = ext.equals("jpeg") ? "JPG" : ext.toUpperCase();
                currentSlices = allImg.stream().filter(s -> !s.equals(filterExt)).toList();
            }
            return;
        }

        // 3. PDF DOCUMENTS (.pdf)
        if (ext.equals("pdf")) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("SUMMARY", "ALL PAGES PNG");
            } else {
                currentSlices = Arrays.asList("DOCX", "TXT", "HTML", "JPG", "PNG");
            }
            return;
        }

        // 4. WORD & TEXT DOCUMENTS (.docx, .doc, .rtf, .txt, .md)
        if (Arrays.asList("docx", "doc", "rtf", "txt", "md").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("SUMMARY", "UPPERCASE", "CLEAN TXT");
            } else {
                List<String> opts = IS_MAC
                        ? Arrays.asList("PDF", "DOCX", "TXT", "HTML", "RTF")
                        : Arrays.asList("PDF", "DOCX", "TXT", "HTML");
                currentSlices = opts.stream().filter(s -> !s.equalsIgnoreCase(ext)).toList();
            }
            return;
        }

        // 5. POWERPOINT PRESENTATIONS (.pptx, .ppt)
        if (Arrays.asList("pptx", "ppt").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("EXTRACT TXT", "SLIDE 1 PNG");
            } else {
                currentSlices = Arrays.asList("PDF", "PNG", "TXT", "HTML");
            }
            return;
        }

        // 6. JSON & WEB FILES (.json, .html, .htm, .mhtml)
        if (ext.equals("json")) {
            currentSlices = Arrays.asList("CSV", "XLSX", "HTML", "PDF", "TXT");
            return;
        }
        if (Arrays.asList("html", "htm", "mhtml").contains(ext)) {
            currentSlices = Arrays.asList("PDF", "DOCX", "TXT");
            return;
        }

        currentSlices = Arrays.asList("PDF", "TXT");
    }

    // =========================================================
    // 2. MINIMALIST LUXURY RADIAL RENDERER (Separated Floating Arcs)
    // =========================================================
    private void drawRadialMenu() {
        wheelPane.getChildren().clear();
        double cx = SIZE / 2.0;
        double cy = SIZE / 2.0;
        int count = currentSlices.size();
        double step = 360.0 / count;

        // Luxury Palette Selection
        LinearGradient baseGrad;
        LinearGradient hoverGrad;
        Color strokeBase;
        Color strokeHover;
        Color textBase;
        Color textHover;
        Color accentColor;

        if (luxuryThemeIndex == 0) {
            // Theme 0: OBSIDIAN & CHAMPAGNE GOLD (Luxury Watch / Bang & Olufsen Vibe)
            baseGrad = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(18, 19, 24, 0.94)),
                    new Stop(1, Color.rgb(10, 11, 14, 0.97)));
            hoverGrad = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(212, 175, 55, 0.92)),
                    new Stop(1, Color.rgb(170, 130, 30, 0.95)));
            strokeBase = Color.rgb(212, 175, 55, 0.25);
            strokeHover = Color.rgb(255, 235, 160, 0.95);
            textBase = Color.rgb(235, 232, 225);
            textHover = Color.rgb(12, 12, 14);
            accentColor = Color.rgb(225, 190, 90);
        } else if (luxuryThemeIndex == 1) {
            // Theme 1: TITANIUM FROST (Minimalist Apple Vision / Teenage Engineering Vibe)
            baseGrad = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(248, 250, 252, 0.94)),
                    new Stop(1, Color.rgb(226, 232, 240, 0.97)));
            hoverGrad = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(15, 23, 42, 0.96)),
                    new Stop(1, Color.rgb(30, 41, 59, 0.98)));
            strokeBase = Color.rgb(148, 163, 184, 0.45);
            strokeHover = Color.rgb(56, 189, 248, 0.90);
            textBase = Color.rgb(15, 23, 42);
            textHover = Color.rgb(248, 250, 252);
            accentColor = Color.rgb(2, 132, 199);
        } else {
            // Theme 2: MIDNIGHT EMERALD & PLATINUM
            baseGrad = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(11, 24, 21, 0.94)),
                    new Stop(1, Color.rgb(6, 15, 13, 0.97)));
            hoverGrad = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(16, 185, 129, 0.92)),
                    new Stop(1, Color.rgb(5, 150, 105, 0.96)));
            strokeBase = Color.rgb(167, 243, 208, 0.22);
            strokeHover = Color.rgb(209, 250, 229, 0.95);
            textBase = Color.rgb(236, 253, 245);
            textHover = Color.rgb(6, 20, 16);
            accentColor = Color.rgb(52, 211, 153);
        }

        // Subtle Outer Halo Ring
        Circle outerHalo = new Circle(cx, cy, OUTER_R + 7);
        outerHalo.setFill(Color.TRANSPARENT);
        outerHalo.setStroke(strokeBase);
        outerHalo.setStrokeWidth(0.8);
        wheelPane.getChildren().add(outerHalo);

        double actualGap = count > 1 ? GAP_DEGREES : 0;

        for (int i = 0; i < count; i++) {
            boolean isHovered = (i == hoveredIndex);
            double rOut = isHovered ? OUTER_R + 3 : OUTER_R; // Subtle luxury expansion on hover
            double startAngle = 90 + (step / 2.0) - (i * step) - (actualGap / 2.0);
            double sweepAngle = -(step - actualGap);

            Path slice = createDonutSlice(cx, cy, INNER_R, rOut, startAngle, sweepAngle);
            slice.setFill(isHovered ? hoverGrad : baseGrad);
            slice.setStroke(isHovered ? strokeHover : strokeBase);
            slice.setStrokeWidth(isHovered ? 1.4 : 0.9);
            slice.setStrokeLineJoin(StrokeLineJoin.ROUND);

            double midRad = Math.toRadians(90 - (i * step));
            double textR = (INNER_R + rOut) / 2.0;
            double tx = cx + textR * Math.cos(midRad);
            double ty = cy - textR * Math.sin(midRad);

            Text label = new Text(currentSlices.get(i));
            label.setFont(Font.font(OS_FONT, FontWeight.BOLD, count > 6 ? 10.0 : 11.0));
            label.setFill(isHovered ? textHover : textBase);
            label.setMouseTransparent(true);
            label.setX(tx - label.getLayoutBounds().getWidth() / 2);
            label.setY(ty + 4);

            wheelPane.getChildren().addAll(slice, label);
        }

        // Minimalist Luxury Center Medallion
        Circle hub = new Circle(cx, cy, INNER_R - 4);
        hub.setFill(centerHovered ? hoverGrad : baseGrad);
        hub.setStroke(centerHovered ? strokeHover : accentColor);
        hub.setStrokeWidth(1.3);

        String extBadge = "SELECT";
        if (activeFile != null) {
            String n = activeFile.getName();
            extBadge = n.contains(".") ? n.substring(n.lastIndexOf('.') + 1).toUpperCase() : "FILE";
        }

        String topLine = centerHovered ? (isToolsMode ? "CONVERT" : "TOOLS") : extBadge;
        String botLine = activeFile == null ? "Browse" : (isToolsMode ? "MODE" : "OPTIONS");

        Text hubTitle = new Text(topLine);
        hubTitle.setFont(Font.font(OS_FONT, FontWeight.EXTRA_BOLD, 10.5));
        hubTitle.setFill(centerHovered ? textHover : accentColor);
        hubTitle.setMouseTransparent(true);
        hubTitle.setX(cx - hubTitle.getLayoutBounds().getWidth() / 2);
        hubTitle.setY(cy - 1);

        Text hubSub = new Text(botLine);
        hubSub.setFont(Font.font(OS_FONT, FontWeight.MEDIUM, 7.5));
        hubSub.setFill(centerHovered ? textHover : textBase.deriveColor(0, 1, 1, 0.65));
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
    // 3. INTERACTIONS (Click, Hover, Tab to Cycle Luxury Themes)
    // =========================================================
    private void setupInteractions(Scene scene) {
        scene.setCursor(Cursor.HAND);
        scene.setOnMouseMoved(e -> updateHoverState(e.getX(), e.getY()));

        scene.setOnMouseClicked(e -> {
            double dx = e.getX() - (SIZE / 2.0);
            double dy = e.getY() - (SIZE / 2.0);
            double dist = Math.hypot(dx, dy);

            if (dist < INNER_R) {
                if (activeFile == null) {
                    FileChooser fc = new FileChooser();
                    fc.setTitle("Select File for M.I.R.A.");
                    File chosen = fc.showOpenDialog(radialStage);
                    if (chosen != null) {
                        activeFile = chosen;
                        updateSlicesForFile(activeFile);
                        drawRadialMenu();
                    }
                } else {
                    isToolsMode = !isToolsMode;
                    updateSlicesForFile(activeFile);
                    drawRadialMenu();
                }
            } else if (dist <= OUTER_R + 4 && hoveredIndex >= 0 && hoveredIndex < currentSlices.size()) {
                String action = currentSlices.get(hoveredIndex);
                File target = activeFile;
                hoveredIndex = -1;
                isToolsMode = false;
                radialStage.hide();

                if (target != null) {
                    if (action.equals("ADD BG")) {
                        Platform.runLater(() -> showAddBgModal(target));
                    } else {
                        CompletableFuture.runAsync(() -> executeFileAction(target, action));
                    }
                }
            } else {
                radialStage.hide();
            }
        });

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                radialStage.hide();
            } else if (e.getCode() == KeyCode.TAB) {
                luxuryThemeIndex = (luxuryThemeIndex + 1) % THEME_NAMES.length;
                drawRadialMenu();
            }
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
        } else if (dist <= OUTER_R + 4) {
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
    // 4. FINDER / EXPLORER SELECTION DETECTOR
    // =========================================================
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
            } else {
                String psCommand = "$shell = New-Object -ComObject Shell.Application; " +
                        "foreach ($w in $shell.Windows()) { " +
                        "if ($w.Document.SelectedItems().Count -gt 0) { " +
                        "$w.Document.SelectedItems().Item(0).Path; break } }";
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command", psCommand).start();
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
    // 5. 100% OFFLINE UNIVERSAL CONVERSION ENGINE
    // =========================================================
    private void executeFileAction(File file, String action) {
        try {
            System.out.println("⚙️ [OFFLINE ENGINE] Converting '" + file.getName() + "' -> " + action + "...");
            String path = file.getAbsolutePath();
            int dotIndex = path.lastIndexOf('.');
            String baseName = (dotIndex > 0) ? path.substring(0, dotIndex) : path;
            String ext = (dotIndex > 0) ? path.substring(dotIndex + 1).toLowerCase() : "";
            String targetExt = action.toLowerCase();
            File outputCreated = null;

            // A. SPREADSHEETS (.xlsx, .xls, .csv, .tsv)
            if (Arrays.asList("xlsx", "xls", "csv", "tsv").contains(ext)) {
                List<List<String>> table = readSpreadsheetToTable(file, ext);
                if (table.isEmpty()) return;

                if (action.equals("SORT A-Z") && table.size() > 1) {
                    List<String> header = table.get(0);
                    List<List<String>> body = table.subList(1, table.size()).stream()
                            .sorted(Comparator.comparing(r -> r.isEmpty() ? "" : r.get(0)))
                            .collect(Collectors.toList());
                    body.add(0, header);
                    outputCreated = new File(baseName + "_Sorted.csv");
                    writeTableToCsv(body, outputCreated);
                } else if (action.equals("DEDUPE")) {
                    List<List<String>> deduped = new ArrayList<>(new LinkedHashSet<>(table));
                    outputCreated = new File(baseName + "_Deduped.csv");
                    writeTableToCsv(deduped, outputCreated);
                } else if (action.equals("SUMMARY")) {
                    outputCreated = new File(baseName + "_Summary.txt");
                    String summary = "File: " + file.getName() + "\nTotal Rows: " + table.size()
                            + "\nTotal Columns: " + (table.get(0).size())
                            + "\nHeaders: " + String.join(", ", table.get(0));
                    Files.writeString(outputCreated.toPath(), summary);
                } else if (action.equals("CSV")) {
                    outputCreated = new File(baseName + ".csv");
                    writeTableToCsv(table, outputCreated);
                } else if (action.equals("XLSX")) {
                    outputCreated = new File(baseName + ".xlsx");
                    writeTableToXlsx(table, outputCreated);
                } else if (action.equals("TXT")) {
                    String txt = table.stream().map(r -> String.join("\t", r)).collect(Collectors.joining("\n"));
                    outputCreated = new File(baseName + ".txt");
                    Files.writeString(outputCreated.toPath(), txt);
                } else if (action.equals("HTML")) {
                    outputCreated = new File(baseName + ".html");
                    Files.writeString(outputCreated.toPath(), buildHtmlTable(table));
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
                            json.append("\"").append(escapeJson(key)).append("\": \"").append(escapeJson(val)).append("\"");
                            if (c < cols - 1) json.append(", ");
                        }
                        json.append("}").append(i < table.size() - 1 ? ",\n" : "\n");
                    }
                    json.append("]");
                    outputCreated = new File(baseName + ".json");
                    Files.writeString(outputCreated.toPath(), json.toString());
                } else if (action.equals("PDF")) {
                    String formatted = table.stream().map(r -> String.join("  |  ", r)).collect(Collectors.joining("\n"));
                    outputCreated = new File(baseName + ".pdf");
                    createSimplePdfFromText(formatted, outputCreated);
                }
            }

            // B. POWERPOINT (.pptx, .ppt)
            else if (Arrays.asList("pptx", "ppt").contains(ext)) {
                try (FileInputStream fis = new FileInputStream(file);
                     XMLSlideShow ppt = new XMLSlideShow(fis)) {
                    Dimension pgsize = ppt.getPageSize();
                    List<XSLFSlide> slides = ppt.getSlides();

                    if (action.equals("TXT") || action.equals("EXTRACT TXT") || action.equals("HTML")) {
                        StringBuilder sb = new StringBuilder();
                        int sNum = 1;
                        for (XSLFSlide slide : slides) {
                            sb.append("--- Slide ").append(sNum++).append(" ---\n");
                            for (XSLFShape shape : slide.getShapes()) {
                                if (shape instanceof XSLFTextShape ts) {
                                    sb.append(ts.getText()).append("\n");
                                }
                            }
                            sb.append("\n");
                        }
                        String outExt = action.equals("HTML") ? "html" : "txt";
                        outputCreated = new File(baseName + "." + outExt);
                        Files.writeString(outputCreated.toPath(), sb.toString());
                    } else if (action.equals("PNG") || action.equals("SLIDE 1 PNG")) {
                        if (!slides.isEmpty()) {
                            BufferedImage img = renderSlide(slides.get(0), pgsize);
                            outputCreated = new File(baseName + "_Slide1.png");
                            ImageIO.write(img, "png", outputCreated);
                        }
                    } else if (action.equals("PDF")) {
                        outputCreated = new File(baseName + ".pdf");
                        try (PDDocument pdfDoc = new PDDocument()) {
                            for (XSLFSlide slide : slides) {
                                BufferedImage img = renderSlide(slide, pgsize);
                                PDPage page = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(pgsize.width, pgsize.height));
                                pdfDoc.addPage(page);
                                PDImageXObject pdImg = LosslessFactoryBridge.fromBufferedImage(pdfDoc, img);
                                try (PDPageContentStream cs = new PDPageContentStream(pdfDoc, page)) {
                                    cs.drawImage(pdImg, 0, 0, pgsize.width, pgsize.height);
                                }
                            }
                            pdfDoc.save(outputCreated);
                        }
                    }
                }
            }

            // C. WORD DOCS, HTML & MHTML (.docx, .doc, .rtf, .txt, .md, .html, .mhtml)
            else if (Arrays.asList("docx", "doc", "rtf", "txt", "md", "html", "htm", "mhtml").contains(ext)) {
                String extractedText = extractTextFromDocOrWeb(file, ext);
                if (action.equals("UPPERCASE")) {
                    outputCreated = new File(baseName + "_UPPER.txt");
                    Files.writeString(outputCreated.toPath(), extractedText.toUpperCase());
                } else if (action.equals("CLEAN TXT") || action.equals("TXT")) {
                    outputCreated = new File(baseName + ".txt");
                    Files.writeString(outputCreated.toPath(), extractedText.trim());
                } else if (action.equals("SUMMARY")) {
                    String[] words = extractedText.trim().split("\\s+");
                    outputCreated = new File(baseName + "_Summary.txt");
                    Files.writeString(outputCreated.toPath(), "Words: " + words.length + "\n\n" + extractedText);
                } else if (action.equals("PDF")) {
                    outputCreated = new File(baseName + ".pdf");
                    createSimplePdfFromText(extractedText, outputCreated);
                } else if (action.equals("DOCX")) {
                    outputCreated = new File(baseName + "_Converted.docx");
                    writeTextToDocx(extractedText, outputCreated);
                } else if (IS_MAC && Arrays.asList("html", "rtf").contains(targetExt) && !ext.equals("mhtml")) {
                    outputCreated = new File(baseName + "_Converted." + targetExt);
                    new ProcessBuilder("textutil", "-convert", targetExt, path, "-output", outputCreated.getAbsolutePath())
                            .start().waitFor();
                } else {
                    outputCreated = new File(baseName + "_Converted." + targetExt);
                    Files.writeString(outputCreated.toPath(), extractedText);
                }
            }

            // D. PDF DOCUMENTS (.pdf)
            else if (ext.equals("pdf")) {
                try (PDDocument doc = Loader.loadPDF(file)) {
                    if (action.equals("TXT") || action.equals("HTML") || action.equals("SUMMARY")) {
                        String text = new PDFTextStripper().getText(doc);
                        outputCreated = new File(baseName + (action.equals("HTML") ? ".html" : ".txt"));
                        if (action.equals("HTML")) {
                            text = "<html><body style='font-family:sans-serif;padding:24px;line-height:1.6;'><pre style='white-space:pre-wrap;'>"
                                    + text.replace("&", "&amp;").replace("<", "&lt;") + "</pre></body></html>";
                        }
                        Files.writeString(outputCreated.toPath(), text);
                    } else if (action.equals("DOCX")) {
                        // 100% Offline Cross-Platform PDF -> DOCX via Apache POI XWPFDocument
                        String text = new PDFTextStripper().getText(doc);
                        outputCreated = new File(baseName + ".docx");
                        writeTextToDocx(text, outputCreated);
                    } else {
                        PDFRenderer renderer = new PDFRenderer(doc);
                        int pagesToRender = action.equals("ALL PAGES PNG") ? doc.getNumberOfPages() : 1;
                        String fmt = action.equalsIgnoreCase("JPG") ? "jpg" : "png";
                        for (int p = 0; p < pagesToRender; p++) {
                            BufferedImage pageImg = renderer.renderImageWithDPI(p, 200);
                            if (fmt.equals("jpg")) {
                                BufferedImage rgb = new BufferedImage(pageImg.getWidth(), pageImg.getHeight(), BufferedImage.TYPE_INT_RGB);
                                Graphics2D g = rgb.createGraphics();
                                g.drawImage(pageImg, 0, 0, java.awt.Color.WHITE, null);
                                g.dispose();
                                pageImg = rgb;
                            }
                            outputCreated = new File(baseName + "_Page" + (p + 1) + "." + fmt);
                            ImageIO.write(pageImg, fmt, outputCreated);
                        }
                    }
                }
            }

            // E. JSON FILES (.json -> CSV, XLSX, HTML, PDF, TXT)
            else if (ext.equals("json")) {
                String rawJson = Files.readString(file.toPath());
                List<List<String>> jsonTable = parseJsonToTable(rawJson);
                if (action.equals("CSV")) {
                    outputCreated = new File(baseName + ".csv");
                    writeTableToCsv(jsonTable, outputCreated);
                } else if (action.equals("XLSX")) {
                    outputCreated = new File(baseName + ".xlsx");
                    writeTableToXlsx(jsonTable, outputCreated);
                } else if (action.equals("HTML")) {
                    outputCreated = new File(baseName + ".html");
                    Files.writeString(outputCreated.toPath(), buildHtmlTable(jsonTable));
                } else if (action.equals("TXT")) {
                    outputCreated = new File(baseName + ".txt");
                    Files.writeString(outputCreated.toPath(), rawJson);
                } else if (action.equals("PDF")) {
                    outputCreated = new File(baseName + ".pdf");
                    createSimplePdfFromText(rawJson, outputCreated);
                }
            }

            // F. IMAGES (.jpg, .jpeg, .png, .heic, .webp, .bmp, .tiff)
            else if (Arrays.asList("jpg", "jpeg", "png", "heic", "webp", "bmp", "tiff").contains(ext)) {
                if (action.equals("PDF")) {
                    BufferedImage img = readImageWithMacFallback(file);
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
                } else if (action.equals("COMPRESS") && IS_MAC) {
                    outputCreated = new File(baseName + "_Compressed.jpg");
                    new ProcessBuilder("sips", "-s", "format", "jpeg", "-s", "formatOptions", "50",
                            path, "--out", outputCreated.getAbsolutePath()).start().waitFor();
                } else if (action.equals("GRAYSCALE")) {
                    BufferedImage src = readImageWithMacFallback(file);
                    if (src != null) {
                        BufferedImage bw = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
                        Graphics2D g = bw.createGraphics();
                        g.drawImage(src, 0, 0, null);
                        g.dispose();
                        outputCreated = new File(baseName + "_BW.png");
                        ImageIO.write(bw, "png", outputCreated);
                    }
                } else if (IS_MAC && Arrays.asList("jpg", "png", "heic", "tiff", "bmp").contains(targetExt)) {
                    String sipsFmt = targetExt.equals("jpg") ? "jpeg" : targetExt;
                    outputCreated = new File(baseName + "_Converted." + targetExt);
                    new ProcessBuilder("sips", "-s", "format", sipsFmt, path, "--out", outputCreated.getAbsolutePath())
                            .start().waitFor();
                } else {
                    BufferedImage img = readImageWithMacFallback(file);
                    if (img != null) {
                        if (targetExt.equals("jpg") || targetExt.equals("bmp")) {
                            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                            Graphics2D g = rgb.createGraphics();
                            g.drawImage(img, 0, 0, java.awt.Color.WHITE, null);
                            g.dispose();
                            img = rgb;
                        }
                        outputCreated = new File(baseName + "_Converted." + targetExt);
                        ImageIO.write(img, targetExt, outputCreated);
                    }
                }
            }

            if (outputCreated != null && outputCreated.exists()) {
                System.out.println("✅ OFFLINE CONVERSION COMPLETE: " + outputCreated.getAbsolutePath());
                showLuxuryToast("Created " + outputCreated.getName(), outputCreated);
                if (IS_MAC) {
                    new ProcessBuilder("afplay", "/System/Library/Sounds/Glass.aiff").start();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // =========================================================
    // 6. OFFLINE PARSERS & WRITERS (XLSX, DOCX, JSON, HTML, PDF)
    // =========================================================
    private BufferedImage readImageWithMacFallback(File file) throws Exception {
        BufferedImage img = ImageIO.read(file);
        if (img == null && IS_MAC) {
            File tmp = File.createTempFile("mira_decode", ".png");
            new ProcessBuilder("sips", "-s", "format", "png", file.getAbsolutePath(), "--out", tmp.getAbsolutePath())
                    .start().waitFor();
            img = ImageIO.read(tmp);
            tmp.delete();
        }
        return img;
    }

    private void writeTableToXlsx(List<List<String>> table, File outXlsx) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(outXlsx)) {
            Sheet sheet = wb.createSheet("Converted Data");
            for (int r = 0; r < table.size(); r++) {
                Row row = sheet.createRow(r);
                List<String> cells = table.get(r);
                for (int c = 0; c < cells.size(); c++) {
                    row.createCell(c).setCellValue(cells.get(c));
                }
            }
            wb.write(fos);
        }
    }

    private void writeTextToDocx(String text, File outDocx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); FileOutputStream fos = new FileOutputStream(outDocx)) {
            for (String line : text.split("\\r?\\n")) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun run = p.createRun();
                run.setText(line);
                run.setFontSize(11);
            }
            doc.write(fos);
        }
    }

    private List<List<String>> parseJsonToTable(String rawJson) {
        List<Map<String, String>> records = new ArrayList<>();
        Set<String> headers = new LinkedHashSet<>();
        Matcher objMatcher = Pattern.compile("\\{([^\\{\\}]*)\\}").matcher(rawJson);
        Pattern kvPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"([^\"]*)\"|[^,\\}\\s]+)");

        while (objMatcher.find()) {
            String body = objMatcher.group(1);
            Matcher kv = kvPattern.matcher(body);
            Map<String, String> rowMap = new LinkedHashMap<>();
            while (kv.find()) {
                String k = kv.group(1).trim();
                String v = kv.group(3) != null ? kv.group(3) : kv.group(2).trim();
                headers.add(k);
                rowMap.put(k, v);
            }
            if (!rowMap.isEmpty()) records.add(rowMap);
        }

        List<List<String>> table = new ArrayList<>();
        if (headers.isEmpty()) {
            table.add(Collections.singletonList("Content"));
            for (String line : rawJson.split("\\r?\\n")) {
                table.add(Collections.singletonList(line.trim()));
            }
            return table;
        }

        List<String> headerList = new ArrayList<>(headers);
        table.add(headerList);
        for (Map<String, String> rec : records) {
            List<String> row = new ArrayList<>();
            for (String h : headerList) {
                row.add(rec.getOrDefault(h, ""));
            }
            table.add(row);
        }
        return table;
    }

    private String buildHtmlTable(List<List<String>> table) {
        StringBuilder html = new StringBuilder("<html><head><meta charset='UTF-8'><style>"
                + "body{font-family:-apple-system,sans-serif;padding:28px;background:#0f172a;color:#f8fafc;}"
                + "table{border-collapse:collapse;width:100%;background:#1e293b;border-radius:8px;overflow:hidden;}"
                + "th,td{border:1px solid #334155;padding:10px 14px;text-align:left;font-size:13px;}"
                + "th{background:#090a0f;color:#e1be5a;text-transform:uppercase;letter-spacing:0.05em;}</style></head><body><table>\n");
        for (int i = 0; i < table.size(); i++) {
            html.append("<tr>");
            for (String cell : table.get(i)) {
                html.append(i == 0 ? "<th>" : "<td>")
                        .append(cell.replace("&", "&amp;").replace("<", "&lt;"))
                        .append(i == 0 ? "</th>" : "</td>");
            }
            html.append("</tr>\n");
        }
        html.append("</table></body></html>");
        return html.toString();
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
                    int lastCol = Math.max(row.getLastCellNum(), 0);
                    for (int c = 0; c < lastCol; c++) {
                        org.apache.poi.ss.usermodel.Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        cells.add(formatter.formatCellValue(cell).trim());
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
            sb.append(row.stream()
                    .map(c -> c.contains(",") || c.contains("\"") ? "\"" + c.replace("\"", "\"\"") + "\"" : c)
                    .collect(Collectors.joining(","))).append("\n");
        }
        Files.writeString(outCsv.toPath(), sb.toString());
    }

    private String extractTextFromDocOrWeb(File file, String ext) throws Exception {
        if (ext.equals("docx")) {
            try (FileInputStream fis = new FileInputStream(file);
                 XWPFDocument doc = new XWPFDocument(fis)) {
                return doc.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
            }
        }
        String raw = Files.readString(file.toPath());
        if (ext.equals("mhtml") || ext.equals("html") || ext.equals("htm")) {
            raw = raw.replace("=\r\n", "").replace("=\n", "").replace("=3D", "=");
            raw = raw.replaceAll("(?s)<(script|style)[^>]*>.*?</\\1>", "");
            raw = raw.replaceAll("<[^>]+>", " ");
        }
        return raw;
    }

    private BufferedImage renderSlide(XSLFSlide slide, Dimension pgsize) {
        BufferedImage img = new BufferedImage(pgsize.width, pgsize.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(java.awt.Color.WHITE);
        g2.fill(new Double(0, 0, pgsize.width, pgsize.height));
        slide.draw(g2);
        g2.dispose();
        return img;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
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

    private void createSimplePdfFromText(String text, File outPdf) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            String[] lines = text.split("\\r?\\n");
            int lineIdx = 0;
            while (lineIdx < lines.length) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    cs.setLeading(14f);
                    cs.newLineAtOffset(45, 740);
                    for (int row = 0; row < 48 && lineIdx < lines.length; row++, lineIdx++) {
                        String clean = lines[lineIdx].replaceAll("[^\\x20-\\x7E]", " ");
                        if (clean.length() > 88) clean = clean.substring(0, 88);
                        cs.showText(clean);
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            doc.save(outPdf);
        }
    }

    // =========================================================
    // 7. LUXURY TOAST BANNER & ADD BG STUDIO
    // =========================================================
    private void showLuxuryToast(String title, File createdFile) {
        Platform.runLater(() -> {
            Stage toast = new Stage(StageStyle.TRANSPARENT);
            toast.setAlwaysOnTop(true);

            HBox banner = new HBox(14);
            banner.setAlignment(Pos.CENTER_LEFT);
            banner.setStyle("-fx-background-color: rgba(10, 11, 14, 0.95); -fx-padding: 12 18; "
                    + "-fx-background-radius: 14; -fx-border-color: rgba(212, 175, 55, 0.55); -fx-border-radius: 14;");

            Label lbl = new Label("✦  " + title);
            lbl.setFont(Font.font(OS_FONT, FontWeight.BOLD, 12));
            lbl.setTextFill(Color.rgb(235, 232, 225));
            banner.getChildren().add(lbl);

            if (createdFile != null && createdFile.exists()) {
                Button revealBtn = new Button("Reveal");
                revealBtn.setStyle("-fx-background-color: #D4AF37; -fx-text-fill: #090A0F; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 4 12;");
                revealBtn.setOnAction(e -> {
                    try {
                        if (IS_MAC) {
                            new ProcessBuilder("open", "-R", createdFile.getAbsolutePath()).start();
                        } else {
                            new ProcessBuilder("explorer.exe", "/select,", createdFile.getAbsolutePath()).start();
                        }
                        toast.close();
                    } catch (Exception ignored) {}
                });
                banner.getChildren().add(revealBtn);
            }

            Scene sc = new Scene(banner, Color.TRANSPARENT);
            toast.setScene(sc);
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            toast.setX(bounds.getMaxX() - 360);
            toast.setY(bounds.getMaxY() - 90);
            toast.show();

            new Timeline(new KeyFrame(Duration.seconds(3.5), e -> toast.close())).play();
        });
    }

    private void showAddBgModal(File file) {
        try {
            BufferedImage orig = readImageWithMacFallback(file);
            if (orig == null) return;
            Stage modal = new Stage();
            modal.setTitle("M.I.R.A. Studio — Luxury Frame");
            VBox root = new VBox(14);
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: #090A0F; -fx-padding: 24;");
            ImageView previewView = new ImageView();
            previewView.setFitWidth(380);
            previewView.setFitHeight(250);
            previewView.setPreserveRatio(true);

            Slider padSlider = new Slider(20, 180, 80);
            Slider cornerSlider = new Slider(0, 80, 24);
            final BufferedImage[] latestRender = new BufferedImage[1];
            Runnable update = () -> {
                latestRender[0] = renderStyledBackground(orig, (int) padSlider.getValue(), (int) cornerSlider.getValue());
                previewView.setImage(SwingFXUtils.toFXImage(latestRender[0], null));
            };
            padSlider.valueProperty().addListener((o, a, b) -> update.run());
            cornerSlider.valueProperty().addListener((o, a, b) -> update.run());
            update.run();

            Button saveBtn = new Button("Export Framed Image");
            saveBtn.setStyle("-fx-background-color: #D4AF37; -fx-text-fill: #090A0F; -fx-background-radius: 8; -fx-padding: 8 22; -fx-font-weight: bold;");
            saveBtn.setOnAction(e -> {
                try {
                    String path = file.getAbsolutePath();
                    File out = new File(path.substring(0, path.lastIndexOf('.')) + "_Framed.png");
                    ImageIO.write(latestRender[0], "png", out);
                    showLuxuryToast("Exported " + out.getName(), out);
                    modal.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            root.getChildren().addAll(previewView, padSlider, cornerSlider, saveBtn);
            modal.setScene(new Scene(root, 440, 420));
            modal.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BufferedImage renderStyledBackground(BufferedImage fg, int pad, int radius) {
        int w = fg.getWidth(), h = fg.getHeight();
        int totalW = w + pad * 2, totalH = h + pad * 2;
        BufferedImage out = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, new java.awt.Color(18, 20, 28), totalW, totalH, new java.awt.Color(38, 32, 20)));
        g2.fillRect(0, 0, totalW, totalH);
        g2.setColor(new java.awt.Color(0, 0, 0, 110));
        g2.fillRoundRect(pad + 4, pad + 12, w, h, radius, radius);

        BufferedImage roundedFg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gFg = roundedFg.createGraphics();
        gFg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gFg.fill(new RoundRectangle2D.Float(0, 0, w, h, radius, radius));
        gFg.setComposite(AlphaComposite.SrcIn);
        gFg.drawImage(fg, 0, 0, null);
        gFg.dispose();

        g2.drawImage(roundedFg, pad, pad, null);
        g2.dispose();
        return out;
    }

    // =========================================================
    // 8. SUMMON MENU & GLOBAL SHORTCUT
    // =========================================================
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

                wheelPane.setScaleX(0.84);
                wheelPane.setScaleY(0.84);
                radialStage.show();
                radialStage.toFront();
                radialStage.requestFocus();

                ScaleTransition st = new ScaleTransition(Duration.millis(110), wheelPane);
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
                    if (IS_MAC && (code == NativeKeyEvent.VC_META || code == 3675 || code == 3676)) {
                        modifierDown = true;
                    } else if (!IS_MAC && code == NativeKeyEvent.VC_SHIFT) {
                        modifierDown = true;
                    } else if (modifierDown && code == NativeKeyEvent.VC_E) {
                        summonRadialMenu();
                    }
                }

                @Override
                public void nativeKeyReleased(NativeKeyEvent e) {
                    int code = e.getKeyCode();
                    if ((IS_MAC && (code == NativeKeyEvent.VC_META || code == 3675 || code == 3676))
                            || (!IS_MAC && code == NativeKeyEvent.VC_SHIFT)) {
                        modifierDown = false;
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("⚠️ Grant Accessibility Permission in System Settings -> Privacy & Security -> Accessibility.");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}