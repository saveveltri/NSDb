/*
 * Copyright 2018-2020 Radicalbit S.r.l.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.radicalbit.nsdb.cluster.coordinator

import io.radicalbit.nsdb.cluster.coordinator.mockedData.MockedData._
import io.radicalbit.nsdb.common.protocol._
import io.radicalbit.nsdb.common.statement._
import io.radicalbit.nsdb.model.TimeContext
import io.radicalbit.nsdb.protocol.MessageProtocol.Commands._
import io.radicalbit.nsdb.protocol.MessageProtocol.Events._

class ReadCoordinatorTemporalAggregatedStatementsSpec extends AbstractTemporalReadCoordinatorSpec {

  "ReadCoordinator" when {

    "receive a select containing a temporal group by with count aggregation" should {
      "execute it successfully when count(*) is used instead of value" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(CountAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 6

        expected.values shouldBe Seq(
          Bit(10000, 1L, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(40000, 1L, Map("lowerBound"  -> 10000L, "upperBound"  -> 40000L), Map()),
          Bit(70000, 1L, Map("lowerBound"  -> 40000L, "upperBound"  -> 70000L), Map()),
          Bit(100000, 1L, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 1L, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 1L, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )

      }

      "execute it successfully when no shard has picked up" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                condition = Some(
                  Condition(ComparisonExpression(dimension = "timestamp",
                                                 comparison = GreaterOrEqualToOperator,
                                                 value = AbsoluteComparisonValue(200000L)))),
                fields = ListFields(List(Field("*", Some(CountAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s")),
                limit = Some(LimitOperator(2))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 0
      }

      "execute it successfully when only one shard has picked up" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                condition = Some(
                  Condition(ComparisonExpression(dimension = "timestamp",
                                                 comparison = GreaterOrEqualToOperator,
                                                 value = AbsoluteComparisonValue(100000L)))),
                fields = ListFields(List(Field("*", Some(CountAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s")),
                limit = Some(LimitOperator(2))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 2

        expected.values shouldBe Seq(
          Bit(130000, 1L, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 1L, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )
      }

      "execute it successfully with limit" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(CountAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s")),
                limit = Some(LimitOperator(2))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 2

        expected.values shouldBe Seq(
          Bit(130000, 1L, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 1L, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )
      }

      "execute it successfully when time ranges contain more than one value" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(CountAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(60000, 60, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 4

        expected.values shouldBe Seq(
          Bit(10000, 1L, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(70000, 2L, Map("lowerBound"  -> 10000L, "upperBound"  -> 70000L), Map()),
          Bit(130000, 2L, Map("lowerBound" -> 70000L, "upperBound"  -> 130000L), Map()),
          Bit(190000, 1L, Map("lowerBound" -> 130000L, "upperBound" -> 190000L), Map())
        )
      }

    }

    "receive a select containing a temporal group by with count aggregation on a double value metric" should {
      "execute it successfully when count(*) is used instead of value" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalDoubleMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(CountAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 6

        expected.values shouldBe Seq(
          Bit(10000, 1L, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(40000, 1L, Map("lowerBound"  -> 10000L, "upperBound"  -> 40000L), Map()),
          Bit(70000, 1L, Map("lowerBound"  -> 40000L, "upperBound"  -> 70000L), Map()),
          Bit(100000, 1L, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 1L, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 1L, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )

      }

      "execute it successfully when time ranges contain more than one value" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalDoubleMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(CountAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(60000, 60, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 4

        expected.values shouldBe Seq(
          Bit(10000, 1L, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(70000, 2L, Map("lowerBound"  -> 10000L, "upperBound"  -> 70000L), Map()),
          Bit(130000, 2L, Map("lowerBound" -> 70000L, "upperBound"  -> 130000L), Map()),
          Bit(190000, 1L, Map("lowerBound" -> 130000L, "upperBound" -> 190000L), Map())
        )
      }

    }

    "receive a select containing a timestamp where condition and a temporal group by with count aggregation" should {
      "execute it successfully in case of GTE" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(CountAggregation)))),
                condition = Some(
                  Condition(ComparisonExpression(dimension = "timestamp",
                                                 comparison = GreaterOrEqualToOperator,
                                                 value = AbsoluteComparisonValue(60000L)))),
                groupBy = Some(TemporalGroupByAggregation(30000L, 30, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 4

        expected.values shouldBe Seq(
          Bit(70000, 1L, Map("lowerBound"  -> 60000L, "upperBound"  -> 70000L), Map()),
          Bit(100000, 1L, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 1L, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 1L, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )
      }

      "execute it successfully in case of LT" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(CountAggregation)))),
                condition = Some(
                  Condition(ComparisonExpression(dimension = "timestamp",
                                                 comparison = LessThanOperator,
                                                 value = AbsoluteComparisonValue(60000L)))),
                groupBy = Some(TemporalGroupByAggregation(30000L, 30, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 2

        expected.values shouldBe Seq(
          Bit(29999, 1L, Map("lowerBound" -> 0L, "upperBound"     -> 29999L), Map()),
          Bit(59999, 1L, Map("lowerBound" -> 29999L, "upperBound" -> 59999L), Map())
        )
      }
    }

    "receive a select containing a temporal group with count aggregation by with an interval higher than the shard interval" should {
      "execute it successfully" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(CountAggregation)))),
                condition = None,
                groupBy = Some(TemporalGroupByAggregation(100000L, 100, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 2

        expected.values shouldBe Seq(
          Bit(90000, 4L, Map("lowerBound"  -> 0L, "upperBound"     -> 90000L), Map()),
          Bit(190000, 2L, Map("lowerBound" -> 90000L, "upperBound" -> 190000L), Map())
        )
      }

      "execute it successfully in case of a where condition" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(CountAggregation)))),
                condition = Some(
                  Condition(ComparisonExpression(dimension = "timestamp",
                                                 comparison = GreaterOrEqualToOperator,
                                                 value = AbsoluteComparisonValue(60000L)))),
                groupBy = Some(TemporalGroupByAggregation(100000L, 100, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 2

        expected.values shouldBe Seq(
          Bit(90000, 2L, Map("lowerBound"  -> 60000L, "upperBound" -> 90000L), Map()),
          Bit(190000, 2L, Map("lowerBound" -> 90000L, "upperBound" -> 190000L), Map())
        )
      }
    }

    "receive a select containing a temporal group by with sum aggregation" should {
      "execute it successfully when sum(*) is used instead of value" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(SumAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 6

        expected.values shouldBe Seq(
          Bit(10000, 1L, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(40000, 4L, Map("lowerBound"  -> 10000L, "upperBound"  -> 40000L), Map()),
          Bit(70000, 7L, Map("lowerBound"  -> 40000L, "upperBound"  -> 70000L), Map()),
          Bit(100000, 5L, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 3L, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 2L, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )

      }

      "execute it successfully when time ranges contain more than one value" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(SumAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(60000, 60, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 4

        expected.values shouldBe Seq(
          Bit(10000, 1L, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(70000, 11L, Map("lowerBound" -> 10000L, "upperBound"  -> 70000L), Map()),
          Bit(130000, 8L, Map("lowerBound" -> 70000L, "upperBound"  -> 130000L), Map()),
          Bit(190000, 2L, Map("lowerBound" -> 130000L, "upperBound" -> 190000L), Map())
        )
      }

    }

    "receive a select containing a temporal group by with sum aggregation on a double value metric" should {
      "execute it successfully when sum(*) is used instead of value" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalDoubleMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(SumAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 6

        expected.values shouldBe Seq(
          Bit(10000, 1.5, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(40000, 4.5, Map("lowerBound"  -> 10000L, "upperBound"  -> 40000L), Map()),
          Bit(70000, 7.5, Map("lowerBound"  -> 40000L, "upperBound"  -> 70000L), Map()),
          Bit(100000, 5.5, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 3.5, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 2.5, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )

      }

      "execute it successfully when time ranges contain more than one value" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalDoubleMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(SumAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(60000, 60, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 4

        expected.values shouldBe Seq(
          Bit(10000, 1.5, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(70000, 12.0, Map("lowerBound" -> 10000L, "upperBound"  -> 70000L), Map()),
          Bit(130000, 9.0, Map("lowerBound" -> 70000L, "upperBound"  -> 130000L), Map()),
          Bit(190000, 2.5, Map("lowerBound" -> 130000L, "upperBound" -> 190000L), Map())
        )
      }

    }

    "receive a select containing a temporal group by with max aggregation" should {
      "execute it successfully when max(*) is used instead of value" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(MaxAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 6

        expected.values shouldBe Seq(
          Bit(10000, 1L, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(40000, 4L, Map("lowerBound"  -> 10000L, "upperBound"  -> 40000L), Map()),
          Bit(70000, 7L, Map("lowerBound"  -> 40000L, "upperBound"  -> 70000L), Map()),
          Bit(100000, 5L, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 3L, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 2L, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )

      }

      "execute it successfully when time ranges contain more than one value" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(MaxAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(60000, 60, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 4

        expected.values shouldBe Seq(
          Bit(10000, 1L, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(70000, 7L, Map("lowerBound"  -> 10000L, "upperBound"  -> 70000L), Map()),
          Bit(130000, 5L, Map("lowerBound" -> 70000L, "upperBound"  -> 130000L), Map()),
          Bit(190000, 2L, Map("lowerBound" -> 130000L, "upperBound" -> 190000L), Map())
        )
      }

    }

    "receive a select containing a temporal group by with max aggregation on a double metric" should {
      "execute it successfully when max(*) is used instead of value" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalDoubleMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(MaxAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 6

        expected.values shouldBe Seq(
          Bit(10000, 1.5, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(40000, 4.5, Map("lowerBound"  -> 10000L, "upperBound"  -> 40000L), Map()),
          Bit(70000, 7.5, Map("lowerBound"  -> 40000L, "upperBound"  -> 70000L), Map()),
          Bit(100000, 5.5, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 3.5, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 2.5, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )

      }

      "execute it successfully when time ranges contain more than one value" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalDoubleMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(MaxAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(60000, 60, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 4

        expected.values shouldBe Seq(
          Bit(10000, 1.5, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(70000, 7.5, Map("lowerBound"  -> 10000L, "upperBound"  -> 70000L), Map()),
          Bit(130000, 5.5, Map("lowerBound" -> 70000L, "upperBound"  -> 130000L), Map()),
          Bit(190000, 2.5, Map("lowerBound" -> 130000L, "upperBound" -> 190000L), Map())
        )
      }

    }

    "receive a select containing a temporal group by with min aggregation" should {
      "execute it successfully when min(*) is used instead of value" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(MinAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 6

        expected.values shouldBe Seq(
          Bit(10000, 1L, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(40000, 4L, Map("lowerBound"  -> 10000L, "upperBound"  -> 40000L), Map()),
          Bit(70000, 7L, Map("lowerBound"  -> 40000L, "upperBound"  -> 70000L), Map()),
          Bit(100000, 5L, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 3L, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 2L, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )

      }

      "execute it successfully when time ranges contain more than one value" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(MinAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(60000, 60, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 4

        expected.values shouldBe Seq(
          Bit(10000, 1L, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(70000, 4L, Map("lowerBound"  -> 10000L, "upperBound"  -> 70000L), Map()),
          Bit(130000, 3L, Map("lowerBound" -> 70000L, "upperBound"  -> 130000L), Map()),
          Bit(190000, 2L, Map("lowerBound" -> 130000L, "upperBound" -> 190000L), Map())
        )
      }

    }

    "receive a select containing a temporal group by with min aggregation on a double metric" should {
      "execute it successfully when min(*) is used instead of value" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalDoubleMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(MinAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 6

        expected.values shouldBe Seq(
          Bit(10000, 1.5, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(40000, 4.5, Map("lowerBound"  -> 10000L, "upperBound"  -> 40000L), Map()),
          Bit(70000, 7.5, Map("lowerBound"  -> 40000L, "upperBound"  -> 70000L), Map()),
          Bit(100000, 5.5, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 3.5, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 2.5, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )

      }

      "execute it successfully when time ranges contain more than one value" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalDoubleMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(MinAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(60000, 60, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 4

        expected.values shouldBe Seq(
          Bit(10000, 1.5, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(70000, 4.5, Map("lowerBound"  -> 10000L, "upperBound"  -> 70000L), Map()),
          Bit(130000, 3.5, Map("lowerBound" -> 70000L, "upperBound"  -> 130000L), Map()),
          Bit(190000, 2.5, Map("lowerBound" -> 130000L, "upperBound" -> 190000L), Map())
        )
      }
    }

    "receive a select containing a temporal group by with avg aggregation" should {
      "execute it successfully when avg(*) is used instead of value" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(AvgAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 6

        expected.values shouldBe Seq(
          Bit(10000, 1.0, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(40000, 4.0, Map("lowerBound"  -> 10000L, "upperBound"  -> 40000L), Map()),
          Bit(70000, 7.0, Map("lowerBound"  -> 40000L, "upperBound"  -> 70000L), Map()),
          Bit(100000, 5.0, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 3.0, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 2.0, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )

      }

      "execute it successfully when time ranges contain more than one value" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(AvgAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(60000, 60, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 4

        expected.values shouldBe Seq(
          Bit(10000, 1.0, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(70000, 5.5, Map("lowerBound"  -> 10000L, "upperBound"  -> 70000L), Map()),
          Bit(130000, 4.0, Map("lowerBound" -> 70000L, "upperBound"  -> 130000L), Map()),
          Bit(190000, 2.0, Map("lowerBound" -> 130000L, "upperBound" -> 190000L), Map())
        )
      }

    }

    "receive a select containing a temporal group by with avg aggregation on a double value metric" should {
      "execute it successfully when avg(*) is used instead of value" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalDoubleMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(AvgAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 6

        expected.values shouldBe Seq(
          Bit(10000, 1.5, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(40000, 4.5, Map("lowerBound"  -> 10000L, "upperBound"  -> 40000L), Map()),
          Bit(70000, 7.5, Map("lowerBound"  -> 40000L, "upperBound"  -> 70000L), Map()),
          Bit(100000, 5.5, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 3.5, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 2.5, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )

      }

      "execute it successfully when time ranges contain more than one value" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalDoubleMetric.name,
                distinct = false,
                fields = ListFields(List(Field("value", Some(AvgAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(60000, 60, "s"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 4

        expected.values shouldBe Seq(
          Bit(10000, 1.5, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(70000, 6.0, Map("lowerBound"  -> 10000L, "upperBound"  -> 70000L), Map()),
          Bit(130000, 4.5, Map("lowerBound" -> 70000L, "upperBound"  -> 130000L), Map()),
          Bit(190000, 2.5, Map("lowerBound" -> 130000L, "upperBound" -> 190000L), Map())
        )
      }

    }

    "receive a select containing a temporal group by with an ordering clause" should {
      "execute it successfully with descending ordering on timestamp" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(CountAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s")),
                order = Some(DescOrderOperator("timestamp"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 6

        expected.values shouldBe Seq(
          Bit(160000, 1L, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map()),
          Bit(130000, 1L, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(100000, 1L, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(70000, 1L, Map("lowerBound"  -> 40000L, "upperBound"  -> 70000L), Map()),
          Bit(40000, 1L, Map("lowerBound"  -> 10000L, "upperBound"  -> 40000L), Map()),
          Bit(10000, 1L, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
        )
      }

      "execute it successfully with ascending ordering on timestamp" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(CountAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s")),
                order = Some(AscOrderOperator("timestamp"))
              )
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values.size shouldBe 6

        expected.values shouldBe Seq(
          Bit(10000, 1L, Map("lowerBound"  -> 0L, "upperBound"      -> 10000L), Map()),
          Bit(40000, 1L, Map("lowerBound"  -> 10000L, "upperBound"  -> 40000L), Map()),
          Bit(70000, 1L, Map("lowerBound"  -> 40000L, "upperBound"  -> 70000L), Map()),
          Bit(100000, 1L, Map("lowerBound" -> 70000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 1L, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 1L, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )
      }

      "return an error if another field is picked up" in {
        awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(CountAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s")),
                order = Some(DescOrderOperator("tag"))
              )
            )
          )

          probe.expectMsgType[SelectStatementFailed]
        }
      }
    }

    "receive a select containing a temporal group by with a grace period" should {
      "execute it successfully with a count aggregation" in {
        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(CountAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s")),
                gracePeriod = Some(GracePeriod("s", 50L))
              ),
              timeContext = Some(TimeContext(currentTime = 160000L))
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values shouldBe Seq(
          Bit(130000, 1L, Map("lowerBound" -> 110000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 1L, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )
      }

      "execute it successfully with sum aggregation" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(SumAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s")),
                gracePeriod = Some(GracePeriod("s", 80L))
              ),
              timeContext = Some(TimeContext(currentTime = 160000L))
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values shouldBe Seq(
          Bit(100000, 5L, Map("lowerBound" -> 80000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 3L, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 2L, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )

      }

      "execute it successfully with avg aggregation" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(AvgAggregation)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s")),
                gracePeriod = Some(GracePeriod("s", 80L))
              ),
              timeContext = Some(TimeContext(currentTime = 160000L))
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values shouldBe Seq(
          Bit(100000, 5.0, Map("lowerBound" -> 80000L, "upperBound"  -> 100000L), Map()),
          Bit(130000, 3.0, Map("lowerBound" -> 100000L, "upperBound" -> 130000L), Map()),
          Bit(160000, 2.0, Map("lowerBound" -> 130000L, "upperBound" -> 160000L), Map())
        )

      }

      "execute it successfully with count aggregation and a condition" in {

        val expected = awaitAssert {

          probe.send(
            readCoordinatorActor,
            ExecuteStatement(
              SelectSQLStatement(
                db = db,
                namespace = namespace,
                metric = TemporalLongMetric.name,
                distinct = false,
                fields = ListFields(List(Field("*", Some(CountAggregation)))),
                condition = Some(
                  Condition(ComparisonExpression(dimension = "timestamp",
                                                 comparison = LessOrEqualToOperator,
                                                 value = AbsoluteComparisonValue(100000L)))),
                groupBy = Some(TemporalGroupByAggregation(30000, 30, "s")),
                gracePeriod = Some(GracePeriod("s", 80L))
              ),
              timeContext = Some(TimeContext(currentTime = 160000L))
            )
          )

          probe.expectMsgType[SelectStatementExecuted]
        }

        expected.values shouldBe Seq(
          Bit(100000, 1L, Map("lowerBound" -> 80000L, "upperBound" -> 100000L), Map())
        )

      }

    }
  }
}
