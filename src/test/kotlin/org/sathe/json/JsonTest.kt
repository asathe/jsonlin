package org.sathe.json

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.text.Charsets.UTF_16

class JsonTest {

    @Rule @JvmField val exception: ExpectedException = ExpectedException.none()

    @Test
    fun canProvideFormattingOptions() {

        val obj = obj(
                "field1" to "value1",
                "field2" to "value2",
                "boolField" to true,
                "booField" to false,
                "listField1" to array(
                        obj("subField1" to "subValue1"),
                        null,
                        obj("subField2" to "subValue2")
                ))

        assertEquals("""{
  "booField" : false,
  "boolField" : true,
  "field1" : "value1",
  "field2" : "value2",
  "listField1" : [
    {
      "subField1" : "subValue1"
    },
    null,
    {
      "subField2" : "subValue2"
    }
  ]
}""", obj.toJson())

        assertEquals("""{"booField":false,"boolField":true,"field1":"value1","field2":"value2","listField1":[{"subField1":"subValue1"},null,{"subField2":"subValue2"}]}""",
                obj.toJson(Minimal()))

        assertEquals("""{ "booField" : false, "boolField" : true, "field1" : "value1", "field2" : "value2", "listField1" : [ { "subField1" : "subValue1" }, null, { "subField2" : "subValue2" } ] }""",
                obj.toJson(OneLiner()))
    }

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
                "anInt" to 123,
                "aBool" to true)

        assertThat("aValue", equalTo(obj.string("aString")))
        assertThat(BigDecimal("12.34"), equalTo(obj.decimal("aDecimal")))
        assertThat(123, equalTo(obj.integer("anInt")))
        assertThat(obj.boolean("aBool"), equalTo(true))

        assertEquals("12.34", obj.string("aDecimal"))
        assertEquals("123", obj.string("anInt"))
        assertEquals("true", obj.string("aBool"))

        assertFailsWith<JsonException>("No entry for 'missing'") {
            obj.string("missing")
        }

        assertFailsWith<JsonException>("Expecting a decimal but got 123") {
            obj.decimal("anInt")
        }

        assertFailsWith<JsonException>("Expecting an integer but got 12.34") {
            obj.integer("aDecimal")
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
    fun canSerialiseNulls() {
        val obj = obj("aList" to listOf(null, null), "987" to null)

        assertEquals("""{"987":null,"aList":[null,null]}""", obj.toJson(Minimal()))
    }

    @Test
    fun canSerialiseAStream() {
        val stream = JsonStream(array(value(123), value(456)).iterator())

        assertThat("[123,456]", equalTo(stream.toJson(Minimal())))
    }

    @Test
    fun canSerialiseAnEmptyObject() {
        val obj = obj()

        assertEquals("{}", obj.toJson(Minimal()))
    }

    @Test
    fun canSerialiseSpecialStringCharacters() {
        val value = "1\t\n\r\"\\\b\u0001\u000c"

        assertEquals("\"1\\t\\n\\r\\\"\\\\\\b\\u0001\\f\"", JsonValue(value).toJson())
    }

    @Test
    fun canSerialiseDoubleByteCharacters() {
        val value = "私"
        assertEquals("\"私\"", JsonValue(value).toJson())
    }

    @Test
    fun canSerialiseNestedListsOfNulls() {
        val obj = obj("aNull" to null, "listOfNulls" to array(null, null))

        assertEquals("""{
  "aNull" : null,
  "listOfNulls" : [
    null,
    null
  ]
}""", obj.toJson())
    }

    @Test
    fun canDeserialiseUTF16() {
        val json = """["foo","bar",{"foo":"bar"},1,2,[3,4],null,true,{"nested":{"inner":{"array":[2,3.14,321987432],"jagged array":[43,[43,[123,32,43,54]]]}}},{"blah":"slime"},"string with \"escaped quotes\", a'p'o's't'r'a'p'h'e's, commas, [brackets] and {braces}"]"""
        val types = JsonParser(JsonLexer(json.byteInputStream())).parseListAsStream()
        assertEquals(value("foo"), types.iterator().next())
        assertEquals(value("bar"), types.iterator().next())

        val jsonAsUtf16 = String(json.toByteArray(UTF_16), UTF_16)
        val utf16types = JsonParser(jsonAsUtf16).parseListAsStream()
        assertEquals(value("foo"), utf16types.iterator().next())
        assertEquals(value("bar"), utf16types.iterator().next())
    }

    @Test
    fun canDeserialiseNumericTypes() {
        assertEquals(value(BigDecimal("123.567")), JsonParser("123.567").parse())
        assertEquals(value(BigDecimal("12.3567")), JsonParser("123.567e-1").parse())
        assertEquals(value(BigDecimal("1235.67")), JsonParser("123.567e+1").parse())
        assertEquals(value(BigDecimal("1.23567E+6")), JsonParser("123567e+1").parse())
        assertEquals(value(BigDecimal("0.12")), JsonParser("0.12").parse())
        assertEquals(value(BigDecimal("-0.12")), JsonParser("-0.12").parse())
        assertEquals(value(BigDecimal("0.12")), JsonParser("00.12").parse())

        assertFailsWith<JsonException>("Invalid numeric format. Expecting +, - or digit for exponent") { JsonParser("1e").parse() }
        assertFailsWith<JsonException>("Invalid numeric format. Current token '1e-', was not expecting '￿'") { JsonParser("1e-").parse() }
        assertFailsWith<JsonException>("Invalid numeric format. Current token '1.', was not expecting '.'") { JsonParser("1..3").parse() }
    }

    @Test
    fun canDeserialiseStrings() {
        assertEquals(value("moo"), JsonParser("\"moo\"").parse())
        assertEquals(value("moo\t\n"), JsonParser("\"moo\\t\\n\"").parse())
        assertEquals(value("moo\"cow\""), JsonParser("\"moo\\\"cow\\\"\"").parse())

        assertEquals(value("moo\u002F"), JsonParser("\"moo\\u002F\"").parse())
        assertEquals(value("moo\uFFFF"), JsonParser("\"moo\\uFfFF\"").parse())

        assertEquals(value("私"), JsonParser("\"私\"").parse())

        assertFailsWith<JsonException>("Unterminated string found") { JsonParser("\"moo").parse() }
        assertFailsWith<JsonException>("Non-hexadecimal character 'q' found") { JsonParser("\"\\u12qw\"").parse() }
    }

    @Test
    fun canDeserialiseBooleansAndNulls() {
        assertEquals(value(true), JsonParser("true").parse())
        assertEquals(value(false), JsonParser("false").parse())
        assertEquals(JsonNull(), JsonParser("null").parse())

        assertFailsWith<JsonException>() { JsonParser("tune").parse() }
        assertFailsWith<JsonException>() { JsonParser("farce").parse() }
    }

    @Test
    fun canDeserialiseObjects() {
        assertEquals(obj("moo" to "cow"),
                JsonParser("{\"moo\":\"cow\"}").parse())
        assertEquals(obj("moo" to "cow", "bah" to "sheep"),
                JsonParser("{\"moo\":\"cow\",\"bah\":\"sheep\"}").parse())

        assertEquals(array("moo", "cow"),
                JsonParser("[\"moo\",\"cow\"]").parse())
        assertEquals(array(obj("moo" to "cow"), obj("bah" to "sheep")),
                JsonParser("[{\"moo\":\"cow\"},{\"bah\":\"sheep\"}]").parse())

        assertEquals(array(obj("moo" to "cow"), obj("bah" to "sheep")),
                JsonParser(array(obj("moo" to "cow"), obj("bah" to "sheep")).toJson(PrettyPrint())).parse())
    }

    @Test
    fun canDeserialiseEmptyContainers() {
        assertEquals(array(), JsonParser("[]").parse())
        assertEquals(obj(), JsonParser("{}").parse())
    }

    @Test
    fun canDeserialiseAsAStream() {
        val stream = JsonParser("[\"moo\"]").parseListAsStream()
        assertEquals(value("moo"), stream.first())
    }

    @Test
    fun canDeserialiseAnEmptyStream() {
        val stream = JsonParser("[]").parseListAsStream()
        assertTrue(stream.toList().isEmpty())
    }

    @Test(expected = JsonException::class)
    fun cannotDeserialiseNonArrayTypesAsAStream() {
        JsonParser("{}").parseListAsStream()
    }

    @Test(expected = JsonException::class)
    fun failsIfListIsBadlyFormed() {
        val stream = JsonParser("[\"moo\"\"cow\"]").parseListAsStream()
        assertEquals(value("moo"), stream.first())
        assertEquals(value("cow"), stream.first())
    }

    @Test
    fun canDeserialiseDeeplyNestedStructures() {
        assertEquals(array(array(array(array(array(array(array(array(array(array(array(array(array(false))))))))))))),
                JsonParser("[[[[[[[[[[[[[false]]]]]]]]]]]]]").parse())
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
    fun canFindDeeplyNestedItems() {
        val json = obj("moo" to array(obj("cow1" to array(1, 2)), obj("cow2" to array(3, 4))))
        assertEquals(obj("cow1" to array(1, 2)), json.find("moo[0]"))
        assertEquals(array(1, 2), json.find("moo[0].cow1"))
        assertEquals(value(4), json.find("moo[1].cow2[1]"))
    }

    @Test
    fun blowsUpIfNotFoundOnAnObject() {
        val json = obj("moo" to array(obj("cow1" to array(1, 2)), obj("cow2" to array(3, 4))))

        exception.expect(JsonException::class.java)
        exception.expectMessage(startsWith("Unable to find cow3"))

        json.find("moo[1].cow3[1]")
    }

    @Test
    fun blowsUpIfNotFoundOnAList() {
        val json = obj("moo" to array(obj("cow1" to array(1, 2)), obj("cow2" to array(3, 4))))

        exception.expect(JsonException::class.java)
        exception.expectMessage(startsWith("Index 3 not found"))

        json.find("moo[1].cow2[3]")
    }

    @Test
    fun returnsTheValueOnAnEmptyPath() {
        val json = value("moo")

        assertEquals(value("moo"), json.find(""))
    }

    @Test
    fun blowsUpIfWeHaveAPathOnALeaf() {
        val json = value("moo")

        exception.expect(JsonException::class.java)
        exception.expectMessage(startsWith("Path \"cow\" goes beyond a leaf - found \"moo\""))

        json.find("cow")
    }
}
