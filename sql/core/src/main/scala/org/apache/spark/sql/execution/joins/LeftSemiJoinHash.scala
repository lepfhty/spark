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

package org.apache.spark.sql.execution.joins

import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions._
import org.apache.spark.sql.catalyst.plans.physical.ClusteredDistribution
import org.apache.spark.sql.execution.{BinaryNode, SparkPlan}

/**
 * :: DeveloperApi ::
 * Build the right table's join keys into a HashSet, and iteratively go through the left
 * table, to find the if join keys are in the Hash set.
 */
@DeveloperApi
case class LeftSemiJoinHash(
    leftKeys: Seq[Expression],
    rightKeys: Seq[Expression],
    left: SparkPlan,
    right: SparkPlan,
    condition: Option[Expression]) extends BinaryNode with HashSemiJoin {

  override def requiredChildDistribution: Seq[ClusteredDistribution] =
    ClusteredDistribution(leftKeys) :: ClusteredDistribution(rightKeys) :: Nil

  protected override def doExecute(): RDD[InternalRow] = {
    right.execute().zipPartitions(left.execute()) { (buildIter, streamIter) =>
      if (condition.isEmpty) {
        val hashSet = buildKeyHashSet(buildIter)
        hashSemiJoin(streamIter, hashSet)
      } else {
        val hashRelation = HashedRelation(buildIter, rightKeyGenerator)
        hashSemiJoin(streamIter, hashRelation)
      }
    }
  }
}
