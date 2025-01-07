package dao

import cats.{Applicative, Monad}
import cats.syntax.applicative._
import cats.syntax.either._
import domain._
import domain.errors._
import doobie._
import doobie.implicits._

trait PasteinSql {
  def findByShorthand(shorthand: LinkShorthand): ConnectionIO[Option[PasteinBody]]
  def create(pastein: CreateRequest): ConnectionIO[ModifyRequest]

  def accessKeyByShorthand(shorthand: LinkShorthand): ConnectionIO[Either[PasteinNotFound, AccessKey]]

  def modifyPastein(pastein: Pastein): ConnectionIO[Either[AppError, Unit]]
}

object PasteinSql {

  object sqls {
    def bodyByShorthandSql(shorthand: LinkShorthand): Query0[PasteinBody] =
      sql"""
           select body
           from PASTEINS
           where shorthand=${shorthand.value}
      """.query[PasteinBody]

    def updateLastAccessTimeSql(shorthand: LinkShorthand): Update0 =
      sql"""
            update PASTEINS
	            set last_access = NOW()
              where shorthand=${shorthand.value};
      """.update

    def accessTokenByShorthandSql(shorthand: LinkShorthand): Query0[String] =
      sql"""
           select access_token
           from PASTEINS
           where shorthand=${shorthand.value}
      """.query[String] // possibly can generate AccessKey from the beginning

    def insertKnowingKeySql(pastein: CreateRequest): Update0 = // this is shit, but let's just work with this
      sql"""
            insert into PASTEINS (body, access_token)
            values (${pastein.body.value}, ${pastein.accessKey.value.getOrElse("you shouldn't have come here")})
           """.update

    def generateAccessKeySql(): Query0[String] =
      sql"""
           select stringify_bigint(pseudo_encrypt(pseudo_encrypt(nextval('access_token_init'))))
      """.query[String]

    def modifyPasteinSql(pastein: Pastein): Update0 =
      sql"""
            update PASTEINS
	            set
	              last_access = NOW(),
                body = ${pastein.body.value}
              where shorthand=${pastein.shorthand.value};
      """.update
  }

  private final class Impl extends PasteinSql {

    import sqls._

    override def findByShorthand(shorthand: LinkShorthand): ConnectionIO[Option[PasteinBody]] = {
      for {
        _ <- updateLastAccessTimeSql(shorthand).run
        body <- bodyByShorthandSql(shorthand).option
      } yield body
    }

    override def create(pastein: CreateRequest): ConnectionIO[ModifyRequest] =
      pastein.accessKey.value match {
        case Some(_) => insertKnowingKeySql(pastein)
          .withUniqueGeneratedKeys[LinkShorthand]("shorthand") // should be unique, if pseudo_encrypt works correctly
          .map(ModifyRequest(_, pastein.accessKey))
        case None => generateAccessKeySql().option.map(os => CreateRequest(pastein.body, AccessKey(os))).flatMap(create)
      }


    override def accessKeyByShorthand(shorthand: LinkShorthand): ConnectionIO[Either[PasteinNotFound, AccessKey]] =
      accessTokenByShorthandSql(shorthand).option.map {
        case None => PasteinNotFound(shorthand).asLeft
        case os => AccessKey(os).asRight
      }

    override def modifyPastein(pastein: Pastein): ConnectionIO[Either[AppError, Unit]] =
      modifyPasteinSql(pastein).run.map{
        case 0 => PasteinNotFound(pastein.shorthand).asLeft
        case _ => ().asRight
      }

  }

  def make: PasteinSql = new Impl
}
