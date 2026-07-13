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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.inventory.humanizeDuration

/** Distinct line colours, cycled by series index. Legend swatches use the same colour. */
private val SeriesColors = listOf(
    Color(0xFF1E88E5), // blue
    Color(0xFFE53935), // red
    Color(0xFF43A047), // green
    Color(0xFFFB8C00), // orange
    Color(0xFF8E24AA), // purple
    Color(0xFF00ACC1), // cyan
    Color(0xFFF4511E), // deep orange
    Color(0xFF6D4C41), // brown
)

fun seriesColor(colorIndex: Int): Color = SeriesColors[colorIndex % SeriesColors.size]

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
                title = { Text("Run-out Graph") },
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

    // Below the axis: a gap to the month labels, then a larger gap to the "Time" title.
    val xTickToLabelGap = with(density) { 3.dp.toPx() }
    val xLabelToTitleGap = with(density) { 8.dp.toPx() }
    val xBottomMargin = with(density) { 4.dp.toPx() }
    val monthLabelHeight = textMeasurer.measure("Aug", labelStyle).size.height.toFloat()
    val xTitleHeight = textMeasurer.measure("Time", titleStyle).size.height.toFloat()

    // Plot insets. The bottom inset is sized to exactly fit the month labels and title, so the plot
    // stretches to the full canvas height rather than leaving empty space beneath the axis.
    val leftPad = yTitleInset + yTitleBand + yTitleToLabelGap + widestYLabelWidth + yLabelToAxisGap
    val rightPad = with(density) { 16.dp.toPx() }
    val topPad = with(density) { 16.dp.toPx() }
    val bottomPad = xTickToLabelGap + monthLabelHeight + xLabelToTitleGap + xTitleHeight + xBottomMargin

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

            // One declining line per series, from (now, total) to (run-out day, 0).
            for (s in data.series) {
                if (s.totalTablets <= 0) continue
                drawLine(
                    color = seriesColor(s.colorIndex),
                    start = Offset(xForDay(0.0), yForTab(s.totalTablets.toDouble())),
                    end = Offset(xForDay(s.runOutDay), yForTab(0.0)),
                    strokeWidth = 4f,
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

            // Draggable thumb straddling the X axis at the cursor. Translucent so the month labels
            // beneath it stay readable, with a firmer outline so the handle itself is still clear.
            val thumbW = with(density) { 44.dp.toPx() }
            val thumbH = with(density) { 22.dp.toPx() }
            val thumbLeft = (cursorX - thumbW / 2f).coerceIn(plotLeft - thumbW / 2f, plotRight - thumbW / 2f)
            val thumbCorner = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            drawRoundRect(
                color = thumbColor.copy(alpha = 0.3f),
                topLeft = Offset(thumbLeft, plotBottom - thumbH / 2f),
                size = Size(thumbW, thumbH),
                cornerRadius = thumbCorner,
            )
            drawRoundRect(
                color = thumbColor.copy(alpha = 0.7f),
                topLeft = Offset(thumbLeft, plotBottom - thumbH / 2f),
                size = Size(thumbW, thumbH),
                cornerRadius = thumbCorner,
                style = Stroke(width = 2f),
            )

            // X-axis title, set well below the month labels.
            drawAxisTitle(
                textMeasurer,
                "Time",
                titleStyle,
                centerX = (plotLeft + plotRight) / 2f,
                top = xLabelTop + monthLabelHeight + xLabelToTitleGap,
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
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(2.dp)),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) { drawRect(seriesColor(s.colorIndex)) }
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
