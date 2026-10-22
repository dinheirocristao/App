package br.com.autotdah.regulacaoproduzmais.data

/** Codificador JSON mínimo, sem dependências, usado na persistência local. */
class JsonException(message: String) : Exception(message)

object Json {

    fun write(value: Any?): String = StringBuilder().also { writeTo(it, value) }.toString()

    private fun writeTo(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v.toString())
            is Double -> {
                if (!v.isFinite()) throw JsonException("Número inválido")
                sb.append(v.toString())
            }
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k as? String ?: throw JsonException("Chave não textual"))
                    sb.append(':')
                    writeTo(sb, value)
                }
                sb.append('}')
            }
            is Collection<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeTo(sb, item)
                }
                sb.append(']')
            }
            else -> throw JsonException("Tipo não suportado: ${v::class.java.name}")
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.ws()
        if (!p.atEnd()) throw JsonException("Conteúdo inesperado na posição ${p.i}")
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun atEnd() = i >= s.length
        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(): Char {
            if (i >= s.length) throw JsonException("Fim inesperado")
            return s[i]
        }

        fun value(): Any? {
            ws()
            return when (val c = peek()) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw JsonException("Caractere inesperado '$c'")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw JsonException("Literal inválido")
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (peek() == '}') { i++; return m }
            while (true) {
                ws()
                if (peek() != '"') throw JsonException("Chave esperada")
                val k = str()
                ws()
                if (peek() != ':') throw JsonException("':' esperado")
                i++
                m[k] = value()
                ws()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw JsonException("',' ou '}' esperado")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            ws()
            if (peek() == ']') { i++; return l }
            while (true) {
                l += value()
                ws()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> throw JsonException("',' ou ']' esperado")
                }
            }
        }

        private fun str(): String {
            val sb = StringBuilder()
            i++
            while (true) {
                val c = peek()
                i++
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = peek()
                        i++
                        when (e) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw JsonException("Escape unicode incompleto")
                                val hex = s.substring(i, i + 4)
                                sb.append(hex.toIntOrNull(16)?.toChar() ?: throw JsonException("Escape inválido"))
                                i += 4
                            }
                            else -> throw JsonException("Escape inválido")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "-+.eE")) i++
            val t = s.substring(start, i)
            return if (t.any { it == '.' || it == 'e' || it == 'E' }) {
                t.toDoubleOrNull() ?: throw JsonException("Número inválido")
            } else {
                t.toLongOrNull() ?: throw JsonException("Número inválido")
            }
        }
    }
}

internal fun Any?.asMap(): Map<*, *> = this as? Map<*, *> ?: throw JsonException("Objeto esperado")
internal fun Map<*, *>.req(k: String): Any = this[k] ?: throw JsonException("Campo ausente: $k")
internal fun Map<*, *>.str(k: String): String = req(k) as? String ?: throw JsonException("Campo inválido: $k")
internal fun Map<*, *>.strOrNull(k: String): String? = this[k] as? String
internal fun Map<*, *>.long(k: String): Long = (req(k) as? Number)?.toLong() ?: throw JsonException("Campo inválido: $k")
internal fun Map<*, *>.longOrNull(k: String): Long? = (this[k] as? Number)?.toLong()
internal fun Map<*, *>.int(k: String): Int = long(k).toInt()
internal fun Map<*, *>.intOrNull(k: String): Int? = longOrNull(k)?.toInt()
internal fun Map<*, *>.bool(k: String, default: Boolean): Boolean = this[k] as? Boolean ?: default
internal fun Map<*, *>.list(k: String): List<*> = req(k) as? List<*> ?: throw JsonException("Lista esperada: $k")

internal inline fun <reified T : Enum<T>> enumOf(name: String): T =
    enumValues<T>().firstOrNull { it.name == name } ?: throw JsonException("Valor desconhecido: $name")
