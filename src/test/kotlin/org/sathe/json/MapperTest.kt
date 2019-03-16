package org.sathe.json

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.Serializable
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MapperTest {

    private val mapper = Json()

    @Test
    fun canUseTheToStringMapper() {
        val bean = ExampleBean()
        val mapper = Json(instanceOf(ExampleBean::class) to ToStringMapper())

        assertEquals("\"ExampleBean\"", mapper.toJson(bean))
    }

    @Test
    fun cannotDeserialiseWithToString() {
        val bean = ExampleBean()
        val mapper = Json(instanceOf(ExampleBean::class) to ToStringMapper())

        assertFailsWith<JsonException> {
            mapper.fromJson(mapper.toJson(bean), ExampleBean::class)
        }
    }

    @Test
    fun mapperAdapterAlwaysFailsToSerialise() {
        val bean = ExampleBean()
        val mapper = Json(instanceOf(ExampleBean::class) to object : MapperAdapter<Any>() {})

        assertFailsWith<JsonException> {
            mapper.toJson(bean)
        }
    }

    @Test
    fun mapperAdapterAlwaysFailsToDeserialise() {
        val mapper = Json(instanceOf(ExampleBean::class) to object : MapperAdapter<Any>() {})

        assertFailsWith<JsonException> {
            mapper.fromJson("{}", ExampleBean::class)
        }
    }

    @Test
    fun deserialisesSimpleTypes() {
        assertEquals(123, mapper.fromJson("123", Int::class))
        assertEquals(123.0, mapper.fromJson("123.0", Double::class))
        assertEquals(BigDecimal("123"), mapper.fromJson("123", BigDecimal::class))
        assertEquals(BigDecimal("123.45"), mapper.fromJson("123.45", BigDecimal::class))
        assertEquals(true, mapper.fromJson("true", Boolean::class))
    }

    @Test
    fun serialisesSimpleTypes() {
        assertEquals("true", mapper.toJson(true))
        assertEquals("123", mapper.toJson(123))
        assertEquals("123", mapper.toJson(123.0))
        assertEquals("123.45", mapper.toJson(BigDecimal("123.45")))
    }

    @Test
    fun canConvertBooleans() {
        val booleans = obj(
                "textTrue" to "true",
                "textFalse" to "false",
                "booleanTrue" to true,
                "booleanFalse" to false
        )

        assertTrue(mapper.fromJson(booleans.child("textTrue"), Boolean::class)!!)
        assertTrue(mapper.fromJson(booleans.child("booleanTrue"), Boolean::class)!!)
        assertTrue(mapper.fromJson(booleans.child("booleanTrue"), Boolean::class.javaPrimitiveType!!.kotlin)!!)
        assertFalse(mapper.fromJson(booleans.child("textFalse"), Boolean::class)!!)
        assertFalse(mapper.fromJson(booleans.child("booleanFalse"), Boolean::class)!!)
    }

    @Test
    fun knownTypesCanBeSerialisedAndDeserialised() {
        assertToAndFromJsonReturnsSameValue(mapOf("key" to "value", "number" to BigInteger("12"), "decimal" to BigDecimal("23.45")))
        assertToAndFromJsonReturnsSameValue(LocalDateTime.now())
        assertToAndFromJsonReturnsSameValue(setOf("item1", "item2"))
        assertToAndFromJsonReturnsSameValue(setOf(true, false))
        assertToAndFromJsonReturnsSameValue(BigInteger("23"))
    }

    private fun assertToAndFromJsonReturnsSameValue(value: Any) {
        val json = mapper.toJson(value)
        assertEquals(value, mapper.fromJson(json, value::class))
    }

    @Test
    fun canSerialiseAndDeserialiseASerialisableType() {
        val obj = obj(
                "field1" to "value1",
                "field2" to "value2",
                "date1" to "2016-11-19",
                "int1" to 123,
                "bool1" to true,
                "map1" to mapOf("key1" to "value1.1"),
                "dec1" to BigDecimal("123.56"),
                "enum1" to ExampleEnum.Entry1,
                "list1" to array("item1", "item2")
        )

        var parsed = mapper.fromJson(obj, ExampleBean::class)!!

        assertEquals("value1", parsed.field1)
        assertEquals("value2", parsed.field2)
        assertEquals(123, parsed.int1)
        assertEquals(BigDecimal("123.56"), parsed.dec1)
        assertEquals(LocalDate.parse("2016-11-19"), parsed.date1)
        assertTrue(parsed.bool1!!)
        assertEquals(ExampleEnum.Entry1, parsed.enum1)
        assertEquals(listOf("item1", "item2"), parsed.list1)

        parsed = mapper.fromJson(obj, ExampleBean::class)!!
        val reconverted = mapper.toJsonType(parsed)

        assertEquals(obj, reconverted)
    }

    @Test
    fun cannotDeserialiseObjectWithNonString() {
        val obj = obj(
                "badSequence1" to array("value1")
        )

        assertFailsWith<JsonException> { mapper.fromJson(obj, BadExampleBean::class)!! }
    }

    @Test
    fun canDealWithStreams() {
        val obj = obj(
                "field1" to "value1",
                "field2" to "value2",
                "list1" to array("item1", "item2"),
                "listOfLists" to array(array("item1.1", "item1.2"), array("item2.1", "item2.2"))
        )

        val bigList = (0..2000).map { obj.copy().add("identifier", it) }
        val json = array(bigList).toJson()
        val inputStream = json.byteInputStream()
        val buffered = BufferedInputStream(inputStream)
        val stream = JsonParser(JsonLexer(buffered)).parseListAsStream()
        stream.first()
        stream.first()
    }

    @Test
    fun canUseJsonStreamsFromTheMapper() {
        val bigList = (0..2000).map { obj("identifier" to "$it") }
        val json = array(bigList).toJson()

        val maps = mapper.fromJsonAsStream(json.byteInputStream(), Map::class)
        assertEquals("0", maps.first()!!["identifier"])
        assertEquals(2000, maps.count())
    }

    @Test
    fun canDealWithListsWithKnownNestedType() {
        val json = array("item1", "item2")
        val list = mapper.fromJson(json, String::class)

        assertEquals(listOf("item1", "item2"), list)
    }

    @Test
    fun generateJsonArraysFromJson() {
        val json = array("item1", "item2").toJson()
        val rehydrated = mapper.fromJson(json.byteInputStream())

        assertEquals(array("item1", "item2"), rehydrated)
    }

    @Test
    fun generateJsonObjectsFromJson() {
        val json = obj("key" to "value").toJson()
        val rehydrated = mapper.fromJson(json.byteInputStream())

        assertEquals(obj("key" to "value"), rehydrated)
    }

    @Test
    fun canDealWithListsOfLists() {
        val obj = obj(
                "type" to "org.sathe.json.ExampleBean",
                "field1" to "value1",
                "field2" to "value2",
                "list1" to array("item1", "item2"),
                "listOfLists" to array(array("item1.1", "item1.2"), array("item2.1", "item2.2"))
        )

        val parsed = mapper.fromJson(obj, ExampleBean::class)!!

        assertEquals("item2.2", parsed.listOfLists!![1][1])
    }

    @Test
    fun canParseNullProperties() {
        val obj = obj()

        val parsed = mapper.fromJson(obj, ExampleBean::class)!!

        assertNull(parsed.field1)
    }

    @Test
    fun canParseEmptyBeans() {
        val ticket = ExampleBean()

        val json = mapper.toJson(ticket)

        assertEquals("{\n}", json)
    }

    class SomeData : Serializable {
        var value: String? = null
        var date: LocalDate? = null
    }

    @Test
    fun handlesSerialisableTypesThroughReflection() {
        val data = SomeData()
        data.value = "a value"
        data.date = LocalDate.now()

        val json = mapper.toJson(data)

        assertEquals("""{
  "date" : "${LocalDate.now()}",
  "value" : "a value"
}""".trimMargin(), json)

        val rehydrated = mapper.fromJson(json, SomeData::class)!!
        assertEquals("a value", rehydrated.value)
        assertEquals(LocalDate.now(), rehydrated.date)
    }
}