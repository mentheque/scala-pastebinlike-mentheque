package controller

import cats.data.ReaderT
import cats.effect.IO
import domain.errors.AppError
import domain.{Pastein, RequestContext}
import service.PasteinStorage
import sttp.tapir.server.ServerEndpoint
//import tofu.syntax.feither._

trait PasteinController[F[_]] {
  def findPasteinByShorthand: ServerEndpoint[Any, F]

  def createPastein: ServerEndpoint[Any, F]

  def verifyAccess: ServerEndpoint[Any, F]

  def modifyPastein: ServerEndpoint[Any, F]

  def all: List[ServerEndpoint[Any, F]]

}

object PasteinController {
  final private class Impl(storage: PasteinStorage[ReaderT[IO, RequestContext, *]]) extends PasteinController[IO] {
    override val findPasteinByShorthand: ServerEndpoint[Any, IO] =
      endpoints.findPasteinByShorthand.serverLogic(shorthand =>
        storage.findByShorthand(shorthand).map(_.left.map[AppError](identity)).run(RequestContext("0"))
      )

    override val createPastein: ServerEndpoint[Any, IO] =
      endpoints.createPastein.serverLogic { case (context, pastein) =>
        storage.create(pastein).run(context)
      }

    override val verifyAccess: ServerEndpoint[Any, IO] =
      endpoints.verifyAccess.serverLogic { case (context, pastein) =>
        storage.verifyAccessKey(pastein).run(context)
      }

    override val modifyPastein: ServerEndpoint[Any, IO] = {
      // for some reason I couldn't properly use mapIn to get the needed signature
      // I even found this fairly similar issue https://github.com/softwaremill/tapir/issues/378,
      // that was marked resolved for my version of tapir, but even the code from the issue wouldn't compile for me.
      // So just this
      endpoints.modifyPastein.serverLogic { case (shorthand, context, createRequest) =>
        storage.modifyPastein(Pastein(createRequest.body, shorthand, createRequest.accessKey)).run(context)
      }
    }


    override val all: List[ServerEndpoint[Any, IO]] =
      List(findPasteinByShorthand, createPastein, verifyAccess, modifyPastein)
  }

  def make(storage: PasteinStorage[ReaderT[IO, RequestContext, *]]): PasteinController[IO] = new Impl(storage)
}
