package org.sathe.json

import java.io.InputStream
import java.io.Serializable
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

class Json(vararg customMappers: Pair<MapperScope, Mapper<*>>) : Context {
    private val defaultMappers = mapOf(
            instanceOf(String::class) to object : Mapper<String> {
                override fun fromJson(json: JsonType, type: KClass<String>, context: Context): String = (json as JsonValue).string()
                override fun toJson(value: String, context: Context): JsonType = value(value)
            },
            instanceOf(Int::class) to object : Mapper<Int> {
                override fun fromJson(json: JsonType, type: KClass<Int>, context: Context): Int = (json as JsonValue).integer()
                override fun toJson(value: Int, context: Context): JsonType = value(value)
            },
            instanceOf(Boolean::class) to object : Mapper<Boolean> {
                override fun fromJson(json: JsonType, type: KClass<Boolean>, context: Context): Boolean = (json as JsonValue).boolean()
                override fun toJson(value: Boolean, context: Context): JsonType = value(value)
            },
            instanceOf(Double::class) to object : Mapper<Double> {
                override fun fromJson(json: JsonType, type: KClass<Double>, context: Context): Double = (json as JsonValue).decimal().toDouble()
                override fun toJson(value: Double, context: Context): JsonType = value(BigDecimal(value))
            },
            instanceOf(BigDecimal::class) to object : Mapper<BigDecimal> {
                override fun fromJson(json: JsonType, type: KClass<BigDecimal>, context: Context): BigDecimal = (json as JsonValue).decimal()
                override fun toJson(value: BigDecimal, context: Context): JsonType = value(value)
            },
            instanceOf(BigInteger::class) to object : Mapper<BigInteger> {
                override fun fromJson(json: JsonType, type: KClass<BigInteger>, context: Context): BigInteger = BigInteger((json as JsonValue).string())
                override fun toJson(value: BigInteger, context: Context): JsonType = value(value)
            },
            instanceOf(LocalDate::class) to object : Mapper<LocalDate> {
                override fun fromJson(json: JsonType, type: KClass<LocalDate>, context: Context): LocalDate = LocalDate.parse((json as JsonValue).string())
                override fun toJson(value: LocalDate, context: Context): JsonType = value(value.toString())
            },
            instanceOf(LocalDateTime::class) to object : Mapper<LocalDateTime> {
                override fun fromJson(json: JsonType, type: KClass<LocalDateTime>, context: Context): LocalDateTime = LocalDateTime.parse((json as JsonValue).string())
                override fun toJson(value: LocalDateTime, context: Context): JsonType = value(value.toString())
            },
            subTypeOf(Enum::class) to EnumMapper<AnEnumSoWeCanRegisterWithTheMapper>(),
            subTypeOf(List::class) to object : MapperAdapter<List<Any>>() {
                override fun toJson(value: List<Any>, context: Context): JsonType = array(value.map { context.toJsonType(it) })
            },
            subTypeOf(Set::class) to object : MapperAdapter<Set<*>>() {
                override fun toJson(value: Set<*>, context: Context): JsonType = array(value.map { context.toJsonType(it) })
                override fun fromJson(json: JsonType, type: KClass<Set<*>>, context: Context) = (fromJson(json, context) as List<*>).toSet()
            },
            subTypeOf(Map::class) to object : MapperAdapter<Map<*, *>>() {
                override fun toJson(value: Map<*, *>, context: Context): JsonType = obj(value
                        .mapKeys { it.key.toString() }
                        .mapValues { context.toJsonType(it.value) })

                override fun fromJson(json: JsonType, type: KClass<Map<*, *>>, context: Context): Map<*, *> = fromJson(json, context) as Map<*, *>
            },
            subTypeOf(Serializable::class) to ReflectionMapper()
    )

    private enum class AnEnumSoWeCanRegisterWithTheMapper

    private val mappers: Map<MapperScope, Mapper<*>> = mapOf(*customMappers) + defaultMappers

    private fun fromJson(json: JsonType, context: Context): Any? = when (json) {
        is JsonObject -> json.entries().map { it.key to fromJson(it.value, context) }.toMap()
        is JsonArray -> json.map { fromJson(it, context) }
        is JsonNull -> null
        is JsonValue -> json.value()
        is JsonSequence -> json.map { fromJson(it, context) }
        else -> null
    }

    override fun <T : Any> fromJson(json: InputStream, type: KClass<T>) =
            fromJson(JsonParser(JsonLexer(json)).parse(), type)

    override fun fromJson(json: InputStream) = JsonParser(JsonLexer(json)).parse()

    override fun <T : Any> fromJsonAsSequence(json: InputStream, nestedType: KClass<T>) =
            JsonParser(JsonLexer(json)).parseAsSequence().map { fromJson(it, nestedType) }

    override fun <T : Any> fromJson(json: String, type: KClass<T>): T? = fromJson(json.byteInputStream(), type)

    override fun <T : Any> fromJson(json: JsonArray, nestedType: KClass<T>) =
            json.map { fromJson(it, nestedType) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> fromJson(json: JsonType, type: KClass<T>): T? {
        return if (json is JsonNull) null
        else mapperFor(type).fromJson(json, type, this)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> toJsonType(value: T?): JsonType {
        return if (value == null) JsonNull()
        else mapperFor(value.javaClass.kotlin).toJson(value, this)
    }

    private fun <T : Any> mapperFor(type: KClass<T>): Mapper<T> {
        return mappers
                .filterKeys { it.isSatisfiedBy(type) }
                .values
                .filterIsInstance<Mapper<T>>()
                .firstOrNull()
                ?: throw IllegalArgumentException("Unable to find a JSON mapper for $type")
    }
}