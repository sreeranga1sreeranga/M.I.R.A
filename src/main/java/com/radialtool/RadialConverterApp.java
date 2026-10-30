package com.radialtool;

import java.util.List;

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
import javafx.scene.effect.BoxBlur;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.robot.Robot;
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
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
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
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.datatransfer.StringSelection;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class RadialConverterApp extends Application {

    private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");
    private static final String OS_FONT = IS_MAC ? "SF Pro Display" : "Segoe UI Variable";

    private static final double SIZE = 310;
    private static final double OUTER_R = 122;
    private static final double INNER_R = 50;
    private static final double GAP_DEGREES = 3.6;

    private Stage radialStage;
    private Pane wheelPane;
    private WritableImage blurredBackdropSnapshot = null;

    private boolean isToolsMode = false;
    private boolean shortcutPaused = false;
    private boolean isOsDarkMode = true;
    private Color osAccentColor = Color.rgb(56, 189, 248);
    private int themeMode = 0; // 0=Auto OS, 1=Obsidian Gold, 2=Frosted Pearl, 3=Midnight Emerald

    private List<String> currentSlices = Arrays.asList("PNG", "JPG", "WEBP", "PDF");
    private int hoveredIndex = -1;
    private boolean centerHovered = false;
    private List<File> activeFiles = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);
        syncWithUserOsTheme();
        setupSystemTrayController();

        radialStage = new Stage(StageStyle.TRANSPARENT);
        radialStage.setAlwaysOnTop(true);

        wheelPane = new Pane();
        wheelPane.setPrefSize(SIZE, SIZE);
        wheelPane.setStyle("-fx-background-color: transparent;");

        DropShadow ambientShadow = new DropShadow();
        ambientShadow.setRadius(28);
        ambientShadow.setOffsetY(6);
        ambientShadow.setColor(Color.rgb(0, 0, 0, 0.58));
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
        System.out.println("✨ M.I.R.A. v2.0 Pro — Active in Background & Menu Bar");
        System.out.println("📦 Multi-File Batch + PDF Merge + Word-Wrap + Hotkeys (1-7)");
        System.out.println("👉 Select 1 or MORE files in Finder/Explorer & press CMD + E");
        System.out.println("==========================================================");
    }

    private void setupSystemTrayController() {
        if (!SystemTray.isSupported()) return;

        java.awt.EventQueue.invokeLater(() -> {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                BufferedImage trayImg = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = trayImg.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new java.awt.Color(56, 189, 248));
                g2.setStroke(new java.awt.BasicStroke(2.2f));
                g2.drawOval(3, 3, 15, 15);
                g2.setColor(java.awt.Color.WHITE);
                g2.fillOval(8, 8, 5, 5);
                g2.dispose();

                PopupMenu popup = new PopupMenu();
                java.awt.MenuItem summonItem = new java.awt.MenuItem("Summon M.I.R.A. Wheel (Cmd+E)");
                summonItem.addActionListener(e -> summonRadialMenu());

                java.awt.Menu themeMenu = new java.awt.Menu("Luxury Theme");
                String[] themeNames = {
                        "Auto (OS Chameleon Sync)",
                        "Obsidian & Champagne Gold",
                        "Frosted Pearl Alabaster",
                        "Midnight Emerald"
                };
                for (int i = 0; i < themeNames.length; i++) {
                    final int idx = i;
                    java.awt.MenuItem tItem = new java.awt.MenuItem(themeNames[i]);
                    tItem.addActionListener(e -> {
                        themeMode = idx;
                        Platform.runLater(this::drawRadialMenu);
                    });
                    themeMenu.add(tItem);
                }

                java.awt.CheckboxMenuItem pauseItem = new java.awt.CheckboxMenuItem("Pause Global Shortcut", false);
                pauseItem.addItemListener(e -> shortcutPaused = pauseItem.getState());

                java.awt.MenuItem quitItem = new java.awt.MenuItem("Quit M.I.R.A.");
                quitItem.addActionListener(e -> {
                    try { GlobalScreen.unregisterNativeHook(); } catch (Exception ignored) {}
                    System.exit(0);
                });

                popup.add(summonItem);
                popup.addSeparator();
                popup.add(themeMenu);
                popup.add(pauseItem);
                popup.addSeparator();
                popup.add(quitItem);

                TrayIcon trayIcon = new TrayIcon(trayImg, "M.I.R.A. Offline Converter", popup);
                trayIcon.setImageAutoSize(true);
                tray.add(trayIcon);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void syncWithUserOsTheme() {
        try {
            if (IS_MAC) {
                Process pDark = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(pDark.getInputStream()))) {
                    String out = r.readLine();
                    isOsDarkMode = (out != null && out.trim().equalsIgnoreCase("Dark"));
                }

                Process pAccent = new ProcessBuilder("defaults", "read", "-g", "AppleAccentColor").start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(pAccent.getInputStream()))) {
                    String val = r.readLine();
                    int code = (val != null) ? Integer.parseInt(val.trim()) : 4;
                    osAccentColor = switch (code) {
                        case -1 -> Color.rgb(148, 163, 184);
                        case 0  -> Color.rgb(244, 63, 94);
                        case 1  -> Color.rgb(249, 115, 22);
                        case 2  -> Color.rgb(234, 179, 8);
                        case 3  -> Color.rgb(16, 185, 129);
                        case 5  -> Color.rgb(168, 85, 247);
                        case 6  -> Color.rgb(236, 72, 153);
                        default -> Color.rgb(56, 189, 248);
                    };
                } catch (Exception e) {
                    osAccentColor = Color.rgb(56, 189, 248);
                }
            } else {
                Process pWin = new ProcessBuilder("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme").start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(pWin.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.contains("AppsUseLightTheme")) {
                            isOsDarkMode = line.trim().endsWith("0x0");
                        }
                    }
                }
                osAccentColor = Color.rgb(56, 189, 248);
            }
        } catch (Exception ignored) {
            isOsDarkMode = true;
            osAccentColor = Color.rgb(56, 189, 248);
        }
    }

    private void updateSlicesForFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            currentSlices = Arrays.asList("PICK FILES");
            return;
        }

        if (files.size() > 1) {
            boolean allPdf = files.stream().allMatch(f -> getExt(f).equals("pdf"));
            boolean allImg = files.stream().allMatch(f -> Arrays.asList("jpg", "jpeg", "png", "heic", "webp", "bmp", "tiff").contains(getExt(f)));

            if (allPdf) {
                currentSlices = Arrays.asList("MERGE PDFS", "BATCH TXT", "BATCH PNG", "ZIP ARCHIVE");
            } else if (allImg) {
                currentSlices = Arrays.asList("COMBINE PDF", "BATCH PNG", "BATCH JPG", "BATCH WEBP", "STRIP EXIF", "ZIP ARCHIVE");
            } else {
                currentSlices = Arrays.asList("ZIP ARCHIVE", "BATCH PDF", "BATCH TXT");
            }
            return;
        }

        File file = files.get(0);
        String ext = getExt(file);

        if (Arrays.asList("xlsx", "xls", "csv", "tsv").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("SORT A-Z", "DEDUPE", "SUMMARY", "COPY TXT");
            } else {
                List<String> opts = Arrays.asList("JSON", "CSV", "XLSX", "HTML", "PDF", "TXT");
                String filter = ext.equals("xls") ? "XLSX" : ext.toUpperCase();
                currentSlices = opts.stream().filter(s -> !s.equalsIgnoreCase(filter)).toList();
            }
            return;
        }

        if (Arrays.asList("jpg", "jpeg", "png", "heic", "webp", "bmp", "tiff").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("ADD BG", "COMPRESS", "GRAYSCALE", "STRIP EXIF");
            } else {
                List<String> allImg = IS_MAC
                        ? Arrays.asList("PNG", "JPG", "WEBP", "HEIC", "TIFF", "BMP", "PDF")
                        : Arrays.asList("PNG", "JPG", "WEBP", "TIFF", "BMP", "PDF");
                String filterExt = ext.equals("jpeg") ? "JPG" : ext.toUpperCase();
                currentSlices = allImg.stream().filter(s -> !s.equals(filterExt)).toList();
            }
            return;
        }

        if (ext.equals("pdf")) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("SPLIT PAGES", "SUMMARY", "ALL PAGES PNG", "COPY TXT");
            } else {
                currentSlices = Arrays.asList("DOCX", "TXT", "HTML", "JPG", "PNG");
            }
            return;
        }

        if (Arrays.asList("docx", "doc", "rtf", "txt", "md").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("SUMMARY", "UPPERCASE", "CLEAN TXT", "COPY TXT");
            } else {
                List<String> opts = IS_MAC
                        ? Arrays.asList("PDF", "DOCX", "TXT", "HTML", "RTF")
                        : Arrays.asList("PDF", "DOCX", "TXT", "HTML");
                currentSlices = opts.stream().filter(s -> !s.equalsIgnoreCase(ext)).toList();
            }
            return;
        }

        if (Arrays.asList("pptx", "ppt").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("EXTRACT TXT", "SLIDE 1 PNG", "COPY TXT");
            } else {
                currentSlices = Arrays.asList("PDF", "PNG", "TXT", "HTML");
            }
            return;
        }

        if (ext.equals("json")) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("SUMMARY", "BEAUTIFY", "MINIFY", "COPY TXT");
            } else {
                currentSlices = Arrays.asList("CSV", "XLSX", "HTML", "PDF", "TXT");
            }
            return;
        }

        if (Arrays.asList("html", "htm", "mhtml").contains(ext)) {
            if (isToolsMode) {
                currentSlices = Arrays.asList("SUMMARY", "CLEAN TXT", "COPY TXT");
            } else {
                currentSlices = Arrays.asList("PDF", "DOCX", "TXT");
            }
            return;
        }

        currentSlices = Arrays.asList("PDF", "TXT", "ZIP ARCHIVE");
    }

    private String getExt(File f) {
        String n = f.getName().toLowerCase();
        return n.contains(".") ? n.substring(n.lastIndexOf('.') + 1) : "";
    }

    private void drawRadialMenu() {
        wheelPane.getChildren().clear();
        double cx = SIZE / 2.0;
        double cy = SIZE / 2.0;
        int count = currentSlices.size();
        double step = 360.0 / count;

        if (blurredBackdropSnapshot != null) {
            ImageView frostedBg = new ImageView(blurredBackdropSnapshot);
            frostedBg.setFitWidth(SIZE);
            frostedBg.setFitHeight(SIZE);
            frostedBg.setEffect(new BoxBlur(22, 22, 3));

            Circle outerClip = new Circle(cx, cy, OUTER_R + 6);
            Circle innerHole = new Circle(cx, cy, INNER_R - 4);
            javafx.scene.shape.Shape ringClip = javafx.scene.shape.Shape.subtract(outerClip, innerHole);
            frostedBg.setClip(ringClip);
            wheelPane.getChildren().add(frostedBg);
        }

        boolean darkPalette = (themeMode == 0) ? isOsDarkMode : (themeMode != 2);
        Color activeAccent = switch (themeMode) {
            case 1 -> Color.rgb(212, 175, 55);
            case 2 -> Color.rgb(2, 132, 199);
            case 3 -> Color.rgb(16, 185, 129);
            default -> osAccentColor;
        };

        LinearGradient sliceGlassBase = darkPalette
                ? new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0.0, Color.rgb(22, 27, 40, 0.90)),
                        new Stop(0.55, Color.rgb(14, 18, 28, 0.92)),
                        new Stop(1.0, activeAccent.deriveColor(0, 0.65, 0.35, 0.90)))
                : new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0.0, Color.rgb(252, 254, 255, 0.93)),
                        new Stop(0.6, Color.rgb(238, 244, 250, 0.91)),
                        new Stop(1.0, activeAccent.deriveColor(0, 0.25, 1.0, 0.91)));

        LinearGradient sliceGlassHover = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, activeAccent.deriveColor(0, 0.95, 1.05, 0.95)),
                new Stop(1.0, activeAccent.deriveColor(8, 1.0, 0.72, 0.97)));

        Color rimBase = darkPalette ? Color.rgb(255, 255, 255, 0.38) : Color.rgb(15, 23, 42, 0.24);
        Color rimHover = Color.WHITE;
        Color textNormal = darkPalette ? Color.rgb(248, 250, 252) : Color.rgb(15, 23, 42);

        Circle outerHalo = new Circle(cx, cy, OUTER_R + 7);
        outerHalo.setFill(Color.TRANSPARENT);
        outerHalo.setStroke(activeAccent.deriveColor(0, 0.8, 1.0, 0.48));
        outerHalo.setStrokeWidth(1.2);
        wheelPane.getChildren().add(outerHalo);

        double actualGap = count > 1 ? GAP_DEGREES : 0;

        for (int i = 0; i < count; i++) {
            boolean isHovered = (i == hoveredIndex);
            double rOut = isHovered ? OUTER_R + 5 : OUTER_R;
            double startAngle = 90 + (step / 2.0) - (i * step) - (actualGap / 2.0);
            double sweepAngle = -(step - actualGap);

            Path slice = createDonutSlice(cx, cy, INNER_R, rOut, startAngle, sweepAngle);
            slice.setFill(isHovered ? sliceGlassHover : sliceGlassBase);
            slice.setStroke(isHovered ? rimHover : rimBase);
            slice.setStrokeWidth(isHovered ? 1.8 : 1.15);
            slice.setStrokeLineJoin(StrokeLineJoin.ROUND);

            if (isHovered) {
                slice.setEffect(new DropShadow(14, activeAccent.deriveColor(0, 1, 1, 0.65)));
            }

            double midRad = Math.toRadians(90 - (i * step));
            double textR = (INNER_R + rOut) / 2.0;
            double tx = cx + textR * Math.cos(midRad);
            double ty = cy - textR * Math.sin(midRad);

            Text label = new Text(currentSlices.get(i));
            label.setFont(Font.font(OS_FONT, FontWeight.EXTRA_BOLD, count > 5 ? 10.0 : 11.0));
            label.setFill(isHovered ? Color.WHITE : textNormal);
            label.setMouseTransparent(true);

            double lw = label.getLayoutBounds().getWidth();
            double lh = label.getLayoutBounds().getHeight();

            Rectangle pill = new Rectangle(
                    tx - (lw / 2.0) - 8,
                    ty - (lh / 2.0) - 2,
                    lw + 16,
                    lh + 4
            );
            pill.setArcWidth(12);
            pill.setArcHeight(12);
            pill.setMouseTransparent(true);
            if (isHovered) {
                pill.setFill(Color.rgb(0, 0, 0, 0.26));
                pill.setStroke(Color.rgb(255, 255, 255, 0.45));
                pill.setStrokeWidth(0.8);
            } else {
                pill.setFill(darkPalette ? Color.rgb(0, 0, 0, 0.38) : Color.rgb(255, 255, 255, 0.68));
                pill.setStroke(darkPalette ? Color.rgb(255, 255, 255, 0.16) : Color.rgb(15, 23, 42, 0.14));
                pill.setStrokeWidth(0.7);
            }

            label.setX(tx - lw / 2.0);
            label.setY(ty + 4);

            wheelPane.getChildren().addAll(slice, pill, label);
        }

        Circle hub = new Circle(cx, cy, INNER_R - 4);
        RadialGradient hubGrad = darkPalette
                ? new RadialGradient(0, 0, 0.35, 0.35, 0.85, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.rgb(34, 42, 60, 0.96)),
                        new Stop(1, Color.rgb(12, 16, 26, 0.98)))
                : new RadialGradient(0, 0, 0.35, 0.35, 0.85, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.rgb(255, 255, 255, 0.98)),
                        new Stop(1, Color.rgb(226, 232, 240, 0.98)));

        hub.setFill(centerHovered ? sliceGlassHover : hubGrad);
        hub.setStroke(centerHovered ? Color.WHITE : activeAccent);
        hub.setStrokeWidth(1.8);

        String extBadge = "SELECT";
        if (activeFiles.size() > 1) {
            extBadge = activeFiles.size() + " FILES";
        } else if (activeFiles.size() == 1) {
            String ext = getExt(activeFiles.get(0));
            extBadge = ext.isEmpty() ? "FILE" : ext.toUpperCase();
        }

        String topLine = centerHovered ? (isToolsMode ? "CONVERT" : "TOOLS") : extBadge;
        String botLine = activeFiles.isEmpty() ? "Browse" : (activeFiles.size() > 1 ? "BATCH MODE" : (isToolsMode ? "TOOLS" : "SPACE:TOOLS"));

        Text hubTitle = new Text(topLine);
        hubTitle.setFont(Font.font(OS_FONT, FontWeight.EXTRA_BOLD, 11.5));
        hubTitle.setFill(centerHovered ? Color.WHITE : (darkPalette ? activeAccent.brighter() : activeAccent.darker()));
        hubTitle.setMouseTransparent(true);
        hubTitle.setX(cx - hubTitle.getLayoutBounds().getWidth() / 2);
        hubTitle.setY(cy - 1);

        Text hubSub = new Text(botLine);
        hubSub.setFont(Font.font(OS_FONT, FontWeight.BOLD, 7.2));
        hubSub.setFill(centerHovered ? Color.WHITE : textNormal.deriveColor(0, 1, 1, 0.78));
        hubSub.setMouseTransparent(true);
        hubSub.setX(cx - hubSub.getLayoutBounds().getWidth() / 2);
        hubSub.setY(cy + 11);

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

    private void setupInteractions(Scene scene) {
        scene.setCursor(Cursor.HAND);
        scene.setOnMouseMoved(e -> updateHoverState(e.getX(), e.getY()));

        scene.setOnMouseClicked(e -> {
            double dx = e.getX() - (SIZE / 2.0);
            double dy = e.getY() - (SIZE / 2.0);
            double dist = Math.hypot(dx, dy);
            boolean altCopyMode = e.isAltDown();

            if (dist < INNER_R) {
                handleCenterHubAction();
            } else if (dist <= OUTER_R + 5 && hoveredIndex >= 0 && hoveredIndex < currentSlices.size()) {
                triggerSliceIndex(hoveredIndex, altCopyMode);
            } else {
                radialStage.hide();
            }
        });

        scene.setOnKeyPressed(e -> {
            KeyCode code = e.getCode();
            if (code == KeyCode.ESCAPE) {
                radialStage.hide();
            } else if (code == KeyCode.SPACE) {
                handleCenterHubAction();
            } else if (code == KeyCode.TAB) {
                e.consume();
                themeMode = (themeMode + 1) % 4;
                drawRadialMenu();
            } else if (code == KeyCode.RIGHT || code == KeyCode.DOWN) {
                if (!currentSlices.isEmpty()) {
                    hoveredIndex = (hoveredIndex + 1) % currentSlices.size();
                    drawRadialMenu();
                }
            } else if (code == KeyCode.LEFT || code == KeyCode.UP) {
                if (!currentSlices.isEmpty()) {
                    hoveredIndex = (hoveredIndex - 1 + currentSlices.size()) % currentSlices.size();
                    drawRadialMenu();
                }
            } else if (code == KeyCode.ENTER && hoveredIndex >= 0 && hoveredIndex < currentSlices.size()) {
                triggerSliceIndex(hoveredIndex, e.isAltDown());
            } else if (code.isDigitKey()) {
                String name = code.getName();
                if (!name.isEmpty() && Character.isDigit(name.charAt(name.length() - 1))) {
                    int digit = name.charAt(name.length() - 1) - '1';
                    if (digit >= 0 && digit < currentSlices.size()) {
                        triggerSliceIndex(digit, e.isAltDown());
                    }
                }
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
                activeFiles = new ArrayList<>(e.getDragboard().getFiles());
                if (hoveredIndex >= 0 && hoveredIndex < currentSlices.size()) {
                    triggerSliceIndex(hoveredIndex, false);
                }
            }
            e.setDropCompleted(true);
        });
    }

    private void handleCenterHubAction() {
        if (activeFiles.isEmpty()) {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select File(s) for M.I.R.A.");
            List<File> chosen = fc.showOpenMultipleDialog(radialStage);
            if (chosen != null && !chosen.isEmpty()) {
                activeFiles = new ArrayList<>(chosen);
                updateSlicesForFiles(activeFiles);
                drawRadialMenu();
            }
        } else if (activeFiles.size() == 1) {
            isToolsMode = !isToolsMode;
            updateSlicesForFiles(activeFiles);
            drawRadialMenu();
        }
    }

    private void triggerSliceIndex(int index, boolean copyToClipboard) {
        String action = currentSlices.get(index);
        List<File> targets = new ArrayList<>(activeFiles);
        hoveredIndex = -1;
        isToolsMode = false;
        radialStage.hide();

        if (targets.isEmpty()) {
            Platform.runLater(this::handleCenterHubAction);
            return;
        }

        if (targets.size() == 1 && action.equals("ADD BG")) {
            Platform.runLater(() -> showAddBgModal(targets.get(0)));
        } else {
            CompletableFuture.runAsync(() -> executeBatchOrSingleAction(targets, action, copyToClipboard));
        }
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
        } else if (dist <= OUTER_R + 5) {
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

    private List<File> getCurrentlySelectedFilesFromOS() {
        List<File> results = new ArrayList<>();
        try {
            if (IS_MAC) {
                String[] cmd = {
                        "osascript",
                        "-e", "tell application \"Finder\"",
                        "-e", "set sel to selection as alias list",
                        "-e", "set out to \"\"",
                        "-e", "repeat with f in sel",
                        "-e", "set out to out & (POSIX path of f) & linefeed",
                        "-e", "end repeat",
                        "-e", "return out",
                        "-e", "end tell"
                };
                Process p = new ProcessBuilder(cmd).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            File f = new File(line.trim());
                            if (f.exists() && f.isFile()) results.add(f);
                        }
                    }
                }
            } else {
                String psCommand = "$shell = New-Object -ComObject Shell.Application; " +
                        "foreach ($w in $shell.Windows()) { " +
                        "if ($w.Document.SelectedItems().Count -gt 0) { " +
                        "foreach ($item in $w.Document.SelectedItems()) { $item.Path }; break } }";
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command", psCommand).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            File f = new File(line.trim());
                            if (f.exists() && f.isFile()) results.add(f);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    public void runHeadlessConversion(File file, String action) {
        executeBatchOrSingleAction(Collections.singletonList(file), action, false);
    }

    private void executeBatchOrSingleAction(List<File> files, String action, boolean copyToClipboard) {
        try {
            if (files.isEmpty()) return;
            File first = files.get(0);
            File parentDir = first.getParentFile();

            if (action.equals("MERGE PDFS")) {
                List<File> pdfFiles = files.stream().filter(f -> getExt(f).equals("pdf")).toList();
                if (pdfFiles.isEmpty()) return;
                File mergedOut = new File(parentDir, "Merged_" + System.currentTimeMillis() + ".pdf");
                PDFMergerUtility merger = new PDFMergerUtility();
                merger.setDestinationFileName(mergedOut.getAbsolutePath());
                for (File f : pdfFiles) {
                    merger.addSource(f);
                }
                merger.mergeDocuments(org.apache.pdfbox.io.IOUtils.createMemoryOnlyStreamCache());
                finishConversionNotification("Merged " + pdfFiles.size() + " PDFs", mergedOut);
                return;
            }

            if (action.equals("COMBINE PDF")) {
                File combinedPdf = new File(parentDir, "Combined_Images_" + System.currentTimeMillis() + ".pdf");
                try (PDDocument doc = new PDDocument()) {
                    int addedCount = 0;
                    for (File imgFile : files) {
                        BufferedImage img = readImageWithMacFallback(imgFile);
                        if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
                            PDPage page = new PDPage();
                            doc.addPage(page);
                            PDImageXObject pdImage = LosslessFactoryBridge.fromBufferedImage(doc, img);
                            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                                float scale = Math.min(520f / img.getWidth(), 720f / img.getHeight());
                                float drawW = img.getWidth() * scale;
                                float drawH = img.getHeight() * scale;
                                cs.drawImage(pdImage, (612 - drawW) / 2f, (792 - drawH) / 2f, drawW, drawH);
                            }
                            addedCount++;
                        }
                    }
                    if (addedCount > 0) {
                        doc.save(combinedPdf);
                        finishConversionNotification("Combined " + addedCount + " Images -> PDF", combinedPdf);
                    }
                }
                return;
            }

            if (action.equals("ZIP ARCHIVE")) {
                File zipOut = new File(parentDir, (files.size() == 1 ? stripExt(first.getName()) : "MIRA_Archive") + ".zip");
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipOut))) {
                    Set<String> seenNames = new HashSet<>();
                    for (File f : files) {
                        String entryName = f.getName();
                        int dup = 1;
                        while (seenNames.contains(entryName)) {
                            int dot = f.getName().lastIndexOf('.');
                            if (dot > 0) {
                                entryName = f.getName().substring(0, dot) + "_" + dup + f.getName().substring(dot);
                            } else {
                                entryName = f.getName() + "_" + dup;
                            }
                            dup++;
                        }
                        seenNames.add(entryName);
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(f.toPath(), zos);
                        zos.closeEntry();
                    }
                }
                finishConversionNotification("Archived " + files.size() + " file(s)", zipOut);
                return;
            }

            String normalizedAction = action.startsWith("BATCH ") ? action.substring(6).trim() : action;
            File lastOutput = null;
            for (File f : files) {
                lastOutput = executeSingleFileAction(f, normalizedAction, copyToClipboard);
            }

            if (lastOutput != null && lastOutput.exists()) {
                String msg = files.size() > 1
                        ? "Batch converted " + files.size() + " files -> " + normalizedAction
                        : "Created " + lastOutput.getName();
                finishConversionNotification(msg, lastOutput);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File executeSingleFileAction(File file, String action, boolean copyToClipboard) throws Exception {
        String path = file.getAbsolutePath();
        int dotIndex = path.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? path.substring(0, dotIndex) : path;
        String ext = getExt(file);
        String targetExt = action.toLowerCase();
        File outputCreated = null;

        if (Arrays.asList("xlsx", "xls", "csv", "tsv").contains(ext)) {
            List<List<String>> table = readSpreadsheetToTable(file, ext);
            if (table.isEmpty()) return null;

            if (action.equals("COPY TXT")) {
                String txt = table.stream().map(r -> String.join("\t", r)).collect(Collectors.joining("\n"));
                copyStringToSystemClipboard(txt);
                showGlossyToast("Copied Spreadsheet Text to Clipboard!", null);
                return null;
            } else if (action.equals("SORT A-Z") && table.size() > 1) {
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
                copyStringToSystemClipboard(summary);
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
                if (copyToClipboard) copyStringToSystemClipboard(txt);
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
                if (copyToClipboard) copyStringToSystemClipboard(json.toString());
            } else if (action.equals("PDF")) {
                String formatted = table.stream().map(r -> String.join("  |  ", r)).collect(Collectors.joining("\n"));
                outputCreated = new File(baseName + ".pdf");
                createWordWrappedPdfFromText(formatted, outputCreated);
            }
        } else if (Arrays.asList("pptx", "ppt").contains(ext)) {
            try (FileInputStream fis = new FileInputStream(file);
                 XMLSlideShow ppt = new XMLSlideShow(fis)) {
                Dimension pgsize = ppt.getPageSize();
                List<XSLFSlide> slides = ppt.getSlides();

                if (action.equals("TXT") || action.equals("EXTRACT TXT") || action.equals("HTML") || action.equals("COPY TXT")) {
                    StringBuilder sb = new StringBuilder();
                    int sNum = 1;
                    for (XSLFSlide slide : slides) {
                        sb.append("--- Slide ").append(sNum++).append(" ---\n");
                        for (XSLFShape shape : slide.getShapes()) {
                            if (shape instanceof XSLFTextShape ts) sb.append(ts.getText()).append("\n");
                        }
                        sb.append("\n");
                    }
                    copyStringToSystemClipboard(sb.toString());
                    if (action.equals("COPY TXT")) {
                        showGlossyToast("Copied Slides Text to Clipboard!", null);
                        return null;
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
        } else if (Arrays.asList("docx", "doc", "rtf", "txt", "md", "html", "htm", "mhtml").contains(ext)) {
            String extractedText = extractTextFromDocOrWeb(file, ext);
            if (action.equals("COPY TXT")) {
                copyStringToSystemClipboard(extractedText);
                showGlossyToast("Copied Document Text to Clipboard!", null);
                return null;
            } else if (action.equals("UPPERCASE")) {
                outputCreated = new File(baseName + "_UPPER.txt");
                Files.writeString(outputCreated.toPath(), extractedText.toUpperCase());
            } else if (action.equals("CLEAN TXT") || action.equals("TXT")) {
                outputCreated = new File(baseName + ".txt");
                Files.writeString(outputCreated.toPath(), extractedText.trim());
                if (copyToClipboard) copyStringToSystemClipboard(extractedText.trim());
            } else if (action.equals("SUMMARY")) {
                String[] words = extractedText.trim().split("\\s+");
                String summary = "File: " + file.getName() + "\nWord Count: " + words.length
                        + "\nCharacters: " + extractedText.length() + "\n\n--- Preview ---\n"
                        + extractedText.substring(0, Math.min(800, extractedText.length()));
                outputCreated = new File(baseName + "_Summary.txt");
                Files.writeString(outputCreated.toPath(), summary);
                copyStringToSystemClipboard(summary);
            } else if (action.equals("PDF")) {
                outputCreated = new File(baseName + ".pdf");
                createWordWrappedPdfFromText(extractedText, outputCreated);
            } else if (action.equals("DOCX")) {
                outputCreated = new File(baseName + "_Converted.docx");
                writeTextToDocx(extractedText, outputCreated);
            } else if (IS_MAC && Arrays.asList("html", "rtf").contains(targetExt) && !ext.equals("mhtml")) {
                outputCreated = new File(baseName + "_Converted." + targetExt);
                new ProcessBuilder("textutil", "-convert", targetExt, path, "-output", outputCreated.getAbsolutePath())
                        .start().waitFor();
            } else {
                outputCreated = new File(baseName + "_Converted." + targetExt);
                if (targetExt.equals("html")) {
                    String html = "<html><head><meta charset='UTF-8'></head><body style='font-family:-apple-system,sans-serif;padding:24px;line-height:1.6;'><pre style='white-space:pre-wrap;'>"
                            + extractedText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</pre></body></html>";
                    Files.writeString(outputCreated.toPath(), html);
                } else {
                    Files.writeString(outputCreated.toPath(), extractedText);
                }
            }
        } else if (ext.equals("pdf")) {
            try (PDDocument doc = Loader.loadPDF(file)) {
                if (action.equals("SPLIT PAGES")) {
                    int total = doc.getNumberOfPages();
                    for (int p = 0; p < total; p++) {
                        try (PDDocument single = new PDDocument()) {
                            single.importPage(doc.getPage(p));
                            outputCreated = new File(baseName + "_Page" + (p + 1) + ".pdf");
                            single.save(outputCreated);
                        }
                    }
                } else if (action.equals("COPY TXT")) {
                    String text = new PDFTextStripper().getText(doc);
                    copyStringToSystemClipboard(text);
                    showGlossyToast("Copied PDF Text to Clipboard!", null);
                    return null;
                } else if (action.equals("SUMMARY")) {
                    String text = new PDFTextStripper().getText(doc);
                    String[] words = text.trim().isEmpty() ? new String[0] : text.trim().split("\\s+");
                    String summary = "File: " + file.getName()
                            + "\nTotal Pages: " + doc.getNumberOfPages()
                            + "\nWord Count: " + words.length
                            + "\nCharacter Count: " + text.length()
                            + "\n\n--- Preview ---\n"
                            + text.substring(0, Math.min(800, text.length()));
                    outputCreated = new File(baseName + "_Summary.txt");
                    Files.writeString(outputCreated.toPath(), summary);
                    copyStringToSystemClipboard(summary);
                } else if (action.equals("TXT") || action.equals("HTML")) {
                    String text = new PDFTextStripper().getText(doc);
                    outputCreated = new File(baseName + (action.equals("HTML") ? ".html" : ".txt"));
                    if (action.equals("HTML")) {
                        text = "<html><head><meta charset='UTF-8'></head><body style='font-family:-apple-system,sans-serif;padding:24px;line-height:1.6;'><pre style='white-space:pre-wrap;'>"
                                + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</pre></body></html>";
                    }
                    Files.writeString(outputCreated.toPath(), text);
                    if (copyToClipboard) copyStringToSystemClipboard(text);
                } else if (action.equals("DOCX")) {
                    String text = new PDFTextStripper().getText(doc);
                    outputCreated = new File(baseName + ".docx");
                    writeTextToDocx(text, outputCreated);
                } else if (action.equals("PNG") || action.equals("JPG") || action.equals("ALL PAGES PNG")) {
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
        } else if (ext.equals("json")) {
            String rawJson = Files.readString(file.toPath());
            if (action.equals("COPY TXT")) {
                copyStringToSystemClipboard(rawJson);
                showGlossyToast("Copied JSON to Clipboard!", null);
                return null;
            } else if (action.equals("SUMMARY")) {
                List<List<String>> jsonTable = parseJsonToTable(rawJson);
                int recordCount = Math.max(0, jsonTable.size() - 1);
                int fieldCount = jsonTable.isEmpty() ? 0 : jsonTable.get(0).size();
                String keys = jsonTable.isEmpty() ? "none" : String.join(", ", jsonTable.get(0));
                String summary = "File: " + file.getName()
                        + "\nEstimated Records: " + recordCount
                        + "\nFields (" + fieldCount + "): " + keys
                        + "\nFile Size: " + file.length() + " bytes"
                        + "\n\n--- Preview ---\n"
                        + rawJson.substring(0, Math.min(800, rawJson.length()));
                outputCreated = new File(baseName + "_Summary.txt");
                Files.writeString(outputCreated.toPath(), summary);
                copyStringToSystemClipboard(summary);
            } else if (action.equals("BEAUTIFY")) {
                String pretty = formatJson(rawJson, true);
                outputCreated = new File(baseName + "_Pretty.json");
                Files.writeString(outputCreated.toPath(), pretty);
                if (copyToClipboard) copyStringToSystemClipboard(pretty);
            } else if (action.equals("MINIFY")) {
                String min = formatJson(rawJson, false);
                outputCreated = new File(baseName + "_Min.json");
                Files.writeString(outputCreated.toPath(), min);
                if (copyToClipboard) copyStringToSystemClipboard(min);
            } else {
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
                    if (copyToClipboard) copyStringToSystemClipboard(rawJson);
                } else if (action.equals("PDF")) {
                    outputCreated = new File(baseName + ".pdf");
                    createWordWrappedPdfFromText(rawJson, outputCreated);
                }
            }
        } else if (Arrays.asList("jpg", "jpeg", "png", "heic", "webp", "bmp", "tiff").contains(ext)) {
            if (action.equals("STRIP EXIF")) {
                BufferedImage cleanImg = readImageWithMacFallback(file);
                if (cleanImg != null) {
                    outputCreated = new File(baseName + "_Clean.png");
                    ImageIO.write(cleanImg, "png", outputCreated);
                }
            } else if (action.equals("PDF")) {
                BufferedImage img = readImageWithMacFallback(file);
                if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
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
            } else if (action.equals("COMPRESS")) {
                outputCreated = new File(baseName + "_Compressed.jpg");
                boolean compressed = false;
                if (IS_MAC) {
                    try {
                        Process p = new ProcessBuilder("sips", "-s", "format", "jpeg", "-s", "formatOptions", "50",
                                path, "--out", outputCreated.getAbsolutePath()).start();
                        if (p.waitFor() == 0 && outputCreated.exists() && outputCreated.length() > 0) {
                            compressed = true;
                        }
                    } catch (Exception ignored) {}
                }
                if (!compressed) {
                    BufferedImage orig = readImageWithMacFallback(file);
                    if (orig != null) {
                        BufferedImage rgb = new BufferedImage(orig.getWidth(), orig.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = rgb.createGraphics();
                        g.drawImage(orig, 0, 0, java.awt.Color.WHITE, null);
                        g.dispose();

                        Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
                        if (writers.hasNext()) {
                            javax.imageio.ImageWriter writer = writers.next();
                            try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(outputCreated)) {
                                writer.setOutput(ios);
                                javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
                                if (param.canWriteCompressed()) {
                                    param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                                    param.setCompressionQuality(0.5f);
                                }
                                writer.write(null, new javax.imageio.IIOImage(rgb, null, null), param);
                            } finally {
                                writer.dispose();
                            }
                        }
                    }
                }
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
                boolean sipsOk = false;
                try {
                    Process p = new ProcessBuilder("sips", "-s", "format", sipsFmt, path, "--out", outputCreated.getAbsolutePath())
                            .start();
                    sipsOk = (p.waitFor() == 0 && outputCreated.exists() && outputCreated.length() > 0);
                } catch (Exception ignored) {}
                if (!sipsOk) {
                    BufferedImage img = readImageWithMacFallback(file);
                    if (img != null) {
                        if (targetExt.equals("jpg") || targetExt.equals("bmp")) {
                            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                            Graphics2D g = rgb.createGraphics();
                            g.drawImage(img, 0, 0, java.awt.Color.WHITE, null);
                            g.dispose();
                            img = rgb;
                        }
                        ImageIO.write(img, targetExt, outputCreated);
                    }
                }
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

        return outputCreated;
    }

    private void finishConversionNotification(String message, File createdFile) {
        if (radialStage != null) {
            showGlossyToast(message, createdFile);
        }
        if (IS_MAC) {
            try { new ProcessBuilder("afplay", "/System/Library/Sounds/Glass.aiff").start(); } catch (Exception ignored) {}
        }
    }

    private void copyStringToSystemClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        } catch (Exception ignored) {}
    }

    private String stripExt(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }

    private void createWordWrappedPdfFromText(String text, File outPdf) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float fontSize = 10.5f;
            float leading = 15.0f;
            float margin = 48f;
            float maxWidth = 612f - (2 * margin);

            List<String> wrappedLines = new ArrayList<>();
            for (String rawLine : text.split("\\r?\\n")) {
                String sanitized = rawLine.replace("\t", "    ").replaceAll("[^\\x20-\\x7E]", " ");
                if (sanitized.isBlank()) {
                    wrappedLines.add("");
                    continue;
                }
                String[] words = sanitized.split(" ");
                StringBuilder currentLine = new StringBuilder();
                for (String word : words) {
                    if (word.isEmpty()) continue;
                    float wordWidth = (font.getStringWidth(word) / 1000f) * fontSize;
                    if (wordWidth > maxWidth) {
                        if (!currentLine.isEmpty()) {
                            wrappedLines.add(currentLine.toString());
                            currentLine = new StringBuilder();
                        }
                        StringBuilder chunk = new StringBuilder();
                        for (int c = 0; c < word.length(); c++) {
                            char ch = word.charAt(c);
                            if ((font.getStringWidth(chunk.toString() + ch) / 1000f) * fontSize > maxWidth) {
                                wrappedLines.add(chunk.toString());
                                chunk = new StringBuilder().append(ch);
                            } else {
                                chunk.append(ch);
                            }
                        }
                        if (!chunk.isEmpty()) currentLine = chunk;
                        continue;
                    }

                    String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
                    float width = (font.getStringWidth(candidate) / 1000f) * fontSize;
                    if (width > maxWidth && !currentLine.isEmpty()) {
                        wrappedLines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        currentLine = new StringBuilder(candidate);
                    }
                }
                if (!currentLine.isEmpty()) wrappedLines.add(currentLine.toString());
            }

            if (wrappedLines.isEmpty()) wrappedLines.add("");

            int lineIdx = 0;
            while (lineIdx < wrappedLines.size()) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.setLeading(leading);
                    cs.newLineAtOffset(margin, 740);
                    for (int row = 0; row < 45 && lineIdx < wrappedLines.size(); row++, lineIdx++) {
                        String line = wrappedLines.get(lineIdx);
                        if (!line.isEmpty()) {
                            cs.showText(line);
                        }
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            doc.save(outPdf);
        }
    }

    private BufferedImage readImageWithMacFallback(File file) throws Exception {
        BufferedImage img = null;
        try {
            img = ImageIO.read(file);
        } catch (Exception ignored) {}

        if (img == null && IS_MAC) {
            File tmp = File.createTempFile("mira_decode", ".png");
            try {
                Process p = new ProcessBuilder("sips", "-s", "format", "png", file.getAbsolutePath(), "--out", tmp.getAbsolutePath()).start();
                if (p.waitFor() == 0 && tmp.exists() && tmp.length() > 0) {
                    img = ImageIO.read(tmp);
                }
            } finally {
                tmp.delete();
            }
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
                String clean = line.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
                run.setText(clean);
                run.setFontSize(11);
            }
            doc.write(fos);
        }
    }

    private java.util.List<java.util.List<String>> parseJsonToTable(String rawJson) {
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
                String v = kv.group(3) != null ? kv.group(3) : (kv.group(2) != null ? kv.group(2).trim() : "");
                headers.add(k);
                rowMap.put(k, v);
            }
            if (!rowMap.isEmpty()) records.add(rowMap);
        }

        java.util.List<java.util.List<String>> table = new ArrayList<>();
        if (headers.isEmpty()) {
            table.add(Collections.singletonList("Content"));
            for (String line : rawJson.split("\\r?\\n")) {
                if (!line.trim().isEmpty()) {
                    table.add(Collections.singletonList(line.trim()));
                }
            }
            return table;
        }

        java.util.List<String> headerList = new ArrayList<>(headers);
        table.add(headerList);
        for (Map<String, String> rec : records) {
            java.util.List<String> row = new ArrayList<>();
            for (String h : headerList) {
                row.add(rec.getOrDefault(h, ""));
            }
            table.add(row);
        }
        return table;
    }

    private String formatJson(String raw, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inQuotes = false;
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (escaped) {
                sb.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                sb.append(ch);
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inQuotes = !inQuotes;
                sb.append(ch);
                continue;
            }
            if (inQuotes) {
                sb.append(ch);
                continue;
            }
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch == '{' || ch == '[') {
                sb.append(ch);
                if (pretty) {
                    indent++;
                    sb.append("\n").append("  ".repeat(indent));
                }
            } else if (ch == '}' || ch == ']') {
                if (pretty) {
                    indent = Math.max(0, indent - 1);
                    sb.append("\n").append("  ".repeat(indent));
                }
                sb.append(ch);
            } else if (ch == ',') {
                sb.append(ch);
                if (pretty) {
                    sb.append("\n").append("  ".repeat(indent));
                }
            } else if (ch == ':') {
                sb.append(ch);
                if (pretty) {
                    sb.append(" ");
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private String buildHtmlTable(List<List<String>> table) {
        StringBuilder html = new StringBuilder("<html><head><meta charset='UTF-8'><style>"
                + "body{font-family:-apple-system,sans-serif;padding:28px;background:#0f172a;color:#f8fafc;}"
                + "table{border-collapse:collapse;width:100%;background:#1e293b;border-radius:8px;overflow:hidden;}"
                + "th,td{border:1px solid #334155;padding:10px 14px;text-align:left;font-size:13px;}"
                + "th{background:#0284c7;color:#ffffff;text-transform:uppercase;letter-spacing:0.05em;}</style></head><body><table>\n");
        for (int i = 0; i < table.size(); i++) {
            html.append("<tr>");
            for (String cell : table.get(i)) {
                html.append(i == 0 ? "<th>" : "<td>")
                        .append(cell.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
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
            List<String> lines = Files.readAllLines(file.toPath());
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (i == 0 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                rows.add(Arrays.stream(line.split(sep, -1)).map(String::trim).collect(Collectors.toList()));
            }
        } else {
            try (Workbook wb = WorkbookFactory.create(file)) {
                if (wb.getNumberOfSheets() == 0) return rows;
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
                    .map(c -> {
                        if (c == null) return "\"\"";
                        if (c.contains(",") || c.contains("\"") || c.contains("\n") || c.contains("\r")) {
                            return "\"" + c.replace("\"", "\"\"") + "\"";
                        }
                        return c;
                    })
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
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static class LosslessFactoryBridge {
        static PDImageXObject fromBufferedImage(PDDocument doc, BufferedImage img) throws Exception {
            try {
                return org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, img);
            } catch (Throwable t) {
                File tmp = File.createTempFile("mira_img", ".png");
                try {
                    ImageIO.write(img, "png", tmp);
                    return PDImageXObject.createFromFileByContent(tmp, doc);
                } finally {
                    tmp.delete();
                }
            }
        }
    }

    private void showGlossyToast(String title, File createdFile) {
        Platform.runLater(() -> {
            Stage toast = new Stage(StageStyle.TRANSPARENT);
            toast.setAlwaysOnTop(true);

            HBox banner = new HBox(14);
            banner.setAlignment(Pos.CENTER_LEFT);
            banner.setStyle("-fx-background-color: rgba(15, 23, 42, 0.92); -fx-padding: 12 18; "
                    + "-fx-background-radius: 16; -fx-border-color: rgba(255, 255, 255, 0.45); -fx-border-radius: 16;");

            Label lbl = new Label("✦  " + title);
            lbl.setFont(Font.font(OS_FONT, FontWeight.BOLD, 12));
            lbl.setTextFill(Color.WHITE);
            banner.getChildren().add(lbl);

            if (createdFile != null && createdFile.exists()) {
                Button revealBtn = new Button("Reveal");
                revealBtn.setStyle("-fx-background-color: #38BDF8; -fx-text-fill: #0F172A; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 4 12;");
                revealBtn.setOnAction(e -> {
                    try {
                        if (IS_MAC) new ProcessBuilder("open", "-R", createdFile.getAbsolutePath()).start();
                        else new ProcessBuilder("explorer.exe", "/select,", createdFile.getAbsolutePath()).start();
                        toast.close();
                    } catch (Exception ignored) {}
                });
                banner.getChildren().add(revealBtn);
            }

            Scene sc = new Scene(banner, Color.TRANSPARENT);
            toast.setScene(sc);
            Rectangle2D bounds = Screen.getPrimary() != null ? Screen.getPrimary().getVisualBounds() : new Rectangle2D(0, 0, 1920, 1080);
            toast.setX(bounds.getMaxX() - 380);
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
            modal.setTitle("M.I.R.A. Studio — Glossy Frame");
            VBox root = new VBox(14);
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: #0F172A; -fx-padding: 24;");
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
            saveBtn.setStyle("-fx-background-color: #38BDF8; -fx-text-fill: #0F172A; -fx-background-radius: 8; -fx-padding: 8 22; -fx-font-weight: bold;");
            saveBtn.setOnAction(e -> {
                try {
                    String path = file.getAbsolutePath();
                    File out = new File(path.substring(0, path.lastIndexOf('.')) + "_Framed.png");
                    ImageIO.write(latestRender[0], "png", out);
                    showGlossyToast("Exported " + out.getName(), out);
                    modal.close();
                } catch (Exception ex) { ex.printStackTrace(); }
            });
            root.getChildren().addAll(previewView, padSlider, cornerSlider, saveBtn);
            modal.setScene(new Scene(root, 440, 420));
            modal.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private BufferedImage renderStyledBackground(BufferedImage fg, int pad, int radius) {
        int w = fg.getWidth(), h = fg.getHeight();
        int totalW = w + pad * 2, totalH = h + pad * 2;
        BufferedImage out = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, new java.awt.Color(56, 189, 248), totalW, totalH, new java.awt.Color(37, 99, 235)));
        g2.fillRect(0, 0, totalW, totalH);
        g2.setColor(new java.awt.Color(0, 0, 0, 100));
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

    public void summonRadialMenu() {
        if (shortcutPaused) return;
        Point p;
        try {
            java.awt.PointerInfo pi = MouseInfo.getPointerInfo();
            p = (pi != null) ? pi.getLocation() : new Point(500, 500);
        } catch (Exception e) {
            p = new Point(500, 500);
        }
        final Point cursor = p;

        CompletableFuture.runAsync(() -> {
            syncWithUserOsTheme();
            List<File> selected = getCurrentlySelectedFilesFromOS();

            Platform.runLater(() -> {
                if (radialStage.isShowing()) {
                    radialStage.hide();
                    return;
                }

                Rectangle2D bounds = Screen.getPrimary() != null ? Screen.getPrimary().getVisualBounds() : new Rectangle2D(0, 0, 1920, 1080);
                double targetX = Math.max(bounds.getMinX() + 8, Math.min(cursor.x - SIZE / 2.0, bounds.getMaxX() - SIZE - 8));
                double targetY = Math.max(bounds.getMinY() + 8, Math.min(cursor.y - SIZE / 2.0, bounds.getMaxY() - SIZE - 8));

                try {
                    Robot robot = new Robot();
                    blurredBackdropSnapshot = robot.getScreenCapture(null, new Rectangle2D(targetX, targetY, SIZE, SIZE));
                } catch (Throwable ignored) {
                    blurredBackdropSnapshot = null;
                }

                activeFiles = selected;
                isToolsMode = false;
                hoveredIndex = -1;
                centerHovered = false;
                updateSlicesForFiles(activeFiles);
                drawRadialMenu();

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
                    }

                    boolean isMod = modifierDown || ((e.getModifiers() & (IS_MAC ? NativeKeyEvent.META_MASK : NativeKeyEvent.SHIFT_MASK)) != 0);
                    if (isMod && code == NativeKeyEvent.VC_E) {
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
        if (IS_MAC) {
            System.setProperty("apple.awt.UIElement", "true");
        }
        launch(args);
    }
}