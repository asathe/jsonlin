package org.sathe.json

interface JsonFormat {
    fun startObject(): String = "{"

    fun endObject(): String = "}"

    fun startList(): String = "["

    fun endList(): String = "]"

    fun separator(): String = ":"

    fun startItem(): String = ""
}

class Minimal : JsonFormat

class PrettyPrint(indentSize: Int = 2) : JsonFormat {

    private val indentChars = "".padEnd(indentSize)
    private var indent = ""

    override fun startObject(): String {
        indent += indentChars
        return "{"
    }

    override fun endObject(): String {
        indent = indent.removePrefix(indentChars)
        return "\n$indent}"
    }

    override fun startList(): String {
        indent += indentChars
        return "["
    }

    override fun endList(): String {
        indent = indent.removePrefix(indentChars)
        return "\n$indent]"
    }

    override fun separator(): String = " : "

    override fun startItem(): String = "\n$indent"

}

class OneLiner : JsonFormat {

    private val indent = " "

    override fun endObject(): String {
        return "$indent}"
    }

    override fun endList(): String {
        return "$indent]"
    }

    override fun separator(): String = " : "

    override fun startItem(): String = "$indent"

}