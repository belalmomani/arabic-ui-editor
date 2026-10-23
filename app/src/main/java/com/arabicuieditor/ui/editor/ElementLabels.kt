package com.arabicuieditor.ui.editor

import androidx.annotation.StringRes
import com.arabicuieditor.R
import com.arabicuieditor.data.model.ElementTypes

/** اسم النوع بالعربية لعرضه في القوائم. */
@StringRes
fun elementLabelRes(type: String): Int = when (type) {
    ElementTypes.TEXT -> R.string.element_text
    ElementTypes.BUTTON -> R.string.element_button
    ElementTypes.IMAGE -> R.string.element_image
    ElementTypes.INPUT -> R.string.element_input
    ElementTypes.CONTAINER -> R.string.element_container
    ElementTypes.CARD -> R.string.element_card
    ElementTypes.DIVIDER -> R.string.element_divider
    ElementTypes.SPACER -> R.string.element_spacer
    ElementTypes.LIST -> R.string.element_list
    else -> R.string.element_unknown
}
