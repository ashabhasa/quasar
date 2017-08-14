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

package quasar.physical.marklogic.qscript

import slamdata.Predef._
import quasar.{BackendName, TestConfig}
import quasar.contrib.pathy.ADir
import quasar.contrib.scalaz.catchable._
import quasar.effect._
import quasar.ejson.EJson
import quasar.fp.reflNT
import quasar.fp.free._
import quasar.physical.marklogic._
import quasar.physical.marklogic.cts._
import quasar.physical.marklogic.fs._
import quasar.physical.marklogic.xcc._
import quasar.physical.marklogic.xquery._
import quasar.physical.marklogic.xquery.syntax._
import quasar.qscript.{Read => _, _}
import quasar.qscript.{MapFuncsCore => MFCore}

import com.marklogic.xcc.{ContentSource, Session}
import matryoshka._
import matryoshka.data.Fix
import matryoshka.implicits._
import org.specs2.specification.core.Fragment
import pathy.Path._
import scalaz._, Scalaz._
import scalaz.concurrent._

final class FilterPlannerSpec extends quasar.Qspec {
  import EitherT.eitherTMonad

  type U      = Fix[Query[Fix[EJson], ?]]
  type SR[A]  = Const[ShiftedRead[ADir], A]
  type QSR[A] = Coproduct[QScriptCore[Fix, ?], SR, A]

  type M[A] = MarkLogicPlanErrT[PrologT[StateT[Free[XccEvalEff, ?], Long, ?], ?], A]
  type G[A] = WriterT[Id, Prologs, A]

  xccSpec(_ => "Filter Planner") { (evalPlan, evalXQuery) =>
    "does not plan with indexes if not available" >> {
      evalPlan(filterExpr("does_not_exist")) must beSome((_: Search[U] \/ XQuery).isRight)
    }

    "uses an element range index when it exists" >> {
      evalXQuery(createPathRangeIndex[G]("year"))

      evalPlan(filterExpr("year")) must beSome((_: Search[U] \/ XQuery).isLeft)
    }
  }

  def filterExpr(idxName: String): Fix[QSR] = {
    def eq(lhs: FreeMap[Fix], rhs: String): FreeMap[Fix] =
      Free.roll(MFC(MFCore.Eq(lhs, MFCore.StrLit(rhs))))

    def projectField(str: String): FreeMap[Fix] =
      Free.roll(MFC(MFCore.ProjectField(HoleF, MFCore.StrLit(str))))

    def shiftedRead(path: ADir): Fix[QSR] =
      Fix(Inject[SR, QSR].inj(Const(ShiftedRead(path, IncludeId))))

    def filter(src: Fix[QSR], f: FreeMap[Fix]): Fix[QSR] =
      Fix(Inject[QScriptCore[Fix, ?], QSR].inj(Filter(src, f)))

    filter(shiftedRead(rootDir[Sandboxed] </> dir("some")), eq(projectField(idxName), "foobar"))
  }

  def xccSpec(desc: BackendName => String)(
   tests: (Fix[QSR] => Option[Search[U] \/ XQuery], G[XQuery] => Unit) => Fragment
  ): Unit =
    TestConfig.fileSystemConfigs(FsType).flatMap(_ traverse_ { case (backend, uri, _) =>
      contentSourceConnection[Task](uri).map { cs =>
        val evalPlan: Fix[QSR] => Option[Search[U] \/ XQuery] = planQs(cs, _)
        val evalXQuery:  G[XQuery] => Unit = runXQuery(cs, _)

        desc(backend.name) >> tests(evalPlan, evalXQuery)
      }.void
    }).unsafePerformSync

  def createPathRangeIndex[F[_]: Monad: PrologW](idxName: String): F[XQuery] = {
    val pathIdx = admin.databaseRangePathIndex[F](
      xdmp.database,
      "string".xs,
      (s"/${idxName}").xs,
      XQuery.StringLit("http://marklogic.com/collation/"),
      fn.False,
      "ignore".xs)

    val addIdxConfig = (admin.getConfiguration[F] |@| pathIdx)((config, idx) =>
      admin.databaseAddRangePathIndex[F](config, xdmp.database, idx)).join

    addIdxConfig >>= (admin.saveConfiguration[F](_))
  }

  def runXQuery(cs: ContentSource, fx: G[XQuery]): Unit = {
    val (prologs, body) = fx.run
    val mainModule = MainModule(Version.`1.0-ml`, prologs, body)

    testing.moduleResults[ReaderT[Task, ContentSource, ?]](mainModule).run(cs).void.unsafePerformSync
  }

  def runXcc[A](f: Free[XccEvalEff, A], sess: Session, cs: ContentSource): A =
    (MonotonicSeq.fromZero >>= { (monoSeq: MonotonicSeq ~> Task) =>
      val xccToTask: XccEvalEff ~> Task =
        reflNT[Task] :+: monoSeq :+: Read.constant[Task, Session](sess) :+: Read.constant[Task, ContentSource](cs)
      val eval: Free[XccEvalEff, ?] ~> Task = foldMapNT(xccToTask)

      eval(f)
    }).unsafePerformSync

  def planQs(cs: ContentSource, qs: Fix[QSR]): Option[Search[U] \/ XQuery] = {
    def planQ[F[_]: Monad: QNameGenerator: PrologW: MonadPlanErr: Xcc](qs: Fix[QSR]
    ): F[Search[U] \/ XQuery] =
      qs.cataM(Planner[F, DocType.Json, QSR, Fix[EJson]].plan[U])

    runXcc(planQ[M](qs).run.run.eval(1), cs.newSession, cs)._2.toOption
  }
}
