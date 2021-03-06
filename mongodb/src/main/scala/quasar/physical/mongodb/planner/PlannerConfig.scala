/*
 * Copyright 2014–2018 SlamData Inc.
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

package quasar.physical.mongodb.planner

import quasar.qscript.MapFunc
import quasar.physical.mongodb.{BsonVersion, MongoQueryModel}
import quasar.physical.mongodb.planner.common.WBM

import matryoshka._
import matryoshka.data._

final case class PlannerConfig[T[_[_]], EX[_], WF[_], M[_]](
  joinHandler: JoinHandler[WF, WBM],
  funcHandler: AlgebraM[M, MapFunc[T, ?], Fix[EX]],
  staticHandler: StaticHandler[T, EX],
  queryModel: MongoQueryModel,
  bsonVersion: BsonVersion)
