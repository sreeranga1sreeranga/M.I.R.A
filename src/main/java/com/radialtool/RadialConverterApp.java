package com.radialtool;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javafx.animation.ScaleTransition;
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
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

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

        System.out.println("==========================================================");
        System.out.println("✨ M.I.R.A. Ready — Clean Build Loaded");
        System.out.println("👉 Select ANY file in Finder/Desktop and press COMMAND + E");
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
        } catch (Exception ignored) {
        }
        return true;
    }

    // =========================================================
    // 1. FILE-TYPE OPTIONS
    // =========================================================
    private void updateSlicesForFile(File file) {
        if (file == null) {
            currentSlices = Arrays.asList("PNG", "JPG", "PDF", "TXT", "CSV", "JSON");
            return;
        }

        String name = file.getName().toLowerCase();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";

        // EXCEL & CSV (.xlsx, .xls, .csv, .tsv)
        if (Arrays.asList("xlsx", "xls", "csv", "tsv").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("SORT A-Z", "DEDUPE", "SUMMARY");
            } else {
                List<String> opts = IS_MAC
                        ? Arrays.asList("JSON", "CSV", "HTML", "PDF", "TXT", "NUMBERS")
                        : Arrays.asList("JSON", "CSV", "HTML", "PDF", "TXT");
                currentSlices = opts.stream().filter(s -> !s.equalsIgnoreCase(ext)).toList();
            }
            return;
        }

        // POWERPOINT (.pptx, .ppt)
        if (Arrays.asList("pptx", "ppt").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("EXTRACT TXT", "SLIDE 1 PNG");
            } else {
                currentSlices = IS_MAC
                        ? Arrays.asList("PDF", "PNG", "TXT", "HTML", "KEYNOTE")
                        : Arrays.asList("PDF", "PNG", "TXT", "HTML");
            }
            return;
        }

        // WORD, TEXT & WEB ARCHIVES (.docx, .doc, .rtf, .txt, .md, .html, .mhtml)
        if (Arrays.asList("docx", "doc", "rtf", "txt", "md", "html", "htm", "mhtml").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("SUMMARY", "UPPERCASE", "CLEAN TXT");
            } else {
                List<String> opts = IS_MAC
                        ? Arrays.asList("PDF", "TXT", "HTML", "RTF", "DOCX", "PAGES")
                        : Arrays.asList("PDF", "TXT", "HTML", "RTF", "DOCX");
                currentSlices = opts.stream().filter(s -> !s.equalsIgnoreCase(ext)).toList();
            }
            return;
        }

        // PDF DOCUMENTS (.pdf)
        if (ext.equals("pdf")) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("SUMMARY", "ALL PAGES PNG");
            } else {
                currentSlices = IS_MAC
                        ? Arrays.asList("JPG", "PNG", "TXT", "HTML", "DOCX", "PAGES")
                        : Arrays.asList("JPG", "PNG", "TXT", "HTML", "DOCX");
            }
            return;
        }

        // IMAGES (.jpg, .jpeg, .png, .heic, .webp, .bmp, .tiff)
        if (Arrays.asList("jpg", "jpeg", "png", "heic", "webp", "bmp", "tiff").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("ADD BG", "CROP", "COMPRESS", "GRAYSCALE");
            } else {
                List<String> allImg = IS_MAC
                        ? Arrays.asList("JPG", "PNG", "HEIC", "WEBP", "TIFF", "PDF")
                        : Arrays.asList("JPG", "PNG", "WEBP", "TIFF", "BMP", "PDF");
                String filterExt = ext.equals("jpeg") ? "JPG" : ext.toUpperCase();
                currentSlices = allImg.stream().filter(s -> !s.equals(filterExt)).toList();
            }
            return;
        }

        // JSON FILES (.json)
        if (ext.equals("json")) {
            currentSlices = Arrays.asList("CSV", "HTML", "PDF", "TXT");
            return;
        }

        currentSlices = Arrays.asList("PDF", "TXT", "HTML");
    }

    // =========================================================
    // 2. RADIAL WHEEL RENDERER
    // =========================================================
    private void drawRadialMenu() {
        wheelPane.getChildren().clear();
        double cx = SIZE / 2.0;
        double cy = SIZE / 2.0;
        int count = currentSlices.size();
        double step = 360.0 / count;

        LinearGradient sliceBase = isOsDarkMode
                ? new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.rgb(22, 27, 38, 0.90)),
                        new Stop(1, Color.rgb(13, 17, 24, 0.94)))
                : new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.rgb(244, 247, 252, 0.94)),
                        new Stop(1, Color.rgb(225, 232, 242, 0.96)));

        LinearGradient sliceHover = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(37, 99, 235, 0.96)),
                new Stop(1, Color.rgb(79, 70, 229, 0.96)));

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
        hub.setFill(centerHovered
                ? sliceHover
                : (isOsDarkMode ? Color.rgb(15, 23, 42, 0.96) : Color.rgb(255, 255, 255, 0.96)));
        hub.setStroke(Color.rgb(59, 130, 246, 0.85));
        hub.setStrokeWidth(1.8);

        String extBadge = "PICK FILE";
        if (activeFile != null) {
            String n = activeFile.getName();
            extBadge = n.contains(".") ? n.substring(n.lastIndexOf('.') + 1).toUpperCase() : "FILE";
        }

        String topLine = centerHovered ? (isToolsMode ? "CONVERT" : "TOOLS") : "[" + extBadge + "]";
        String botLine = activeFile == null ? "Click Here" : (centerHovered ? "Switch" : "Click:Tools");

        Text hubTitle = new Text(topLine);
        hubTitle.setFont(Font.font(OS_FONT, FontWeight.EXTRA_BOLD, 10.0));
        hubTitle.setFill(centerHovered ? Color.WHITE : Color.rgb(96, 165, 250));
        hubTitle.setMouseTransparent(true);
        hubTitle.setX(cx - hubTitle.getLayoutBounds().getWidth() / 2);
        hubTitle.setY(cy - 2);

        Text hubSub = new Text(botLine);
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
    // 3. INTERACTIONS
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
    // 4. RESTORED MULTI-LINE FINDER APPLESCRIPT (The One That Worked!)
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
                        if (f.exists()) {
                            System.out.println("🎯 M.I.R.A. Locked onto File: " + f.getAbsolutePath());
                            return f;
                        }
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
    // 5. UNIVERSAL CONVERSION ENGINE
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

            // A. EXCEL (.xlsx, .xls) & CSV/TSV
            if (Arrays.asList("xlsx", "xls", "csv", "tsv").contains(ext)) {
                if (action.equals("NUMBERS") && IS_MAC) {
                    new ProcessBuilder("open", "-a", "Numbers", path).start();
                    return;
                }
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
                } else if (action.equals("TXT")) {
                    String txt = table.stream().map(r -> String.join("\t", r)).collect(Collectors.joining("\n"));
                    outputCreated = new File(baseName + ".txt");
                    Files.writeString(outputCreated.toPath(), txt);
                } else if (action.equals("HTML")) {
                    StringBuilder html = new StringBuilder("<html><head><meta charset='UTF-8'><style>"
                            + "body{font-family:-apple-system,sans-serif;padding:24px;background:#f8fafc;}"
                            + "table{border-collapse:collapse;width:100%;background:white;}"
                            + "th,td{border:1px solid #cbd5e1;padding:8px 12px;text-align:left;font-size:13px;}"
                            + "th{background:#1e293b;color:white;}</style></head><body><table>\n");
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
                    outputCreated = new File(baseName + ".html");
                    Files.writeString(outputCreated.toPath(), html.toString());
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

            // B. POWERPOINT (.pptx)
            else if (Arrays.asList("pptx", "ppt").contains(ext)) {
                if (action.equals("KEYNOTE") && IS_MAC) {
                    new ProcessBuilder("open", "-a", "Keynote", path).start();
                    return;
                }
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
                if (action.equals("PAGES") && IS_MAC) {
                    new ProcessBuilder("open", "-a", "Pages", path).start();
                    return;
                }
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
                } else if (IS_MAC && Arrays.asList("html", "rtf", "docx").contains(targetExt) && !ext.equals("mhtml")) {
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
                        Files.writeString(outputCreated.toPath(), text);
                    } else if (action.equals("DOCX")) {
                        String text = new PDFTextStripper().getText(doc);
                        File tmpTxt = File.createTempFile("mira_pdf", ".txt");
                        Files.writeString(tmpTxt.toPath(), text);
                        outputCreated = new File(baseName + ".docx");
                        if (IS_MAC) {
                            new ProcessBuilder("textutil", "-convert", "docx", tmpTxt.getAbsolutePath(),
                                    "-output", outputCreated.getAbsolutePath()).start().waitFor();
                        } else {
                            Files.writeString(outputCreated.toPath(), text);
                        }
                        tmpTxt.delete();
                    } else if (action.equals("PAGES") && IS_MAC) {
                        outputCreated = new File(baseName + ".txt");
                        Files.writeString(outputCreated.toPath(), new PDFTextStripper().getText(doc));
                        new ProcessBuilder("open", "-a", "Pages", outputCreated.getAbsolutePath()).start();
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

            // E. JSON FILES (.json)
            else if (ext.equals("json")) {
                String rawJson = Files.readString(file.toPath());
                if (action.equals("TXT") || action.equals("HTML") || action.equals("CSV")) {
                    outputCreated = new File(baseName + "." + targetExt);
                    Files.writeString(outputCreated.toPath(), rawJson);
                } else if (action.equals("PDF")) {
                    outputCreated = new File(baseName + ".pdf");
                    createSimplePdfFromText(rawJson, outputCreated);
                }
            }

            // F. IMAGES (.jpg, .jpeg, .png, .heic, .webp, .bmp, .tiff)
            else if (Arrays.asList("jpg", "jpeg", "png", "heic", "webp", "bmp", "tiff").contains(ext)) {
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
                } else if (action.equals("COMPRESS") && IS_MAC) {
                    outputCreated = new File(baseName + "_Compressed.jpg");
                    new ProcessBuilder("sips", "-s", "format", "jpeg", "-s", "formatOptions", "50",
                            path, "--out", outputCreated.getAbsolutePath()).start().waitFor();
                } else if (action.equals("GRAYSCALE")) {
                    BufferedImage src = ImageIO.read(file);
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
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) {
                        outputCreated = new File(baseName + "_Converted." + targetExt);
                        ImageIO.write(img, targetExt, outputCreated);
                    }
                }
            }

            if (outputCreated != null && outputCreated.exists()) {
                System.out.println("✅ SUCCESS! Created: " + outputCreated.getAbsolutePath());
                if (IS_MAC) {
                    new ProcessBuilder("afplay", "/System/Library/Sounds/Glass.aiff").start();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // =========================================================
    // 6. PARSERS & HELPERS
    // =========================================================
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

    private void showCropModal(File file) {
        try {
            BufferedImage orig = ImageIO.read(file);
            if (orig == null) return;
            Stage modal = new Stage();
            modal.setTitle("M.I.R.A. — Crop Image");
            VBox root = new VBox(14);
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: #18181B; -fx-padding: 20;");
            ImageView preview = new ImageView(SwingFXUtils.toFXImage(orig, null));
            preview.setFitWidth(360);
            preview.setFitHeight(280);
            preview.setPreserveRatio(true);

            Button applyBtn = new Button("Apply Square Crop");
            applyBtn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 24; -fx-font-weight: bold;");
            applyBtn.setOnAction(e -> {
                try {
                    int w = orig.getWidth(), h = orig.getHeight();
                    int m = (int) (Math.min(w, h) * 0.85);
                    BufferedImage cropped = orig.getSubimage((w - m) / 2, (h - m) / 2, m, m);
                    String path = file.getAbsolutePath();
                    File out = new File(path.substring(0, path.lastIndexOf('.')) + "_Cropped.png");
                    ImageIO.write(cropped, "png", out);
                    System.out.println("✅ SUCCESS! Created: " + out.getAbsolutePath());
                    modal.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            root.getChildren().addAll(preview, applyBtn);
            modal.setScene(new Scene(root, 420, 380));
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

            Button saveBtn = new Button("Save Styled Image");
            saveBtn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 20; -fx-font-weight: bold;");
            saveBtn.setOnAction(e -> {
                try {
                    String path = file.getAbsolutePath();
                    File out = new File(path.substring(0, path.lastIndexOf('.')) + "_Styled.png");
                    ImageIO.write(latestRender[0], "png", out);
                    System.out.println("✅ SUCCESS! Created: " + out.getAbsolutePath());
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
        g2.setPaint(new GradientPaint(0, 0, new java.awt.Color(56, 189, 248), totalW, totalH, new java.awt.Color(99, 102, 241)));
        g2.fillRect(0, 0, totalW, totalH);
        g2.setColor(new java.awt.Color(0, 0, 0, 80));
        g2.fillRoundRect(pad + 4, pad + 10, w, h, radius, radius);

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
    // 7. SUMMON MENU
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