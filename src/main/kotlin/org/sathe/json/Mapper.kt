package org.sathe.json

import java.io.InputStream
import java.io.Serializable
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField

interface Context {
    fun <T : Any> fromJsonAsStream(json: InputStream, nestedType: KClass<T>): Iterable<T?>
    fun <T : Any> fromJson(json: String, type: KClass<T>): T?
    fun <T : Any> fromJson(json: InputStream, type: KClass<T>): T?
    fun <T : Any> fromJson(json: JsonType, type: KClass<T>): T?
    fun <T : Any> fromJson(json: JsonArray, nestedType: KClass<T>): List<T?>
    fun <T : Any> toJsonType(value: T?): JsonType
    fun fromJson(json: InputStream): JsonType
    fun toJson(value: Any): String = toJsonType(value).toJson()
}

interface Mapper<T : Any> {
    fun fromJson(json: JsonType, type: KClass<T>, context: Context): T
    fun toJson(value: T, context: Context): JsonType
}

abstract class MapperAdapter<T : Any> : Mapper<T> {
    override fun fromJson(json: JsonType, type: KClass<T>, context: Context): T = throw JsonException("Deserialization not supported for ${this.javaClass.simpleName}")
    override fun toJson(value: T, context: Context): JsonType = throw JsonException("Deserialization not supported for ${this.javaClass.simpleName}")
}

class ToStringMapper : Mapper<Any> {
    override fun fromJson(json: JsonType, type: KClass<Any>, context: Context): Any = throw JsonException("Only serialising supported with the ToStringMapper")
    override fun toJson(value: Any, context: Context): JsonType = value(value.toString())
}

class EnumMapper<T : Enum<T>> : Mapper<T> {
    override fun fromJson(json: JsonType, type: KClass<T>, context: Context): T = java.lang.Enum.valueOf(type.java, (json as JsonValue).string())
    override fun toJson(value: T, context: Context): JsonType = value(value.name)
}

class ReflectionMapper : Mapper<Any> {
    override fun fromJson(json: JsonType, type: KClass<Any>, context: Context): Any {
        val newInstance = type.constructors.first { it.parameters.isEmpty() }.call()
        json as JsonObject
        type.members.forEach {
            if (it is KMutableProperty1<*, *>) {
                val javaField = it.javaField!!
                javaField.isAccessible = true
                if (json.hasChild(it.name)) {
                    javaField.set(newInstance, convert(json.child(it.name), context, javaField.genericType))
                }
            }
        }
        return newInstance
    }

    private fun convert(json: JsonType, context: Context, type: ParameterizedType): Any? {
        if (List::class.java.isAssignableFrom(type.rawType as Class<*>)) {
            json as JsonArray
            return json.map {
                convert(it, context, type.actualTypeArguments[0])
            }
        }
        if (Map::class.java.isAssignableFrom(type.rawType as Class<*>)) {
            json as JsonObject
            return json.entries().map {
                (key, value) -> key to convert(value, context, type.actualTypeArguments[1])
            }.toMap()
        }
        throw JsonException("Unable to deserialize ${json.toJson()} to ${type.rawType} and type parameters ${type.actualTypeArguments.joinToString()}")
    }

    private fun convert(json: JsonType, context: Context, type: Class<*>): Any? {
        return context.fromJson(json, type.kotlin)
    }

    private fun convert(json: JsonType, context: Context, type: Type): Any? {
        return when (type) {
            is WildcardType -> convert(json, context, type.upperBounds[0])
            is ParameterizedType -> convert(json, context, type)
            else -> convert(json, context, type as Class<*>)
        }
    }

    override fun toJson(value: Any, context: Context): JsonType {
        val obj = JsonObject()
        value.javaClass.kotlin.members
                .filter { it is KProperty<*> }
                .forEach {
                    val attributeValue = it.call(value)
                    if (attributeValue != null) {
                        obj.add(it.name, context.toJsonType(attributeValue))
                    }
                }
        return obj
    }
}

private enum class AnEnumSoWeCanRegisterWithTheMapper

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
            subTypeOf(Set::class) to object : MapperAdapter<Set<Any>>() {
                override fun toJson(value: Set<Any>, context: Context): JsonType = array(value.map { context.toJsonType(it) })
                override fun fromJson(json: JsonType, type: KClass<Set<Any>>, context: Context) = (fromJson(json, context) as List<Any>).toSet()
            },
            subTypeOf(Map::class) to object : MapperAdapter<Map<Any, Any?>>() {
                override fun toJson(value: Map<Any, Any?>, context: Context): JsonType = obj(value
                        .mapKeys { it.key.toString() }
                        .mapValues { context.toJsonType(it.value) })
                override fun fromJson(json: JsonType, type: KClass<Map<Any, Any?>>, context: Context): Map<Any, Any> = fromJson(json, context) as Map<Any, Any>
            },
            subTypeOf(Serializable::class) to ReflectionMapper()
    )

    private val mappers: Map<MapperScope, Mapper<*>> = mapOf(*customMappers) + defaultMappers

    private fun fromJson(json: JsonType, context: Context): Any? = when (json) {
        is JsonObject -> json.entries().map { it.key to fromJson(it.value, context) }.toMap()
        is JsonArray -> json.map { fromJson(it, context) }
        is JsonNull -> null
        is JsonValue -> json.value()
        is JsonStream -> json.map { fromJson(it, context) }
        else -> null
    }

    override fun <T : Any> fromJson(json: InputStream, type: KClass<T>): T? {
        return fromJson(JsonParser(JsonLexer(json)).parse(), type)
    }

    override fun fromJson(json: InputStream): JsonType {
        return JsonParser(JsonLexer(json)).parse()
    }

    override fun <T : Any> fromJsonAsStream(json: InputStream, nestedType: KClass<T>): Iterable<T?> {
        val list = JsonParser(JsonLexer(json)).parseListAsStream()
        return object : Iterable<T?> {
            override fun iterator(): Iterator<T?> {
                return object : Iterator<T?> {
                    private val iterator = list.iterator()
                    override fun hasNext(): Boolean = iterator.hasNext()
                    override fun next(): T? = fromJson(iterator.next(), nestedType)
                }
            }
        }
    }

    override fun <T : Any> fromJson(json: String, type: KClass<T>): T? = fromJson(json.byteInputStream(), type)

    override fun <T : Any> fromJson(json: JsonArray, nestedType: KClass<T>): List<T?> {
        return json.map { fromJson(it, nestedType) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> fromJson(json: JsonType, type: KClass<T>): T? {
        if (json is JsonNull) {
            return null
        }
        val mapper = mappers
                .filterKeys { it(type) }.values
                .elementAtOrElse(0) { throw IllegalArgumentException("Unable to find a JSON mapper for type $type") } as Mapper<T>
        return mapper
                .fromJson(json, type, this)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> toJsonType(value: T?): JsonType {
        if (value == null) {
            return JsonNull()
        }
        val mapper = mappers
                .filterKeys { it(value.javaClass.kotlin) }.values
                .elementAtOrElse(0) { throw IllegalArgumentException("Unable to find a JSON mapper for $value") } as Mapper<T>
        return mapper.toJson(value, this)
    }
}

interface MapperScope {
    operator fun invoke(actual: KClass<out Any>): Boolean
}

fun instanceOf(scope: KClass<out Any>) = object : MapperScope {
    override fun invoke(actual: KClass<out Any>): Boolean = scope == actual
}

fun subTypeOf(superclass: KClass<out Any>) = object : MapperScope {
    override fun invoke(actual: KClass<out Any>): Boolean = superclass.java.isAssignableFrom(actual.java)
}
