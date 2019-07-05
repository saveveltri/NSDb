/*
 * Copyright 2018 Radicalbit S.r.l.
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

package io.radicalbit.nsdb.statement

import io.radicalbit.nsdb.common.statement._
import org.scalatest.{Matchers, WordSpec}
import spire.math.Interval
import spire.math.interval.{Closed, Open, Unbound}
import spire.implicits._

class TimeRangeExtractorSpec extends WordSpec with Matchers {

  "A TimeRangeExtractor" when {

    "receive a simple expression that does not invole the timestamp" should {
      "parse it successfully" in {
        TimeRangeExtractor.extractTimeRange(
          Some(
            RangeExpression(dimension = "other", value1 = 2L, value2 = 4L)
          )) shouldBe List.empty
      }
    }

    "receive a tupled expression that does not invole the timestamp" should {
      "parse it successfully with and operator" in {
        TimeRangeExtractor.extractTimeRange(
          Some(
            TupledLogicalExpression(
              expression1 = RangeExpression(dimension = "other", value1 = 2L, value2 = 4L),
              operator = AndOperator,
              expression2 = RangeExpression(dimension = "other2", value1 = 2L, value2 = 4L)
            )
          )) shouldBe List.empty
      }

      "parse it successfully with or operator" in {
        TimeRangeExtractor.extractTimeRange(
          Some(
            TupledLogicalExpression(
              expression1 = RangeExpression(dimension = "other", value1 = 2L, value2 = 4L),
              operator = OrOperator,
              expression2 = RangeExpression(dimension = "other2", value1 = 2L, value2 = 4L)
            )
          )) shouldBe List.empty
      }
    }

    "receive an expression containing a range selection" should {
      "parse it successfully" in {
        TimeRangeExtractor.extractTimeRange(
          Some(
            RangeExpression(dimension = "timestamp", value1 = 2L, value2 = 4L)
          )) shouldBe List(
          Interval.closed(2L, 4L)
        )
      }
    }

    "receive an expression containing a GTE selection" should {
      "parse it successfully" in {
        TimeRangeExtractor.extractTimeRange(
          Some(ComparisonExpression(dimension = "timestamp", comparison = GreaterOrEqualToOperator, value = 10L))
        ) shouldBe List(
          Interval.fromBounds(Closed(10L), Unbound())
        )
      }
    }

    "receive an expression containing a GT AND a LTE selection" should {
      "parse it successfully" in {
        TimeRangeExtractor.extractTimeRange(
          Some(
            TupledLogicalExpression(
              expression1 = ComparisonExpression(dimension = "timestamp", comparison = GreaterThanOperator, value = 2L),
              operator = AndOperator,
              expression2 =
                ComparisonExpression(dimension = "timestamp", comparison = LessOrEqualToOperator, value = 4l)
            )
          )) shouldBe List(
          Interval.openLower(2, 4)
        )
      }
    }

    "receive an expression containing a NOT condition" should {
      "parse it successfully" in {
        TimeRangeExtractor.extractTimeRange(
          Some(
            UnaryLogicalExpression(
              expression = ComparisonExpression(dimension = "timestamp", comparison = GreaterThanOperator, value = 2L),
              operator = NotOperator
            )
          )) shouldBe List(
          Interval.fromBounds(Unbound(), Closed(2))
        )

        TimeRangeExtractor.extractTimeRange(
          Some(
            UnaryLogicalExpression(
              expression = RangeExpression("timestamp", 2L, 4L),
              operator = NotOperator
            )
          )) shouldBe List(
          Interval.fromBounds(Unbound(), Open(2)),
          Interval.fromBounds(Open(4), Unbound())
        )
      }
    }

    "receive an expression containing a GTE OR a LT selection" should {
      "parse it successfully" in {
        TimeRangeExtractor.extractTimeRange(
          Some(
            UnaryLogicalExpression(
              expression = TupledLogicalExpression(
                expression1 =
                  ComparisonExpression(dimension = "timestamp", comparison = GreaterOrEqualToOperator, value = 2L),
                operator = OrOperator,
                expression2 = ComparisonExpression(dimension = "timestamp", comparison = LessThanOperator, value = 4L)
              ),
              operator = NotOperator
            )
          )) shouldBe List(
          Interval.fromBounds(Unbound(), Open(0))
        )
      }
    }

    "receive an expression involving the timestamp and another irrelevant" should {
      "parse it successfully" in {
        TimeRangeExtractor.extractTimeRange(
          Some(
            TupledLogicalExpression(
              expression1 =
                ComparisonExpression(dimension = "timestamp", comparison = GreaterOrEqualToOperator, value = 2L),
              operator = OrOperator,
              expression2 = EqualityExpression(dimension = "name", value = "john")
            )
          )) shouldBe List(
          Interval.fromBounds(Closed(2l), Unbound())
        )

        TimeRangeExtractor.extractTimeRange(
          Some(
            TupledLogicalExpression(
              expression1 =
                ComparisonExpression(dimension = "timestamp", comparison = GreaterOrEqualToOperator, value = 2L),
              operator = AndOperator,
              expression2 = EqualityExpression(dimension = "name", value = "john")
            )
          )) shouldBe List(
          Interval.fromBounds(Closed(2l), Unbound())
        )
      }
    }

    "executing computeRangeForInterval " should {
      "return a seq of ranges" in {
        val res = TimeRangeExtractor.computeRangeForInterval(10L, 0L, 5L, Seq.empty)
        res.size shouldBe 2
        res.map(_.label).contains("5-10") shouldBe true
        res.map(_.label).contains("0-5") shouldBe true
      }
    }

    "executing computeRanges " should {
      "return a seq of ranges for a RangeExpression" in {
        val res = TimeRangeExtractor.computeRanges(
          5L,
          None,
          Some(Condition(RangeExpression(dimension = "timestamp", value1 = 0L, value2 = 10L))),
          100L)
        res.size shouldBe 2
        res.map(_.label) shouldBe Seq("5-10", "0-5")
      }

      "return a seq of ranges for a left bounded interval(>=) lower than now " in {
        val res = TimeRangeExtractor.computeRanges(
          5L,
          None,
          Some(
            Condition(
              ComparisonExpression(dimension = "timestamp", comparison = GreaterOrEqualToOperator, value = 10L))),
          100L)
        res.map(_.label) shouldBe Seq("95-100",
                                      "90-95",
                                      "85-90",
                                      "80-85",
                                      "75-80",
                                      "70-75",
                                      "65-70",
                                      "60-65",
                                      "55-60",
                                      "50-55")
      }
      "return a seq of ranges for a right bounded interval(<=) lower than now " in {
        val res = TimeRangeExtractor.computeRanges(
          5L,
          None,
          Some(
            Condition(ComparisonExpression(dimension = "timestamp", comparison = LessOrEqualToOperator, value = 100L))),
          200L)

        res.exists(r => r.max == 99 && r.min == 95) shouldBe true
        res.size shouldBe 10
      }
      "return a seq of ranges for both right and left bounded interval( >= && <=) " in {
        val res = TimeRangeExtractor.computeRanges(
          5L,
          None,
          Some(
            Condition(TupledLogicalExpression(
              ComparisonExpression(dimension = "timestamp", comparison = GreaterOrEqualToOperator, value = 70L),
              AndOperator,
              ComparisonExpression(dimension = "timestamp", comparison = LessOrEqualToOperator, value = 100L)
            ))),
          200L
        )

        res.map(_.label) shouldBe Seq("95-100", "90-95", "85-90", "80-85", "75-80", "70-75")
      }

      "return a seq of ranges for both right and left bounded interval( > && <) " in {
        val res = TimeRangeExtractor.computeRanges(
          5L,
          None,
          Some(
            Condition(TupledLogicalExpression(
              ComparisonExpression(dimension = "timestamp", comparison = GreaterThanOperator, value = 70L),
              AndOperator,
              ComparisonExpression(dimension = "timestamp", comparison = LessThanOperator, value = 100L)
            ))),
          200L
        )

        res.map(_.label) shouldBe Seq("94-99", "89-94", "84-89", "79-84", "74-79", "69-74")
      }
    }

  }
}
