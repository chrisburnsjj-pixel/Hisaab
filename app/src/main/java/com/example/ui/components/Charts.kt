package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class CategorySlice(
    val category: String,
    val amount: Double,
    val percentage: Float, // 0f .. 1f
    val color: Color
)

data class MonthlyBarData(
    val label: String,
    val income: Double,
    val expense: Double
)

data class DailyTrendPoint(
    val label: String,
    val amount: Double
)

val ChartCategoryColors = listOf(
    Color(0xFF10B981), // Emerald
    Color(0xFF3B82F6), // Blue
    Color(0xFFF59E0B), // Amber
    Color(0xFFEF4444), // Red
    Color(0xFF8B5CF6), // Purple
    Color(0xFFEC4899), // Pink
    Color(0xFF06B6D4), // Cyan
    Color(0xFFF97316), // Orange
    Color(0xFF14B8A6), // Teal
    Color(0xFF6366F1), // Indigo
    Color(0xFF64748B)  // Slate
)

/**
 * Interactive Donut / Pie Chart with category legend.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDonutChart(
    slices: List<CategorySlice>,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No category data for this period",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val totalAmount = slices.sumOf { it.amount }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(700))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(190.dp)
                    .pointerInput(slices) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = offset.x - center.x
                            val dy = offset.y - center.y
                            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                            val innerR = (size.width / 2f) * 0.55f
                            val outerR = size.width / 2f

                            if (dist in innerR..outerR) {
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (angle < 0) angle += 360f

                                var cumulative = 0f
                                var foundIndex: Int? = null
                                for (i in slices.indices) {
                                    val sweep = slices[i].percentage * 360f
                                    if (angle in cumulative..(cumulative + sweep)) {
                                        foundIndex = i
                                        break
                                    }
                                    cumulative += sweep
                                }
                                selectedIndex = if (selectedIndex == foundIndex) null else foundIndex
                            } else {
                                selectedIndex = null
                            }
                        }
                    }
            ) {
                val strokeWidth = size.width * 0.22f
                val radius = (size.width - strokeWidth) / 2f
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)

                var startAngle = 0f
                slices.forEachIndexed { index, slice ->
                    val sweepAngle = slice.percentage * 360f * animationProgress.value
                    val isSelected = selectedIndex == index

                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(
                            width = if (isSelected) strokeWidth * 1.15f else strokeWidth,
                            cap = StrokeCap.Butt
                        )
                    )
                    startAngle += sweepAngle
                }
            }

            // Center details
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val activeSlice = selectedIndex?.let { slices.getOrNull(it) }
                if (activeSlice != null) {
                    Text(
                        text = activeSlice.category,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = activeSlice.color,
                        maxLines = 1
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", activeSlice.percentage * 100f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format(Locale.US, "%s %,.0f", currencySymbol, activeSlice.amount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Total Spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%s %,.0f", currencySymbol, totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${slices.size} Categories",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Legend Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slices.forEachIndexed { index, slice ->
                val isSelected = selectedIndex == index
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) slice.color.copy(alpha = 0.15f) else Color.Transparent
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(slice.color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${slice.category} (${String.format(Locale.US, "%.0f%%", slice.percentage * 100f)})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Bar Chart comparing Income vs Expenses by Month.
 */
@Composable
fun MonthlyBarChart(
    data: List<MonthlyBarData>,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No monthly data available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val maxVal = remember(data) {
        val highest = data.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0
        if (highest <= 0.0) 100.0 else highest * 1.15
    }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(800))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(IncomeGreen)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ExpenseRed)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Expense", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                val availableW = size.width
                val availableH = size.height
                val barPairWidth = (availableW / data.size.toFloat())
                val barW = (barPairWidth * 0.32f).coerceAtMost(28f)
                val spacing = 4f

                // Draw background grid lines (3 horizontal guide lines)
                val gridColor = Color.LightGray.copy(alpha = 0.25f)
                for (i in 1..3) {
                    val y = availableH * (i / 4f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(availableW, y),
                        strokeWidth = 1f
                    )
                }

                data.forEachIndexed { i, item ->
                    val pairCenterX = (i * barPairWidth) + (barPairWidth / 2f)

                    val incomeH = ((item.income / maxVal).toFloat() * availableH * animProgress.value)
                        .coerceAtLeast(3f)
                    val expenseH = ((item.expense / maxVal).toFloat() * availableH * animProgress.value)
                        .coerceAtLeast(3f)

                    val incomeLeft = pairCenterX - barW - (spacing / 2f)
                    val expenseLeft = pairCenterX + (spacing / 2f)

                    // Draw Income Bar
                    drawRoundRect(
                        color = IncomeGreen,
                        topLeft = Offset(incomeLeft, availableH - incomeH),
                        size = Size(barW, incomeH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )

                    // Draw Expense Bar
                    drawRoundRect(
                        color = ExpenseRed,
                        topLeft = Offset(expenseLeft, availableH - expenseH),
                        size = Size(barW, expenseH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                }
            }

            // Month Labels Row below the canvas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(30.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                data.forEach { item ->
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Line Chart for Daily Trends with glowing gradient fill.
 */
@Composable
fun DailyTrendLineChart(
    points: List<DailyTrendPoint>,
    currencySymbol: String,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No daily records for trendline",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val maxVal = remember(points) {
        val highest = points.maxOfOrNull { it.amount } ?: 1.0
        if (highest <= 0.0) 100.0 else highest * 1.2
    }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(900))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val w = size.width
                val h = size.height

                if (points.size < 2) {
                    val singleY = h - ((points[0].amount / maxVal).toFloat() * h)
                    drawCircle(color = lineColor, radius = 6f, center = Offset(w / 2f, singleY))
                    return@Canvas
                }

                val stepX = w / (points.size - 1).toFloat()

                val linePath = Path()
                val fillPath = Path()

                fillPath.moveTo(0f, h)

                points.forEachIndexed { index, point ->
                    val x = index * stepX
                    val y = h - ((point.amount / maxVal).toFloat() * h * animProgress.value).coerceIn(4f, h - 4f)

                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.lineTo(x, y)
                    } else {
                        // Smooth cubic bezier
                        val prevX = (index - 1) * stepX
                        val prevY = h - ((points[index - 1].amount / maxVal).toFloat() * h * animProgress.value).coerceIn(4f, h - 4f)
                        val cpx1 = prevX + (stepX / 2f)
                        val cpy1 = prevY
                        val cpx2 = prevX + (stepX / 2f)
                        val cpy2 = y
                        linePath.cubicTo(cpx1, cpy1, cpx2, cpy2, x, y)
                        fillPath.cubicTo(cpx1, cpy1, cpx2, cpy2, x, y)
                    }
                }

                fillPath.lineTo(w, h)
                fillPath.close()

                // Draw Gradient Area under the curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.35f), Color.Transparent),
                        startY = 0f,
                        endY = h
                    )
                )

                // Draw Line
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw points
                points.forEachIndexed { index, point ->
                    val x = index * stepX
                    val y = h - ((point.amount / maxVal).toFloat() * h * animProgress.value).coerceIn(4f, h - 4f)
                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(x, y))
                    drawCircle(color = lineColor, radius = 2.5.dp.toPx(), center = Offset(x, y))
                }
            }

            // Labels under chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (points.isNotEmpty()) {
                    Text(
                        text = points.first().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (points.size > 2) {
                        Text(
                            text = points[points.size / 2].label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = points.last().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
