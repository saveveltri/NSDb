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

package io.radicalbit.nsdb.sql.parser

import io.radicalbit.nsdb.common.statement._
import io.radicalbit.nsdb.sql.parser.StatementParserResult._
import org.scalatest.Inside._
import org.scalatest.{Matchers, WordSpec}

class GracePeriodStatementSpec extends WordSpec with Matchers {

  private val parser = new SQLStatementParser

  private val seconds = 1000L
  private val minutes = 60 * seconds
  private val hours   = 60 * minutes
  private val days    = 24 * hours

  "A SQL parser instance" when {

    "receive a select with a grace period" should {

      "parse it successfully for an hours size period" in {
        inside(
          parser.parse(db = "db", namespace = "namespace", input = "SELECT * FROM people since 6h")
        ) {
          case success: SqlStatementParserSuccess =>
            inside(success.statement) {
              case selectSQLStatement: SelectSQLStatement =>
                selectSQLStatement shouldBe
                  SelectSQLStatement(
                    db = "db",
                    namespace = "namespace",
                    metric = "people",
                    distinct = false,
                    fields = AllFields(),
                    gracePeriod = Some(GracePeriod(6 * hours, "h", 6))
                  )
            }
        }
      }

      "parse it successfully for a seconds size period" in {
        inside(
          parser.parse(db = "db", namespace = "namespace", input = "SELECT * FROM people since 6s")
        ) {
          case success: SqlStatementParserSuccess =>
            inside(success.statement) {
              case selectSQLStatement: SelectSQLStatement =>
                selectSQLStatement shouldBe
                  SelectSQLStatement(
                    db = "db",
                    namespace = "namespace",
                    metric = "people",
                    distinct = false,
                    fields = AllFields(),
                    gracePeriod = Some(GracePeriod(6 * seconds, "s", 6))
                  )
            }
        }
      }

      "parse it successfully for a minutes size period" in {
        inside(
          parser.parse(db = "db", namespace = "namespace", input = "SELECT * FROM people since 6m")
        ) {
          case success: SqlStatementParserSuccess =>
            inside(success.statement) {
              case selectSQLStatement: SelectSQLStatement =>
                selectSQLStatement shouldBe
                  SelectSQLStatement(
                    db = "db",
                    namespace = "namespace",
                    metric = "people",
                    distinct = false,
                    fields = AllFields(),
                    gracePeriod = Some(GracePeriod(6 * minutes, "m", 6))
                  )
            }
        }
      }

      "parse it successfully for a days size period" in {
        inside(
          parser.parse(db = "db", namespace = "namespace", input = "SELECT * FROM people since 6d")
        ) {
          case success: SqlStatementParserSuccess =>
            inside(success.statement) {
              case selectSQLStatement: SelectSQLStatement =>
                selectSQLStatement shouldBe
                  SelectSQLStatement(
                    db = "db",
                    namespace = "namespace",
                    metric = "people",
                    distinct = false,
                    fields = AllFields(),
                    gracePeriod = Some(GracePeriod(6 * days, "d", 6))
                  )
            }
        }
      }

      "fail if other time measures are provided" in {
        parser.parse(db = "db", namespace = "namespace", input = "SELECT * FROM people since 6y") shouldBe a[
          SqlStatementParserFailure]
      }
    }

    "receive a select with a where condition and a grace period" should {
      "parse it successfully relative time in complex condition with brackets" in {
        inside(
          parser.parse(
            db = "db",
            namespace = "namespace",
            "SELECT name FROM people WHERE (name like $an$ and surname = pippo) and timestamp IN (2,4)  since 6h")
        ) {
          case success: SqlStatementParserSuccess =>
            inside(success.statement) {
              case selectSQLStatement: SelectSQLStatement =>
                selectSQLStatement shouldBe
                  SelectSQLStatement(
                    db = "db",
                    namespace = "namespace",
                    metric = "people",
                    distinct = false,
                    fields = ListFields(List(Field("name", None))),
                    condition = Some(Condition(TupledLogicalExpression(
                      expression1 = TupledLogicalExpression(
                        expression1 = LikeExpression("name", "$an$"),
                        operator = AndOperator,
                        expression2 =
                          EqualityExpression(dimension = "surname", value = AbsoluteComparisonValue("pippo"))
                      ),
                      operator = AndOperator,
                      expression2 = RangeExpression(dimension = "timestamp",
                                                    value1 = AbsoluteComparisonValue(2L),
                                                    value2 = AbsoluteComparisonValue(4L))
                    ))),
                    gracePeriod = Some(GracePeriod(6 * hours, "h", 6))
                  )
            }
        }
      }
    }

    "receive a select with a where condition an order and a limit" should {
      "parse it successfully relative time in complex condition with brackets" in {
        inside(
          parser.parse(db = "db",
                       namespace = "namespace",
                       "SELECT name FROM people WHERE surname = pippo order by name desc since 6h limit 5")
        ) {
          case success: SqlStatementParserSuccess =>
            inside(success.statement) {
              case selectSQLStatement: SelectSQLStatement =>
                selectSQLStatement shouldBe
                  SelectSQLStatement(
                    db = "db",
                    namespace = "namespace",
                    metric = "people",
                    distinct = false,
                    fields = ListFields(List(Field("name", None))),
                    condition = Some(
                      Condition(EqualityExpression(dimension = "surname", value = AbsoluteComparisonValue("pippo")))),
                    order = Some(DescOrderOperator(dimension = "name")),
                    limit = Some(LimitOperator(5)),
                    gracePeriod = Some(GracePeriod(6 * hours, "h", 6))
                  )
            }
        }
      }

      "fail if grace period is provided after the limit" in {
        parser.parse(db = "db",
                     namespace = "namespace",
                     "SELECT name FROM people WHERE surname = pippo order by name desc limit 5 since 6h") shouldBe a[
          SqlStatementParserFailure]
      }
    }

  }

}
