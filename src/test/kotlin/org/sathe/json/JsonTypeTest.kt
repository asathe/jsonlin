package org.sathe.json

import org.hamcrest.CoreMatchers.containsString
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

        assertFailsWith<JsonException> {
            obj.string("missing")
        }.let {
            assertEquals("No entry for 'missing'", it.message)
        }

        assertFailsWith<JsonException> {
            obj.decimal("anInt")
        }.let {
            assertEquals("Expecting a decimal but got 123", it.message)
        }

        assertFailsWith<JsonException> {
            obj.integer("aDecimal")
        }.let {
            assertEquals("Expecting an integer but got 12.34", it.message)
        }

        assertFailsWith<JsonException> {
            obj.boolean("aDecimal")
        }.let {
            assertEquals("Expecting a boolean but got 12.34", it.message)
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

    @Test
    fun canFindAnElementInATree() {
        val json = obj("moo" to obj("cow" to value("woo!")))
        assertEquals(json.find("moo.cow"), value("woo!"))
    }

    @Test
    fun canFindAnElementInATreeInAList() {
        val json = obj("moo" to obj("cow" to array(value("woo!"))))
        assertEquals(json.find("moo.cow[0]"), value("woo!"))
    }

    @Test
    fun canFindAnElementWithPathLikeKey() {
        val json = obj("moo.cow[1]/bah" to "sheep")
        assertEquals(value("sheep"), json.find("moo.cow[1]/bah"))

        assertFailsWith<JsonException> { json.find("moo.cow[1]") }.let {
            assertThat(it.message, containsString("Unable to find moo.cow[1]"))
        }
        assertFailsWith<JsonException> { json.find("moo") }.let {
            assertThat(it.message, containsString("Unable to find moo"))
        }
    }

    @Test
    fun canFindDeeplyNestedItems() {
        val json = obj("moo" to array(obj("cow1" to array(1, 2)), obj("cow2" to array(3, 4))))
        assertEquals(obj("cow1" to array(1, 2)), json.find("moo[0]"))
        assertEquals(array(1, 2), json.find("moo[0].cow1"))
        assertEquals(value(4), json.find("moo[1].cow2[1]"))
    }

    @Test
    fun blowsUpIfNotFoundOnAnObject() {
        val json = obj("moo" to array(obj("cow1" to array(1, 2)), obj("cow2" to array(3, 4))))

        assertFailsWith<JsonException> { json.find("moo[1].cow3[1]") }.let {
            assertThat(it.message, containsString("Unable to find cow3"))
        }
    }

    @Test
    fun blowsUpIfNotFoundOnAList() {
        val json = obj("moo" to array(obj("cow1" to array(1, 2)), obj("cow2" to array(3, 4))))

        assertFailsWith<JsonException> { json.find("moo[1].cow2[3]") }.let {
            assertThat(it.message, containsString("Index 3 not found"))
        }
    }

    @Test
    fun returnsTheValueOnAnEmptyPath() {
        val json = value("moo")

        assertEquals(value("moo"), json.find(""))
    }

    @Test
    fun returnsNulls() {
        val json = obj("moo" to null)

        assertEquals(JsonNull(), json.find("moo"))
    }

    @Test
    fun canFindPathsInStreamsButConsumesEntriesUpToRequestedIndex() {
        val stream = JsonParser("[{\"moo\": \"cow\"}, {\"bah\": \"sheep\"}]").parseListAsStream()

        assertEquals(value("cow"), stream.find("[0].moo"))
        assertEquals(value("sheep"), stream.find("[0].bah"))
    }

    @Test
    fun blowsUpIfWeHaveAPathOnALeaf() {
        val json = value("moo")

        assertFailsWith<JsonException> { json.find("cow") }.let {
            assertEquals("Path \"cow\" goes beyond a leaf - found \"moo\"", it.message)
        }
    }

}