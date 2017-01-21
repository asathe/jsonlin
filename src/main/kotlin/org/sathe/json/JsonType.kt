package org.sathe.json

import java.io.StringWriter
import java.io.Writer
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

interface JsonType {
    fun toJson(writer: Writer, format: JsonFormat = PrettyPrint())
    fun toJson(format: JsonFormat = PrettyPrint()): String {
        val writer = StringWriter()
        toJson(writer, format)
        return writer.toString()
    }

    fun accept(visitor: JsonVisitor)
    fun find(path: String): JsonType {
        val jsonPath = JsonPath(path)
        accept(jsonPath)
        return jsonPath.result()
    }
}

interface JsonVisitor {

    fun visit(json: JsonObject)

    fun visit(json: JsonArray)

    fun visit(json: JsonValue)

    fun visit(json: JsonNull)

    fun visit(json: JsonStream)

}

class JsonPath(path: String) : JsonVisitor {

    private val tokens = StringTokenizer(path, ".[]", true)
    private var result: JsonType? = null

    override fun visit(json: JsonObject) {
        if (!tokens.hasMoreTokens()) {
            result = json
            return
        }
        var token = tokens.nextToken()
        if (token == ".") {
            token = tokens.nextToken()
        }
        if (json.hasChild(token)) {
            json.child(token).accept(this)
        } else {
            throw JsonException("Unable to find $token in $json")
        }
    }

    override fun visit(json: JsonArray) {
        if (!tokens.hasMoreTokens()) {
            result = json
            return
        }
        assert(tokens.nextToken() == "[")
        val index = Integer.valueOf(tokens.nextToken())
        if (json.size() <= index) {
            throw JsonException("Index $index not found in $json")
        }
        assert(tokens.nextToken() == "]")
        json[index].accept(this)
    }

    override fun visit(json: JsonValue) {
        if (tokens.hasMoreTokens()) {
            throw JsonException("Path \"${tokens.toList().joinToString()}\" goes beyond a leaf - found $json")
        }
        result = json
    }

    override fun visit(json: JsonNull) {
        if (tokens.hasMoreTokens()) {
            throw JsonException("Path \"${tokens.toList().joinToString()}\" goes beyond a leaf - found $json")
        }
        result = json
    }

    override fun visit(json: JsonStream) {
        assert(tokens.nextToken() == "[")
        val index = Integer.valueOf(tokens.nextToken())
        assert(tokens.nextToken() == "]")
        json.elementAt(index).accept(this)
    }

    fun result(): JsonType {
        return result!!
    }
}

class JsonObject() : JsonType {
    private val entries = linkedMapOf<String, JsonType>()

    constructor(data: Map<*, *>) : this() {
        data.forEach { entries.put(it.key.toString(), convert(it.value)) }
    }

    override fun toJson(writer: Writer, format: JsonFormat) {
        val maxIndex = entries.size - 1
        writer.write(format.startObject())
        entries.entries.forEachIndexed { i, entry ->
            writer.write(format.startItem())
            encoded(writer, entry.key)
            writer.write(format.separator())
            entry.value.toJson(writer, format)
            if (i < maxIndex) writer.write(",")
        }
        writer.write(format.endObject())
    }

    override fun accept(visitor: JsonVisitor) {
        visitor.visit(this)
    }

    fun add(key: String, item: JsonType): JsonObject {
        entries.put(key, item)
        return this
    }

    fun add(key: String, item: String): JsonObject {
        entries.put(key, value(item))
        return this
    }

    fun add(key: String, item: BigDecimal): JsonObject {
        entries.put(key, value(item))
        return this
    }

    fun add(key: String, item: Int): JsonObject {
        entries.put(key, value(item))
        return this
    }

    fun add(key: String, item: BigInteger): JsonObject {
        entries.put(key, value(item))
        return this
    }

    fun add(key: String, item: Boolean): JsonObject {
        entries.put(key, value(item))
        return this
    }

    fun child(key: String): JsonType {
        return entries[key] ?: throw JsonException("No entry for '$key'")
    }

    fun hasChild(key: String): Boolean {
        return entries.containsKey(key)
    }

    fun string(key: String): String {
        val value = entries[key] ?: throw JsonException("No entry for '$key'")
        return if (value is JsonValue) value.string()
        else throw JsonException("Expecting a string for '$key' but got '$value'")
    }

    fun decimal(key: String): BigDecimal {
        val value = entries[key] ?: throw JsonException("No entry for '$key'")
        return if (value is JsonValue) value.decimal()
        else throw JsonException("Expecting a decimal for '$key' but got '$value'")
    }

    fun integer(key: String): Int {
        val value = entries[key] ?: throw JsonException("No entry for '$key'")
        return if (value is JsonValue) value.integer()
        else throw JsonException("Expecting an integer for '$key' but got '$value'")
    }

    fun boolean(key: String): Boolean {
        val value = entries[key] ?: throw JsonException("No entry for '$key'")
        return if (value is JsonValue) value.boolean()
        else throw JsonException("Expecting a boolean for '$key' but got '$value'")
    }

    fun list(key: String): JsonArray {
        val value = entries[key] ?: throw JsonException("No entry for '$key'")
        return if (value is JsonArray) value
        else throw JsonException("Expecting a list for '$key' but got '$value'")
    }

    override fun toString(): String = toJson(Minimal())

    override fun equals(other: Any?): Boolean = if (other is JsonObject) entries == other.entries else false

    override fun hashCode(): Int = entries.hashCode()

    fun copy(): JsonObject {
        val copy: HashMap<String, JsonType> = hashMapOf()
        copy.putAll(entries)
        return JsonObject(copy)
    }
}

class JsonStream(val types: Iterator<JsonType>) : JsonType, Iterable<JsonType> {
    override fun toJson(writer: Writer, format: JsonFormat) {
        writer.write(format.startList())
        types.forEach { item ->
            writer.write(format.startItem())
            item.toJson(writer, format)
            if (types.hasNext()) writer.write(",")
        }
        writer.write(format.endList())
    }

    override fun iterator(): Iterator<JsonType> {
        return types
    }

    override fun accept(visitor: JsonVisitor) {
        visitor.visit(this)
    }
}

class JsonArray() : JsonType, Iterable<JsonType> {
    private val items = ArrayList<JsonType>()

    constructor(entries: List<Any?>) : this() {
        entries.forEach { items.add(convert(it)) }
    }

    override fun iterator(): Iterator<JsonType> {
        return items.iterator()
    }

    override fun toJson(writer: Writer, format: JsonFormat) {
        val maxIndex = items.size - 1
        writer.write(format.startList())
        items.forEachIndexed { i, item ->
            writer.write(format.startItem())
            item.toJson(writer, format)
            if (i < maxIndex) writer.write(",")
        }
        writer.write(format.endList())
    }

    override fun accept(visitor: JsonVisitor) {
        visitor.visit(this)
    }

    operator fun get(index: Int): JsonType {
        return items[index]
    }

    fun add(item: JsonType): JsonArray {
        items.add(item)
        return this
    }

    fun size(): Int {
        return items.size
    }

    override fun toString(): String = toJson(Minimal())

    override fun equals(other: Any?): Boolean = if (other is JsonArray) items == other.items else false

    override fun hashCode(): Int = items.hashCode()
}

private val encodings = Array(128, {
    when (it) {
        '\t'.toInt() -> "\\t"
        '\n'.toInt() -> "\\n"
        '\r'.toInt() -> "\\r"
        '\b'.toInt() -> "\\b"
        '\"'.toInt() -> "\\\""
        '\\'.toInt() -> "\\\\"
        '\u000C'.toInt() -> "\\f"
        in 0..0x1f -> "\\u%04x".format(it)
        else -> null
    }
})

private fun encoded(writer: Writer, value: String) {
    writer.write("\"")
    value.forEach { writer.write(encodedCharacterFor(it)) }
    writer.write("\"")
}

private fun encodedCharacterFor(c: Char): String {
    if (c.toInt() >= encodings.size) return c.toString()
    val encoded = encodings[c.toInt()]
    return encoded ?: c.toString()
}

class JsonValue(val value: Any?) : JsonType {
    override fun toJson(writer: Writer, format: JsonFormat) {
        when (value) {
            is String -> encoded(writer, value)
            else -> writer.write("$value")
        }
    }

    override fun accept(visitor: JsonVisitor) {
        visitor.visit(this)
    }

    fun string(): String = value.toString()

    fun decimal(): BigDecimal = when (value) {
        is BigDecimal -> value
        is String -> BigDecimal(value)
        else -> throw JsonException("Expecting a decimal but got $value")
    }

    fun integer(): Int = when (value) {
        is BigInteger -> value.intValueExact()
        is Int -> value
        is String -> value.toInt()
        else -> throw JsonException("Expecting an integer but got $value")
    }

    fun boolean(): Boolean = if (value is Boolean) value else throw JsonException("Expecting a boolean but got $value")

    override fun toString(): String = toJson(Minimal())

    override fun equals(other: Any?): Boolean = if (other is JsonValue) value!! == other.value else false

    override fun hashCode(): Int = value!!.hashCode()
}

class JsonNull : JsonType {
    override fun toJson(writer: Writer, format: JsonFormat) = writer.write("null")

    override fun accept(visitor: JsonVisitor) {
        visitor.visit(this)
    }

    override fun equals(other: Any?): Boolean = other is JsonNull

    override fun hashCode(): Int = 0
}

private fun convert(value: Any?): JsonType {
    return when (value) {
        null -> JsonNull()
        is JsonType -> value
        is Int -> JsonValue(value)
        is String -> JsonValue(value)
        is BigDecimal -> JsonValue(value)
        is Boolean -> JsonValue(value)
        is Enum<*> -> JsonValue(value.name)
        is Map<*, *> -> JsonObject(value)
        is List<*> -> JsonArray(value)
        else -> throw JsonException("Unable to convert " + value)
    }
}

fun obj(vararg entries: Pair<String, *>): JsonObject = JsonObject(sortedMapOf(*entries))
fun obj(entries: Map<String, JsonType>): JsonObject = JsonObject(entries)
fun array(vararg entries: Any?): JsonArray = JsonArray(listOf(*entries))
fun array(entries: List<Any>): JsonArray = JsonArray(entries)
fun value(value: String): JsonValue = JsonValue(value)
fun value(value: Int): JsonValue = JsonValue(value)
fun value(value: BigDecimal): JsonValue = JsonValue(value)
fun value(value: BigInteger): JsonValue = JsonValue(value)
fun value(value: Boolean): JsonValue = JsonValue(value)

