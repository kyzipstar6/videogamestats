// ==================== VideoGamestatsLineChart.java ====================
package app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class VideoGamestatsLineChart<X,Y> extends LineChart<X, Y> {

    public static final class SeriesStyle {
        private Paint  strokePaint  = Color.web("#1f77b4");
        private double lineWidth    = 2.0;
        private Paint  outlinePaint = null;
        private double outlineWidth = 0.0;
        private Paint  symbolFill   = null;
        private Paint  symbolStroke = null;

        public SeriesStyle() {}
        public SeriesStyle(Paint strokePaint) { setStroke(strokePaint); }

        public Paint getStroke() { return strokePaint; }
        public SeriesStyle setStroke(Paint paint) { this.strokePaint = paint; return this; }
        public double getLineWidth() { return lineWidth; }
        public SeriesStyle setLineWidth(double width) { this.lineWidth = width; return this; }
        public Paint getOutline() { return outlinePaint; }
        public SeriesStyle setOutline(Paint paint) { this.outlinePaint = paint; return this; }
        public double getOutlineWidth() { return outlineWidth; }
        public SeriesStyle setOutlineWidth(double width) { this.outlineWidth = width; return this; }
        public Paint getSymbolFill() { return symbolFill; }
        public SeriesStyle setSymbolFill(Paint paint) { this.symbolFill = paint; return this; }
        public Paint getSymbolStroke() { return symbolStroke; }
        public SeriesStyle setSymbolStroke(Paint paint) { this.symbolStroke = paint; return this; }
        public SeriesStyle withBorder(Paint paint, double width) {
            this.outlinePaint = paint; this.outlineWidth = width; return this;
        }

        public static SeriesStyle parse(String spec) {
            SeriesStyle style = new SeriesStyle();
            if (spec == null || spec.trim().isEmpty()) return style;
            for (String rawPart : spec.split(";")) {
                String part = rawPart.trim();
                if (part.isEmpty()) continue;
                int eq = part.indexOf('=');
                if (eq < 0) { style.setStroke(parsePaint(part)); continue; }
                String key = part.substring(0, eq).trim().toLowerCase();
                String val = part.substring(eq + 1).trim();
                try {
                    switch (key) {
                        case "stroke": case "line": case "color":
                            style.setStroke(parsePaint(val)); break;
                        case "width": case "linewidth":
                            style.setLineWidth(Double.parseDouble(val)); break;
                        case "outline": case "border":
                            style.setOutline(parsePaint(val)); break;
                        case "outlinewidth": case "borderwidth":
                            style.setOutlineWidth(Double.parseDouble(val)); break;
                        case "symbol": case "symbolfill":
                            style.setSymbolFill(parsePaint(val)); break;
                        case "symbolstroke":
                            style.setSymbolStroke(parsePaint(val)); break;
                        default: break;
                    }
                } catch (RuntimeException ex) {
                    throw new IllegalArgumentException("Invalid series style '" + spec
                            + "' (offending part: '" + part + "')", ex);
                }
            }
            return style;
        }

        public static Paint parsePaint(String text) {
            if (text == null) return null;
            String s = text.trim();
            if (s.isEmpty() || s.equalsIgnoreCase("none") || s.equalsIgnoreCase("null")) return null;
            if (s.equalsIgnoreCase("transparent")) return Color.TRANSPARENT;
            String lower = s.toLowerCase();
            if (lower.startsWith("gradient(") && s.endsWith(")")) {
                String inner = s.substring("gradient(".length(), s.length() - 1);
                String[] parts = inner.split(",");
                if (parts.length < 2)
                    throw new IllegalArgumentException("A gradient needs at least two colours: " + s);
                double angle = 0;
                int index = 0;
                if (parts.length >= 3) { angle = Double.parseDouble(parts[0].trim()); index = 1; }
                Color c1 = Color.web(parts[index].trim());
                Color c2 = Color.web(parts[index + 1].trim());
                return linearGradient(c1, c2, angle);
            }
            return Color.web(s);
        }

        public static LinearGradient linearGradient(Color from, Color to, double angleDegrees) {
            double rad = Math.toRadians(angleDegrees);
            double dx = Math.cos(rad) / 2.0;
            double dy = Math.sin(rad) / 2.0;
            return new LinearGradient(
                    0.5 - dx, 0.5 - dy, 0.5 + dx, 0.5 + dy,
                    true, CycleMethod.NO_CYCLE,
                    new Stop(0, from), new Stop(1, to));
        }
    }

    static final String[] DEFAULT_PALETTE = {
            "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
            "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"
    };

    private final Map<XYChart.Data<X, Y>, Text> labels = new HashMap<>();
    private final Map<XYChart.Series<X, Y>, SeriesVisual> visuals = new HashMap<>();
    private final Map<XYChart.Data<X, Y>, Node> hoverTargets = new HashMap<>();

    private final BooleanProperty showDataLabels =
            new SimpleBooleanProperty(this, "showDataLabels", false) {
                @Override protected void invalidated() { refreshLabelVisibility(); }
            };

    private final BooleanProperty showDataLabelsOnHover =
            new SimpleBooleanProperty(this, "showDataLabelsOnHover", false) {
                @Override
                protected void invalidated() {
                    if (get()) {
                        for (XYChart.Series<X, Y> s : getData())
                            for (XYChart.Data<X, Y> d : s.getData())
                                ensureHoverTarget(d);
                    } else {
                        for (Node n : hoverTargets.values()) getPlotChildren().remove(n);
                        hoverTargets.clear();
                    }
                    refreshLabelVisibility();
                }
            };

    private String[] styleSpecs = new String[0];
    private final List<SeriesStyle> parsedStyles = new ArrayList<>();
    private Shape pointShape = null;
	private boolean curvedLine = false;
private boolean updatecontent = true;

/** Called when the mouse enters a data-point symbol; gets that point's extra value. */
private java.util.function.Consumer<Object> onDataPointHover = null;

public final void setOnDataPointHover(java.util.function.Consumer<Object> callback) {
    this.onDataPointHover = callback;
}

    SimpleIntegerProperty selectedId = new SimpleIntegerProperty(0);

    public VideoGamestatsLineChart() {
        this((Axis<X>) new NumberAxis(), (Axis<Y>) new NumberAxis());
    }

    public VideoGamestatsLineChart(Axis<X> xAxis, Axis<Y> yAxis) {
        super(xAxis, yAxis);
        getStyleClass().add("deepseek-line-chart");
        setCreateSymbols(false);
        setAnimated(false);
    }

    public VideoGamestatsLineChart(String[] seriesStyles, Shape pointShape,
                                   boolean curvedLine, boolean labelShown) {
        this();
        setSeriesStyles(seriesStyles);
        setPointShape(pointShape);
        setCurvedLine(curvedLine);
        setShowDataLabels(labelShown);
    }

    public VideoGamestatsLineChart(String[] seriesStyles,
                                   boolean curvedLine, boolean labelShown) {
        this();
        setSeriesStyles(seriesStyles);
        setCurvedLine(curvedLine);
        setShowDataLabels(labelShown);
    }

    public VideoGamestatsLineChart(Axis<X> xAxis, Axis<Y> yAxis,
                                   String[] seriesStyles, Shape pointShape,
                                   boolean curvedLine, boolean labelShown) {
        this(xAxis, yAxis);
        setSeriesStyles(seriesStyles);
        setPointShape(pointShape);
        setCurvedLine(curvedLine);
        setShowDataLabels(labelShown);
    }

    public VideoGamestatsLineChart(Axis<X> xAxis, Axis<Y> yAxis,
                                   String[] seriesStyles,
                                   boolean curvedLine, boolean labelShown) {
        this(xAxis, yAxis);
        setSeriesStyles(seriesStyles);
        setCurvedLine(curvedLine);
        setShowDataLabels(labelShown);
    }

    public VideoGamestatsLineChart(Axis<X> xAxis, Axis<Y> yAxis,
                                   String[] seriesStyles,
                                   boolean curvedLine, boolean labelShown, boolean updateContent) {
        this(xAxis, yAxis);
        setSeriesStyles(seriesStyles);
        setCurvedLine(curvedLine);
        setShowDataLabels(labelShown);
        setUpdateContent(updateContent);
    }

    public final BooleanProperty showDataLabelsOnHoverProperty() { return showDataLabelsOnHover; }
    public final boolean isShowDataLabelsOnHover() { return showDataLabelsOnHover.get(); }
    public final void setShowDataLabelsOnHover(boolean v) { showDataLabelsOnHover.set(v); }

    private void refreshLabelVisibility() {
        boolean always = showDataLabels.get();
        boolean onHover = showDataLabelsOnHover.get();
        for (Map.Entry<XYChart.Data<X, Y>, Text> e : labels.entrySet()) {
            Text label = e.getValue();
            if (always) label.setVisible(true);
            else if (onHover) label.setVisible(Boolean.TRUE.equals(label.getProperties().get("hovered")));
            else label.setVisible(false);
        }
    }

    private void ensureHoverTarget(XYChart.Data<X, Y> item) {
        if (hoverTargets.containsKey(item)) return;
        Circle hit = new Circle(10, Color.TRANSPARENT);
        hit.setStroke(null);
        hit.setMouseTransparent(false);
        hit.setOnMouseEntered(e -> setHover(item, true));
        hit.setOnMouseExited(e -> setHover(item, false));
        getPlotChildren().add(hit);
        hoverTargets.put(item, hit);
    }

    private void setHover(XYChart.Data<X, Y> item, boolean hovered) {
        Text label = labels.get(item);
        if (label == null) return;
        label.getProperties().put("hovered", hovered);
        if (hovered) { label.setVisible(true); label.toFront(); }
        else if (!showDataLabels.get()) label.setVisible(false);
    }

    public final boolean isShowDataLabels() { return showDataLabels.get(); }
    public final void setShowDataLabels(boolean value) { showDataLabels.set(value); }
    public final Shape getPointShape() { return pointShape; }

    public final void setPointShape(Shape shape) {
    this.pointShape = shape;
    // Wipe existing symbols; they're re-created lazily in ensureNodes().
    for (XYChart.Series<X, Y> series : getData()) {
        for (XYChart.Data<X, Y> item : series.getData()) {
            Node old = item.getNode();
            if (old != null) {
                getPlotChildren().remove(old);
                item.setNode(null);
            }
        }
    }
    requestLayout();
}
    public final boolean isCurvedLine() { return curvedLine; }
    public final void setCurvedLine(boolean curved) { this.curvedLine = curved; requestLayout(); }
    public final void setUpdateContent(boolean curved) { this.updatecontent = curved; }

    public final void setSeriesStyles(String[] specs) {
        this.styleSpecs = (specs == null) ? new String[0] : specs.clone();
        parsedStyles.clear();
        for (String spec : this.styleSpecs)
            parsedStyles.add(spec == null ? null : SeriesStyle.parse(spec));
        applyStyles();
    }

    public final void setSeriesStyle(int seriesIndex, String spec) {
        if (seriesIndex < 0) return;
        List<String> list = new ArrayList<>();
        for (String s : styleSpecs) list.add(s);
        while (list.size() <= seriesIndex) list.add(null);
        list.set(seriesIndex, spec);
        setSeriesStyles(list.toArray(new String[0]));
    }

    public final void setSeriesStyle(int seriesIndex, SeriesStyle style) {
        SeriesVisual visual = visualForIndex(seriesIndex);
        if (visual != null) { applyStyle(visual, style); requestLayout(); }
    }

    public final SeriesStyle getSeriesStyle(int seriesIndex) {
        SeriesVisual visual = visualForIndex(seriesIndex);
        return visual == null ? resolveStyle(seriesIndex) : visual.style;
    }

    public static Polygon starSymbol(double outerRadius, double innerRadius, int branches) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < branches * 2; i++) {
            double r = (i % 2 == 0) ? outerRadius : innerRadius;
            double angle = -Math.PI / 2.0 + i * Math.PI / branches;
            polygon.getPoints().addAll(r * Math.cos(angle), r * Math.sin(angle));
        }
        return polygon;
    }

    public static Polygon regularPolygonSymbol(double radius, int sides) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < sides; i++) {
            double angle = -Math.PI / 2.0 + i * 2.0 * Math.PI / sides;
            polygon.getPoints().addAll(radius * Math.cos(angle), radius * Math.sin(angle));
        }
        return polygon;
    }

    public static Polygon polygonSymbol(double... xy) {
        Polygon polygon = new Polygon();
        polygon.getPoints().addAll(box(xy));
        return polygon;
    }

    private static Double[] box(double[] values) {
        Double[] result = new Double[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i];
        return result;
    }

    /** Copies a prototype shape so that every data point gets its own node. */
   public Shape copyShape(Shape proto) {
    if (proto == null) return null;
    Shape copy;
    if (proto instanceof Polygon) {
        Polygon src = (Polygon) proto;
        Polygon dst = new Polygon();
        dst.getPoints().setAll(src.getPoints());
        copy = dst;
    } else if (proto instanceof Polyline) {
        Polyline src = (Polyline) proto;
        Polyline dst = new Polyline();
        dst.getPoints().setAll(src.getPoints());
        copy = dst;
    } else if (proto instanceof Circle) {
        Circle src = (Circle) proto;
        copy = new Circle(src.getCenterX(), src.getCenterY(), src.getRadius());
    } else if (proto instanceof Ellipse) {
        Ellipse src = (Ellipse) proto;
        copy = new Ellipse(src.getCenterX(), src.getCenterY(), src.getRadiusX(), src.getRadiusY());
    } else if (proto instanceof Rectangle) {
        Rectangle src = (Rectangle) proto;
        copy = new Rectangle(src.getX(), src.getY(), src.getWidth(), src.getHeight());
    } else if (proto instanceof Line) {
        Line src = (Line) proto;
        copy = new Line(src.getStartX(), src.getStartY(), src.getEndX(), src.getEndY());
    } else {
        copy = new Circle(4);
    }
    copy.setFill(proto.getFill());
    copy.setStroke(proto.getStroke());
    copy.setStrokeWidth(proto.getStrokeWidth());
    copy.setOpacity(proto.getOpacity());
    return copy;
}

    @Override
    protected void seriesAdded(XYChart.Series<X, Y> series, int seriesIndex) {
        SeriesVisual visual = new SeriesVisual();
        visual.style = resolveStyle(seriesIndex);
        applyStyle(visual, visual.style);
        visuals.put(series, visual);
        getPlotChildren().add(visual.outline);
        getPlotChildren().add(visual.line);
        requestLayout();
    }

    @Override
    protected void seriesRemoved(XYChart.Series<X, Y> series) {
        SeriesVisual visual = visuals.remove(series);
        if (visual != null) getPlotChildren().removeAll(visual.outline, visual.line);
        for (XYChart.Data<X, Y> item : new ArrayList<>(series.getData()))
            dataItemRemoved(item, series);
        requestLayout();
    }

    @Override
    protected void dataItemAdded(XYChart.Series<X, Y> series, int itemIndex,
                                 XYChart.Data<X, Y> item) {
        ensureNodes(series, item);
    }

    @Override
    protected void dataItemRemoved(XYChart.Data<X, Y> item, XYChart.Series<X, Y> series) {
        Node symbol = item.getNode();
        if (symbol != null) {
            getPlotChildren().remove(symbol);
            item.setNode(null);
        }
        Text label = labels.remove(item);
        if (label != null) getPlotChildren().remove(label);
        Node hover = hoverTargets.remove(item);
        if (hover != null) getPlotChildren().remove(hover);
    }

    @Override
    protected void dataItemChanged(XYChart.Data<X, Y> item) {
        Text label = labels.get(item);
        if (label != null) label.setText(formatValue(item.getYValue()));
    }

    @Override
    protected void layoutPlotChildren() {
        for (XYChart.Series<X, Y> series : getData()) {
            SeriesVisual visual = visuals.get(series);
            if (visual == null) {
                seriesAdded(series, getData().indexOf(series));
                visual = visuals.get(series);
                if (visual == null) continue;
            }

            List<Point2D> points = new ArrayList<>();
            for (XYChart.Data<X, Y> item : series.getData()) {
                ensureNodes(series, item);

                X xValue = item.getXValue();
                Y yValue = item.getYValue();
                if (xValue == null || yValue == null) continue;

                double x = getXAxis().getDisplayPosition(xValue);
                double y = getYAxis().getDisplayPosition(yValue);
                if (Double.isNaN(x) || Double.isNaN(y)) continue;
                points.add(new Point2D(x, y));

                Node symbol = item.getNode();
                if (symbol != null) { symbol.setLayoutX(x); symbol.setLayoutY(y); }

                Text label = labels.get(item);
                if (label != null) {
                    label.setText(formatValue(yValue));
                    label.setLayoutX(x - label.getLayoutBounds().getWidth() / 2.0);
                    label.setLayoutY(y - 8);
                }
                Node hover = hoverTargets.get(item);
                if (hover != null) { hover.setLayoutX(x); hover.setLayoutY(y); }
            }

            buildPath(visual.outline.getElements(), points, curvedLine);
            buildPath(visual.line.getElements(), points, curvedLine);
        }
    }

    private static final class SeriesVisual {
        final Path outline = new Path();
        final Path line = new Path();
        SeriesStyle style;
    }

    private void ensureNodes(XYChart.Series<X, Y> series, XYChart.Data<X, Y> item) {
    // 1) data-point symbol (also the hover target)
    if (pointShape != null && item.getNode() == null) {
        Node symbol = createSymbol(series);
        if (symbol != null) {
            final Object extra = item.getExtraValue();
            symbol.setOnMouseEntered(ev -> {
                if (onDataPointHover != null) onDataPointHover.accept(extra);
            });
            // Make the symbol a bit easier to hit
            symbol.setPickOnBounds(true);
            item.setNode(symbol);
            getPlotChildren().add(symbol);
        }
    }

    // 2) value label
    if (!labels.containsKey(item)) {
        Text label = new Text(formatValue(item.getYValue()));
        label.setTextOrigin(VPos.BOTTOM);
        label.setFont(Font.font(10));
        label.setFill(Color.web("#3c3c3c"));
        label.setMouseTransparent(false);
        label.getStyleClass().add("chart-line-data-label");
        label.setOnMouseEntered(e -> setHover(item, true));
        label.setOnMouseExited(e -> setHover(item, false));
        label.setVisible(showDataLabels.get());
        getPlotChildren().add(label);
        labels.put(item, label);
    }

    // 3) optional invisible hover region for tooltips
    if (showDataLabelsOnHover.get()) {
        ensureHoverTarget(item);
    }
}

    private Node createSymbol(XYChart.Series<X, Y> series) {
        if (pointShape == null) return null;
        Shape symbol = copyShape(pointShape);
        if (symbol == null) return null;
        SeriesStyle style = styleOf(series);
        if (symbol.getFill() == null) {
            symbol.setFill(style.getSymbolFill() != null ? style.getSymbolFill() : style.getStroke());
        } else if (style.getSymbolFill() != null) {
            symbol.setFill(style.getSymbolFill());
        }
        if (style.getSymbolStroke() != null) symbol.setStroke(style.getSymbolStroke());
        return symbol;
    }

    private void applyStyle(SeriesVisual visual, SeriesStyle style) {
        visual.style = style;
        visual.line.setFill(null);
        visual.line.setStroke(style.getStroke());
        visual.line.setStrokeWidth(style.getLineWidth());
        visual.line.setStrokeLineCap(StrokeLineCap.ROUND);
        visual.line.setStrokeLineJoin(StrokeLineJoin.ROUND);

        visual.outline.setFill(null);
        visual.outline.setStrokeLineCap(StrokeLineCap.ROUND);
        visual.outline.setStrokeLineJoin(StrokeLineJoin.ROUND);
        if (style.getOutline() != null && style.getOutlineWidth() > 0) {
            visual.outline.setStroke(style.getOutline());
            visual.outline.setStrokeWidth(style.getLineWidth() + 2 * style.getOutlineWidth());
            visual.outline.setVisible(true);
        } else {
            visual.outline.setVisible(false);
        }
    }

    private void applyStyles() {
        List<XYChart.Series<X, Y>> data = getData();
        for (int i = 0; i < data.size(); i++) {
            SeriesVisual visual = visuals.get(data.get(i));
            if (visual != null) applyStyle(visual, resolveStyle(i));
        }
        requestLayout();
    }

    private SeriesVisual visualForIndex(int index) {
        List<XYChart.Series<X, Y>> data = getData();
        if (index < 0 || index >= data.size()) return null;
        return visuals.get(data.get(index));
    }

    private SeriesStyle styleOf(XYChart.Series<X, Y> series) {
        SeriesVisual visual = visuals.get(series);
        if (visual != null && visual.style != null) return visual.style;
        return resolveStyle(getData().indexOf(series));
    }

    private SeriesStyle resolveStyle(int index) {
        if (index >= 0 && index < parsedStyles.size()) {
            SeriesStyle style = parsedStyles.get(index);
            if (style != null) return style;
        }
        SeriesStyle fallback = new SeriesStyle();
        fallback.setStroke(Color.web(DEFAULT_PALETTE[Math.floorMod(index, DEFAULT_PALETTE.length)]));
        return fallback;
    }

    private static void buildPath(ObservableList<PathElement> elements,
                                  List<Point2D> points, boolean curved) {
        elements.clear();
        if (points.isEmpty()) return;
        elements.add(new MoveTo(points.get(0).getX(), points.get(0).getY()));
        if (points.size() == 1) return;
        if (!curved) {
            for (int i = 1; i < points.size(); i++)
                elements.add(new LineTo(points.get(i).getX(), points.get(i).getY()));
            return;
        }
        int last = points.size() - 1;
        for (int i = 0; i < last; i++) {
            Point2D p0 = points.get(Math.max(0, i - 1));
            Point2D p1 = points.get(i);
            Point2D p2 = points.get(i + 1);
            Point2D p3 = points.get(Math.min(last, i + 2));

            double c1x = p1.getX() + (p2.getX() - p0.getX()) / 6.0;
            double c1y = p1.getY() + (p2.getY() - p0.getY()) / 6.0;
            double c2x = p2.getX() - (p3.getX() - p1.getX()) / 6.0;
            double c2y = p2.getY() - (p3.getY() - p1.getY()) / 6.0;

            elements.add(new CubicCurveTo(c1x, c1y, c2x, c2y, p2.getX(), p2.getY()));
        }
    }

    private static String formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return String.valueOf(d);
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
            return String.format("%.2f", d);
        }
        return String.valueOf(value);
    }
}