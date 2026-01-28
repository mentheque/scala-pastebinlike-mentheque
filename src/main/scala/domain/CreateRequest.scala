package domain

import derevo.circe.{decoder, encoder}
import derevo.derive
import sttp.tapir.derevo.schema
import tofu.logging.derivation._

@derive(loggable, encoder, decoder, schema)
final case class CreateRequest(body: PasteinBody, accessKey: AccessKey)

@derive(loggable, encoder, decoder, schema)
final case class ModifyRequest(shorthand: LinkShorthand, accessKey: AccessKey)

@derive(loggable, encoder, decoder, schema)
final case class Pastein(body: PasteinBody, shorthand: LinkShorthand, accessKey: AccessKey)

object CreateRequest {
  implicit val pastein2CreateRequest: Pastein => CreateRequest =
    p => CreateRequest(p.body, p.accessKey)
}

object ModifyRequest {
  implicit val pastein2ModifyRequest: Pastein => ModifyRequest =
    p => ModifyRequest(p.shorthand, p.accessKey)
}
