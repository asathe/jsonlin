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

    fun visit(json: JsonSequence)

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
        if (token == ".") token = tokens.nextToken()

        json.child(generateKey(token, json)).accept(this)
    }

    private tailrec fun generateKey(token: String, json: JsonObject): String {
        if (json.hasChild(token)) {
            return token
        }
        if (!tokens.hasMoreTokens()) {
            throw JsonException("Unable to find $token in $json")
        }
        return generateKey(token + tokens.nextToken(), json)
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

    override fun visit(json: JsonSequence) {
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
    private val entries = LinkedHashMap<String, JsonType>()

    constructor(data: Map<*, *>) : this() {
        data.forEach { entries[it.key.toString()] = convert(it.value) }
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
        entries[key] = item
        return this
    }

    fun add(key: String, item: String): JsonObject {
        entries[key] = value(item)
        return this
    }

    fun add(key: String, item: BigDecimal): JsonObject {
        entries[key] = value(item)
        return this
    }

    fun add(key: String, item: Int): JsonObject {
        entries[key] = value(item)
        return this
    }

    fun add(key: String, item: BigInteger): JsonObject {
        entries[key] = value(item)
        return this
    }

    fun add(key: String, item: Boolean): JsonObject {
        entries[key] = value(item)
        return this
    }

    fun child(key: String) = entries[key] ?: throw JsonException("No entry for '$key'")

    fun hasChild(key: String) = entries.containsKey(key)

    fun string(key: String) = asValue(key).string()

    fun decimal(key: String) = asValue(key).decimal()

    fun integer(key: String) = asValue(key).integer()

    fun boolean(key: String) = asValue(key).boolean()

    fun list(key: String): JsonArray {
        val value = entries[key] ?: throw JsonException("No entry for '$key'")
        return value as? JsonArray
                ?: throw JsonException("Expecting a list for '$key' but got '$value'")
    }

    fun entries() = entries.toMap().entries

    override fun toString(): String = toJson(Minimal())

    override fun equals(other: Any?): Boolean = if (other is JsonObject) entries == other.entries else false

    override fun hashCode(): Int = entries.hashCode()

    fun copy(): JsonObject {
        val copy: HashMap<String, JsonType> = HashMap()
        copy.putAll(entries)
        return JsonObject(copy)
    }

    private fun asValue(key: String): JsonValue {
        val value = entries[key] ?: throw JsonException("No entry for '$key'")
        return value as JsonValue
    }
}

class JsonSequence(private val types: Iterator<JsonType>) : JsonType, Sequence<JsonType> {
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

private val encodings = Array(128) {
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
}

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

class JsonValue(private val value: Any) : JsonType {
    override fun toJson(writer: Writer, format: JsonFormat) {
        when (value) {
            is String -> encoded(writer, value)
            else -> writer.write("$value")
        }
    }

    override fun accept(visitor: JsonVisitor) {
        visitor.visit(this)
    }

    fun value(): Any = value

    fun string(): String = value.toString()

    fun decimal(): BigDecimal = when (value) {
        is BigDecimal -> value
        is BigInteger -> BigDecimal(value)
        is String -> BigDecimal(value)
        is Int -> BigDecimal(value)
        else -> throw incorrectType("a decimal")
    }

    fun integer(): Int = when (value) {
        is BigInteger -> value.intValueExact()
        is Int -> value
        is String -> value.toInt()
        else -> throw incorrectType("an integer")
    }

    fun boolean(): Boolean = when (value) {
        is Boolean -> value
        is String -> value.toBoolean()
        else -> throw incorrectType("a boolean")
    }

    private fun incorrectType(type: String): JsonException =
            JsonException("Expecting $type but got $value (${value.javaClass.simpleName})")

    override fun toString(): String = toJson(Minimal())

    override fun equals(other: Any?): Boolean = if (other is JsonValue) value == other.value else false

    override fun hashCode(): Int = value.hashCode()
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
        is String -> JsonValue(value)
        is Number -> JsonValue(value)
        is Boolean -> JsonValue(value)
        is Enum<*> -> JsonValue(value.name)
        is Map<*, *> -> JsonObject(value)
        is List<*> -> JsonArray(value)
        else -> throw JsonException("Unable to convert $value")
    }
}

fun obj(vararg entries: Pair<String, *>): JsonObject {
    val map = LinkedHashMap<String, Any?>()
    map.putAll(entries)
    return JsonObject(map)
}
fun obj(entries: Map<String, JsonType>): JsonObject = JsonObject(entries)
fun array(vararg entries: Any?): JsonArray = JsonArray(listOf(*entries))
fun array(entries: List<Any>): JsonArray = JsonArray(entries)
fun value(value: String): JsonValue = JsonValue(value)
fun value(value: Number): JsonValue = JsonValue(value)
fun value(value: Boolean): JsonValue = JsonValue(value)

