package com.example.aitoui.runout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.inventory.humanizeDuration
import com.example.aitoui.ui.heading

/**
 * A colourblind-accessible line style: colour, thickness and dash pattern. Series are told apart by all
 * three, not colour alone, so viewers with colour-vision deficiency can still distinguish them. Colours
 * are from the Okabe–Ito palette (designed for colourblind safety); widths stay slim (2–3.5dp) and the
 * dash patterns are chosen to be plainly different from one another.
 */
data class SeriesStyle(
    val color: Color,
    /** Line thickness in dp. */
    val widthDp: Float,
    /** Dash on/off intervals in dp, or null for a solid line. */
    val dashDp: List<Float>?,
) {
    fun strokeWidthPx(density: Density): Float = with(density) { widthDp.dp.toPx() }

    fun pathEffect(density: Density): PathEffect? = dashDp?.let { intervals ->
        PathEffect.dashPathEffect(FloatArray(intervals.size) { with(density) { intervals[it].dp.toPx() } })
    }
}

// Colour + width + dash chosen together so each entry differs from its neighbours in more than one way.
private val SeriesStyles = listOf(
    SeriesStyle(Color(0xFF0072B2), 2.5f, null),                     // blue — solid
    SeriesStyle(Color(0xFFD55E00), 3.0f, listOf(10f, 6f)),         // vermillion — dashed
    SeriesStyle(Color(0xFF009E73), 2.5f, listOf(2f, 6f)),          // bluish green — dotted
    SeriesStyle(Color(0xFFCC79A7), 3.0f, listOf(12f, 5f, 2f, 5f)), // reddish purple — dash-dot
    SeriesStyle(Color(0xFFE69F00), 2.5f, listOf(18f, 8f)),         // orange — long dash
    SeriesStyle(Color(0xFF56B4E9), 3.5f, listOf(10f, 6f)),         // sky blue — dashed (thicker)
    SeriesStyle(Color(0xFF000000), 2.0f, listOf(2f, 6f)),          // black — dotted (thin)
    SeriesStyle(Color(0xFFB8860B), 3.0f, null),                    // dark gold — solid
)

fun seriesStyle(colorIndex: Int): SeriesStyle = SeriesStyles[colorIndex % SeriesStyles.size]

@Composable
fun RunOutGraphRoot(
    onBack: () -> Unit,
    viewModel: RunOutGraphViewModel = viewModel(factory = RunOutGraphViewModel.Factory),
) {
    val data by viewModel.state.collectAsStateWithLifecycle()
    RunOutGraphScreen(data = data, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunOutGraphScreen(
    data: RunOutGraphData,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Run-out Graph", modifier = Modifier.heading()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        // A fixed (non-scrolling) layout: description pinned to the top, legend pinned to the bottom,
        // and the graph stretched to fill the space between them.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = "How each dispensable unit's supply — the tablets in hand plus your remaining " +
                    "script repeats — runs down over time. Drag the thumb on the time axis to read off " +
                    "how much is left at any point in the future.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            // The time cursor as a fraction [0,1] of the X domain, so it survives domain changes.
            var cursorFraction by remember { mutableFloatStateOf(0f) }

            if (data.isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nothing to graph yet. Add a daily schedule and some stock or scripts.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                RunOutChart(
                    data = data,
                    cursorFraction = cursorFraction,
                    onCursorFractionChange = { cursorFraction = it },
                    // Fill the space between text and legend. No external padding, so the small gaps
                    // above and below the graph come only from the neighbours' padding.
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                RunOutLegend(
                    data = data,
                    cursorDay = cursorFraction * data.domainDays,
                    // Horizontal margins match the description text block; the small top gap matches the
                    // gap below the "Time" label inside the graph area.
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun RunOutChart(
    data: RunOutGraphData,
    cursorFraction: Float,
    onCursorFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cursorDay = cursorFraction * data.domainDays

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val axisColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cursorColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.primary
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val titleStyle = TextStyle(fontSize = 11.sp, color = labelColor, fontWeight = FontWeight.Medium)

    // The Y-axis title's top-of-letters aligns with the left edge of the text block above (16dp in).
    val yTitleInset = with(density) { 16.dp.toPx() }
    val yTitleLayout = textMeasurer.measure("Tablets", titleStyle)
    // After a -90° rotation the title occupies a horizontal band as wide as its (unrotated) height.
    val yTitleBand = yTitleLayout.size.height.toFloat()
    val widestYLabelWidth = textMeasurer.measure(data.domainTablets.toString(), labelStyle).size.width.toFloat()
    val yTitleToLabelGap = with(density) { 5.dp.toPx() }   // small: title's bottom sits close to the numbers
    val yLabelToAxisGap = with(density) { 5.dp.toPx() }

    // Below the axis: the month labels, then the "Time" title, with the draggable thumb overlaid on
    // the title (the thumb is taller than the title, so it overhangs it slightly).
    val xTickToLabelGap = with(density) { 3.dp.toPx() }
    val xLabelToTitleGap = with(density) { 8.dp.toPx() }
    val xBottomMargin = with(density) { 4.dp.toPx() }
    val thumbWidth = with(density) { 44.dp.toPx() }
    val thumbHeight = with(density) { 22.dp.toPx() }
    val monthLabelHeight = textMeasurer.measure("Aug", labelStyle).size.height.toFloat()
    val xTitleHeight = textMeasurer.measure("Time", titleStyle).size.height.toFloat()
    // How far the title/thumb band extends below the title's top (the thumb overhangs the title).
    val xTitleBandBelow = maxOf(xTitleHeight, xTitleHeight / 2f + thumbHeight / 2f)

    // Plot insets. The bottom inset is sized to exactly fit the month labels, the thumb and the title,
    // so the plot stretches to fill the canvas rather than leaving empty space beneath the axis.
    val leftPad = yTitleInset + yTitleBand + yTitleToLabelGap + widestYLabelWidth + yLabelToAxisGap
    val rightPad = with(density) { 16.dp.toPx() }
    val topPad = with(density) { 16.dp.toPx() }
    val bottomPad = xTickToLabelGap + monthLabelHeight + xLabelToTitleGap + xTitleBandBelow + xBottomMargin

    fun fractionForX(x: Float, widthPx: Int): Float {
        val plotLeft = leftPad
        val plotRight = widthPx - rightPad
        return ((x - plotLeft) / (plotRight - plotLeft)).coerceIn(0f, 1f)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(data) {
                detectHorizontalDragGestures(
                    onDragStart = { pos -> onCursorFractionChange(fractionForX(pos.x, size.width)) },
                ) { change, _ -> onCursorFractionChange(fractionForX(change.position.x, size.width)) }
            }
            .pointerInput(data) {
                detectTapGestures { pos -> onCursorFractionChange(fractionForX(pos.x, size.width)) }
            },
    ) {
            val plotLeft = leftPad
            val plotRight = size.width - rightPad
            val plotTop = topPad
            val plotBottom = size.height - bottomPad
            val plotW = plotRight - plotLeft
            val plotH = plotBottom - plotTop

            fun xForDay(day: Double): Float = plotLeft + (day / data.domainDays).toFloat() * plotW
            fun yForTab(tab: Double): Float = plotBottom - (tab / data.domainTablets).toFloat() * plotH

            // Y-axis gridlines, ticks and labels at each tablet step. Labels are right-aligned close
            // to the axis so the title sits just to their left.
            var tab = 0
            while (tab <= data.domainTablets) {
                val y = yForTab(tab.toDouble())
                drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1f)
                drawLine(axisColor, Offset(plotLeft - 6f, y), Offset(plotLeft, y), strokeWidth = 2f)
                val layout = textMeasurer.measure(tab.toString(), labelStyle)
                drawText(
                    layout,
                    topLeft = Offset(plotLeft - yLabelToAxisGap - layout.size.width, y - layout.size.height / 2f),
                )
                tab += data.tabletTickStep
            }

            // X-axis ticks and labels at each calendar-month boundary, labelled with the month name.
            val xLabelTop = plotBottom + xTickToLabelGap
            for (t in data.monthTicks) {
                if (t.dayOffset < 0.0 || t.dayOffset > data.domainDays) continue
                val x = xForDay(t.dayOffset)
                drawLine(axisColor, Offset(x, plotBottom), Offset(x, plotBottom + 6f), strokeWidth = 2f)
                val layout = textMeasurer.measure(t.label, labelStyle)
                drawText(layout, topLeft = Offset(x - layout.size.width / 2f, xLabelTop))
            }

            // Axes.
            drawLine(axisColor, Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), strokeWidth = 3f)
            drawLine(axisColor, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), strokeWidth = 3f)

            // One declining line per series, from (now, total) to (run-out day, 0), each drawn with its
            // own colour, thickness and dash pattern so it reads without relying on colour alone.
            for (s in data.series) {
                if (s.totalTablets <= 0) continue
                val style = seriesStyle(s.colorIndex)
                drawLine(
                    color = style.color,
                    start = Offset(xForDay(0.0), yForTab(s.totalTablets.toDouble())),
                    end = Offset(xForDay(s.runOutDay), yForTab(0.0)),
                    strokeWidth = style.strokeWidthPx(this),
                    pathEffect = style.pathEffect(this),
                )
            }

            // Vertical time cursor (dashed).
            val cursorX = xForDay(cursorDay)
            drawLine(
                color = cursorColor,
                start = Offset(cursorX, plotTop),
                end = Offset(cursorX, plotBottom),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            )

            // X-axis title, directly below the month labels.
            val xTitleTop = xLabelTop + monthLabelHeight + xLabelToTitleGap
            drawAxisTitle(
                textMeasurer,
                "Time",
                titleStyle,
                centerX = (plotLeft + plotRight) / 2f,
                top = xTitleTop,
            )

            // Draggable thumb, centred vertically on the "Time" label and drawn over it — translucent,
            // so the label reads through — with a firmer outline so the handle stays clear.
            val thumbTop = xTitleTop + xTitleHeight / 2f - thumbHeight / 2f
            val thumbLeft =
                (cursorX - thumbWidth / 2f).coerceIn(plotLeft - thumbWidth / 2f, plotRight - thumbWidth / 2f)
            val thumbCorner = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            drawRoundRect(
                color = thumbColor.copy(alpha = 0.3f),
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = thumbCorner,
            )
            drawRoundRect(
                color = thumbColor.copy(alpha = 0.7f),
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = thumbCorner,
                style = Stroke(width = 2f),
            )

            // Y-axis title, rotated so its top-of-letters sits at yTitleInset (aligned to the text above)
            // and it is centred vertically along the axis.
            val yCenter = plotTop + plotH / 2f
            val yTitleTy = yCenter + yTitleLayout.size.width / 2f
            rotate(degrees = -90f, pivot = Offset(yTitleInset, yTitleTy)) {
                drawText(yTitleLayout, topLeft = Offset(yTitleInset, yTitleTy))
            }
        }
}

/**
 * The legend beneath the graph: one row per series with a colour swatch, the unit's name (taking the
 * flexible space) and the days remaining as at the time cursor. The days figure has a fixed width and
 * is clipped to a single line, so the rows don't jiggle as its text changes length while scrubbing.
 */
@Composable
private fun RunOutLegend(
    data: RunOutGraphData,
    cursorDay: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Remaining at cursor",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        data.series.forEach { s ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // A sample line drawn exactly as this series appears on the graph (colour, thickness,
                // dash), so the legend key does not depend on colour alone.
                val style = seriesStyle(s.colorIndex)
                Canvas(modifier = Modifier.size(width = 44.dp, height = 16.dp)) {
                    val midY = size.height / 2f
                    drawLine(
                        color = style.color,
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = style.strokeWidthPx(this),
                        pathEffect = style.pathEffect(this),
                    )
                }
                Text(
                    text = s.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = humanizeDuration(s.daysRemainingAt(cursorDay)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp),
                )
            }
        }
    }
}

/** Draws [text] horizontally centred on [centerX] with its top at [top]. */
private fun DrawScope.drawAxisTitle(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    centerX: Float,
    top: Float,
) {
    val layout: TextLayoutResult = textMeasurer.measure(text, style)
    drawText(layout, topLeft = Offset(centerX - layout.size.width / 2f, top))
}
