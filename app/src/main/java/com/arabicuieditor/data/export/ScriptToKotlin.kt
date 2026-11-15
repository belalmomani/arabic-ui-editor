package com.arabicuieditor.data.export

/**
 * محوّل مجموعة جزئية من سكربت المعاينة (JS) إلى Kotlin للتصدير. أي بنية غير مدعومة (حلقات، دوال،
 * تعبيرات قوالب...) تجعل [convert] يعيد null فيُترك للمستخدم تعليق TODO يحوي الكود الأصلي.
 *
 * المدعوم: `vars.x` (= += -= *= /= ++ --)، `$('اسم').text/.visible/.color/.bg` (قراءة وكتابة)،
 * `toast() navigate() back() sound()`، `if / else`، والتعابير: أرقام ونصوص وقيم منطقية
 * و+ - * / % ومقارنات و&& || !. دلالة `+`: نص إن كان أحد طرفيها نصًّا وإلا جمع رقمي.
 */
object ScriptToKotlin {
    data class Result(val kotlin: String, val elements: Set<String>, val usesToast: Boolean)

    class Env(
        /** اسم الشاشة ← مسار التنقل، أو null إن لم توجد. */
        val route: (String) -> String?,
        /** اسم العنصر ← نصه الابتدائي، أو null إن لم يوجد عنصر بهذا الاسم. */
        val initialText: (String) -> String?,
        val initialVisible: (String) -> Boolean,
        /** معرّف صوت ← استدعاء Kotlin، أو null إن كان غير صالح. */
        val soundCall: (String) -> String?,
        val esc: (String) -> String
    )

    const val MAX_CODE = 4000

    private class Unsupported : Exception()

    private enum class K { NUM, STR, ID, OP, EOF }
    private data class Tok(val k: K, val s: String)
    private enum class T { NUM, STR, BOOL, VAR }
    private data class Ex(val t: T, val code: String, val name: String? = null)

    fun convert(code: String, env: Env): Result? {
        if (code.isBlank() || code.length > MAX_CODE) return null
        return try {
            Parser(tokenize(code), env).program()
        } catch (_: Unsupported) {
            null
        }
    }

    private val OPS3 = listOf("===", "!==")
    private val OPS2 = listOf("==", "!=", "<=", ">=", "&&", "||", "+=", "-=", "*=", "/=", "++", "--", "=>")

    private fun tokenize(src: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                src.startsWith("//", i) -> while (i < src.length && src[i] != '\n') i++
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2)
                    if (end < 0) throw Unsupported()
                    i = end + 2
                }
                c.isDigit() -> {
                    var j = i
                    while (j < src.length && (src[j].isDigit() || src[j] == '.')) j++
                    out += Tok(K.NUM, src.substring(i, j)); i = j
                }
                c == '\'' || c == '"' -> {
                    val sb = StringBuilder()
                    var j = i + 1
                    while (j < src.length && src[j] != c) {
                        if (src[j] == '\\') {
                            j++
                            if (j >= src.length) throw Unsupported()
                            when (src[j]) { 'n' -> sb.append('\n'); 't' -> sb.append('\t'); else -> sb.append(src[j]) }
                        } else sb.append(src[j])
                        j++
                    }
                    if (j >= src.length) throw Unsupported()
                    out += Tok(K.STR, sb.toString()); i = j + 1
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    var j = i
                    while (j < src.length && (src[j].isLetterOrDigit() || src[j] == '_' || src[j] == '$')) j++
                    out += Tok(K.ID, src.substring(i, j)); i = j
                }
                else -> {
                    val op = OPS3.firstOrNull { src.startsWith(it, i) } ?: OPS2.firstOrNull { src.startsWith(it, i) } ?: c.toString()
                    if (op == "=>" || op.length == 1 && op[0] !in "+-*/%<>!=(){}.;,") throw Unsupported()
                    out += Tok(K.OP, op); i += op.length
                }
            }
        }
        out += Tok(K.EOF, "")
        return out
    }

    private class Parser(val toks: List<Tok>, val env: Env) {
        var p = 0
        val touched = LinkedHashSet<String>()
        var usesToast = false

        fun peek() = toks[p]
        fun isOp(s: String) = peek().k == K.OP && peek().s == s
        fun isId(s: String) = peek().k == K.ID && peek().s == s
        fun eatOp(s: String): Boolean = if (isOp(s)) { p++; true } else false
        fun needOp(s: String) { if (!eatOp(s)) throw Unsupported() }
        fun needStr(): String { val t = toks[p]; if (t.k != K.STR) throw Unsupported(); p++; return t.s }
        fun needId(): String { val t = toks[p]; if (t.k != K.ID) throw Unsupported(); p++; return t.s }

        fun program(): Result {
            val out = ArrayList<String>()
            while (peek().k != K.EOF) statement(out)
            return Result(out.joinToString("; "), touched, usesToast)
        }

        fun q(s: String) = "\"${env.esc(s)}\""

        // ---- تحويلات الأنواع ----
        fun num(e: Ex): String = when (e.t) {
            T.NUM -> e.code
            T.VAR -> "AppLogic.num(${q(e.name!!)})"
            else -> throw Unsupported()
        }

        fun str(e: Ex): String = when (e.t) {
            T.STR -> e.code
            T.NUM -> "AppLogic.fmt(${e.code})"
            T.BOOL -> "${e.code}.toString()"
            T.VAR -> "AppLogic.str(${q(e.name!!)})"
        }

        fun bool(e: Ex): String = when (e.t) {
            T.BOOL -> e.code
            T.VAR -> "AppLogic.vars[${q(e.name!!)}].let { it == true || (it is Number && it.toDouble() != 0.0) || (it is String && it.isNotEmpty()) }"
            else -> throw Unsupported()
        }

        // ---- التعابير (من الأدنى أسبقية إلى الأعلى) ----
        fun expr(): Ex = or()

        fun or(): Ex {
            var l = and()
            while (eatOp("||")) { val r = and(); l = Ex(T.BOOL, "(${bool(l)} || ${bool(r)})") }
            return l
        }

        fun and(): Ex {
            var l = eq()
            while (eatOp("&&")) { val r = eq(); l = Ex(T.BOOL, "(${bool(l)} && ${bool(r)})") }
            return l
        }

        fun eq(): Ex {
            var l = rel()
            while (isOp("==") || isOp("!=") || isOp("===") || isOp("!==")) {
                val neg = toks[p].s.startsWith("!")
                p++
                val r = rel()
                val body = when {
                    l.t == T.NUM || r.t == T.NUM -> "${num(l)} == ${num(r)}"
                    l.t == T.BOOL || r.t == T.BOOL -> "${bool(l)} == ${bool(r)}"
                    else -> "${str(l)} == ${str(r)}"
                }
                l = Ex(T.BOOL, if (neg) "!($body)" else "($body)")
            }
            return l
        }

        fun rel(): Ex {
            var l = add()
            while (isOp("<") || isOp(">") || isOp("<=") || isOp(">=")) {
                val op = toks[p].s; p++
                val r = add()
                l = Ex(T.BOOL, "(${num(l)} $op ${num(r)})")
            }
            return l
        }

        fun add(): Ex {
            var l = mul()
            while (isOp("+") || isOp("-")) {
                val op = toks[p].s; p++
                val r = mul()
                l = if (op == "+" && (l.t == T.STR || r.t == T.STR)) Ex(T.STR, "(${str(l)} + ${str(r)})")
                else Ex(T.NUM, "(${num(l)} $op ${num(r)})")
            }
            return l
        }

        fun mul(): Ex {
            var l = unary()
            while (isOp("*") || isOp("/") || isOp("%")) {
                val op = toks[p].s; p++
                val r = unary()
                l = Ex(T.NUM, "(${num(l)} $op ${num(r)})")
            }
            return l
        }

        fun unary(): Ex {
            if (eatOp("!")) return Ex(T.BOOL, "(!${bool(unary())})")
            if (eatOp("-")) return Ex(T.NUM, "(-${num(unary())})")
            return primary()
        }

        /** `$('اسم')` ثم `.خاصية`؛ يعيد (الاسم، الخاصية). */
        fun elementRef(): Pair<String, String> {
            needOp("("); val n = needStr(); needOp(")"); needOp(".")
            val prop = needId()
            if (env.initialText(n) == null || prop !in setOf("text", "visible", "color", "bg")) throw Unsupported()
            return n to prop
        }

        fun primary(): Ex {
            val t = peek()
            when (t.k) {
                K.NUM -> {
                    p++
                    val d = t.s.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw Unsupported()
                    return Ex(T.NUM, d.toString())
                }
                K.STR -> { p++; return Ex(T.STR, q(t.s)) }
                K.ID -> when {
                    t.s == "true" || t.s == "false" -> { p++; return Ex(T.BOOL, t.s) }
                    t.s == "vars" -> {
                        p++; needOp(".")
                        return Ex(T.VAR, "", needId())
                    }
                    t.s == "$" -> {
                        p++
                        val (n, prop) = elementRef()
                        return when (prop) {
                            "text" -> Ex(T.STR, "AppLogic.prop(${q(n)}, \"text\", ${q(env.initialText(n).orEmpty())})")
                            "visible" -> Ex(T.BOOL, "AppLogic.visible(${q(n)}, ${env.initialVisible(n)})")
                            "color" -> Ex(T.STR, "(AppLogic.props[${q("$n|textColor")}] ?: \"\")")
                            else -> Ex(T.STR, "(AppLogic.props[${q("$n|bgColor")}] ?: \"\")")
                        }
                    }
                    else -> throw Unsupported()
                }
                K.OP -> if (t.s == "(") {
                    p++
                    val e = expr()
                    needOp(")")
                    return e
                }
                else -> {}
            }
            throw Unsupported()
        }

        // ---- العبارات ----
        fun endStatement() { eatOp(";") }

        fun block(out: MutableList<String>) {
            if (eatOp("{")) {
                while (!isOp("}")) { if (peek().k == K.EOF) throw Unsupported(); statement(out) }
                needOp("}")
            } else statement(out)
        }

        fun statement(out: MutableList<String>) {
            if (eatOp(";")) return
            if (isOp("{")) { block(out); return }
            val t = peek()
            if (t.k != K.ID) throw Unsupported()
            when (t.s) {
                "if" -> {
                    p++; needOp("(")
                    val c = bool(expr())
                    needOp(")")
                    val a = ArrayList<String>(); block(a)
                    var code = "if ($c) { ${a.joinToString("; ")} }"
                    if (isId("else")) {
                        p++
                        val b = ArrayList<String>(); block(b)
                        code += " else { ${b.joinToString("; ")} }"
                    }
                    out += code
                }
                "vars" -> { p++; needOp("."); varStatement(needId(), out) }
                "$" -> { p++; elementStatement(out) }
                "toast" -> {
                    p++; needOp("("); val m = str(expr()); needOp(")"); endStatement()
                    usesToast = true
                    out += "Toast.makeText(ctx, $m, Toast.LENGTH_SHORT).show()"
                }
                "navigate" -> {
                    p++; needOp("("); val n = needStr(); needOp(")"); endStatement()
                    out += "onNavigate(${q(env.route(n) ?: throw Unsupported())})"
                }
                "back" -> { p++; needOp("("); needOp(")"); endStatement(); out += "onBack()" }
                "sound" -> {
                    p++; needOp("("); val id = needStr(); needOp(")"); endStatement()
                    out += env.soundCall(id) ?: throw Unsupported()
                }
                else -> throw Unsupported()
            }
        }

        fun varStatement(name: String, out: MutableList<String>) {
            val n = q(name)
            when {
                eatOp("++") -> out += "AppLogic.add($n, 1.0)"
                eatOp("--") -> out += "AppLogic.add($n, -1.0)"
                eatOp("=") -> {
                    val e = expr()
                    out += "AppLogic.vars[$n] = " + when (e.t) {
                        T.VAR -> "(AppLogic.vars[${q(e.name!!)}] ?: \"\")"
                        else -> e.code
                    }
                }
                eatOp("+=") -> {
                    val e = expr()
                    out += if (e.t == T.STR) "AppLogic.vars[$n] = AppLogic.str($n) + ${e.code}"
                    else "AppLogic.add($n, ${num(e)})"
                }
                eatOp("-=") -> out += "AppLogic.add($n, -${num(expr()).let { "($it)" }})"
                eatOp("*=") -> out += "AppLogic.vars[$n] = AppLogic.num($n) * ${num(expr()).let { "($it)" }}"
                eatOp("/=") -> out += "AppLogic.vars[$n] = AppLogic.num($n) / ${num(expr()).let { "($it)" }}"
                else -> throw Unsupported()
            }
            endStatement()
        }

        fun elementStatement(out: MutableList<String>) {
            val (n, prop) = elementRef()
            needOp("=")
            val e = expr()
            touched += n
            out += when (prop) {
                "text" -> "AppLogic.props[${q("$n|text")}] = ${str(e)}"
                "visible" -> "AppLogic.props[${q("$n|visible")}] = if (${bool(e)}) \"true\" else \"false\""
                "color" -> "AppLogic.props[${q("$n|textColor")}] = ${str(e)}"
                else -> "AppLogic.props[${q("$n|bgColor")}] = ${str(e)}"
            }
            endStatement()
        }
    }
}
