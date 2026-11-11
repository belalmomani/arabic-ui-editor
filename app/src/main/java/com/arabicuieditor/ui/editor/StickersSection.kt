package com.arabicuieditor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.data.model.ComponentPreset
import com.arabicuieditor.data.model.Stickers
import com.arabicuieditor.ui.editor.canvas.rememberProjectImage

private const val COLUMNS = 3

/**
 * فئة «ملصقات» في المكتبة: حزم بصور جاهزة تُضاف كأزرار (صورة بتأثير ضغط وإجراء نقر قابل للضبط).
 * شبكة ثابتة من ثلاثة أعمدة داخل التمرير الرأسي للوحة؛ تُحمَّل الصور مصغّرة عند ظهورها.
 */
@Composable
internal fun StickersSection(onAdd: (ComponentPreset) -> Unit, modifier: Modifier = Modifier) {
    var packIndex by rememberSaveable { mutableIntStateOf(0) }
    val pack = Stickers.packs[packIndex.coerceIn(0, Stickers.packs.lastIndex)]
    Column(modifier.testTag("stickers_section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Stickers.packs.forEachIndexed { i, pk ->
                FilterChip(
                    selected = pk === pack,
                    onClick = { packIndex = i },
                    label = { Text(stringResource(pk.nameRes)) },
                    modifier = Modifier.testTag("sticker_pack_${pk.id}")
                )
            }
        }
        pack.tiles.chunked(COLUMNS).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTiles.forEach { t ->
                    val index = pack.tiles.indexOf(t) + 1
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAdd(Stickers.presetFor(t)) }
                            .testTag("sticker_${t.id}")
                    ) {
                        Box(Modifier.padding(4.dp).aspectRatio(maxOf(0.6f, minOf(1.8f, t.w.toFloat() / t.h))), contentAlignment = Alignment.Center) {
                            val bmp = rememberProjectImage(t.fileName)
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = stringResource(R.string.sticker_label, index),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                repeat(COLUMNS - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
