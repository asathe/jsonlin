package org.sathe.json

import java.io.InputStream
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
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
    override fun fromJson(json: JsonType, type: KClass<T>, context: Context): T = throw JsonException("Deserialization not supported for ${this::class.simpleName}")
    override fun toJson(value: T, context: Context): JsonType = throw JsonException("Serialization not supported for ${this::class.simpleName}")
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
        json as JsonObject
        return type.constructors.first { it.parameters.isEmpty() }.call()
                .apply { setProperty(this, type, json, context) }
    }

    private fun setProperty(target: Any, type: KClass<Any>, json: JsonObject, context: Context) {
        type.members
                .filterIsInstance<KMutableProperty1<*, *>>()
                .forEach { setValue(it, json, target, context) }
    }

    private fun setValue(property: KMutableProperty1<*, *>, json: JsonObject, target: Any, context: Context) {
        val javaField = property.javaField!!
        javaField.isAccessible = true
        if (json.hasChild(property.name)) {
            javaField.set(target, convert(json.child(property.name), context, javaField.genericType))
        }
    }

    private fun convert(json: JsonType, context: Context, type: ParameterizedType): Any? {
        return when {
            isList(type) && json is JsonArray -> json.map { convert(it, context, type.actualTypeArguments[0]) }
            isMap(type) && json is JsonObject -> json.entries().map { it.key to convert(it.value, context, type.actualTypeArguments[1]) }.toMap()
            else -> throw JsonException("Unable to deserialize ${json.toJson()} to ${type.rawType} and type parameters ${type.actualTypeArguments.joinToString()}")
        }
    }

    private fun isMap(type: ParameterizedType) = Map::class.java.isAssignableFrom(type.rawType as Class<*>)

    private fun isList(type: ParameterizedType) = List::class.java.isAssignableFrom(type.rawType as Class<*>)

    private fun convert(json: JsonType, context: Context, type: Class<*>) = context.fromJson(json, type.kotlin)

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
                .filterIsInstance<KProperty<*>>()
                .forEach { it.call(value)?.let { attr -> obj.add(it.name, context.toJsonType(attr)) } }
        return obj
    }
}

interface MapperScope {
    fun isSatisfiedBy(actual: KClass<out Any>): Boolean
}

fun instanceOf(scope: KClass<out Any>) = object : MapperScope {
    override fun isSatisfiedBy(actual: KClass<out Any>): Boolean = scope == actual
}

fun subTypeOf(superclass: KClass<out Any>) = object : MapperScope {
    override fun isSatisfiedBy(actual: KClass<out Any>): Boolean = superclass.java.isAssignableFrom(actual.java)
}
