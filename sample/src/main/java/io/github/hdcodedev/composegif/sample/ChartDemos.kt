package io.github.hdcodedev.composegif.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dautovicharis.charts.LineChart
import io.github.dautovicharis.charts.PieChart
import io.github.dautovicharis.charts.model.toChartDataSet
import io.github.dautovicharis.charts.model.toMultiChartDataSet
import io.github.hdcodedev.composegif.annotations.GifInteraction
import io.github.hdcodedev.composegif.annotations.GifInteractionTarget
import io.github.hdcodedev.composegif.annotations.GifInteractionType
import io.github.hdcodedev.composegif.annotations.GifSwipeDirection
import io.github.hdcodedev.composegif.annotations.GifSwipeDistance
import io.github.hdcodedev.composegif.annotations.GifSwipeSpeed
import io.github.hdcodedev.composegif.annotations.RecordGif
import io.github.hdcodedev.composegif.sample.ui.theme.DemoTheme

@Composable
fun ChartDemoScreen(modifier: Modifier = Modifier) {
    val demos =
        listOf(
            ChartDemo(
                title = "Pie Chart",
                subtitle = "Default @RecordGif settings",
                content = { PieChartDemo() },
            ),
            ChartDemo(
                title = "Multi Line Chart",
                subtitle = "Swipe interaction recording",
                content = { MultiLineChartDemo() },
            ),
        )

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    text = "Charts Playground",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Text(
                    text = "Chart demos powered by dautovicharis/charts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(demos) { demo ->
                DemoCard(demo = demo)
            }
        }
    }
}

private data class ChartDemo(
    val title: String,
    val subtitle: String,
    val content: @Composable () -> Unit,
)

@Composable
private fun DemoCard(demo: ChartDemo) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = demo.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = demo.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            demo.content()
        }
    }
}

@RecordGif
@Composable
internal fun PieChartDemo() {
    val dataSet =
        listOf(30f, 25f, 20f, 15f, 10f).toChartDataSet(
            title = "Market Share",
            postfix = "%",
            labels = listOf("Android", "iOS", "Web", "Desktop", "Other"),
        )
    PieChart(dataSet = dataSet)
}

@RecordGif(
    name = "multi_line_custom_gesture",
    durationMs = 3200,
    interactionNodeTag = "LineChartPlot",
    interactions = [
        GifInteraction(
            type = GifInteractionType.SWIPE,
            target = GifInteractionTarget.CENTER,
            direction = GifSwipeDirection.LEFT_TO_RIGHT,
            distance = GifSwipeDistance.LONG,
            speed = GifSwipeSpeed.NORMAL,
            framesAfter = 12,
        ),
    ],
)
@Composable
internal fun MultiLineChartDemo() {
    val items =
        listOf(
            "Web Store" to
                listOf(
                    420f,
                    510f,
                    480f,
                    530f,
                    560f,
                    590f,
                ),
            "Mobile App" to
                listOf(
                    360f,
                    420f,
                    410f,
                    460f,
                    500f,
                    540f,
                ),
            "Partner Sales" to
                listOf(
                    280f,
                    320f,
                    340f,
                    360f,
                    390f,
                    420f,
                ),
        )
    val dataSet =
        items.toMultiChartDataSet(
            title = "Weekly Revenue by Channel",
            prefix = "$",
            categories =
                listOf(
                    "Week 1",
                    "Week 2",
                    "Week 3",
                    "Week 4",
                    "Week 5",
                    "Week 6",
                ),
        )
    LineChart(dataSet = dataSet)
}

@Preview(showBackground = true)
@Composable
private fun ChartDemoScreenPreview() {
    DemoTheme(dynamicColor = false) {
        ChartDemoScreen()
    }
}
