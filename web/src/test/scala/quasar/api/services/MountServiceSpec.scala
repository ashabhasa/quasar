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

package quasar.api.services

import slamdata.Predef._
import quasar._
import quasar.api._, ApiErrorEntityDecoder._
import quasar.api.PathUtils._
import quasar.api.matchers._
import quasar.common.CIName
import quasar.contrib.pathy._, PathArbitrary._
import quasar.effect.{Failure, KeyValueStore, Timing}
import quasar.fp._
import quasar.fp.free._
import quasar.fs._
import quasar.fs.mount.{MountRequest => MR, _}
import quasar.fs.mount.cache.{VCache, ViewCache}, VCache.VCacheKVS
import quasar.sql._

import java.time.Instant
import scala.concurrent.duration._
import scala.Predef.$conforms

import argonaut._, Argonaut._
import org.http4s._, headers._, Status._
import org.http4s.argonaut._
import org.specs2.specification.core.Fragment
import matryoshka.data.Fix
import pathy.Path, Path._
import pathy.argonaut.PosixCodecJson._
import pathy.scalacheck.PathyArbitrary._
import scalaz.{Failure => _, _}, Scalaz._
import scalaz.concurrent.Task

class MountServiceSpec extends quasar.Qspec with Http4s {
  import posixCodec.printPath
  import PathError._, Mounting.PathTypeMismatch

  type Eff[A] = (Task :\: Timing :\: VCacheKVS :\: ManageFile :\: Mounting :\: MountingFailure :/: PathMismatchFailure)#M[A]

  type Mounted = Set[MR]
  type TestSvc = Request => Free[Eff, (Response, Mounted)]

  val StubFs = FileSystemType("stub")
  val fooUri = ConnectionUri("foo")
  val barUri = ConnectionUri("foo")
  val invalidUri = ConnectionUri("invalid")
  val sampleStatements: List[Statement[Fix[Sql]]] = List(FunctionDecl(CIName("FOO"), List(CIName("Bar")), Fix(boolLiteral(true))))

  val M = Mounting.Ops[Eff]

  val nineteenEighty = Instant.parse("1980-01-01T00:00:00.00Z")

  val timingInterp = λ[Timing ~> Task] {
    case Timing.Timestamp => Task.now(nineteenEighty)
    case Timing.Nanos     => Task.now(0)
  }

  val vcacheInterp: Task[VCacheKVS ~> Task] = KeyValueStore.impl.default[AFile, ViewCache]

  val vcache = VCacheKVS.Ops[Eff]

  def runTest[A](f: TestSvc => Free[Eff, A]): A = {
    type MEff[A] = Coproduct[Task, MountConfigs, A]

    (TaskRef(Set.empty[MR]) |@| TaskRef(Map.empty[APath, MountConfig]))
      .tupled.flatMap { case (mountedRef, configsRef) =>

      val mounter: Mounting ~> Free[MEff, ?] = Mounter.kvs[Task, MEff](
        {
          case MR.MountFileSystem(_, typ, `invalidUri`) =>
            MountingError.invalidConfig(
              MountConfig.fileSystemConfig(typ, invalidUri),
              "invalid connectionUri (simulated)".wrapNel
            ).left.point[Task]

          case mntReq =>
            mountedRef.modify(_ + mntReq).void map \/.right
        },
        mntReq => mountedRef.modify(_ - mntReq).void)

      val meff: MEff ~> Task =
        reflNT[Task] :+: KeyValueStore.impl.fromTaskRef(configsRef)

      (vcacheInterp ⊛ InMemory.runFs(InMemory.InMemState.empty)) { (vci, fsi) =>
        val effR: Eff ~> FailedResponseOr =
          liftMT[Task, FailedResponseT]                                                        :+:
          (liftMT[Task, FailedResponseT] compose timingInterp)                                 :+:
          (liftMT[Task, FailedResponseT] compose vci)                                          :+:
          (liftMT[Task, FailedResponseT] compose fsi compose injectNT[ManageFile, FileSystem]) :+:
          (liftMT[Task, FailedResponseT] compose (foldMapNT(meff) compose mounter))            :+:
          FailedResponse.fromFailureMessage[Task, MountingError]                               :+:
          FailedResponse.fromFailureMessage[Task, PathTypeMismatch]

        val effT: Eff ~> Task =
          reflNT[Task]                                   :+:
          timingInterp                                   :+:
          vci                                            :+:
          (fsi compose injectNT[ManageFile, FileSystem]) :+:
          (foldMapNT(meff) compose mounter)              :+:
          Failure.showRuntimeError[Task, MountingError]    :+:
          Failure.showRuntimeError[Task, PathTypeMismatch]

        val service = mount.service[Eff].toHttpService(effR).orNotFound

        val testSvc: TestSvc =
          req => injectFT[Task, Eff] apply (service(req) flatMap (mountedRef.read strengthL _))

        f(testSvc) foldMap effT
      }.join
    }.unsafePerformSync
  }

  def beMountNotFoundError(path: APath) =
    beApiErrorWithMessage(
      Status.NotFound withReason "Mount point not found.",
      "path" := path)

  def beInvalidConfigError(rsn: String) =
    equal(ApiError.apiError(
      Status.BadRequest withReason "Invalid mount configuration.",
      "reasons" := List(rsn)))

  "Mount Service" should {
    "GET" should {
      "succeed with correct filesystem path" >> prop { d: ADir =>
        runTest { service =>
          for {
            _    <- M.mountFileSystem(d, StubFs, ConnectionUri("foo"))
            r    <- service(Request(uri = pathUri(d)))
            (res, _) = r
            body <- lift(res.as[Json]).into[Eff]
          } yield {
            (body must_== Json("stub" -> Json("connectionUri" := "foo"))) and
            (res.status must_== Ok)
          }
        }
      }

      "succeed with correct, problematic filesystem path" in {
        // NB: the trailing '%' is what breaks http4s
        val d = rootDir </> dir("a.b c/d\ne%f%")

        runTest { service =>
          for {
            _    <- M.mountFileSystem(d, StubFs, ConnectionUri("foo"))
            r    <- service(Request(uri = pathUri(d)))
            (res, _) = r
            body <- lift(res.as[Json]).into[Eff]
          } yield {
            (body must_== Json("stub" -> Json("connectionUri" := "foo"))) and
            (res.status must_== Ok)
          }
        }
      }

      "succeed with correct view path" >> prop { f: AFile =>
        runTest { service =>
          val cfg = MountConfig.viewConfig0(sqlB"select * from zips where pop > :cutoff", "cutoff" -> "1000")
          val cfgStr = EncodeJson.of[MountConfig].encode(cfg)

          for {
            _    <- M.mount(f, cfg)

            r    <- service(Request(uri = pathUri(f)))
            (res, _) = r
            body <- lift(res.as[Json]).into[Eff]
          } yield {
            (body must_== cfgStr) and (res.status must_== Ok)
          }
        }
      }

      "succeed with correct SQL Statements" >> prop { d: ADir =>
        runTest { service =>
          val cfg = MountConfig.moduleConfig(sampleStatements)
          val cfgJson = EncodeJson.of[MountConfig].encode(cfg)

          for {
            _    <- M.mountModule(d, sampleStatements)
            r    <- service(Request(uri = pathUri(d)))
            (res, _) = r
            body <- lift(res.as[Json]).into[Eff]
          } yield {
            (body must_=== cfgJson) and (res.status must_=== Ok)
          }
        }
      }

      "be 404 with missing mount (dir)" >> prop { d: APath =>
        runTest { service =>
          for {
            r   <- service(Request(uri = pathUri(d)))
            (res, _) = r
            err <- lift(res.as[ApiError]).into[Eff]
          } yield {
            err must beMountNotFoundError(d)
          }
        }
      }

      "be 404 with path type mismatch" >> prop { fp: AFile =>
        runTest { service =>
          val dp = fileParent(fp) </> dir(fileName(fp).value)

          for {
            _   <- M.mountFileSystem(dp, StubFs, ConnectionUri("foo"))
            r   <- service(Request(uri = pathUri(fp)))
            (res, _) = r
            err <- lift(res.as[ApiError]).into[Eff]
          } yield {
            err must beMountNotFoundError(fp)
          }
        }
      }
    }

    "MOVE" should {
      import org.http4s.Method.{MOVE, PUT}

      def destination(p: pathy.Path[_, _, Sandboxed]) = Header(Destination.name.value, UriPathCodec.printPath(p))

      "succeed with filesystem mount" >> prop { (srcHead: String, srcTail: RDir, dstHead: String, dstTail: RDir, view: RFile) =>
        val scopedExpr = sqlB"select * from zips where pop > :cutoff"
        val vars = Variables(Map(VarName("cutoff") -> VarValue("1000")))

        // NB: distinct first segments means no possible conflict, but doesn't
        // hit every possible scenario.
        (srcHead != "" && dstHead != "" && srcHead != dstHead) ==> {
          runTest { service =>
            val src = rootDir </> dir(srcHead) </> srcTail
            val dst = rootDir </> dir(dstHead) </> dstTail
            for {
              _        <- M.mountFileSystem(src, StubFs, fooUri)
              _        <- M.mountView(src </> view, scopedExpr, vars)

              r        <- service(Request(
                            method = MOVE,
                            uri = pathUri(src),
                            headers = Headers(destination(dst))))

              (res, mntd) = r
              body     <- lift(res.as[String]).into[Eff]

              srcAfter <- M.lookupConfig(src).run.run
              dstAfter <- M.lookupConfig(dst).run.run
              srcViewAfter <- M.lookupConfig(src </> view).run.run
              dstViewAfter <- M.lookupConfig(dst </> view).run.run
            } yield {
              (body must_== s"moved ${printPath(src)} to ${printPath(dst)}")                            and
              (res.status must_== Ok)                                                                   and
              (mntd must_== Set(
                MR.mountFileSystem(dst, StubFs, fooUri),
                MR.mountView(dst </> view, scopedExpr, vars)))                                          and
              (srcAfter must beNone)                                                                    and
              (dstAfter must beSome(MountConfig.fileSystemConfig(StubFs, fooUri).right[MountingError])) and
              (srcViewAfter must beNone)                                                                and
              (dstViewAfter must beSome(MountConfig.viewConfig(scopedExpr, vars).right[MountingError]))
            }
          }
        }
      }

      "succeed with view mount" >> prop { (src: AFile, dst: AFile) =>
        val scopedExpr = sqlB"select * from zips where pop > :cutoff"
        val vars = Variables(Map(VarName("cutoff") -> VarValue("1000")))

        (src ≠ dst) ==> {
          runTest { service =>
            for {
              _        <- M.mountView(src, scopedExpr, vars)

              r        <- service(Request(
                            method = MOVE,
                            uri = pathUri(src),
                            headers = Headers(destination(dst))))

              (res, mntd) = r
              body     <- lift(res.as[String]).into[Eff]

              srcAfter <- M.lookupConfig(src).run.run
              dstAfter <- M.lookupConfig(dst).run.run
            } yield {
              (body must_== s"moved ${printPath(src)} to ${printPath(dst)}") and
              (res.status must_== Ok)                                        and
              (mntd must_== Set(MR.mountView(dst, scopedExpr, vars)))        and
              (srcAfter must beNone)                                         and
              (dstAfter must beSome(MountConfig.viewConfig(scopedExpr, vars).right[MountingError]))
            }
          }
        }
      }

      "succeed with a cached view mount" >> prop { (src: AFile, dst: AFile) =>
        val expr = sqlB"α"
        val vars = Variables.empty
        val cfg = MountConfig.ViewConfig(expr, vars)
        val cfgStr = EncodeJson.of[MountConfig].encode(cfg)
        val maxAge = 7.seconds
        val vc = ViewCache.mk(cfg, maxAge.toSeconds, nineteenEighty, src)

        (src ≠ dst) ==> {
          runTest { service =>
            for {
              put      <- lift(Request(
                              method = PUT,
                              uri = pathUri(src),
                              headers = Headers(`Cache-Control`(CacheDirective.`max-age`(maxAge))))
                            .withBody(cfgStr)).into[Eff]
              _        <- service(put)
              r        <- service(Request(
                            method = MOVE,
                            uri = pathUri(src),
                            headers = Headers(destination(dst))))
              (res, mntd) = r
              body     <- lift(res.as[String]).into[Eff]
              vcSrc    <- vcache.get(src).run
              vcDst    <- vcache.get(dst).run
              srcAfter <- M.lookupConfig(src).run.run
              dstAfter <- M.lookupConfig(dst).run.run
            } yield {
              (body must_== s"moved ${printPath(src)} to ${printPath(dst)}")             and
              (res.status must_== Ok)                                                    and
              (vcSrc must beNone)                                                        and
              (vcDst ∘ (_.copy(dataFile = src)) must beSome(vc)) and
              (mntd must_== Set(MR.mountView(dst, expr, vars)))                          and
              (srcAfter must beNone)                                                     and
              (dstAfter must beSome(MountConfig.viewConfig(expr, vars).right[MountingError]))
            }
          }
        }
      }

      "succeed with module mount" >> prop { (src: ADir, dst: ADir) =>
        (src ≠ dst) ==> {
          runTest { service =>
            for {
              _        <- M.mountModule(src, sampleStatements)

              r        <- service(Request(
                method = MOVE,
                uri = pathUri(src),
                headers = Headers(destination(dst))))

              (res, mntd) = r
              body     <- lift(res.as[String]).into[Eff]

              srcAfter <- M.lookupConfig(src).run.run
              dstAfter <- M.lookupConfig(dst).run.run
            } yield {
              (body must_== s"moved ${printPath(src)} to ${printPath(dst)}") and
                (res.status must_== Ok)                                      and
                (mntd must_== Set(MR.mountModule(dst, sampleStatements)))    and
                (srcAfter must beNone)                                       and
                (dstAfter must beSome(MountConfig.moduleConfig(sampleStatements).right[MountingError]))
            }
          }
        }
      }

      "succeed with module mount if destination is within src" >> {
        val src = rootDir </> dir("foo")
        val dst = rootDir </> dir("foo") </> dir("bar")
        runTest { service =>
          for {
            _        <- M.mountModule(src, sampleStatements)

            r        <- service(Request(
              method = MOVE,
              uri = pathUri(src),
              headers = Headers(destination(dst))))

            (res, mntd) = r
            body     <- lift(res.as[String]).into[Eff]

            srcAfter <- M.lookupConfig(src).run.run
            dstAfter <- M.lookupConfig(dst).run.run
          } yield {
            (body must_== s"moved ${printPath(src)} to ${printPath(dst)}") and
              (res.status must_== Ok)                                      and
              (mntd must_== Set(MR.mountModule(dst, sampleStatements)))    and
              (srcAfter must beNone)                                       and
              (dstAfter must beSome(MountConfig.moduleConfig(sampleStatements).right[MountingError]))
          }
        }
      }

      "be 404 with missing source" >> prop { (src: ADir, dst: ADir) =>
        runTest { service =>
          for {
            r   <- service(Request(
                     method = MOVE,
                     uri = pathUri(src),
                     headers = Headers(destination(dst))))

            (res, mntd) = r
            err <- lift(res.as[ApiError]).into[Eff]
          } yield {
            (err must beApiErrorLike(pathNotFound(src))) and
            (mntd must beEmpty)
          }
        }
      }

      "be 400 with no specified Destination" >> prop { (src: ADir) =>
        runTest { service =>
          for {
            _   <- M.mountFileSystem(src, StubFs, fooUri)

            r   <- service(Request(
                      method = MOVE,
                      uri = pathUri(src)))

            (res, mntd) = r
            err <- lift(res.as[ApiError]).into[Eff]
          } yield {
            (err must beHeaderMissingError("Destination")) and
            (mntd must_== Set(MR.mountFileSystem(src, StubFs, fooUri)))
          }
        }
      }

      "be 400 with relative path destination" >> prop { (src: ADir, dst: RDir) =>
        runTest { service =>
          for {
            _   <- M.mountFileSystem(src, StubFs, fooUri)

            r   <- service(Request(
                     method = MOVE,
                     uri = pathUri(src),
                     headers = Headers(destination(dst))))

            (res, mntd) = r
            err <- lift(res.as[ApiError]).into[Eff]
          } yield {
            (err must equal(ApiError.apiError(
              Status.BadRequest withReason "Expected an absolute directory.",
              "path" := dst))) and
            (mntd must_== Set(MR.mountFileSystem(src, StubFs, fooUri)))
          }
        }
      }

      "be 400 with non-directory path destination" >> prop { (src: ADir, dst: AFile) =>
        runTest { service =>
          for {
            _   <- M.mountFileSystem(src, StubFs, fooUri)

            r   <- service(Request(
                     method = MOVE,
                     uri = pathUri(src),
                     headers = Headers(destination(dst))))

            (res, mntd) = r
            err <- lift(res.as[ApiError]).into[Eff]
          } yield {
            (err must equal(ApiError.apiError(
              Status.BadRequest withReason "Expected an absolute directory.",
              "path" := dst))) and
            (mntd must_== Set(MR.mountFileSystem(src, StubFs, fooUri)))
          }
        }
      }
    }

    def xFileName(p: pathy.Path[_, _, Sandboxed]) = Header(XFileName.name.value, UriPathCodec.printPath(p))

    "Common" >> {
      import org.http4s.Method.POST
      import org.http4s.Method.PUT

      trait RequestBuilder {
        def apply[B](parent: ADir, mount: RPath, body: B, headers: Header*)(implicit B: EntityEncoder[B]): Free[Eff, Request]
      }

      def testBoth(test: RequestBuilder => Fragment) = {
        "POST" should {
          test(new RequestBuilder {
            def apply[B](parent: ADir, mount: RPath, body: B, headers: Header*)(implicit B: EntityEncoder[B]) =
              lift(Request(
                method = POST,
                uri = pathUri(parent),
                headers = Headers(xFileName(mount) :: headers.toList))
              .withBody(body)).into[Eff]
            })
        }

        "PUT" should {
          test(new RequestBuilder {
            def apply[B](parent: ADir, mount: RPath, body: B, headers: Header*)(implicit B: EntityEncoder[B]) =
              lift(Request(
                method = PUT,
                uri = pathUri(parent </> mount),
                headers = Headers(headers.toList))
              .withBody(body)).into[Eff]
          })
        }
      }

      testBoth { reqBuilder =>
        "succeed with filesystem path" >> prop { (parent: ADir, fsDir: RDir) =>
          runTest { service =>
            for {
              req   <- reqBuilder(parent, fsDir, """{"stub": { "connectionUri": "foo" } }""")
              r     <- service(req)
              (res, mntd) = r
              body  <- lift(res.as[String]).into[Eff]
              dst   =  parent </> fsDir
              after <- M.lookupConfig(dst).run.run
            } yield {
              (body must_== s"added ${printPath(dst)}")                   and
              (res.status must_== Ok)                                     and
              (mntd must_== Set(MR.mountFileSystem(dst, StubFs, fooUri))) and
              (after must beSome(MountConfig.fileSystemConfig(StubFs, fooUri).right[MountingError]))
            }
          }
        }

        "succeed with view path" >> prop { (parent: ADir, f: RFile) =>
          val parent: ADir = rootDir
          val f: RFile = file("a")

          runTest { service =>
            val scopedExpr = sqlB"select * from zips where pop < :cutoff"
            val vars = Variables(Map(VarName("cutoff") -> VarValue("1000")))
            val cfg  = MountConfig.viewConfig(scopedExpr, vars)
            val cfgStr = EncodeJson.of[MountConfig].encode(cfg)

            for {
              req   <- reqBuilder(parent, f, cfgStr)
              r     <- service(req)
              (res, mntd) = r
              body  <- lift(res.as[String]).into[Eff]
              dst   =  parent </> f
              after <- M.lookupConfig(dst).run.run
            } yield {
              (body must_== s"added ${printPath(dst)}")               and
              (res.status must_== Ok)                                 and
              (mntd must_== Set(MR.mountView(dst, scopedExpr, vars))) and
              (after must beSome(cfg.right[MountingError]))
            }
          }
        }

        "succeed with view under existing fs path" >> prop { (fs: ADir, viewSuffix: RFile) =>
          runTest { service =>
            val scopedExpr = sqlB"select * from zips where pop < :cutoff"
            val vars = Variables(Map(VarName("cutoff") -> VarValue("1000")))
            val cfg  = MountConfig.viewConfig(scopedExpr, vars)
            val cfgStr = EncodeJson.of[MountConfig].encode(cfg)

            val view = fs </> viewSuffix

            for {
              _         <- M.mountFileSystem(fs, StubFs, fooUri)

              req       <- reqBuilder(fs, viewSuffix, cfgStr)
              r         <- service(req)
              (res, mntd) = r
              body      <- lift(res.as[String]).into[Eff]

              afterFs   <- M.lookupConfig(fs).run.run
              afterView <- M.lookupConfig(view).run.run
            } yield {
              (body must_== s"added ${printPath(view)}") and
              (res.status must_== Ok)                    and
              (mntd must_== Set(
                MR.mountFileSystem(fs, StubFs, fooUri),
                MR.mountView(view, scopedExpr, vars)
              ))                                         and
              (afterFs must beSome)                      and
              (afterView must beSome(cfg.right[MountingError]))
            }
          }
        }

        "succeed with view 'above' existing fs path" >> prop { (d: ADir, view: RFile, fsSuffix: RDir) =>
          runTest { service =>
            val scopedExpr = sqlB"select * from zips where pop < :cutoff"
            val vars = Variables(Map(VarName("cutoff") -> VarValue("1000")))
            val cfg  = MountConfig.viewConfig(scopedExpr, vars)
            val cfgStr = EncodeJson.of[MountConfig].encode(cfg)

            val fs = d </> posixCodec.parseRelDir(posixCodec.printPath(view) + "/").flatMap(sandbox(currentDir, _)).get </> fsSuffix

            for {
              _     <- M.mountFileSystem(fs, StubFs, fooUri)

              req   <- reqBuilder(d, view, cfgStr)
              r     <- service(req)
              (res, mntd) = r
              body  <- lift(res.as[String]).into[Eff]
              vdst  =  d </> view
              after <- M.lookupConfig(vdst).run.run
            } yield {
              (body must_== s"added ${printPath(vdst)}") and
              (res.status must_== Ok)                    and
              (mntd must_== Set(
                MR.mountFileSystem(fs, StubFs, fooUri),
                MR.mountView(vdst, scopedExpr, vars)
              ))                                         and
              (after must beSome(cfg.right[MountingError]))
            }
          }
        }

        "succeed with module path" >> prop { (parent: ADir, d: RDir) =>
          runTest { service =>
            val cfgStr = EncodeJson.of[MountConfig].encode(MountConfig.moduleConfig(sampleStatements))

            for {
              req   <- reqBuilder(parent, d, cfgStr)
              r     <- service(req)
              (res, mntd) = r
              body  <- lift(res.as[String]).into[Eff]
              dst   =  parent </> d
              after <- M.lookupConfig(dst).run.run
            } yield {
              (body must_== s"added ${printPath(dst)}")                   and
                (res.status must_== Ok)                                   and
                (mntd must_== Set(MR.mountModule(dst, sampleStatements))) and
                (after must beSome(MountConfig.moduleConfig(sampleStatements).right[MountingError]))
            }
          }
        }

        "create view cache if non-existant" >> prop { (d: ADir, f: RFile) =>
          runTest { service =>
            val expr = sqlB"α"
            val vars = Variables.empty
            val cfg = MountConfig.ViewConfig(expr, vars)
            val cfgStr = EncodeJson.of[MountConfig].encode(cfg)
            val df = d </> f
            val maxAge = 7.seconds
            val vc = ViewCache.mk(cfg, maxAge.toSeconds, nineteenEighty, df)

            for {
              req   <- reqBuilder(d, f, cfgStr, `Cache-Control`(CacheDirective.`max-age`(7.seconds)))
              r     <- service(req)
              (res, mntd) = r
              body  <- lift(res.as[String]).into[Eff]
              vcg   <- vcache.get(df).run
              after <- M.lookupConfig(df).run.run
            } yield {
              (body must_= s"added ${printPath(df)}")           and
              (res.status must_= Ok)                            and
              (vcg ∘ (_.copy(dataFile = df)) must beSome(vc))   and
              (mntd must_=== Set(MR.mountView(df, expr, vars))) and
              (after must beSome(MountConfig.viewConfig(expr, vars).right[MountingError]))
            }
          }
        }

        "be 409 with fs above existing fs path" >> prop { (d: ADir, fs: RDir, fsSuffix: RDir) =>
          (!identicalPath(fsSuffix, currentDir)) ==> {
            runTest { service =>
              val cfgStr = EncodeJson.of[MountConfig].encode(MountConfig.fileSystemConfig(StubFs, fooUri))
              val fs1 = d </> fs </> fsSuffix

              for {
                _     <- M.mountFileSystem(fs1, StubFs, fooUri)

                req   <- reqBuilder(d, fs, cfgStr)
                r     <- service(req)
                (res, mntd) = r
                jerr  <- lift(res.as[Json]).into[Eff]
                dst   =  d </> fs
                after <- M.lookupConfig(dst).run.run
              } yield {
                (jerr must_== Json("error" := s"cannot mount at ${printPath(dst)} because existing mount below: ${printPath(fs1)}")) and
                (res.status must_== Conflict)                               and
                (mntd must_== Set(MR.mountFileSystem(fs1, StubFs, fooUri))) and
                (after must beNone)
              }
            }
          }
        }.pendingUntilFixed("test harness does not yet detect conflicts")

        "be 400 with fs config and file path in X-File-Name header" >> prop { (parent: ADir, fsFile: RFile) =>
          runTest { service =>
            for {
              req <- reqBuilder(parent, fsFile, """{ "stub": { "connectionUri": "foo" } }""")
              r   <- service(req)
              (res, mntd) = r
              err <- lift(res.as[ApiError]).into[Eff]
            } yield {
              (err must beApiErrorWithMessage(
                Status.BadRequest withReason "Incorrect path type.",
                "path" := (parent </> fsFile))) and
              (mntd must beEmpty)
            }
          }
        }

        "be 400 with view config and dir path in X-File-Name header" >> prop { (parent: ADir, viewDir: RDir) =>
          runTest { service =>
            val cfg = MountConfig.viewConfig0(sqlB"select * from zips where pop < :cutoff", "cutoff" -> "1000")
            val cfgStr = EncodeJson.of[MountConfig].encode(cfg)

            for {
              req <- reqBuilder(parent, viewDir, cfgStr)
              r   <- service(req)
              (res, mntd) = r
              err <- lift(res.as[ApiError]).into[Eff]
            } yield {
              (err must beApiErrorWithMessage(
                Status.BadRequest withReason "Incorrect path type.",
                "path" := (parent </> viewDir))) and
              (mntd must beEmpty)
            }
          }
        }

        "be 400 with invalid JSON" >> prop { (parent: ADir, f: RFile) =>
          runTest { service =>
            for {
              req <- reqBuilder(parent, f, "{")
              r   <- service(req)
              (res, mntd) = r
              err <- lift(res.as[ApiError]).into[Eff]
            } yield {
              (err must beApiErrorWithMessage(BadRequest withReason "Malformed input.")) and
              (mntd must beEmpty)
            }
          }
        }

        "be 400 with invalid connection uri" >> prop { (parent: ADir, d: RDir) =>
          runTest { service =>
            for {
              req <- reqBuilder(parent, d, """{ "stub": { "connectionUri": "invalid" } }""")
              r   <- service(req)
              (res, mntd) = r
              err <- lift(res.as[ApiError]).into[Eff]
            } yield {
              (err must beInvalidConfigError("invalid connectionUri (simulated)")) and
              (mntd must beEmpty)
            }
          }
        }

        "be 400 with invalid view URI" >> prop { (parent: ADir, f: RFile) =>
          runTest { service =>
            for {
              req <- reqBuilder(parent, f, """{ "view": { "connectionUri": "foo://bar" } }""")
              r   <- service(req)
              (res, mntd) = r
              err <- lift(res.as[ApiError]).into[Eff]
            } yield {
              (err must beApiErrorWithMessage(BadRequest)) and
              (mntd must beEmpty)
            }
          }
        }
      }
    }

    "POST" should {
      import org.http4s.Method.POST

      "be 409 with existing filesystem path" >> prop { (parent: ADir, fsDir: RDir) =>
        runTest { service =>
          val mntPath = parent </> fsDir

          for {
            _     <- M.mountFileSystem(mntPath, StubFs, barUri)

            req   <- lift(Request(
                       method = POST,
                       uri = pathUri(parent),
                       headers = Headers(xFileName(fsDir)))
                     .withBody("""{ "stub": { "connectionUri": "foo" } }""")).into[Eff]

            r     <- service(req)
            (res, mntd) = r
            err   <- lift(res.as[ApiError]).into[Eff]

            after <- M.lookupConfig(mntPath).run.run
          } yield {
            (err must beApiErrorLike(pathExists(mntPath)))                  and
            (mntd must_== Set(MR.mountFileSystem(mntPath, StubFs, barUri))) and
            (after must beSome(MountConfig.fileSystemConfig(StubFs, barUri).right[MountingError]))
          }
        }
      }

      "be 409 for view cache in place of existing view" >> prop { (d: ADir, f: RFile) =>
        runTest { service =>
          val expr = sqlB"α"
          val vars = Variables.empty
          val cfgStr = EncodeJson.of[MountConfig].encode(MountConfig.viewConfig(expr, vars))
          val df = d </> f
          val maxAge = 7.seconds

          for {
            _     <- lift(Request(
                       method = Method.PUT,
                       uri = pathUri(df))
                       .withBody(cfgStr)).into[Eff] >>= (service)
            put   <- lift(Request(
                       method = Method.POST,
                       uri = pathUri(d),
                       headers = Headers(xFileName(f), `Cache-Control`(CacheDirective.`max-age`(maxAge))))
                       .withBody(cfgStr)).into[Eff]
            r     <- service(put)
            (res, mntd) = r
            err   <- lift(res.as[ApiError]).into[Eff]
            vcg   <- vcache.get(df).run
            after <- M.lookupConfig(df).run.run
          } yield {
            (res.status must_= Conflict)                      and
            (err must beApiErrorLike(pathExists(df)))         and
            (vcg must beEmpty)                                and
            (mntd must_=== Set(MR.mountView(df, expr, vars))) and
            (after must beSome(MountConfig.viewConfig(expr, vars).right[MountingError]))
          }
        }
      }

      "be 409 for view cache in place of existing view cache" >> prop { (d: ADir, f: RFile) =>
        runTest { service =>
          val expr1 = sqlB"α"
          val expr2 = sqlB"β"
          val vars = Variables.empty
          val cfg1 = MountConfig.ViewConfig(expr1, vars)
          val cfg2 = MountConfig.ViewConfig(expr2, vars)
          val cfgStr1 = EncodeJson.of[MountConfig].encode(cfg1)
          val cfgStr2 = EncodeJson.of[MountConfig].encode(cfg2)
          val df = d </> f
          val maxAge = 7.seconds
          val vc = ViewCache.mk(cfg1, maxAge.toSeconds, nineteenEighty, df)

          for {
            _     <- lift(Request(
                       method = Method.PUT,
                       uri = pathUri(df))
                       .withBody(cfgStr1)).into[Eff] >>= (service)
            vcg   <- vcache.put(df, vc)
            put   <- lift(Request(
                       method = Method.POST,
                       uri = pathUri(d),
                       headers = Headers(xFileName(f), `Cache-Control`(CacheDirective.`max-age`(maxAge))))
                       .withBody(cfgStr2)).into[Eff]
            r     <- service(put)
            (res, mntd) = r
            err   <- lift(res.as[ApiError]).into[Eff]
            vcg   <- vcache.get(df).run
            after <- M.lookupConfig(df).run.run
          } yield {
            (res.status must_= Conflict)                       and
            (err must beApiErrorLike(pathExists(df)))          and
            (vcg ∘ (_.copy(dataFile = df)) must beSome(vc))    and
            (mntd must_=== Set(MR.mountView(df, expr1, vars))) and
            (after must beSome(MountConfig.viewConfig(expr1, vars).right[MountingError]))
          }
        }
      }

      "be 400 with missing X-File-Name header" >> prop { (parent: ADir) =>
        runTest { service =>
          for {
            req <- lift(Request(
                     method = POST,
                     uri = pathUri(parent))
                   .withBody("""{ "stub": { "connectionUri": "foo" } }""")).into[Eff]
            r   <- service(req)
            (res, mntd) = r
            err <- lift(res.as[ApiError]).into[Eff]
          } yield {
            (err must beHeaderMissingError("X-File-Name")) and
            (mntd must beEmpty)
          }
        }
      }
    }

    "PUT" should {
      import org.http4s.Method.PUT

      "succeed with overwritten filesystem" >> prop { (fsDir: ADir) =>
        runTest { service =>
          for {
            _     <- M.mountFileSystem(fsDir, StubFs, barUri)

            req   <- lift(Request(
                       method = PUT,
                       uri = pathUri(fsDir))
                     .withBody("""{ "stub": { "connectionUri": "foo" } }""")).into[Eff]

            r     <- service(req)
            (res, mntd) = r
            body  <- lift(res.as[String]).into[Eff]

            after <- M.lookupConfig(fsDir).run.run
          } yield {
            (body must_== s"updated ${printPath(fsDir)}")                 and
            (res.status must_== Ok)                                       and
            (mntd must_== Set(MR.mountFileSystem(fsDir, StubFs, fooUri))) and
            (after must beSome(MountConfig.fileSystemConfig(StubFs, fooUri).right[MountingError]))
          }
        }
      }

      def runPutCachedViewTemplate(
        name: String,
        setupRequest: (Duration, AFile) => Request,
        changeExpr: Boolean,
        expectFreshCache: Boolean) = {

        name >> prop { (d: ADir, f: RFile) =>
          runTest { service =>
            val expr1 = sqlB"α"
            val expr2 = if (changeExpr) sqlB"β" else expr1
            val vars = Variables.empty
            val df = d </> f
            val maxAge = 7.seconds

            def mkCache(ex: ScopedExpr[Fix[Sql]]) = {
              val cfg = MountConfig.ViewConfig(ex, vars)
              val cfgStr = EncodeJson.of[MountConfig].encode(cfg)
              val viewCache = ViewCache.mk(cfg, maxAge.toSeconds, nineteenEighty, df)
              (cfgStr, viewCache)
            }
            def modifyViewCache: ViewCache => ViewCache = _.copy(cacheReads = 42)

            val (cfgStr1, vc1) = mkCache(expr1)
            val (cfgStr2, vc2) = mkCache(expr2)

            for {
              _     <- lift(setupRequest(maxAge, df).withBody(cfgStr1)).into[Eff] >>= (service)
              _     <- vcache.modify(df, modifyViewCache)
              put   <- lift(Request(
                         method = Method.PUT,
                         uri = pathUri(df),
                         headers = Headers(`Cache-Control`(CacheDirective.`max-age`(maxAge))))
                         .withBody(cfgStr2)).into[Eff]
              r     <- service(put)
              (res, mntd) = r
              body  <- lift(res.as[String]).into[Eff]
              vcg   <- vcache.get(df).run
              after <- M.lookupConfig(df).run.run
            } yield {
              (body must_= s"updated ${printPath(df)}")         and
              (res.status must_= Ok)                            and
              (vcg ∘ (_.copy(dataFile = df)) must beSome(if (expectFreshCache) vc2 else modifyViewCache(vc2)))   and
              (mntd must_=== Set(MR.mountView(df, expr2, vars))) and
              (after must beSome(MountConfig.viewConfig(expr2, vars).right[MountingError]))
            }
          }
        }
      }

      runPutCachedViewTemplate(
        "succeed with fresh cache for view cache replacing view with same expr",
        { (maxAge, df) => Request(method = Method.PUT, uri = pathUri(df)) },
        changeExpr = false,
        expectFreshCache = true)

      runPutCachedViewTemplate(
        "succeed with fresh cache for view cache replacing view with changed expr",
        { (maxAge, df) => Request(method = Method.PUT, uri = pathUri(df)) },
        changeExpr = true,
        expectFreshCache = true)

      runPutCachedViewTemplate(
        "succeed with fresh cache for view cache replacing view cache with changed expr",
        { (maxAge, df) =>
            Request(
              method = Method.PUT,
              headers = Headers(`Cache-Control`(CacheDirective.`max-age`(maxAge))),
              uri = pathUri(df)) },
        changeExpr = true,
        expectFreshCache = true)

      runPutCachedViewTemplate(
        "succeed while keeping existing cache for view cache replacing view cache with same expr",
        { (maxAge, df) =>
            Request(
              method = Method.PUT,
              headers = Headers(`Cache-Control`(CacheDirective.`max-age`(maxAge))),
              uri = pathUri(df)) },
        changeExpr = false,
        expectFreshCache = false)

    }

    "DELETE" should {
      import org.http4s.Method.DELETE

      "succeed with filesystem path" >> prop { (d: ADir) =>
        runTest { service =>
          for {
            _     <- M.mountFileSystem(d, StubFs, ConnectionUri("foo"))

            r     <- service(Request(
                       method = DELETE,
                       uri = pathUri(d)))
            (res, mntd) = r
            body  <- lift(res.as[String]).into[Eff]

            after <- M.lookupConfig(d).run.run
          } yield {
            (body must_== s"deleted ${printPath(d)}") and
            (res.status must_== Ok)                   and
            (mntd must beEmpty)                       and
            (after must beNone)
          }
        }
      }

      "succeed with filesystem path and nested view" >> prop { (d: ADir, f: RFile) =>
        runTest { service =>
          val cfg = MountConfig.viewConfig0(sqlB"select * from zips where pop > :cutoff", "cutoff" -> "1000")

          for {
            _     <- M.mountFileSystem(d, StubFs, ConnectionUri("foo"))
            _     <- M.mount(d </> f, cfg)

            r     <- service(Request(
                       method = DELETE,
                       uri = pathUri(d)))
            (res, mntd) = r
            body  <- lift(res.as[String]).into[Eff]

            after <- M.lookupConfig(d).run.run
          } yield {
            (body must_== s"deleted ${printPath(d)}") and
            (res.status must_== Ok)                   and
            (mntd must beEmpty)                       and
            (after must beNone)
          }
        }
      }

      "succeed with view path" >> prop { (f: AFile) =>
        runTest { service =>
          val cfg = MountConfig.viewConfig0(sqlB"select * from zips where pop > :cutoff", "cutoff" -> "1000")

          for {
            _     <- M.mount(f, cfg)

            r     <- service(Request(
                       method = DELETE,
                       uri = pathUri(f)))
            (res, mntd) = r
            body  <- lift(res.as[String]).into[Eff]

            after <- M.lookupConfig(f).run.run
          } yield {
            (body must_== s"deleted ${printPath(f)}") and
            (res.status must_== Ok)                   and
            (mntd must beEmpty)                       and
            (after must beNone)
          }
        }
      }

      "succeed with module path" >> prop { d: ADir =>
        val statements: List[Statement[Fix[Sql]]] = List(FunctionDecl(CIName("FOO"), List(CIName("Bar")), Fix(boolLiteral(true))))

        runTest { service =>
          for {
            _     <- M.mountModule(d, statements)

            r     <- service(Request(
              method = DELETE,
              uri = pathUri(d)))
            (res, mntd) = r
            body  <- lift(res.as[String]).into[Eff]

            after <- M.lookupConfig(d).run.run
          } yield {
            (body must_== s"deleted ${printPath(d)}") and
              (res.status must_== Ok)                 and
              (mntd must beEmpty)                     and
              (after must beNone)
          }
        }
      }

      "succeed with view cache path" >> prop { f: AFile =>
        runTest { service =>
          val cfgStr = EncodeJson.of[MountConfig].encode(MountConfig.viewConfig(sqlB"α", Variables.empty))

          for {
            _     <- lift(Request(
                       method = Method.PUT,
                       uri = pathUri(f),
                       headers = Headers(`Cache-Control`(CacheDirective.`max-age`(7.seconds))))
                       .withBody(cfgStr)).into[Eff] >>= (service)
            r     <- service(Request(method = DELETE, uri = pathUri(f)))
            (res, mntd) = r
            body  <- lift(res.as[String]).into[Eff]
            vcg   <- vcache.get(f).run
            after <- M.lookupConfig(f).run.run
          } yield {
            (body must_= s"deleted ${printPath(f)}") and
            (res.status must_= Ok)                   and
            (vcg must beNone)                        and
            (mntd must beEmpty)                      and
            (after must beNone)
          }
        }
      }

      "be 404 with missing path" >> prop { p: APath =>
        runTest { service =>
          for {
            r   <- service(Request(method = DELETE, uri = pathUri(p)))
            (res, mntd) = r
            err <- lift(res.as[ApiError]).into[Eff]
          } yield {
            (err must beApiErrorLike(pathNotFound(p))) and
            (mntd must beEmpty)
          }
        }
      }
    }
  }
}
