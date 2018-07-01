package org.sathe.json

import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDate

class ExampleBean : Serializable {
    var field1: String? = null
    var field2: String? = null
    var date1: LocalDate? = null
    var int1: Int? = null
    var bool1: Boolean? = null
    var dec1: BigDecimal? = null
    var list1: List<String>? = null
    var listOfLists: List<List<String>>? = null
    var enum1: ExampleEnum? = null

    override fun toString(): String = "ExampleBean"
}