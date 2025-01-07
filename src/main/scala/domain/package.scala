import derevo.circe.{decoder, encoder}
import derevo.derive
import doobie.Read
import io.circe.{Decoder, Encoder}
import io.estatico.newtype.macros.newtype
import sttp.tapir.Schema
import tofu.logging.derivation._

package object domain {
  @derive(loggable, encoder, decoder)
  @newtype
  case class PasteinBody(value: String)

  object PasteinBody {
    implicit val read: Read[PasteinBody] = Read[String].map(PasteinBody.apply)
    implicit val schema: Schema[PasteinBody] =
      Schema.schemaForString.map(string => Some(PasteinBody(string)))(_.value)
  }

  // Now, unwrapped derivation of encoders/decoders works correctly for a newtype, but newtypes don't allow
  // smart constructors, which I need to limit length of a user-defined accessToken. So to have a newtype-style codec,
  // writing encoder/decoder manually
  @derive(loggable)
  final case class AccessKey(value: Option[String])

  object AccessKey {
    val characterLimit: Int = 15

    def apply(value: Option[String]): AccessKey = new AccessKey(value.map(_.take(characterLimit)))

    implicit val read: Read[AccessKey] = Read[Option[String]].map(AccessKey.apply)
    implicit val schema: Schema[AccessKey] = {
      Schema.schemaForOption[String].map(option => Some(AccessKey(option)))(_.value)
    }

    implicit val unwrappedEncoder: Encoder[AccessKey] = Encoder.encodeOption[String].contramap(_.value)
    implicit val unwrappedDecoder: Decoder[AccessKey] = Decoder.decodeOption[String].map(AccessKey.apply)
  }

  @derive(loggable, encoder, decoder)
  @newtype
  case class LinkShorthand(value: String)

  object LinkShorthand {
    implicit val read: Read[LinkShorthand] = Read[String].map(LinkShorthand.apply)
    implicit val schema: Schema[LinkShorthand] =
      Schema.schemaForString.map(string => Some(LinkShorthand(string)))(_.value)
  }
}
