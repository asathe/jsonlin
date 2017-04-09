## JSONLIN - A(nother) JSON parser/lexer library with support for mappers

Yet another JSON library written as a way to help me explore the Kotlin language

Supports:
* Parsing input streams into <code>JsonType</code>s
* Supports <code>find()</code>ing nested types
* You can add mappers to convert to/from JSON to concrete types
* Inbuilt mappers for
  * Primitives
  * java.time.LocalDate and LocalDateTime
  * Enums
  * Basic reflection (in progress) for serializable types