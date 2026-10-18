package com.radialtool;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;

import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RadialConverterApp extends Application {

    // 1. Detect OS (Mac vs Windows)
    private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");

    // 2. OS-Specific Design Tokens
    private static final String OS_FONT = IS_MAC ? "SF Pro Display" : "Segoe UI Semibold";
    private static final Color SLICE_BASE = IS_MAC ? Color.rgb(248, 222, 198, 0.92) : Color.rgb(32, 32, 35, 0.94);
    private static final Color SLICE_HOVER = IS_MAC ? Color.rgb(235, 87, 52, 0.96) : Color.rgb(0, 120, 212, 0.96);
    private static final Color TEXT_BASE = IS_MAC ? Color.rgb(70, 50, 40) : Color.rgb(240, 240, 240);
    private static final Color TEXT_HOVER = Color.WHITE;
    private static final Color STROKE_COLOR = IS_MAC ? Color.rgb(255, 255, 255, 0.80) : Color.rgb(255, 255, 255, 0.20);

    private Stage radialStage;
    private Pane wheelPane; // Using Pane instead of Group so the circle NEVER shifts or clips!
    private boolean isToolsMode = false;
    private List<String> currentSlices = Arrays.asList("DOCX", "JPG", "PNG", "TXT");
    private int hoveredIndex = -1;
    private boolean centerHovered = false;
    private File draggedFile = null;
    private PauseTransition modeSwitchTimer;

    // Extra padding around OUTER_R so drop shadow and circle never clip
    private static final double SIZE = 380;
    private static final double OUTER_R = 145;
    private static final double INNER_R = 54;

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false); // Runs invisibly in the background

        radialStage = new Stage(StageStyle.TRANSPARENT);
        radialStage.setAlwaysOnTop(true);

        wheelPane = new Pane();
        wheelPane.setPrefSize(SIZE, SIZE);
        wheelPane.setMinSize(SIZE, SIZE);
        wheelPane.setMaxSize(SIZE, SIZE);
        wheelPane.setStyle("-fx-background-color: transparent;");

        DropShadow shadow = new DropShadow();
        shadow.setRadius(IS_MAC ? 24 : 14);
        shadow.setColor(Color.rgb(0, 0, 0, IS_MAC ? 0.32 : 0.50));
        wheelPane.setEffect(shadow);

        Scene scene = new Scene(wheelPane, SIZE, SIZE, Color.TRANSPARENT);
        setupDragAndDrop(scene);

        // Hold file in center ring for 500ms to toggle between Convert <-> Tools
        modeSwitchTimer = new PauseTransition(Duration.millis(500));
        modeSwitchTimer.setOnFinished(e -> toggleMode());

        radialStage.setScene(scene);
        drawRadialMenu();

        // Start Global Invisible Listener (Command + Drag OR Shift + Drag)
        registerGlobalHook();

        System.out.println("====================================================");
        System.out.println("Radial Drop Tool is running invisibly in background!");
        System.out.println("Drag any file and press COMMAND (⌘) or SHIFT to open");
        System.out.println("====================================================");
    }

    private void toggleMode() {
        isToolsMode = !isToolsMode;
        updateSlicesForFile(draggedFile);
        drawRadialMenu();
    }

    private void updateSlicesForFile(File file) {
        if (isToolsMode) {
            currentSlices = Arrays.asList("COMPRESS", "METADATA", "EDIT", "ANNOTATE", "ADD BG", "CROP", "REDACT");
            return;
        }
        if (file != null) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".pdf")) {
                currentSlices = Arrays.asList("DOCX", "JPG", "PNG", "TXT");
            } else {
                currentSlices = Arrays.asList("PNG", "WEBP", "HEIC", "TIFF", "AVIF", "BMP", "PDF", "DOCX");
            }
        }
    }

    // =========================================================
    // 1. FULL CENTERED RADIAL WHEEL (No Clipping!)
    // =========================================================
    private void drawRadialMenu() {
        wheelPane.getChildren().clear();
        double cx = SIZE / 2.0;
        double cy = SIZE / 2.0;
        int count = currentSlices.size();
        double step = 360.0 / count;

        // Outer subtle glass rim
        Circle outerRim = new Circle(cx, cy, OUTER_R + 6);
        outerRim.setFill(Color.rgb(255, 235, 215, 0.35));
        outerRim.setStroke(Color.rgb(255, 255, 255, 0.6));
        outerRim.setStrokeWidth(1.5);
        wheelPane.getChildren().add(outerRim);

        for (int i = 0; i < count; i++) {
            // Center top slice at 12 o'clock (90 + step/2)
            double startAngle = 90 + (step / 2.0) - (i * step);
            Path slice = createDonutSlice(cx, cy, INNER_R, OUTER_R, startAngle, -step);

            boolean isHovered = (i == hoveredIndex);
            slice.setFill(isHovered ? SLICE_HOVER : SLICE_BASE);
            slice.setStroke(STROKE_COLOR);
            slice.setStrokeWidth(IS_MAC ? 2.0 : 1.2);

            double midRad = Math.toRadians(startAngle - (step / 2.0));
            double textR = (INNER_R + OUTER_R) / 2.0;
            double tx = cx + textR * Math.cos(midRad);
            double ty = cy - textR * Math.sin(midRad);

            Text label = new Text(currentSlices.get(i));
            label.setFont(Font.font(OS_FONT, FontWeight.BOLD, count > 5 ? 10 : 12));
            label.setFill(isHovered ? TEXT_HOVER : TEXT_BASE);
            label.setMouseTransparent(true);
            label.setX(tx - label.getLayoutBounds().getWidth() / 2);
            label.setY(ty + 4);

            wheelPane.getChildren().addAll(slice, label);
        }

        // Hollow/Translucent Center Ring so your dragged file sits inside the circle!
        Circle hub = new Circle(cx, cy, INNER_R - 2);
        hub.setFill(centerHovered
                ? Color.rgb(235, 87, 52, 0.45)
                : Color.rgb(255, 255, 255, 0.25));
        hub.setStroke(STROKE_COLOR);
        hub.setStrokeWidth(2.0);

        String centerLabelText = draggedFile != null
                ? (isToolsMode ? "TOOLS" : "CONVERT")
                : "DROP FILE";
        Text hubText = new Text(centerLabelText);
        hubText.setFont(Font.font(OS_FONT, FontWeight.BOLD, 10));
        hubText.setFill(TEXT_BASE);
        hubText.setMouseTransparent(true);
        hubText.setX(cx - hubText.getLayoutBounds().getWidth() / 2);
        hubText.setY(cy + 4);

        wheelPane.getChildren().addAll(hub, hubText);
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
    // 2. DRAG & DROP + MOUSE HOVER HANDLERS
    // =========================================================
    private void updateHoverPosition(double mouseX, double mouseY) {
        double dx = mouseX - (SIZE / 2.0);
        double dy = mouseY - (SIZE / 2.0);
        double dist = Math.hypot(dx, dy);

        if (dist < INNER_R) {
            hoveredIndex = -1;
            if (!centerHovered) {
                centerHovered = true;
                modeSwitchTimer.playFromStart();
                drawRadialMenu();
            }
        } else if (dist <= OUTER_R) {
            centerHovered = false;
            modeSwitchTimer.stop();
            int count = currentSlices.size();
            double step = 360.0 / count;
            // Match top-centered slice angle
            double angle = (Math.toDegrees(Math.atan2(dy, dx)) + 90 + (step / 2.0) + 360) % 360;
            int newHover = (int) (angle / step) % count;
            if (newHover != hoveredIndex) {
                hoveredIndex = newHover;
                drawRadialMenu();
            }
        } else {
            hoveredIndex = -1;
            centerHovered = false;
            modeSwitchTimer.stop();
            drawRadialMenu();
        }
    }

    private void setupDragAndDrop(Scene scene) {
        scene.setOnDragEntered(e -> {
            if (e.getDragboard().hasFiles()) {
                draggedFile = e.getDragboard().getFiles().get(0);
                updateSlicesForFile(draggedFile);
                drawRadialMenu();
                e.acceptTransferModes(TransferMode.COPY);
            }
        });

        scene.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
                updateHoverPosition(e.getX(), e.getY());
            }
        });

        // Also allow normal mouse movement hover if summoned via Command click
        scene.setOnMouseMoved(e -> updateHoverPosition(e.getX(), e.getY()));

        scene.setOnDragExited(e -> {
            hoveredIndex = -1;
            centerHovered = false;
            modeSwitchTimer.stop();
            radialStage.hide();
        });

        scene.setOnDragDropped(e -> {
            int chosenIndex = hoveredIndex;
            File fileToProcess = draggedFile;
            hoveredIndex = -1;
            centerHovered = false;
            isToolsMode = false;
            radialStage.hide();

            if (fileToProcess != null && chosenIndex >= 0 && chosenIndex < currentSlices.size()) {
                String action = currentSlices.get(chosenIndex);
                Platform.runLater(() -> executeFileAction(fileToProcess, action));
            }
            e.setDropCompleted(true);
        });
    }

    // =========================================================
    // 3. FILE CONVERSION & PROCESSING ENGINE
    // =========================================================
    private void executeFileAction(File file, String action) {
        try {
            String path = file.getAbsolutePath();
            int dotIndex = path.lastIndexOf('.');
            String baseName = (dotIndex > 0) ? path.substring(0, dotIndex) : path;
            String ext = (dotIndex > 0) ? path.substring(dotIndex + 1).toLowerCase() : "";

            if (action.equals("ADD BG")) {
                showAddBgModal(file);
                return;
            }
            if (action.equals("CROP")) {
                showCropModal(file);
                return;
            }
            if (action.equals("COMPRESS")) {
                BufferedImage src = ImageIO.read(file);
                if (src != null) {
                    ImageIO.write(src, "jpg", new File(baseName + " Compressed.jpg"));
                }
                return;
            }

            // PDF Conversions
            if (ext.equals("pdf")) {
                try (PDDocument doc = Loader.loadPDF(file)) {
                    if (action.equals("TXT") || action.equals("DOCX")) {
                        String text = new PDFTextStripper().getText(doc);
                        Files.writeString(new File(baseName + "." + action.toLowerCase()).toPath(), text);
                    } else {
                        PDFRenderer renderer = new PDFRenderer(doc);
                        BufferedImage pageImg = renderer.renderImageWithDPI(0, 200);
                        String fmt = action.equalsIgnoreCase("JPG") ? "jpg" : "png";
                        ImageIO.write(pageImg, fmt, new File(baseName + "." + fmt));
                    }
                }
                return;
            }

            // Image Conversions
            BufferedImage img = ImageIO.read(file);
            if (img != null) {
                String fmt = action.toLowerCase();
                if (fmt.equals("jpg") || fmt.equals("bmp")) {
                    BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = rgb.createGraphics();
                    g.drawImage(img, 0, 0, java.awt.Color.WHITE, null);
                    g.dispose();
                    img = rgb;
                }
                File out = new File(baseName + "." + fmt);
                ImageIO.write(img, fmt, out);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // =========================================================
    // 4. CROP & ADD BG MODALS (Matching Video 00:06 & 00:14)
    // =========================================================
    private void showCropModal(File file) {
        try {
            BufferedImage orig = ImageIO.read(file);
            if (orig == null) return;

            Stage modal = new Stage();
            modal.setTitle("Crop Image");

            VBox root = new VBox(14);
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: #F6EFEA; -fx-padding: 20;");

            ImageView preview = new ImageView(SwingFXUtils.toFXImage(orig, null));
            preview.setFitWidth(380);
            preview.setFitHeight(320);
            preview.setPreserveRatio(true);

            HBox ratioBar = new HBox(8);
            ratioBar.setAlignment(Pos.CENTER);
            Label rLbl = new Label("Aspect Ratio:");
            rLbl.setStyle("-fx-text-fill: #555; -fx-font-weight: bold;");
            ratioBar.getChildren().add(rLbl);

            final double[] selectedRatio = {0.0}; // 0 = Free/Square, 1 = 1:1, 1.33 = 4:3, 1.77 = 16:9
            String[] ratios = {"Free", "1:1", "4:3", "16:9", "3:2"};
            for (String r : ratios) {
                Button b = new Button(r);
                b.setStyle("-fx-background-color: #E8DDD5; -fx-text-fill: #333; -fx-background-radius: 6;");
                b.setOnAction(e -> {
                    if (r.equals("1:1")) selectedRatio[0] = 1.0;
                    else if (r.equals("4:3")) selectedRatio[0] = 4.0 / 3.0;
                    else if (r.equals("16:9")) selectedRatio[0] = 16.0 / 9.0;
                    else if (r.equals("3:2")) selectedRatio[0] = 3.0 / 2.0;
                    else selectedRatio[0] = 0.8;
                });
                ratioBar.getChildren().add(b);
            }

            Button applyBtn = new Button("Apply");
            applyBtn.setStyle("-fx-background-color: #EB5734; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 24; -fx-font-weight: bold;");
            applyBtn.setOnAction(e -> {
                try {
                    int w = orig.getWidth();
                    int h = orig.getHeight();
                    int cropW = (int) (w * 0.85);
                    int cropH = selectedRatio[0] > 0 ? (int) Math.min(h, cropW / selectedRatio[0]) : (int) (h * 0.75);
                    int x = (w - cropW) / 2;
                    int y = (h - cropH) / 2;
                    BufferedImage cropped = orig.getSubimage(x, y, cropW, cropH);

                    String path = file.getAbsolutePath();
                    String baseName = path.substring(0, path.lastIndexOf('.'));
                    ImageIO.write(cropped, "jpg", new File(baseName + " Cropped.jpg"));
                    modal.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });

            root.getChildren().addAll(preview, ratioBar, applyBtn);
            modal.setScene(new Scene(root, 460, 460));
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
            modal.setTitle("Add BG");

            VBox root = new VBox(12);
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: #F6EFEA; -fx-padding: 20;");

            ImageView previewView = new ImageView();
            previewView.setFitWidth(420);
            previewView.setFitHeight(270);
            previewView.setPreserveRatio(true);

            Slider padSlider = new Slider(20, 200, 90);
            Slider cornerSlider = new Slider(0, 80, 28);
            ComboBox<String> themeBox = new ComboBox<>();
            themeBox.getItems().addAll("Warm Sunset", "Purple Dream", "Ocean Breeze", "Dark Slate");
            themeBox.getSelectionModel().selectFirst();

            final BufferedImage[] latestRender = new BufferedImage[1];
            Runnable updatePreview = () -> {
                latestRender[0] = renderStyledBackground(orig, (int) padSlider.getValue(), (int) cornerSlider.getValue(), themeBox.getValue());
                previewView.setImage(SwingFXUtils.toFXImage(latestRender[0], null));
            };

            padSlider.valueProperty().addListener((o, oldV, newV) -> updatePreview.run());
            cornerSlider.valueProperty().addListener((o, oldV, newV) -> updatePreview.run());
            themeBox.setOnAction(e -> updatePreview.run());
            updatePreview.run();

            Button saveBtn = new Button("Save with Background");
            saveBtn.setStyle("-fx-background-color: #EB5734; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 20; -fx-font-weight: bold;");
            saveBtn.setOnAction(e -> {
                try {
                    String path = file.getAbsolutePath();
                    String baseName = path.substring(0, path.lastIndexOf('.'));
                    ImageIO.write(latestRender[0], "png", new File(baseName + " With Background.png"));
                    modal.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });

            root.getChildren().addAll(
                    previewView,
                    new HBox(10, new Label("Background:"), themeBox),
                    new HBox(10, new Label("Padding:"), padSlider),
                    new HBox(10, new Label("Corners:"), cornerSlider),
                    saveBtn
            );
            modal.setScene(new Scene(root, 480, 500));
            modal.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BufferedImage renderStyledBackground(BufferedImage fg, int pad, int radius, String theme) {
        int w = fg.getWidth();
        int h = fg.getHeight();
        int totalW = w + pad * 2;
        int totalH = h + pad * 2;

        java.awt.Color c1 = new java.awt.Color(255, 110, 80), c2 = new java.awt.Color(255, 180, 120);
        if ("Purple Dream".equals(theme)) { c1 = new java.awt.Color(155, 89, 182); c2 = new java.awt.Color(108, 92, 231); }
        else if ("Ocean Breeze".equals(theme)) { c1 = new java.awt.Color(33, 147, 176); c2 = new java.awt.Color(109, 213, 237); }
        else if ("Dark Slate".equals(theme)) { c1 = new java.awt.Color(35, 37, 38); c2 = new java.awt.Color(65, 67, 69); }

        BufferedImage out = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setPaint(new GradientPaint(0, 0, c1, totalW, totalH, c2));
        g2.fillRect(0, 0, totalW, totalH);

        g2.setColor(new java.awt.Color(0, 0, 0, 70));
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
    // 5. SUMMON CENTERED AT FILE + COMMAND/SHIFT HOOK
    // =========================================================
    public void summonAtCursor() {
        Point p = MouseInfo.getPointerInfo().getLocation();
        Platform.runLater(() -> {
            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();

            // Center the radial wheel directly on the cursor, clamped inside screen edges
            double targetX = Math.max(screenBounds.getMinX() + 10,
                    Math.min(p.x - SIZE / 2.0, screenBounds.getMaxX() - SIZE - 10));
            double targetY = Math.max(screenBounds.getMinY() + 10,
                    Math.min(p.y - SIZE / 2.0, screenBounds.getMaxY() - SIZE - 10));

            radialStage.setX(targetX);
            radialStage.setY(targetY);

            if (!radialStage.isShowing()) {
                wheelPane.setScaleX(0.75);
                wheelPane.setScaleY(0.75);
                radialStage.show();
                radialStage.toFront();

                ScaleTransition st = new ScaleTransition(Duration.millis(140), wheelPane);
                st.setToX(1.0);
                st.setToY(1.0);
                st.play();
            }
        });
    }

    private void registerGlobalHook() {
        try {
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            logger.setUseParentHandlers(false);

            GlobalScreen.registerNativeHook();
            CommandOrShiftDragDetector detector = new CommandOrShiftDragDetector(
                    this::summonAtCursor,
                    () -> Platform.runLater(() -> {
                        if (radialStage.isShowing() && draggedFile == null) {
                            radialStage.hide();
                        }
                    })
            );
            GlobalScreen.addNativeMouseListener(detector);
            GlobalScreen.addNativeMouseMotionListener(detector);
            GlobalScreen.addNativeKeyListener(detector);
        } catch (Exception e) {
            System.err.println("Please grant Accessibility Permission to VS Code / Terminal in macOS System Settings -> Privacy & Security -> Accessibility!");
        }
    }

    private static class CommandOrShiftDragDetector implements NativeMouseInputListener, NativeKeyListener {
        private boolean mouseDown = false;
        private boolean isDragging = false;
        private boolean modifierHeld = false;
        private final Runnable onSummon;
        private final Runnable onRelease;

        public CommandOrShiftDragDetector(Runnable onSummon, Runnable onRelease) {
            this.onSummon = onSummon;
            this.onRelease = onRelease;
        }

        @Override
        public void nativeMousePressed(NativeMouseEvent e) {
            mouseDown = true;
            if (modifierHeld) {
                onSummon.run();
            }
        }

        @Override
        public void nativeMouseDragged(NativeMouseEvent e) {
            isDragging = true;
            if (modifierHeld) {
                onSummon.run();
            }
        }

        @Override
        public void nativeMouseReleased(NativeMouseEvent e) {
            mouseDown = false;
            isDragging = false;
            onRelease.run();
        }

        @Override
        public void nativeKeyPressed(NativeKeyEvent e) {
            int code = e.getKeyCode();
            // Triggers on Mac COMMAND (VC_META) OR SHIFT (VC_SHIFT)
            if (code == NativeKeyEvent.VC_META || code == NativeKeyEvent.VC_SHIFT) {
                modifierHeld = true;
                if (mouseDown || isDragging) {
                    onSummon.run();
                }
            }
        }

        @Override
        public void nativeKeyReleased(NativeKeyEvent e) {
            int code = e.getKeyCode();
            if (code == NativeKeyEvent.VC_META || code == NativeKeyEvent.VC_SHIFT) {
                modifierHeld = false;
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}