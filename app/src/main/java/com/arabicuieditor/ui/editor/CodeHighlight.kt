package com.arabicuieditor.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/** تلوين بسيط للكود (Kotlin/Dart/XML) على خلفية فاتحة: تعليقات، سلاسل، كلمات مفتاحية، أرقام. بلا أي تغيير في النص. */
object CodeHighlight {
    private val keywords = setOf(
        "package", "import", "class", "object", "fun", "val", "var", "if", "else", "when", "for", "while", "return",
        "private", "internal", "const", "null", "true", "false", "is", "in", "by", "override", "data", "enum",
        "void", "final", "const", "extends", "with", "new", "this", "super", "static", "late", "required", "String",
        "int", "double", "bool", "Widget", "BuildContext", "build", "state"
    )
    private val comment = Color(0xFF77838D)
    private val string = Color(0xFF23866B)
    private val keyword = Color(0xFF1F4E8C)
    private val number = Color(0xFFB7791F)
    private val tag = Color(0xFF8A3B7A)

    private val token = Regex(
        "(//[^\\n]*|<!--[\\s\\S]*?-->)" +                 // 1 تعليق
            "|(\"(?:[^\"\\\\\\n]|\\\\.)*\")" +            // 2 سلسلة
            "|(</?[A-Za-z][\\w.:-]*|/?>)" +               // 3 وسم XML
            "|(\\b\\d+(?:\\.\\d+)?[fFLdp]*\\b)" +         // 4 رقم
            "|(\\b[A-Za-z_]\\w*\\b)"                      // 5 معرّف
    )

    /** الأكواد الطويلة جدًا تُعرض بلا تلوين لتبقى الواجهة سريعة. */
    const val MAX_CHARS = 200_000

    fun highlight(code: String): AnnotatedString {
        if (code.length > MAX_CHARS) return AnnotatedString(code)
        return buildAnnotatedString {
            var last = 0
            for (m in token.findAll(code)) {
                if (m.range.first > last) append(code.substring(last, m.range.first))
                val text = m.value
                val color = when {
                    m.groups[1] != null -> comment
                    m.groups[2] != null -> string
                    m.groups[3] != null -> tag
                    m.groups[4] != null -> number
                    text in keywords -> keyword
                    else -> null
                }
                if (color == null) append(text) else withStyle(SpanStyle(color = color)) { append(text) }
                last = m.range.last + 1
            }
            if (last < code.length) append(code.substring(last))
        }
    }
}
