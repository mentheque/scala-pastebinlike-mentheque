package controller

import domain._
import domain.errors._
import sttp.tapir._
import sttp.tapir.codec.newtype.codecForNewType
import sttp.tapir.json.circe._

object endpoints {

  val findPasteinByShorthand
  : PublicEndpoint[LinkShorthand, AppError, Option[PasteinBody], Any] =
    endpoint.get
      .in("pastein" / path[LinkShorthand])
      .errorOut(jsonBody[AppError])
      .out(jsonBody[Option[PasteinBody]])

  val createPastein
  : PublicEndpoint[(RequestContext, CreateRequest), AppError, ModifyRequest, Any] =
    endpoint.post
      .in("pastein")
      .in(header[RequestContext]("X-Request-Id"))
      .in(jsonBody[CreateRequest])
      .errorOut(jsonBody[AppError])
      .out(jsonBody[ModifyRequest])

  val verifyAccess
  : PublicEndpoint[(RequestContext, ModifyRequest), AppError, Boolean, Any] =
    endpoint.get
      .in("verify")
      .in(header[RequestContext]("X-Request-Id"))
      .in(jsonBody[ModifyRequest])
      .errorOut(jsonBody[AppError])
      .out(jsonBody[Boolean])

  val modifyPastein
  : PublicEndpoint[(LinkShorthand, RequestContext, CreateRequest), AppError, Unit, Any] =
    endpoint.put
      .in("pastein" / path[LinkShorthand])
      .in(header[RequestContext]("X-Request-Id"))
      .in(jsonBody[CreateRequest])
      .errorOut(jsonBody[AppError])

}
