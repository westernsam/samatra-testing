package com.springer.samatra.testing.cats

import cats.Monad
import com.springer.samatra.routing.Routings.{Controller, HttpResp, Routes}
import com.springer.samatra.routing.StandardResponses.Halt
import com.springer.samatra.routing.StandardResponses.Implicits.fromString
import com.springer.samatra.testing.cats.CatsControllerTestHelpers._
import org.scalatest.FunSpec
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures
import cats.syntax.functor._


import scala.language.higherKinds

class CatsExampleTest extends FunSpec with ScalaFutures {

  trait IpResolver[F[_]] {
    def ip: F[String]
  }

  class SamatraCatsExample[F[_] : Monad](ipResolver: IpResolver[F])(implicit m2http: F[HttpResp] => HttpResp) extends Controller {

    get("/ip") { _ =>
      for {
        ip <- ipResolver.ip
      } yield fromString(ip.toUpperCase)
    }
  }

  it("should work with Id monad") {
    import cats.Id

    class FakeIpGetter extends IpResolver[Id] {
      override def ip: Id[String] = "4.3.2.1"
    }

    val controller: Routes = new SamatraCatsExample(new FakeIpGetter)
    val (status, _, _, body) = controller.get("/ip").run()

    status shouldBe 200
    new String(body) shouldBe "4.3.2.1"
  }

  it("should work with Future monad") {
    import cats.instances.future._
    import com.springer.samatra.routing.FutureResponses.Implicits.fromFuture

    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.Future


    class FakeIpGetter extends IpResolver[Future] {
      override def ip: Future[String] = Future.successful("4.3.2.1")
    }

    val controller: Routes = new SamatraCatsExample(new FakeIpGetter)
    val (status, _, _, body) = whenReady(controller.get("/ip")) {
      _.run()
    }

    status shouldBe 200
    new String(body) shouldBe "4.3.2.1"
  }

  it("should work with Option monad") {
    import cats.instances.option._

    class FakeIpGetter extends IpResolver[Option] {
      override def ip: Option[String] = Some("4.3.2.1")
    }

    implicit val optToHttp: Option[HttpResp] => HttpResp = _.getOrElse(Halt(404))

    val controller: Routes = new SamatraCatsExample(new FakeIpGetter)
    val (status, _, _, body) = controller.get("/ip").get.run()

    status shouldBe 200
    new String(body) shouldBe "4.3.2.1"
  }

}