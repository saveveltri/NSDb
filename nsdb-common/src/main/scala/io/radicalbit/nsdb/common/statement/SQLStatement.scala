package io.radicalbit.nsdb.common.statement

import io.radicalbit.nsdb.common.JSerializable

sealed trait SQLStatementType
case object Select extends SQLStatementType
case object Insert extends SQLStatementType

case class Field(name: String, aggregation: Option[Aggregation])

sealed trait SelectedFields
case object AllFields                                         extends SelectedFields
case class ListFields(fields: List[Field])                    extends SelectedFields
case class ListAssignment(fields: Map[String, JSerializable]) extends SelectedFields

sealed trait Expression
case class Condition(expression: Expression)
case class UnaryLogicalExpression(expression: Expression, operator: SingleLogicalOperator) extends Expression
case class TupledLogicalExpression(expression1: Expression, operator: TupledLogicalOperator, expression2: Expression)
    extends Expression
case class ComparisonExpression[T](dimension: String, comparison: ComparisonOperator, value: T) extends Expression
case class RangeExpression[T](dimension: String, value1: T, value2: T)                          extends Expression
case class EqualityExpression[T](dimension: String, value: T)                                   extends Expression

sealed trait LogicalOperator
sealed trait SingleLogicalOperator extends LogicalOperator
case object NotOperator            extends SingleLogicalOperator
sealed trait TupledLogicalOperator extends LogicalOperator
case object AndOperator            extends TupledLogicalOperator
case object OrOperator             extends TupledLogicalOperator

sealed trait ComparisonOperator
case object GreaterThanOperator      extends ComparisonOperator
case object GreaterOrEqualToOperator extends ComparisonOperator
case object LessThanOperator         extends ComparisonOperator
case object LessOrEqualToOperator    extends ComparisonOperator

sealed trait Aggregation
case object CountAggregation extends Aggregation
case object MaxAggregation   extends Aggregation
case object MinAggregation   extends Aggregation
case object SumAggregation   extends Aggregation

sealed trait OrderOperator {
  def dimension: String
}
case class AscOrderOperator(override val dimension: String)  extends OrderOperator
case class DescOrderOperator(override val dimension: String) extends OrderOperator

case class LimitOperator(value: Int)

sealed trait SQLStatement {
  def namespace: String
  def metric: String
}

case class SelectSQLStatement(override val namespace: String,
                              override val metric: String,
                              fields: SelectedFields,
                              condition: Option[Condition] = None,
                              groupBy: Option[String] = None,
                              order: Option[OrderOperator] = None,
                              limit: Option[LimitOperator] = None)
    extends SQLStatement

case class InsertSQLStatement(override val namespace: String,
                              override val metric: String,
                              timestamp: Option[Long],
                              dimensions: ListAssignment,
                              value: JSerializable)
    extends SQLStatement

case class DeleteSQLStatement(override val namespace: String, override val metric: String, condition: Condition)
    extends SQLStatement

case class DropSQLStatement(override val namespace: String, override val metric: String) extends SQLStatement