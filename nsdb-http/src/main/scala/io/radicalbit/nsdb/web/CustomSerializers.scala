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

package io.radicalbit.nsdb.web

import io.radicalbit.nsdb.common.statement._
import spray.json.DefaultJsonProtocol._
import spray.json._

object CustomSerializers {

  implicit object AggregationJsonFormat extends RootJsonFormat[Aggregation] {
    def write(a: Aggregation) = a match {
      case CountAggregation => JsString("count")
      case MaxAggregation   => JsString("max")
      case MinAggregation   => JsString("min")
      case FirstAggregation => JsString("first")
      case LastAggregation  => JsString("last")
      case SumAggregation   => JsString("sum")
      case AvgAggregation   => JsString("avg")
    }
    def read(value: JsValue): Aggregation =
      value match {
        case JsString("count")  => CountAggregation
        case JsString("max") => MaxAggregation
        case JsString("min") =>MinAggregation
        case JsString("first") =>FirstAggregation
        case JsString("last") =>LastAggregation
        case JsString("sum") =>SumAggregation
        case JsString("avg") => AvgAggregation
      }
  }

    implicit object ComparisonOperatorFormat extends RootJsonFormat[ComparisonOperator] {
      def write(a: ComparisonOperator) = a match {
        case GreaterThanOperator      => JsString(">")
        case GreaterOrEqualToOperator => JsString(">=")
        case LessThanOperator         => JsString("<")
        case LessOrEqualToOperator    => JsString("<=")
      }
      def read(value: JsValue): ComparisonOperator =
        value match {
          case JsString(">" ) => GreaterThanOperator
          case JsString(">=") => GreaterOrEqualToOperator
          case JsString("<" ) => LessThanOperator
          case JsString("<=") => LessOrEqualToOperator
        }
    }

    implicit object LogicalOperatorFormat extends RootJsonFormat[LogicalOperator] {
      def write(a: LogicalOperator) = a match {
        case AndOperator => JsString("and")
        case OrOperator  => JsString("or")
        case NotOperator => JsString("not")
      }
      def read(value: JsValue): LogicalOperator =
        value match {
          case JsString("and") => AndOperator
          case JsString("or" ) => OrOperator
          case JsString("not") => NotOperator
        }
    }

  implicit object OrderOperatorFormat extends RootJsonFormat[OrderOperator] {
    def write(a: OrderOperator) = a match {
      case AscOrderOperator(orderBy) =>
                  JsObject(("order_by", JsString(orderBy)), ("direction", JsString("asc")))
                case DescOrderOperator(orderBy) =>
                  JsObject(("order_by", JsString(orderBy)), ("direction", JsString("desc")))
    }

    def read(value: JsValue): OrderOperator = value.asJsObject.getFields("order_by", "direction") match {
      case JsString(order) ::  JsString("asc") :: Nil =>AscOrderOperator(order)
      case JsString(order) ::  JsString("desc") :: Nil =>DescOrderOperator(order)
      case _ => deserializationError("Invalid Order Operator Format")
    }
  }

//  implicit object NullableExpressionSerializer      extends RootJsonFormat[NullableExpression] {
//    def write(a: NullableExpression) = JsObject(("dimension", JsString(a.dimension)), ("comparison", JsString("null")))
//    def read(value: JsValue): NullableExpression = value.asJsObject.getFields("dimension", "comparison") match {
//      case JsString(dimension) :: JsString("null") :: Nil => NullableExpression(dimension)
//      case _ => deserializationError("Invalid Nullable Expression Format")
//    }
//  }

  implicit val nullableExpressionFormat = jsonFormat1(NullableExpression.apply)
  implicit val likeExpressionFormat = jsonFormat2(LikeExpression.apply)

  implicit object EqualityExpressionFormat extends JsonWriter[EqualityExpression[_]] {
    override def write(obj: EqualityExpression[_]): JsValue = obj match {
      case EqualityExpression(dimension, AbsoluteComparisonValue(value: Long)) =>
        JsObject(
          ("dimension", JsString(dimension)),
            ("comparison", JsString("=")),
            ("value", JsNumber(value)))
      case EqualityExpression(dimension, AbsoluteComparisonValue(value: Int)) =>
        JsObject(
          ("dimension", JsString(dimension)),
            ("comparison", JsString("=")),
            ("value", JsNumber(value)))
      case EqualityExpression(dimension, AbsoluteComparisonValue(value: String)) =>
        JsObject(
          ("dimension", JsString(dimension)),
            ("comparison", JsString("=")),
            ("value", JsString(value)))
      case EqualityExpression(dimension, AbsoluteComparisonValue(value: Double)) =>
        JsObject(
          ("dimension", JsString(dimension)),
            ("comparison", JsString("=")),
            ("value", JsNumber(value)))
      case EqualityExpression(dimension,
      RelativeComparisonValue(value: Long, operator, quantity: Long, unitMeasure)) =>
        JsObject(
            ("dimension", JsString(dimension)),
            ("comparison", JsString("=")),
            (
              "value",
              JsObject(
           ("value", JsNumber(value)),
                  ("operator", JsString(operator)),
                  ("quantity", JsNumber(quantity)),
                  ("unitMeasure", JsString(unitMeasure)))
              )
          )
    }
  }

  implicit object OrderOperatorFormat extends RootJsonFormat[OrderOperator] {
    def write(a: OrderOperator) = a match {
      case AscOrderOperator(orderBy) =>
        JsObject(("order_by", JsString(orderBy)), ("direction", JsString("asc")))
      case DescOrderOperator(orderBy) =>
        JsObject(("order_by", JsString(orderBy)), ("direction", JsString("desc")))
    }

    def read(value: JsValue): OrderOperator = value.asJsObject.getFields("order_by", "direction") match {
      case JsString(order) ::  JsString("asc") :: Nil =>AscOrderOperator(order)
      case JsString(order) ::  JsString("desc") :: Nil =>DescOrderOperator(order)
      case _ => deserializationError("Invalid Order Operator Format")
    }
  }

  implicit object GroupByAggregationFormat extends RootJsonFormat[GroupByAggregation] {
    def write(a: OrderOperator) = a match {
      case AscOrderOperator(orderBy) =>
        JsObject(("order_by", JsString(orderBy)), ("direction", JsString("asc")))
      case DescOrderOperator(orderBy) =>
        JsObject(("order_by", JsString(orderBy)), ("direction", JsString("desc")))
    }

    def read(value: JsValue): OrderOperator = value.asJsObject.getFields("order_by", "direction") match {
      case JsString(order) ::  JsString("asc") :: Nil =>AscOrderOperator(order)
      case JsString(order) ::  JsString("desc") :: Nil =>DescOrderOperator(order)
      case _ => deserializationError("Invalid Order Operator Format")
    }
  }

  implicit val selectSQLStatementFormat = jsonFormat9(SelectSQLStatement.apply)

}

