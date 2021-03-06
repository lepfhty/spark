/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.sql.catalyst.expressions

import java.util.Comparator

import org.apache.spark.sql.catalyst.analysis.TypeCheckResult
import org.apache.spark.sql.catalyst.expressions.codegen.{CodegenFallback, CodeGenContext, GeneratedExpressionCode}
import org.apache.spark.sql.types._

/**
 * Given an array or map, returns its size.
 */
case class Size(child: Expression) extends UnaryExpression with ExpectsInputTypes {
  override def dataType: DataType = IntegerType
  override def inputTypes: Seq[AbstractDataType] = Seq(TypeCollection(ArrayType, MapType))

  override def nullSafeEval(value: Any): Int = child.dataType match {
    case _: ArrayType => value.asInstanceOf[ArrayData].numElements()
    case _: MapType => value.asInstanceOf[MapData].numElements()
  }

  override def genCode(ctx: CodeGenContext, ev: GeneratedExpressionCode): String = {
    nullSafeCodeGen(ctx, ev, c => s"${ev.primitive} = ($c).numElements();")
  }
}

/**
 * Sorts the input array in ascending / descending order according to the natural ordering of
 * the array elements and returns it.
 */
case class SortArray(base: Expression, ascendingOrder: Expression)
  extends BinaryExpression with ExpectsInputTypes with CodegenFallback {

  def this(e: Expression) = this(e, Literal(true))

  override def left: Expression = base
  override def right: Expression = ascendingOrder
  override def dataType: DataType = base.dataType
  override def inputTypes: Seq[AbstractDataType] = Seq(ArrayType, BooleanType)

  override def checkInputDataTypes(): TypeCheckResult = base.dataType match {
    case _ @ ArrayType(n: AtomicType, _) => TypeCheckResult.TypeCheckSuccess
    case _ @ ArrayType(n, _) => TypeCheckResult.TypeCheckFailure(
                    s"Type $n is not the AtomicType, we can not perform the ordering operations")
    case other =>
      TypeCheckResult.TypeCheckFailure(s"ArrayType(AtomicType) is expected, but we got $other")
  }

  @transient
  private lazy val lt = {
    val ordering = base.dataType match {
      case _ @ ArrayType(n: AtomicType, _) => n.ordering.asInstanceOf[Ordering[Any]]
    }

    new Comparator[Any]() {
      override def compare(o1: Any, o2: Any): Int = {
        if (o1 == null && o2 == null) {
          0
        } else if (o1 == null) {
          -1
        } else if (o2 == null) {
          1
        } else {
          ordering.compare(o1, o2)
        }
      }
    }
  }

  @transient
  private lazy val gt = {
    val ordering = base.dataType match {
      case _ @ ArrayType(n: AtomicType, _) => n.ordering.asInstanceOf[Ordering[Any]]
    }

    new Comparator[Any]() {
      override def compare(o1: Any, o2: Any): Int = {
        if (o1 == null && o2 == null) {
          0
        } else if (o1 == null) {
          1
        } else if (o2 == null) {
          -1
        } else {
          -ordering.compare(o1, o2)
        }
      }
    }
  }

  override def nullSafeEval(array: Any, ascending: Any): Any = {
    val elementType = base.dataType.asInstanceOf[ArrayType].elementType
    val data = array.asInstanceOf[ArrayData].toArray[AnyRef](elementType)
    java.util.Arrays.sort(
      data,
      if (ascending.asInstanceOf[Boolean]) lt else gt)
    new GenericArrayData(data.asInstanceOf[Array[Any]])
  }

  override def prettyName: String = "sort_array"
}
