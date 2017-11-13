/*
 * Copyright 2014–2017 SlamData Inc.
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

package quasar.physical.rdbms

import quasar.Qspec
import quasar.physical.rdbms.common.TablePath.Separator
import quasar.physical.rdbms.common.{CustomSchema, DbParams, DefaultSchema}
import quasar.physical.rdbms.testutils.RdbmsPathyArbitrary._
import pathy.Path.DirName
import pathy.Path.DirName._

import scalaz.syntax.show._

class SchemaNameTest extends Qspec {

  implicit val dbParams: DbParams = DbParams("public")

  "Schema Name" should {

    "return correct last dir name" in {
      prop { (d1: DirName, d2: DirName, d3: DirName) =>
        CustomSchema(d1.shows).lastDirName must_=== d1
        CustomSchema(d1.shows + Separator + d2.shows).lastDirName must_=== d2
        CustomSchema(d1.shows + Separator + d2.shows + Separator + d3.shows).lastDirName must_=== d3
      }
    }

    "check whether schema is a child to another" in {
      prop { (d1: DirName, d2: DirName, d3: DirName) =>

        CustomSchema("public" + Separator + d1.shows)
          .isDirectChildOf(DefaultSchema) must beTrue
        CustomSchema(d1.shows + Separator + d2.shows)
          .isDirectChildOf(CustomSchema(d1.shows)) must beTrue
        CustomSchema(d1.shows + Separator + d2.shows + Separator + d3.shows)
          .isDirectChildOf(CustomSchema(d1.shows)) must beFalse
        CustomSchema(d1.shows + Separator + d2.shows + Separator + d3.shows)
          .isDirectChildOf(CustomSchema(d1.shows + Separator + d2.shows)) must beTrue
      }
    }
  }
}
