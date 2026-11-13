package com.arabicuieditor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.OutlinedButton
import com.arabicuieditor.data.model.StickerFavorites
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
    // الفهرس -1 = «المفضلة»، وما بعده حزم الملصقات.
    var packIndex by rememberSaveable { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("stickers", android.content.Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(StickerFavorites.decode(prefs.getString("fav", null))) }
    val inFavorites = packIndex < 0
    val pack = Stickers.packs[packIndex.coerceIn(0, Stickers.packs.lastIndex)]
    val shown: List<Stickers.Tile> = if (inFavorites) favorites.mapNotNull { Stickers.tile(it) } else pack.tiles
    Column(modifier.testTag("stickers_section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { onAdd(Stickers.blankImageButton) },
            modifier = Modifier.fillMaxWidth().testTag("sticker_blank_button")
        ) { Text(stringResource(R.string.stk_blank_button)) }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = inFavorites,
                onClick = { packIndex = -1 },
                label = { Text("★ " + stringResource(R.string.stk_favorites)) },
                modifier = Modifier.testTag("sticker_pack_fav")
            )
            Stickers.packs.forEachIndexed { i, pk ->
                FilterChip(
                    selected = !inFavorites && pk === pack,
                    onClick = { packIndex = i },
                    label = { Text(stringResource(pk.nameRes)) },
                    modifier = Modifier.testTag("sticker_pack_${pk.id}")
                )
            }
        }
        Text(
            stringResource(if (inFavorites && shown.isEmpty()) R.string.stk_favorites_empty else R.string.stk_favorites_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        shown.chunked(COLUMNS).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTiles.forEach { t ->
                    val index = (Stickers.packs.firstOrNull { it.tiles.contains(t) } ?: pack).tiles.indexOf(t) + 1
                    val isFav = t.id in favorites
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .pointerInput(t.id) {
                                detectTapGestures(
                                    onTap = { onAdd(Stickers.presetFor(t)) },
                                    onLongPress = {
                                        favorites = StickerFavorites.toggle(favorites, t.id)
                                        prefs.edit().putString("fav", StickerFavorites.encode(favorites)).apply()
                                    }
                                )
                            }
                            .testTag("sticker_${t.id}")
                    ) {
                        Box(Modifier.padding(4.dp).aspectRatio(maxOf(0.6f, minOf(1.8f, t.w.toFloat() / t.h))), contentAlignment = Alignment.Center) {
                            val bmp = rememberProjectImage(t.fileName)
                            if (isFav) {
                                Text("★", color = androidx.compose.ui.graphics.Color(0xFFD99A36), modifier = Modifier.align(Alignment.TopStart).testTag("fav_${t.id}"))
                            }
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
