package org.sathe.json

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFails
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
    fun canBuildAJsonObject() {
        val obj = obj()
                .add("anInt", 123)
                .add("aString", "aValue")
                .add("aDecimal", BigDecimal("12.34"))
                .add("aBigInt", BigInteger("12345"))
                .add("aBool", true)
                .add("aList", array(value("123")))

        assertThat(obj.integer("anInt"), equalTo(123))
        assertThat(obj.string("aString"), equalTo("aValue"))
        assertThat(obj.decimal("aDecimal"), equalTo(BigDecimal("12.34")))
        assertThat(obj.integer("aBigInt"), equalTo(12345))
        assertThat(obj.boolean("aBool"), equalTo(true))
        assertThat(obj.list("aList")[0], equalTo(value("123") as JsonType))
    }

    @Test
    fun canExtractSimpleValuesFromAnObject() {
        val obj = obj(
                "aList" to array("item1", "item2"),
                "aString" to "aValue",
                "aDecimal" to BigDecimal("12.34"),
                "aNull" to null,
                "anInt" to 123,
                "aBool" to true)

        assertThat(obj.string("aString"), equalTo("aValue"))
        assertThat(obj.decimal("aDecimal"), equalTo("12.34".toBigDecimal()))
        assertThat(obj.integer("anInt"), equalTo(123))
        assertThat(obj.boolean("aBool"), equalTo(true))

        assertEquals("12.34", obj.string("aDecimal"))
        assertEquals("123", obj.string("anInt"))
        assertEquals("true", obj.string("aBool"))
        assertEquals(JsonNull(), obj.child("aNull"))

        assertEquals(BigDecimal("123"), obj.decimal("anInt"))

        assertThat(assertFailsWith<JsonException> { obj.string("missing") }.message,
                equalTo("No entry for 'missing'"))

        assertThat(assertFailsWith<JsonException> { obj.integer("aDecimal") }.message,
                equalTo("Expecting an integer but got 12.34 (BigDecimal)"))

        assertThat(assertFailsWith<JsonException> { obj.boolean("aDecimal") }.message,
                equalTo("Expecting a boolean but got 12.34 (BigDecimal)"))

        assertThat(assertFailsWith<ClassCastException> { obj.boolean("aList") }.message,
                containsString("class org.sathe.json.JsonArray cannot be cast to class org.sathe.json.JsonValue"))
    }

    @Test
    fun reportsIncorrectTypes() {
        val badType = JsonValue(LocalDate.now())

        assertThat(assertFails { badType.decimal() }.message,
                containsString("Expecting a decimal but got 2020-08-24 (LocalDate)"))
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

        assertThat(assertFailsWith<JsonException> { json.find("moo.cow[1]") }.message, containsString("Unable to find moo.cow[1]"))
        assertThat(assertFailsWith<JsonException> { json.find("moo") }.message, containsString("Unable to find moo"))
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

        assertThat(assertFailsWith<JsonException> { json.find("moo[1].cow3[1]") }.message, containsString("Unable to find cow3"))
    }

    @Test
    fun blowsUpIfNotFoundOnAList() {
        val json = obj("moo" to array(obj("cow1" to array(1, 2)), obj("cow2" to array(3, 4))))

        assertThat(assertFailsWith<JsonException> { json.find("moo[1].cow2[3]") }.message, containsString("Index 3 not found"))
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
        val stream = JsonParser("[{\"moo\": \"cow\"}, {\"bah\": \"sheep\"}]").parseAsSequence()

        assertEquals(value("cow"), stream.find("[0].moo"))
        assertEquals(value("sheep"), stream.find("[0].bah"))
    }

    @Test
    fun blowsUpIfWeHaveAPathOnALeaf() {
        val json = value("moo")

        assertEquals("Path \"cow\" goes beyond a leaf - found \"moo\"",
                assertFailsWith<JsonException> { json.find("cow") }.message)
    }

}