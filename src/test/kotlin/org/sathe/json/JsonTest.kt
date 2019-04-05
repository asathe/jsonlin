package org.sathe.json

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.math.BigDecimal
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
  "field1" : "value1",
  "field2" : "value2",
  "boolField" : true,
  "booField" : false,
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

        assertEquals("""{"field1":"value1","field2":"value2","boolField":true,"booField":false,"listField1":[{"subField1":"subValue1"},null,{"subField2":"subValue2"}]}""",
                obj.toJson(Minimal()))

        assertEquals("""{ "field1" : "value1", "field2" : "value2", "boolField" : true, "booField" : false, "listField1" : [ { "subField1" : "subValue1" }, null, { "subField2" : "subValue2" } ] }""",
                obj.toJson(OneLiner()))
    }

    @Test
    fun canSerialiseNulls() {
        val obj = obj("aList" to listOf(null, null), "987" to null)

        assertEquals("""{"aList":[null,null],"987":null}""", obj.toJson(Minimal()))
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
        val json = """["foo","bar",{"foo":"bar"},1,2,[3,4],null,true,{"nested":{"inner":{"array":[2,3.14,321987432],"jagged array":[43,[43,[123,32,43,54]]]}}},{"blah":"slime"},"string with \"escaped quotes\", a'p'o's't'r'o'p'h'e's, commas, [brackets] and {braces}"]"""
        val types = JsonParser(json).parseListAsStream()
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
        assertFailsWith<JsonException>("Invalid numeric format. Current capture '1e-', was not expecting '￿'") { JsonParser("1e-").parse() }
        assertFailsWith<JsonException>("Invalid numeric format. Current capture '1.', was not expecting '.'") { JsonParser("1..3").parse() }
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

        assertFailsWith<JsonException> { JsonParser("tune").parse() }
        assertFailsWith<JsonException> { JsonParser("farce").parse() }
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
                JsonParser(array(obj("moo" to "cow"), obj("bah" to "sheep")).toJson()).parse())
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
        assertEquals(array(array(array(false))),
                JsonParser("[[[false]]]").parse())
    }

}
