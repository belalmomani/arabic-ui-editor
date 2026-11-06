package com.arabicuieditor.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * أيقونات خطية موحّدة لواجهة المحرر: شبكة 24×24، سماكة خط 1.8، أطراف وزوايا مستديرة، بلا تعبئة.
 * اللون يأتي من tint في Icon (الخط الأسود هنا مجرد قناع).
 */
object AppIcons {
    private const val STROKE = 1.8f

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block
            )
        }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t)
        horizontalLineTo(r - rad)
        arcTo(rad, rad, 0f, false, true, r, t + rad)
        verticalLineTo(b - rad)
        arcTo(rad, rad, 0f, false, true, r - rad, b)
        horizontalLineTo(l + rad)
        arcTo(rad, rad, 0f, false, true, l, b - rad)
        verticalLineTo(t + rad)
        arcTo(rad, rad, 0f, false, true, l + rad, t)
        close()
    }

    val Plus = icon("plus") { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) }

    val Undo = icon("undo") {
        moveTo(9f, 14f); lineTo(4f, 9f); lineTo(9f, 4f)
        moveTo(4f, 9f); horizontalLineTo(15f)
        arcTo(5f, 5f, 0f, false, true, 15f, 19f); horizontalLineTo(11f)
    }

    val Redo = icon("redo") {
        moveTo(15f, 14f); lineTo(20f, 9f); lineTo(15f, 4f)
        moveTo(20f, 9f); horizontalLineTo(9f)
        arcTo(5f, 5f, 0f, false, false, 9f, 19f); horizontalLineTo(13f)
    }

    val Play = icon("play") {
        circle(12f, 12f, 9f)
        moveTo(10f, 8.5f); lineTo(16f, 12f); lineTo(10f, 15.5f); close()
    }

    val Layers = icon("layers") {
        moveTo(12f, 3f); lineTo(21f, 8f); lineTo(12f, 13f); lineTo(3f, 8f); close()
        moveTo(3f, 12f); lineTo(12f, 17f); lineTo(21f, 12f)
        moveTo(3f, 16f); lineTo(12f, 21f); lineTo(21f, 16f)
    }

    val Edit = icon("edit") {
        moveTo(4f, 20f); lineTo(4f, 16f); lineTo(16f, 4f); lineTo(20f, 8f); lineTo(8f, 20f); close()
        moveTo(13.5f, 6.5f); lineTo(17.5f, 10.5f)
    }

    val Page = icon("page") {
        roundRect(5f, 3f, 19f, 21f, 2f)
        moveTo(10f, 18f); horizontalLineTo(14f)
    }

    val Fit = icon("fit") {
        moveTo(4f, 9f); verticalLineTo(4f); horizontalLineTo(9f)
        moveTo(15f, 4f); horizontalLineTo(20f); verticalLineTo(9f)
        moveTo(20f, 15f); verticalLineTo(20f); horizontalLineTo(15f)
        moveTo(9f, 20f); horizontalLineTo(4f); verticalLineTo(15f)
    }

    val Contrast = icon("contrast") {
        circle(12f, 12f, 9f)
        moveTo(12f, 3f); verticalLineTo(21f)
    }

    val Device = icon("device") {
        roundRect(7f, 2.5f, 17f, 21.5f, 2f)
        moveTo(11f, 18.5f); horizontalLineTo(13f)
    }

    val Duplicate = icon("duplicate") {
        roundRect(9f, 9f, 20f, 20f, 2f)
        moveTo(15f, 9f); verticalLineTo(6f)
        arcTo(2f, 2f, 0f, false, false, 13f, 4f); horizontalLineTo(6f)
        arcTo(2f, 2f, 0f, false, false, 4f, 6f); verticalLineTo(13f)
        arcTo(2f, 2f, 0f, false, false, 6f, 15f); horizontalLineTo(9f)
    }

    val ChevronDown = icon("chevronDown") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
    val ChevronUp = icon("chevronUp") { moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f) }

    val Trash = icon("trash") {
        moveTo(4f, 7f); horizontalLineTo(20f)
        moveTo(10f, 11f); verticalLineTo(17f)
        moveTo(14f, 11f); verticalLineTo(17f)
        moveTo(6f, 7f); lineTo(7f, 19f)
        arcTo(2f, 2f, 0f, false, false, 9f, 21f); horizontalLineTo(15f)
        arcTo(2f, 2f, 0f, false, false, 17f, 19f); lineTo(18f, 7f)
        moveTo(9f, 7f); verticalLineTo(4f); horizontalLineTo(15f); verticalLineTo(7f)
    }

    val Code = icon("code") {
        moveTo(8f, 7f); lineTo(3f, 12f); lineTo(8f, 17f)
        moveTo(16f, 7f); lineTo(21f, 12f); lineTo(16f, 17f)
        moveTo(14f, 5f); lineTo(10f, 19f)
    }

    /** سهم رجوع يتجه يمينًا (يُستعمل في RTL فيعني «رجوع»). */
    val Back = icon("back") {
        moveTo(5f, 12f); horizontalLineTo(19f)
        moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
    }
}
