package org.sathe.json

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonTypeTest {
    @Test
    fun canAddEntriesToAJsonObject() {
        val obj = obj()

        obj
                .add("field1", value("value1"))
                .add("field2", value("value2"))

        assertEquals("""{ "field1" : "value1", "field2" : "value2" }""",
                obj.toJson(OneLiner()))
    }

    @Test
    fun canAddEntriesToAJsonArray() {
        val arr = array()

        arr
                .add(value("value1"))
                .add(value("value2"))

        assertEquals("""[ "value1", "value2" ]""",
                arr.toJson(OneLiner()))
    }

    @Test
    fun canExtractSimpleValuesFromAnObject() {
        val obj = obj(
                "aString" to "aValue",
                "aDecimal" to BigDecimal("12.34"),
                "aNull" to null,
                "anInt" to 123,
                "aBool" to true)

        assertThat("aValue", equalTo(obj.string("aString")))
        assertThat(BigDecimal("12.34"), equalTo(obj.decimal("aDecimal")))
        assertThat(123, equalTo(obj.integer("anInt")))
        assertThat(obj.boolean("aBool"), equalTo(true))

        assertEquals("12.34", obj.string("aDecimal"))
        assertEquals("123", obj.string("anInt"))
        assertEquals("true", obj.string("aBool"))
        assertEquals(JsonNull(), obj.child("aNull"))

        assertFailsWith<JsonException>("No entry for 'missing'") {
            obj.string("missing")
        }

        assertFailsWith<JsonException>("Expecting a decimal but got 123") {
            obj.decimal("anInt")
        }

        assertFailsWith<JsonException>("Expecting an integer but got 12.34") {
            obj.integer("aDecimal")
        }

        assertFailsWith<JsonException>("Expecting an boolean but got 12.34") {
            obj.boolean("aDecimal")
        }
    }

    @Test
    fun wrapsSimpleTypesAsJsonValues() {
        val obj = obj()

        assertEquals(obj, obj.add("keyString", "value"))
        assertEquals(obj, obj.add("keyInt", 123))
        assertEquals(obj, obj.add("keyDecimal", BigDecimal("12.34")))
        assertEquals(obj, obj.add("keyBigInt", BigInteger("1234")))
        assertEquals(obj, obj.add("keyBool", true))

        obj.add("keyInt", 124)
        assertEquals(value(124), obj.child("keyInt"))
    }

    @Test
    fun canGetListsFromObjects() {
        val obj = obj()
        obj.add("keyInt", array("moo", "cow"))
        assertEquals(array("moo", "cow"), obj.list("keyInt"))
    }

}