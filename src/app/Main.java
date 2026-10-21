// ==================== Main.java ====================
package app;
import javafx.scene.chart.Axis;          
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javafx.scene.shape.*;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javafx.scene.layout.StackPane;
import java.util.ArrayList;   // already there
import java.util.List;        // already there
import java.nio.file.Path;    // already there
import java.nio.file.Paths;   // already there
import java.nio.file.Files;   // already there
import java.io.IOException;   // already there

public class Main extends Application {
    private static final Path DATA_DIRECTORY = Paths.get(
            System.getProperty("user.home"), ".videogamestats");
    private static final Path DATA_FILE = DATA_DIRECTORY.resolve("stats-v1.tsv");
    private static final Path NICKNAME_FILE = DATA_DIRECTORY.resolve("nickname.txt");
    private static final String[] STAT_NAMES = {"Playstation trophies",
        "XBox Gamer points", "Zombies killed", "Missions completed",
        "Humans killed", "Animals killed", "Animals hunted", "Aliens killed",
        "Time driven a car", "Time driven an aircraft", "Time driven a boat",
        "Time played","Game of the day"
    };
    static SimpleObjectProperty<GameRecord> selectedDay = new SimpleObjectProperty<>();
    static SimpleObjectProperty<String> gameofdaybg =
            new SimpleObjectProperty<>(toLocalPath("files/bgs/2.png","videogamestats"));
    static final List<GameRecord> records = new ArrayList<>();
    static VBox gamePrevis;

    private final List<ToggleButton> statToggles = new ArrayList<>();
    private BorderPane root;
    private VideoGamestatsLineChart<String,Double> chart;
    private NumberAxis chartYAxis;           
    private HBox chartPeriods;
    private HBox statsPeriods;
    private String selectedPeriod = "Weekly";
    private Button[] tabs;
    private Pane chartPane;
    private VBox statsPane;
    private Label chartSubtitle;
    private Label chartStatus;
    private Label progressSummary;
    private Label sessionCount;
    private Label statsHeading;
    private Label[] statValueLabels;
    private String backgroundFileName;
    private String nickname = "";
    private ScrollPane gamePrevisScroll;

    @Override
    public void start(Stage stage) {
        root = new BorderPane();
        root.setPadding(new Insets(20));
        root.setBackground(new Background(new BackgroundFill(
                Color.web("#10141f"), null, null)));

        tabs = new Button[] { new Button("CHARTS"), new Button("STATS") };
        Button addData  = new Button("+  ADD DATA");
        Button settings = new Button("\u2699  BACKGROUND");
        addData .setStyle(navStyle());
        settings.setStyle(navStyle());

        HBox tabsBox = new HBox(10);
        tabsBox.setAlignment(Pos.CENTER_LEFT);
        tabsBox.setPadding(new Insets(0, 0, 18, 0));
        tabsBox.getChildren().addAll(tabs[0], tabs[1]);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        tabsBox.getChildren().addAll(spacer, settings, addData);

        tabs[0].setOnAction(e -> showTab(0));
        tabs[1].setOnAction(e -> showTab(1));
        addData .setOnAction(e -> showAddDataDialog(stage));
        settings.setOnAction(e -> chooseBackground(stage));

        String loadError = null;
        try {
            loadSavedData();
        } catch (IOException ex) {
            loadError = "Saved game data could not be loaded: " + ex.getMessage();
        }

        chartPane = buildChartPane();
        statsPane = buildStatsPane();
        root.setTop(tabsBox);
        showTab(0);

        Scene scene = new Scene(root);
        stage.setTitle("Videogame Stats");
        stage.setMinWidth(760);
        stage.setMinHeight(600);
        stage.setScene(scene);
        scene.getStylesheets().add("data:text/css," + BASIC_CSS);
        stage.setOnCloseRequest(e -> pushStats());
        stage.show();

        askNickname();
        refreshStats();
        refreshChart();

        if (loadError != null) showValidationError(loadError);
    }

    // ---- Nickname ----
    private static String toLocalPath(String relPath) {
        String nfname = new File("").getAbsolutePath().replace("\\", "/").replace("//", "/");
        return ("file:" + nfname + "/" + relPath).replace("java", "2026java").replace("//", "/");
    }

    private static String toLocalPath(String relPath, String projectname) {
        String nfname = new File("").getAbsolutePath().replace("\\", "/").replace("//", "/");
        return ("file:" + nfname + "/" + projectname + "/" + relPath).replace("java", "2026java").replace("//", "/");
    }

    private void askNickname() {
        try {
            Files.createDirectories(DATA_DIRECTORY);

            if (Files.exists(NICKNAME_FILE)) {
                nickname = Files.readString(NICKNAME_FILE, StandardCharsets.UTF_8).trim();
            }

            if (nickname.isEmpty()) {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Welcome");
                dialog.setHeaderText(null);
                dialog.setContentText("Enter nickname:");
                dialog.showAndWait().ifPresent(name -> {
                    nickname = name.trim();
                    if (nickname.isEmpty()) nickname = "player";
                    try {
                        Files.writeString(NICKNAME_FILE, nickname, StandardCharsets.UTF_8);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                });
                if (nickname.isEmpty()) nickname = "player";
            }

            if (statsHeading != null) {
                statsHeading.setText("Stats summary for " + nickname);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ---- Chart pane ----
    private Pane buildChartPane() {
    VBox content = new VBox(18);
    content.setPadding(new Insets(24));
    content.setStyle(panelStyle());

    // ---- header / toggles / axes / chart (unchanged, add to `content`) ----
    Label heading = new Label("YOUR FIELD RECORD");
    heading.setStyle("-fx-text-fill: #f2f5ff; -fx-font-size: 24px; -fx-font-weight: bold;");

    chartSubtitle = new Label();
    chartSubtitle.setStyle("-fx-text-fill: #9ba9c5; -fx-font-size: 13px;");
    chartStatus = new Label();
    chartStatus.setStyle("-fx-text-fill: #9ba9c5; -fx-font-size: 12px;");

    HBox statButtons = new HBox(8);
    statButtons.setAlignment(Pos.CENTER_LEFT);
    ToggleGroup statGroup = new ToggleGroup();
    statToggles.clear();
    for (int i = 0; i < STAT_NAMES.length; i++) {
        ToggleButton button = new ToggleButton(STAT_NAMES[i]);
        button.setToggleGroup(statGroup);
        button.setSelected(i == 0);
        button.setStyle(chipStyle(button.isSelected()));
        button.setOnAction(e -> {
            if (!button.isSelected()) button.setSelected(true);
            for (ToggleButton candidate : statToggles)
                candidate.setStyle(chipStyle(candidate.isSelected()));
            refreshChart();
        });
        statToggles.add(button);
        statButtons.getChildren().add(button);
    }
    ScrollPane statScroller = new ScrollPane(statButtons);
    statScroller.setFitToHeight(true);
    statScroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    statScroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    statScroller.setMinHeight(68);
    statScroller.setPrefHeight(68);
    statScroller.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

    chartPeriods = new HBox(8);
    chartPeriods.setAlignment(Pos.CENTER_LEFT);
    ToggleGroup periodGroup = new ToggleGroup();
    for (String period : new String[] { "Weekly", "Monthly", "Yearly" }) {
        ToggleButton button = new ToggleButton(period);
        button.setToggleGroup(periodGroup);
        button.setSelected(period.equals(selectedPeriod));
        button.setStyle(chipStyle(button.isSelected()));
        button.setOnAction(e -> {
            if (!button.isSelected()) button.setSelected(true);
            selectedPeriod = period;
            syncPeriodButtons();
            refreshChart();
            refreshStats();
        });
        chartPeriods.getChildren().add(button);
    }

    CategoryAxis xAxis = new CategoryAxis();
    xAxis.setLabel("Date");
    xAxis.setStyle("-fx-tick-label-fill: white; -fx-text-fill: white;");

    NumberAxis yAxis = new NumberAxis();
    yAxis.setLabel("Total");
    yAxis.setForceZeroInRange(true);
    yAxis.setAutoRanging(false);
    yAxis.setLowerBound(0);
    yAxis.setUpperBound(1);
    yAxis.setStyle("-fx-tick-label-fill: white; -fx-text-fill: white;");
    chartYAxis = yAxis;

    @SuppressWarnings("unchecked")
    Axis<Double> yAxisTyped = (Axis<Double>)(Axis<?>) yAxis;

    chart = new VideoGamestatsLineChart<String, Double>(xAxis, yAxisTyped,
            new String[] {
                "gradient(90,#ff5f6d,#ffc371);width=1;outline=#7a1f26;outlineWidth=1.5",
                "#2b8cbe;width=2"
            },
            false,
            true, true);
    chart.setMinHeight(220);

    // ---- gamePrevis overlay ----
    gamePrevis = new VBox(4);
    gamePrevis.setPadding(new Insets(6));
    Label hint = new Label("Hover a data point to see that session");
    hint.setStyle("-fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.55); -fx-padding: 2 5 2 5;");
    gamePrevis.getChildren().add(hint);

    gamePrevisScroll = new ScrollPane(gamePrevis);
    gamePrevisScroll.setPrefSize(220, 220);
    gamePrevisScroll.setMaxSize(220, 220);
    gamePrevisScroll.setStyle("-fx-background: rgba(19,27,43,0.92);"
            + "-fx-background-color: rgba(19,27,43,0.92);"
            + "-fx-border-color: #293750; -fx-border-radius: 8;");

    // ---- wire hover: chart point -> gamePrevis ----
    chart.setPointShape(new Circle(5));   // small visible dot, also the hover target
    chart.setOnDataPointHover(extra -> {
        if (extra instanceof GameRecord rec)
            updateGameRecordInfo(gamePrevis, rec);
    });

    content.getChildren().addAll(heading, chartSubtitle, chartStatus,
            statScroller, chartPeriods, chart);
    VBox.setVgrow(chart, Priority.ALWAYS);

    // ---- assemble ----
    StackPane stack = new StackPane(content, gamePrevisScroll);
    StackPane.setAlignment(gamePrevisScroll, Pos.BOTTOM_RIGHT);
    StackPane.setMargin(gamePrevisScroll, new Insets(0, 20, 20, 0));
    return stack;
}
    static void updateGameRecordInfo(VBox gamePrevis, GameRecord record) {
    if (gamePrevis == null || record == null) return;

    // Each session stores the bg the user picked at log-time. Fall back to the
    // current global selection only if that session had none.
    String bgUri = record.gameOfDayBg != null ? record.gameOfDayBg : gameofdaybg.get();
    gamePrevis.setBackground(background(bgUri));
    gamePrevis.getChildren().clear();

    Label dayLabel = new Label(record.day.toString());
    dayLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;"
            + "-fx-background-color: rgba(0,0,0,0.55); -fx-padding: 2 5 2 5;");
    gamePrevis.getChildren().add(dayLabel);

    for (int i = 0; i < STAT_NAMES.length - 1 && i < record.values.length; i++) {
        if (!record.hasValue[i]) continue;
        Label nl = new Label(STAT_NAMES[i] + ": " + record.values[i]);
        nl.setStyle("-fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.55);"
                + "-fx-padding: 1 4 1 4;");
        gamePrevis.getChildren().add(nl);
    }

    if (record.gameOfDay != null && !record.gameOfDay.isEmpty()) {
        Label g = new Label("Game of the day: " + record.gameOfDay);
        g.setStyle("-fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.55);"
                + "-fx-padding: 1 4 1 4;");
        gamePrevis.getChildren().add(g);
    }
}

    // ---- Stats pane ----
    private VBox buildStatsPane() {
        VBox pane = new VBox(18);
        pane.setPadding(new Insets(24));
        pane.setStyle(panelStyle());

        statsHeading = new Label("Stats summary");
        statsHeading.setStyle("-fx-text-fill: #f2f5ff; -fx-font-size: 24px; -fx-font-weight: bold;");
        sessionCount = new Label();
        sessionCount.setStyle("-fx-text-fill: #9ba9c5; -fx-font-size: 13px;");

        statsPeriods = new HBox(8);
        statsPeriods.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup periodGroup = new ToggleGroup();
        for (String period : new String[] { "Weekly", "Monthly", "Yearly" }) {
            ToggleButton button = new ToggleButton(period);
            button.setToggleGroup(periodGroup);
            button.setSelected(period.equals(selectedPeriod));
            button.setStyle(chipStyle(button.isSelected()));
            button.setOnAction(e -> {
                if (!button.isSelected()) button.setSelected(true);
                selectedPeriod = period;
                syncPeriodButtons();
                refreshChart();
                refreshStats();
            });
            statsPeriods.getChildren().add(button);
        }

        GridPane cards = new GridPane();
        cards.setHgap(12);
        cards.setVgap(12);
        for (int col = 0; col < 2; col++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(50);
            cc.setHgrow(Priority.ALWAYS);
            cards.getColumnConstraints().add(cc);
        }

        statValueLabels = new Label[STAT_NAMES.length];
        int index = 0;
        for (String stat : STAT_NAMES) {
            VBox card = new VBox(8);
            card.setPadding(new Insets(16));
            card.setMinHeight(100);
            card.setStyle("-fx-background-color: rgba(19, 27, 43, 0.92); -fx-background-radius: 10;"
                    + "-fx-border-color: #293750; -fx-border-radius: 10;");
            Label name = new Label(stat.toUpperCase());
            name.setStyle("-fx-text-fill: #8fa0bf; -fx-font-size: 10px; -fx-font-weight: bold;");
            Label value = new Label("0");
            statValueLabels[index] = value;
            value.setStyle("-fx-text-fill: #5ce1c6; -fx-font-size: 26px; -fx-font-weight: bold;");
            card.getChildren().addAll(name, value);
            cards.add(card, index % 2, index / 2);
            index++;
        }

        VBox progressCard = new VBox(8);
        progressCard.setPadding(new Insets(16));
        progressCard.setStyle("-fx-background-color: rgba(19, 27, 43, 0.92); -fx-background-radius: 10;"
                + "-fx-border-color: #293750; -fx-border-radius: 10;");
        Label progressTitle = new Label("GAME PROGRESS LOG");
        progressTitle.setStyle("-fx-text-fill: #8fa0bf; -fx-font-size: 10px; -fx-font-weight: bold;");
        progressSummary = new Label("No progress entries yet. Add data to start your log.");
        progressSummary.setWrapText(true);
        progressSummary.setStyle("-fx-text-fill: #d6deef; -fx-font-size: 13px;");
        progressCard.getChildren().addAll(progressTitle, progressSummary);

        ScrollPane scroller = new ScrollPane(cards);
        scroller.setFitToWidth(true);
        scroller.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        pane.getChildren().addAll(statsHeading, sessionCount, statsPeriods, scroller, progressCard);
        VBox.setVgrow(scroller, Priority.ALWAYS);
        return pane;
    }

    // ---- Tabs ----
    private void showTab(int index) {
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setStyle(i == index ? navActiveStyle() : navStyle());
        }
        root.setCenter(index == 0 ? chartPane : statsPane);
        if (index == 1) refreshStats();
    }

    // ---- Add data dialog ----
    private static List<Path> listBackgroundFiles() {
    List<Path> result = new ArrayList<>();
    List<Path> candidates = new ArrayList<>();
    String cwd = new File("").getAbsolutePath();
    candidates.add(Paths.get(cwd, "files", "bgs"));
    // Same "java" -> "2026java" trick the toLocalPath helper uses
    candidates.add(Paths.get(cwd.replace("\\", "/").replace("java", "2026java"),
                             "files", "bgs"));

    for (Path dir : candidates) {
        if (!Files.isDirectory(dir)) continue;
        try (var s = Files.list(dir)) {
            s.filter(Files::isRegularFile)
             .filter(p -> {
                 String n = p.getFileName().toString().toLowerCase();
                 return n.endsWith(".png") || n.endsWith(".jpg")
                     || n.endsWith(".jpeg") || n.endsWith(".gif");
             })
             .sorted()
             .forEach(result::add);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (!result.isEmpty()) break;
    }
    return result;
}

    private static Background background(String url) {
        BackgroundSize siz = new BackgroundSize(1, 1, true, true, false, false);
        return new Background(new BackgroundImage(new Image(url), BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, siz));
    }

    private void showAddDataDialog(Stage owner) {
        Dialog<GameRecord> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Add game data");
        dialog.setHeaderText("LOG A SESSION");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox form = new VBox(12);
        form.setPadding(new Insets(16));
        form.setStyle("-fx-background-color: #182031;");
        form.getChildren().add(dialogLabel("SESSION DATE"));

        DatePicker day = new DatePicker(LocalDate.now());
        day.setMaxWidth(Double.MAX_VALUE);
        form.getChildren().add(day);

        form.getChildren().addAll(dialogLabel("SESSION STATS"), new Separator());
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(12);
        statsGrid.setVgap(8);
        statsGrid.getColumnConstraints().addAll(
                new ColumnConstraints(220), new ColumnConstraints(300));

        TextField[] fields = new TextField[STAT_NAMES.length];

        List<Path> bgFiles = listBackgroundFiles();
VBox bgsCont = new VBox(4);
bgsCont.setPadding(new Insets(4));
for (Path bgPath : bgFiles) {
    Pane np = new Pane();
    np.setPrefSize(50, 50);
    np.setMinSize(50, 50);
    np.setMaxSize(50, 50);
    String bgUri = bgPath.toUri().toString();
    np.setBackground(background(bgUri));
    np.setStyle("-fx-border-color: #293750; -fx-border-width: 1;");
    np.setOnMouseClicked(ev -> {
        gameofdaybg.set(bgUri);
        for (Node c : bgsCont.getChildren()) {
            if (c instanceof Pane p)
                p.setStyle("-fx-border-color: #293750; -fx-border-width: 1;");
        }
        np.setStyle("-fx-border-color: #5ce1c6; -fx-border-width: 2;");
    });
    bgsCont.getChildren().add(np);
}
if (bgFiles.isEmpty()) {
    Label none = new Label("No images in files/bgs");
    none.setStyle("-fx-text-fill: #9ba9c5;");
    bgsCont.getChildren().add(none);
}
ScrollPane bgVis = new ScrollPane(bgsCont);
bgVis.setPrefSize(160, 100);
bgVis.setStyle("-fx-background: #101827;");

        for (int i = 0; i < STAT_NAMES.length; i++) {
            String nameStat = STAT_NAMES[i];
            String label = (i >= 8 && !nameStat.toLowerCase().contains("game of day"))
                    ? STAT_NAMES[i] + " (hours)"
                    : (nameStat.toLowerCase().contains("game of day"))
                            ? nameStat : STAT_NAMES[i];
            Label statLabel = dialogLabel(label);
            statLabel.setWrapText(true);
            statsGrid.add(statLabel, 0, i);
            fields[i] = new TextField();
            fields[i].setPromptText(i >= 6 ? "Hours (optional)" : "Optional");
            fields[i].setStyle(fieldStyle());
            fields[i].setMaxWidth(Double.MAX_VALUE);
            statsGrid.add(fields[i], 1, i);
            if (nameStat.toLowerCase().contains("game of day"))
                statsGrid.add(new HBox(2, new Label("Game of the day Image"), bgVis), 1, i);
        }
        statsGrid.getColumnConstraints().get(1).setHgrow(Priority.ALWAYS);
        form.getChildren().add(statsGrid);

        form.getChildren().addAll(dialogLabel("GAMES & PROGRESS"), new Separator());
        VBox gameRows = new VBox(8);
        List<GameProgressInput> gameInputs = new ArrayList<>();
        addGameProgressRow(gameRows, gameInputs);
        Button addGame = new Button("+  ADD GAME");
        addGame.setStyle(chipStyle(false));
        addGame.setOnAction(e -> addGameProgressRow(gameRows, gameInputs));
        form.getChildren().addAll(gameRows, addGame);

        ScrollPane formScroller = new ScrollPane(form);
        formScroller.setFitToWidth(true);
        formScroller.setPrefViewportHeight(500);
        formScroller.setMinViewportHeight(320);
        formScroller.setStyle("-fx-background: #182031; -fx-background-color: #182031;");
        dialog.getDialogPane().setContent(formScroller);
        dialog.getDialogPane().setPrefWidth(720);
        dialog.getDialogPane().setStyle("-fx-background-color: #182031;");

        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(
                javafx.event.ActionEvent.ACTION, event -> {
                    for (int i = 0; i < fields.length - 1; i++) {
                        String text = fields[i].getText().trim();
                        if (text.isEmpty()) continue;
                        try {
                            double v = Double.parseDouble(text);
                            if (Double.isNaN(v) || Double.isInfinite(v) || v < 0)
                                throw new NumberFormatException();
                        } catch (NumberFormatException ex) {
                            event.consume();
                            showValidationError("Enter a valid, non-negative number for every stat.");
                            return;
                        }
                    }
                    for (GameProgressInput g : gameInputs) {
                        boolean hasT = !g.title.getText().trim().isEmpty();
                        boolean hasP = !g.progress.getText().trim().isEmpty();
                        if (hasT != hasP) {
                            event.consume();
                            showValidationError(
                                    "For each game, enter both the game title and its progress, or leave both blank.");
                            return;
                        }
                    }
                });

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            double[] values = new double[fields.length - 1];
            boolean[] hasValue = new boolean[fields.length - 1];
            for (int i = 0; i < fields.length - 1; i++) {
                String text = fields[i].getText().trim();
                if (!text.isEmpty()) {
                    values[i] = Double.parseDouble(text);
                    hasValue[i] = true;
                }
            }
            String gameofday = fields[fields.length - 1].getText();
            List<GameProgress> games = new ArrayList<>();
            for (GameProgressInput input : gameInputs) {
                String title = input.title.getText().trim();
                String progress = input.progress.getText().trim();
                if (!title.isEmpty() && !progress.isEmpty())
                    games.add(new GameProgress(title, progress));
            }
            return new GameRecord(day.getValue() == null ? LocalDate.now() : day.getValue(),
                    values, hasValue, games, gameofday, gameofdaybg.get());
        });

        dialog.showAndWait().ifPresent(record -> {
            records.add(record);
            try {
                saveData();
                refreshChart();
                refreshStats();
            } catch (IOException ex) {
                records.remove(record);
                showValidationError("The session could not be saved: " + ex.getMessage());
            }
        });
    }

    private void addGameProgressRow(VBox rows, List<GameProgressInput> gameInputs) {
        TextField title = new TextField();
        title.setPromptText("Game title");
        title.setStyle(fieldStyle());
        TextField progress = new TextField();
        progress.setPromptText("Game progress");
        progress.setStyle(fieldStyle());
        HBox row = new HBox(10, title, progress);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        HBox.setHgrow(progress, Priority.ALWAYS);
        rows.getChildren().add(row);
        gameInputs.add(new GameProgressInput(title, progress));
    }

    // ---- Background image ----
    private void chooseBackground(Stage owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a game screenshot or background");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = chooser.showOpenDialog(owner);
        if (file == null) return;

        Image image = new Image(file.toURI().toString(), false);
        if (image.isError()) {
            showValidationError("That image could not be loaded. Please choose another file.");
            return;
        }

        String extension = "";
        String fileName = file.getName();
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) extension = fileName.substring(dot).toLowerCase();

        String newBackgroundName = "background-" + UUID.randomUUID() + extension;
        Path newBackground = DATA_DIRECTORY.resolve(newBackgroundName);
        String oldBackgroundName = backgroundFileName;

        try {
            Files.createDirectories(DATA_DIRECTORY);
            Files.copy(file.toPath(), newBackground, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException ex) {
            showValidationError("The background could not be saved: " + ex.getMessage());
            return;
        }

        backgroundFileName = newBackgroundName;
        try {
            saveData();
            applyBackground(newBackground);
        } catch (IOException ex) {
            backgroundFileName = oldBackgroundName;
            try { saveData(); } catch (IOException rex) { ex.addSuppressed(rex); }
            try { Files.deleteIfExists(newBackground); }
            catch (IOException rex) { ex.addSuppressed(rex); }
            showValidationError("The background could not be saved: " + ex.getMessage());
            return;
        }
        try {
            deleteStoredBackground(oldBackgroundName);
        } catch (IOException ex) {
            showValidationError("The background was saved, but the previous image could not be removed: "
                    + ex.getMessage());
        }
    }

    private void applyBackground(Path path) throws IOException {
        Image image = new Image(path.toUri().toString(), false);
        if (image.isError())
            throw new IOException("Could not decode " + path.getFileName() + ".", image.getException());
        BackgroundSize size = new BackgroundSize(100, 100, true, true, false, true);
        BackgroundImage bg = new BackgroundImage(image, BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, size);
        root.setBackground(new Background(bg));
    }

    private void deleteStoredBackground(String name) throws IOException {
        if (name != null && Paths.get(name).getFileName().toString().equals(name))
            Files.deleteIfExists(DATA_DIRECTORY.resolve(name));
    }

    // ---- Chart refresh ----
    private void refreshChart() {
        if (chart == null) return;

        String selectedStat = null;
        for (ToggleButton button : statToggles) {
            if (button.isSelected()) { selectedStat = button.getText(); break; }
        }
        if (selectedStat == null) selectedStat = STAT_NAMES[0];
        int metricIndex = indexOfStat(selectedStat);
        chartSubtitle.setText(selectedStat + "  \u2022  " + selectedPeriod);

        chart.getData().clear();

        List<ChartBucket> buckets = createBuckets();
        List<String> categories = new ArrayList<>();
        boolean anyValueInPeriod = false;

        XYChart.Series<String, Double> segment = null;
        for (ChartBucket bucket : buckets) {
    double total = 0.0;
    List<GameRecord> bucketRecords = new ArrayList<>();
    for (GameRecord record : records) {
        if (!record.day.isBefore(bucket.start) && !record.day.isAfter(bucket.end)
                && metricIndex < record.hasValue.length
                && record.hasValue[metricIndex]) {
            total += record.values[metricIndex];
            bucketRecords.add(record);
        }
    }
    categories.add(bucket.label);

    if (!bucketRecords.isEmpty()) {
        anyValueInPeriod = true;
        if (segment == null) {
            segment = new XYChart.Series<>();
            segment.setName(selectedStat);
            chart.getData().add(segment);
            segment.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null)
                    newNode.setStyle("-fx-stroke: #5ce1c6; -fx-stroke-width: 2px;");
            });
        }

        // Pick the latest record in this bucket for hover detail.
        GameRecord latest = bucketRecords.get(0);
        for (GameRecord r : bucketRecords)
            if (r.day.isAfter(latest.day)) latest = r;

        XYChart.Data<String, Double> point = new XYChart.Data<>(bucket.label, total);
        point.setExtraValue(latest);
        segment.getData().add(point);
    } else {
        segment = null;
    }
}

        ((CategoryAxis) chart.getXAxis())
                .setCategories(FXCollections.observableArrayList(categories));
        chartStatus.setText(anyValueInPeriod ? ""
                : "No \"" + selectedStat + "\" values recorded in this "
                  + selectedPeriod.toLowerCase() + ".");

        Platform.runLater(this::updateBounds);
    }

    @SuppressWarnings("unchecked")
    private void updateBounds() {
        try {
        	NumberAxis yAxis = chartYAxis;
        	if (yAxis == null) return;
            yAxis.setAutoRanging(false);
            double maxY = 0.0;
            double minY = 0.0;
            for (int i = 0; i < chart.getData().size(); i++) {
                XYChart.Series<String, Double> series =
                        (XYChart.Series<String, Double>) chart.getData().get(i);
                if (series == null) continue;
                for (XYChart.Data<String, Double> data : series.getData()) {
                    if (data == null || data.getYValue() == null) continue;
                    double val = data.getYValue();
                    if (val > maxY) maxY = val;
                    if (val < minY) minY = val;
                }
            }
            yAxis.setLowerBound(minY);
            yAxis.setUpperBound(maxY > 0 ? maxY : 1.0);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private List<ChartBucket> createBuckets() {
        List<ChartBucket> buckets = new ArrayList<>();
        LocalDate today = LocalDate.now();
        if ("Weekly".equals(selectedPeriod)) {
            LocalDate first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            for (int i = 0; i < 7; i++) {
                LocalDate d = first.plusDays(i);
                buckets.add(new ChartBucket(d, d, d.getDayOfWeek().toString().substring(0, 3)));
            }
        } else if ("Monthly".equals(selectedPeriod)) {
            YearMonth month = YearMonth.from(today);
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate d = month.atDay(day);
                buckets.add(new ChartBucket(d, d, Integer.toString(day)));
            }
        } else {
            for (int month = 1; month <= 12; month++) {
                YearMonth ym = YearMonth.of(today.getYear(), month);
                buckets.add(new ChartBucket(ym.atDay(1), ym.atEndOfMonth(),
                        ym.getMonth().toString().substring(0, 3)));
            }
        }
        return buckets;
    }

    // ---- Stats refresh ----
    private void refreshStats() {
        if (statsPane == null) return;
        LocalDate today = LocalDate.now();
        LocalDate start, end = today;
        if ("Weekly".equals(selectedPeriod)) {
            start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        } else if ("Monthly".equals(selectedPeriod)) {
            start = today.withDayOfMonth(1);
        } else {
            start = today.withDayOfYear(1);
        }

        int sessionsInPeriod = 0;
        for (GameRecord record : records)
            if (!record.day.isBefore(start) && !record.day.isAfter(end)) sessionsInPeriod++;

        for (int i = 0; i < STAT_NAMES.length; i++) {
            double total = 0;
            for (GameRecord record : records) {
                if (!record.day.isBefore(start) && !record.day.isAfter(end)
                        && i < record.values.length && record.hasValue[i])
                    total += record.values[i];
            }
            statValueLabels[i].setText(formatTotal(total));
        }

        sessionCount.setText(records.size() + " sessions recorded \u00b7 " + sessionsInPeriod
                + " in this " + selectedPeriod.toLowerCase());
        if (statsHeading != null && !nickname.isEmpty())
            statsHeading.setText("Stats summary for " + nickname);

        List<String> lines = new ArrayList<>();
        for (int i = records.size() - 1; i >= 0; i--) {
            GameRecord record = records.get(i);
            if (record.day.isBefore(start) || record.day.isAfter(end)) continue;
            for (GameProgress game : record.games)
                lines.add(record.day + "  \u2022  " + game.title + ": " + game.progress);
        }
        progressSummary.setText(lines.isEmpty()
                ? "No progress entries for this " + selectedPeriod.toLowerCase() + "."
                : String.join("\n", lines));
    }

    // ---- File I/O ----
    private void loadSavedData() throws IOException {
        if (!Files.exists(DATA_FILE)) return;
        List<GameRecord> loadedRecords = new ArrayList<>();
        String loadedBackground = null;
        GameRecord currentRecord = null;
        boolean foundHeader = false;
        boolean foundBackground = false;
        int lineNumber = 0;

        try (BufferedReader reader = Files.newBufferedReader(DATA_FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] parts = line.split("\t", -1);
                if (!foundHeader) {
                    if (!"VIDEOGAMESTATS\t1".equals(line)) throw invalidData(lineNumber);
                    foundHeader = true;
                    continue;
                }
                if ("B".equals(parts[0]) && parts.length == 2 && !foundBackground) {
                    loadedBackground = "-".equals(parts[1]) ? null : decodeText(parts[1], lineNumber);
                    if (loadedBackground != null
                            && !Paths.get(loadedBackground).getFileName().toString().equals(loadedBackground))
                        throw invalidData(lineNumber);
                    foundBackground = true;
                } else if ("R".equals(parts[0]) && parts.length == STAT_NAMES.length + 2
                        && currentRecord == null) {
                    LocalDate date;
                    try { date = LocalDate.parse(parts[1]); }
                    catch (RuntimeException ex) { throw invalidData(lineNumber); }
                    double[] values = new double[STAT_NAMES.length];
                    boolean[] hasValue = new boolean[STAT_NAMES.length];
                    for (int i = 0; i < STAT_NAMES.length; i++) {
                        if (!"-".equals(parts[i + 2])) {
                            try {
                                values[i] = Double.parseDouble(parts[i + 2]);
                                if (Double.isNaN(values[i]) || Double.isInfinite(values[i])
                                        || values[i] < 0)
                                    throw new NumberFormatException();
                            } catch (NumberFormatException ex) { throw invalidData(lineNumber); }
                            hasValue[i] = true;
                        }
                    }
                    currentRecord = new GameRecord(date, values, hasValue, new ArrayList<>());
                } else if ("G".equals(parts[0]) && parts.length == 3 && currentRecord != null) {
                    currentRecord.games.add(new GameProgress(
                            decodeText(parts[1], lineNumber), decodeText(parts[2], lineNumber)));
                } else if ("E".equals(parts[0]) && parts.length == 1 && currentRecord != null) {
                    loadedRecords.add(currentRecord);
                    currentRecord = null;
                } else {
                    throw invalidData(lineNumber);
                }
            }
        }
        if (!foundHeader || !foundBackground || currentRecord != null)
            throw invalidData(lineNumber);

        records.clear();
        records.addAll(loadedRecords);
        backgroundFileName = loadedBackground;
        if (backgroundFileName != null) {
            Path savedBackground = DATA_DIRECTORY.resolve(backgroundFileName);
            if (!Files.isRegularFile(savedBackground))
                throw new IOException("The saved background image is missing: " + backgroundFileName);
            applyBackground(savedBackground);
        }
    }

    private void saveData() throws IOException {
        Files.createDirectories(DATA_DIRECTORY);
        Path temporaryFile = Files.createTempFile(DATA_DIRECTORY, "stats-", ".tmp");
        try {
            try (BufferedWriter writer =
                         Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                writer.write("VIDEOGAMESTATS\t1");
                writer.newLine();
                writer.write("B\t"
                        + (backgroundFileName == null ? "-" : encodeText(backgroundFileName)));
                writer.newLine();
                for (GameRecord record : records) {
                    writer.write("R\t" + record.day);
                    for (int i = 0; i < record.values.length; i++) {
                        writer.write("\t");
                        writer.write(record.hasValue[i] ? Double.toString(record.values[i]) : "-");
                    }
                    writer.newLine();
                    for (GameProgress game : record.games) {
                        writer.write("G\t" + encodeText(game.title) + "\t"
                                + encodeText(game.progress));
                        writer.newLine();
                    }
                    writer.write("E");
                    writer.newLine();
                }
            }
            try {
                Files.move(temporaryFile, DATA_FILE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryFile, DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeText(String value, int lineNumber) throws IOException {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw invalidData(lineNumber);
        }
    }

    private IOException invalidData(int lineNumber) {
        return new IOException("Invalid saved-data format at line " + lineNumber + ".");
    }

    // ---- Helpers ----
    private void syncPeriodButtons() {
        for (Node node : chartPeriods.getChildren()) {
            if (node instanceof ToggleButton button) {
                boolean active = button.getText().equals(selectedPeriod);
                button.setSelected(active);
                button.setStyle(chipStyle(active));
            }
        }
        for (Node node : statsPeriods.getChildren()) {
            if (node instanceof ToggleButton button) {
                boolean active = button.getText().equals(selectedPeriod);
                button.setSelected(active);
                button.setStyle(chipStyle(active));
            }
        }
    }

    private int indexOfStat(String stat) {
        for (int i = 0; i < STAT_NAMES.length; i++)
            if (STAT_NAMES[i].equals(stat)) return i;
        return 0;
    }

    private String formatTotal(double total) {
        if (total == Math.rint(total)) return String.format("%,.0f", total);
        return String.format("%,.1f", total);
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("Check your entry");
        alert.showAndWait();
    }

    private Label dialogLabel(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.web("#dce5f5"));
        label.setFont(Font.font("System", 13));
        return label;
    }

    private String fieldStyle() {
        return "-fx-control-inner-background: #101827; -fx-text-fill: #f2f5ff;"
                + "-fx-prompt-text-fill: #74839d; -fx-border-color: #394963;"
                + "-fx-border-radius: 5; -fx-background-radius: 5;";
    }

    private String panelStyle() {
        return "-fx-background-color: rgba(24, 32, 49, 0.94); -fx-background-radius: 14;"
                + "-fx-border-color: #293750; -fx-border-radius: 14;";
    }

    private String navActiveStyle() {
        return "-fx-background-color: #aee15c; -fx-text-fill: #10141f; -fx-font-weight: bold;"
                + "-fx-background-radius: 7; -fx-padding: 11 18;";
    }

    private String navStyle() {
        return "-fx-background-color: #202a3d; -fx-text-fill: #b9c4dc; -fx-font-weight: bold;"
                + "-fx-background-radius: 7; -fx-padding: 11 18;";
    }

    private String chipStyle(boolean selected) {
        return selected
                ? "-fx-background-color: #64461e; -fx-text-fill: #10141f; -fx-font-weight: bold;"
                        + "-fx-background-radius: 16; -fx-padding: 8 13;"
                : "-fx-background-color: #202a3d; -fx-text-fill: #c3cde1;"
                        + "-fx-background-radius: 16; -fx-padding: 8 13;";
    }

    public void pushStats() {
        try {
            saveData();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ---- Data classes ----
    static class GameRecord {
        final LocalDate day;
        final double[] values;
        final boolean[] hasValue;
        final List<GameProgress> games;
        final String gameOfDay;
        final String gameOfDayBg;

        GameRecord(LocalDate day, double[] values, boolean[] hasValue, List<GameProgress> games) {
            this.day = day;
            this.values = values;
            this.hasValue = hasValue;
            this.games = games;
            this.gameOfDay = null;
            this.gameOfDayBg = null;
        }

        GameRecord(LocalDate day, double[] values, boolean[] hasValue, List<GameProgress> games,
                   String gameOfDay, String gameOfDayBg) {
            this.day = day;
            this.values = values;
            this.hasValue = hasValue;
            this.games = games;
            this.gameOfDay = gameOfDay;
            this.gameOfDayBg = gameOfDayBg;
        }
    }

    static class GameProgress {
        final String title;
        final String progress;
        GameProgress(String title, String progress) {
            this.title = title;
            this.progress = progress;
        }
    }

    private static class GameProgressInput {
        final TextField title;
        final TextField progress;
        GameProgressInput(TextField title, TextField progress) {
            this.title = title;
            this.progress = progress;
        }
    }

    private static class ChartBucket {
        final LocalDate start;
        final LocalDate end;
        final String label;
        ChartBucket(LocalDate start, LocalDate end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }
    }

    private static final String BASIC_CSS = """
        .chart { -fx-background-color: transparent; }
        """;

    public static void main(String[] args) {
        launch(args);
    }
}
