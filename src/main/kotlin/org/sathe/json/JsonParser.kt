package org.sathe.json

class JsonParser(val lexer: JsonLexer) {

    constructor(json: String) : this(JsonLexer(json.byteInputStream()))

    fun parse() = parse(lexer.next())

    fun parseListAsStream(): JsonStream {
        val firstToken = lexer.next()
        assertThat(firstToken == "[") { "expecting start of array but got $firstToken" }

        return JsonStream(object : Iterator<JsonType> {
            private var size = 0
            private var currentToken = lexer.next()

            override fun next(): JsonType {
                if (++size > 1) {
                    assertThat(currentToken == ",") { "expecting list separator but got $currentToken"}
                    currentToken = lexer.next()
                }

                val type = parse(currentToken)
                currentToken = lexer.next()
                return type
            }

            override fun hasNext(): Boolean = currentToken != "]"
        })
    }

    private fun parse(token: Any?): JsonType {
        return when (token) {
            "{" -> toJsonObject()
            "[" -> toJsonArray()
            null -> JsonNull()
            else -> JsonValue(token)
        }
    }

    private fun toJsonArray(): JsonArray {
        val array = JsonArray()
        var entry = lexer.next()
        while (entry != "]") {
            array.add(parse(entry))
            entry = lexer.next()
            if (entry == ",") {
                entry = lexer.next()
                assertThat(entry != "]") { "expecting another item but got ']'" }
            }
        }
        return array
    }

    private fun toJsonObject(): JsonObject {
        val obj = JsonObject()
        var key = lexer.next()
        while (key != "}") {
            assertThat(key is String) { "expected a string but got '$key'" }
            val separator = lexer.next()
            assertThat(separator == ":") { "expected ':' but got '$separator'" }
            obj.add(key.toString(), parse())
            key = lexer.next()
            if (key == ",") {
                key = lexer.next()
                assertThat(key != "}") { "expecting another key but got '}'" }
            }
        }
        return obj
    }

    private fun assertThat(expression: Boolean, message: () -> String) {
        if (!expression) {
            throw JsonException(message())
        }
    }
}

