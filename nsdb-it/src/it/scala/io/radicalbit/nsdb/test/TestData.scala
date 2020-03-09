package io.radicalbit.nsdb.test

import io.radicalbit.nsdb.common.protocol.Bit

object TestData {

  object LongMetric {

    val name = "longMetric"

    val testRecords: List[Bit] = List(
      Bit(1L, 1L, Map("surname"  -> "Doe"), Map("name" -> "John")),
      Bit(2L, 2L, Map("surname"  -> "Doe"), Map("name" -> "John")),
      Bit(4L, 3L, Map("surname"  -> "D"), Map("name"   -> "J")),
      Bit(6L, 4L, Map("surname"  -> "Doe"), Map("name" -> "Bill")),
      Bit(8L, 5L, Map("surname"  -> "Doe"), Map("name" -> "Frank")),
      Bit(10L, 6L, Map("surname" -> "Doe"), Map("name" -> "Frankie"))
    )

  }

  object DoubleMetric {

    val name = "doubleMetric"

    val testRecords: List[Bit] = List(
      Bit(2L, 1.5, Map("surname"  -> "Doe"), Map("name" -> "John")),
      Bit(4L, 1.5, Map("surname"  -> "Doe"), Map("name" -> "John")),
      Bit(6L, 1.5, Map("surname"  -> "Doe"), Map("name" -> "Bill")),
      Bit(8L, 1.5, Map("surname"  -> "Doe"), Map("name" -> "Frank")),
      Bit(10L, 1.5, Map("surname" -> "Doe"), Map("name" -> "Frankie"))
    )

  }

  object AggregationMetric {

    val name = "aggregationMetric"

    val testRecords: List[Bit] = List(
      Bit(1L, 2L, Map("surname"  -> "Doe"), Map("name" -> "John", "age"    -> 15L, "height" -> 30.5)),
      Bit(4L, 2L, Map("surname"  -> "Doe"), Map("name" -> "John", "age"    -> 20L, "height" -> 30.5)),
      Bit(2L, 1L, Map("surname"  -> "Doe"), Map("name" -> "John", "age"    -> 15L, "height" -> 30.5)),
      Bit(6L, 1L, Map("surname"  -> "Doe"), Map("name" -> "Bill", "age"    -> 15L, "height" -> 31.0)),
      Bit(8L, 1L, Map("surname"  -> "Doe"), Map("name" -> "Frank", "age"   -> 15L, "height" -> 32.0)),
      Bit(10L, 1L, Map("surname" -> "Doe"), Map("name" -> "Frankie", "age" -> 15L, "height" -> 32.0))
    )

  }

}
