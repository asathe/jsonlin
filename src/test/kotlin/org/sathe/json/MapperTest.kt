package org.sathe.json

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

enum class AnEnum {
    Entry1, Entry2
}

class ExampleBean() : Serializable {
    var field1: String? = null
    var field2: String? = null
    var date1: LocalDate? = null
    var int1: Int? = null
    var bool1: Boolean? = null
    var dec1: BigDecimal? = null
    var list1: List<String>? = null
    var listOfLists: List<List<String>>? = null
    var enum1: AnEnum? = null

    override fun toString(): String = "ExampleBean"
}

private val mapper = Json()

class MapperTest {

    @Test
    fun canUseTheToStringMapper() {
        val bean = ExampleBean()
        val mapper = Json(instanceOf(ExampleBean::class) to ToStringMapper())

        assertEquals("\"ExampleBean\"", mapper.toJson(bean))
    }

    @Test
    fun canSerialiseAndDeserialiseASerialisableType() {
        val obj = obj(
                "type" to "org.sathe.json.ExampleBean",
                "field1" to "value1",
                "field2" to "value2",
                "date1" to "2016-11-19",
                "int1" to 123,
                "bool1" to true,
                "dec1" to BigDecimal("123.56"),
                "enum1" to AnEnum.Entry1,
                "list1" to array("item1", "item2")
        )

        var parsed = mapper.fromJson(obj, ExampleBean::class)!!

        assertEquals("value1", parsed.field1)
        assertEquals("value2", parsed.field2)
        assertEquals(123, parsed.int1)
        assertEquals(BigDecimal("123.56"), parsed.dec1)
        assertEquals(LocalDate.parse("2016-11-19"), parsed.date1)
        assertTrue(parsed.bool1!!)
        assertEquals(AnEnum.Entry1, parsed.enum1)
        assertEquals(listOf("item1", "item2"), parsed.list1)

        parsed = mapper.fromJson(obj, ExampleBean::class)!!
        val reconverted = mapper.toJsonType(parsed)

        assertEquals(obj, reconverted)
    }

    @Test
    fun canDealWithStreams() {
        val obj = obj(
                "type" to "org.sathe.json.ExampleBean",
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
    fun canDealWithListsWithKnownNestedType() {
        val json = array("item1", "item2")
        val list = mapper.fromJson(json, String::class)

        assertEquals(listOf("item1", "item2"), list)
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
        val obj = obj("type" to "org.sathe.json.ExampleBean")

        val parsed = mapper.fromJson(obj, ExampleBean::class)!!

        assertNull(parsed.field1)
    }

    @Test
    fun canParseEmptyBeans() {
        val ticket = ExampleBean()

        val json = mapper.toJson(ticket)

        assertEquals("""{
  "type" : "org.sathe.json.ExampleBean"
}""", json)
    }
}