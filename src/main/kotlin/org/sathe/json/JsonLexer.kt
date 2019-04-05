package org.sathe.json

import java.io.InputStream
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.math.BigDecimal
import java.math.BigInteger

class JsonLexer(stream: InputStream) : Iterator<Any?> {

    private val reader = InputStreamReader(stream).buffered()
    private val tokenBuilder = StringBuilder()

    private var currentChar: Char = '\u0000'

    init {
        nextChar()
    }

    override fun hasNext(): Boolean = currentChar != '\uffff'

    override fun next(): Any? {
        while (currentChar.isWhitespace()) {
            nextChar()
        }
        return when (currentChar) {
            '{' -> readAndReturn("{")
            '[' -> readAndReturn("[")
            '"' -> stringValue()
            '}' -> readAndReturn("}")
            ']' -> readAndReturn("]")
            ',' -> readAndReturn(",")
            ':' -> readAndReturn(":")
            't' -> booleanValue("true")
            'f' -> booleanValue("false")
            'n' -> nullValue()
            else -> numericValue()
        }
    }

    private fun nextChar(): Char {
        currentChar = reader.read().toChar()
        return currentChar
    }

    private fun readAndReturn(token: String): String {
        nextChar()
        return token
    }

    private fun appendAndFetchNext() {
        tokenBuilder.append(currentChar)
        nextChar()
    }

    private fun numericValue(): Number {
        var isDecimal = false
        tokenBuilder.setLength(0)

        fun addDigits(firstIsMandatory: Boolean) {
            assertThat(!firstIsMandatory || currentChar.isDigit()) { "Invalid numeric format. Current capture '$tokenBuilder', was not expecting '$currentChar'" }
            while (currentChar.isDigit()) {
                appendAndFetchNext()
            }
        }

        fun addExponent() {
            if (currentChar == 'e' || currentChar == 'E') {
                isDecimal = true
                appendAndFetchNext()
                assertThat(currentChar == '-' || currentChar == '+' || currentChar.isDigit()) { "Invalid numeric format. Expecting +, - or digit for exponent" }
                val signed = currentChar
                appendAndFetchNext()
                addDigits(signed == '-' || signed == '+')
            }
        }

        fun addNegativeSign() {
            if (currentChar == '-') {
                appendAndFetchNext()
            }
        }

        fun addDecimalPlace() {
            if (currentChar == '.') {
                isDecimal = true
                appendAndFetchNext()
                addDigits(true)
            }
            addDigits(false)
        }

        addNegativeSign()
        addDigits(true)
        addDecimalPlace()
        addExponent()
        return if (isDecimal) BigDecimal(tokenBuilder.toString()) else BigInteger(tokenBuilder.toString())
    }

    private fun booleanValue(expected: String): Boolean {
        val token = knownWord(expected.length)
        assertThat(token == expected) { "Expecting boolean value but got $token" }

        return token.toBoolean()
    }

    private fun nullValue(): Any? {
        val token = knownWord(4)
        assertThat(token == "null") { "Expecting null value but got $token" }
        return null
    }

    private fun knownWord(length: Int): String {
        tokenBuilder.setLength(0)
        tokenBuilder.append(currentChar)
        for (i in 2..length) tokenBuilder.append(nextChar())
        nextChar()
        return tokenBuilder.toString()
    }

    private fun stringValue(): String {
        tokenBuilder.setLength(0)

        fun hexValue(char: Char): Int {
            return when (char) {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> char - '0'
                'a', 'b', 'c', 'd', 'e', 'f' -> char - 'W'
                'A', 'B', 'C', 'D', 'E', 'F' -> char - '7'
                else -> throw JsonException("Non-hexadecimal character '$char' found")
            }
        }

        fun unicodeCharacter(): Char {
            var value = hexValue(nextChar())
            value = (value shl 4) + hexValue(nextChar())
            value = (value shl 4) + hexValue(nextChar())
            value = (value shl 4) + hexValue(nextChar())
            return value.toChar()
        }

        fun escapedCharacter() {
            val escapedCharacter = nextChar()
            when (escapedCharacter) {
                '"' -> tokenBuilder.append('"')
                't' -> tokenBuilder.append('\t')
                'r' -> tokenBuilder.append('\r')
                'n' -> tokenBuilder.append('\n')
                'b' -> tokenBuilder.append('\b')
                'f' -> tokenBuilder.append('\u000C')
                '\\' -> tokenBuilder.append('\\')
                'u' -> tokenBuilder.append(unicodeCharacter())
            }
        }

        while (nextChar() != '"') {
            assertThat(currentChar != '\uFFFF') { "Unterminated string found" }
            when (currentChar) {
                '\\' -> escapedCharacter()
                else -> tokenBuilder.append(currentChar)
            }
        }
        nextChar()

        return tokenBuilder.toString()
    }

    private fun assertThat(expression: Boolean, message: () -> String) {
        if (!expression) {
            throw JsonException(message())
        }
    }

}