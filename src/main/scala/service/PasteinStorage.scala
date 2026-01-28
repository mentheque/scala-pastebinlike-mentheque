package service

import cats.FlatMap
import cats.effect.kernel.MonadCancelThrow
import cats.implicits.{catsSyntaxApply, toFunctorOps}
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import dao.PasteinSql
import domain._
import domain.errors._
import doobie._
import doobie.implicits._
import tofu.logging.Logging
import tofu.syntax.logging._

trait PasteinStorage[F[_]] {
  def findByShorthand(shorthand: LinkShorthand): F[Either[InternalError, Option[PasteinBody]]]

  def create(pastein: CreateRequest): F[Either[AppError, ModifyRequest]]

  def verifyAccessKey(pastein: ModifyRequest): F[Either[AppError, Boolean]]

  def modifyPastein(pastein: Pastein): F[Either[AppError, Unit]]
}

object PasteinStorage {
  def make[F[_] : MonadCancelThrow](
                                     pasteinSql: PasteinSql,
                                     transactor: Transactor[F]
                                   ): PasteinStorage[F] =
    new PasteinStorage[F] {
      override def findByShorthand(shorthand: LinkShorthand): F[Either[InternalError, Option[PasteinBody]]] =
        pasteinSql
          .findByShorthand(shorthand)
          .transact(transactor)
          .attempt
          .map(_.leftMap(InternalError.apply))

      override def create(pastein: CreateRequest): F[Either[AppError, ModifyRequest]] =
        pasteinSql.create(pastein).transact(transactor).attempt.map {
          case Left(th) => InternalError(th).asLeft
          case Right(ModifyRequest(shorthand, AccessKey(None))) => UnableToGenerateAccessToken().asLeft
          case Right(someCreatedPastein) => someCreatedPastein.asRight // have to map from
          // Either[Throwable, ...] => Either[InternalError, ...]
        }

      private def flattenErrorsAndMap[A, B](f: A => B)
                                           (x: Either[Throwable, Either[AppError, A]]): Either[AppError, B] =
        x match {
          case Left(th) => InternalError(th).asLeft
          case Right(Left(err)) => err.asLeft
          case Right(Right(a)) => f(a).asRight
        }

      override def verifyAccessKey(pastein: ModifyRequest): F[Either[AppError, Boolean]] =
        pasteinSql
          .accessKeyByShorthand(pastein.shorthand)
          .transact(transactor)
          .attempt
          .map(flattenErrorsAndMap(pastein.accessKey.equals(_)))

      override def modifyPastein(pastein: Pastein): F[Either[AppError, Unit]] =
        verifyAccessKey(pastein).flatMap {
          case Left(err) => err.asLeft[Unit].pure[F]
          case Right(false) => IncorrectAccessKey(pastein.shorthand).asLeft[Unit]
            .leftMap[AppError](identity).pure[F] // have to do all of this hoop jumping to not get an error from
          // F[Either[IncorrectAccessKey, ...]] instead of F[Either[AppError, ...]]. Weird
          case Right(true) => pasteinSql.modifyPastein(pastein).transact(transactor)
            .attempt
            .map(flattenErrorsAndMap(identity))
        }
    }

  private final class LoggingImpl[F[_] : FlatMap](storage: PasteinStorage[F])(
    implicit logging: Logging[F]
  ) extends PasteinStorage[F] {
    private def surroundWithLogs[Error, Res](
                                              io: F[Either[Error, Res]]
                                            )(
                                              inputLog: String
                                            )(errorOutputLog: Error => (String, Option[Throwable]))(
                                              successOutputLog: Res => String
                                            ): F[Either[Error, Res]] = {
      info"$inputLog" *> io.flatTap {
        case Left(error) =>
          val (logString: String, throwable: Option[Throwable]) =
            errorOutputLog(error)
          throwable.fold(error"$logString")(err => errorCause"$logString"(err))
        case Right(success) => info"${successOutputLog(success)}"
      }
    }

    override def findByShorthand(shorthand: LinkShorthand): F[Either[InternalError, Option[PasteinBody]]] =
      surroundWithLogs(storage.findByShorthand(shorthand))("Finding pastein by shorthand")(err =>
        (s"Failed to find pastein by shorthand ${err.message}", Some(err.cause0))
      )(success => s"Found pastein: $success")


    override def create(pastein: CreateRequest): F[Either[AppError, ModifyRequest]] =
      surroundWithLogs(storage.create(pastein))("Creating pastein")(err =>
        (err.message, err.cause)
      )(success => s"Created pastein: $success")

    override def verifyAccessKey(pastein: ModifyRequest): F[Either[AppError, Boolean]] =
      surroundWithLogs(storage.verifyAccessKey(pastein))("Verifying access")(err =>
        (err.message, err.cause)
      )(success => s"Access verification resulted in: $success")

    override def modifyPastein(pastein: Pastein): F[Either[AppError, Unit]] =
      surroundWithLogs(storage.modifyPastein(pastein))("Modifying pastein")(err =>
        (err.message, err.cause)
      )(success => s"Modified successfully")
  }

  def makeLogging[F[_] : MonadCancelThrow](
                                            pasteinSql: PasteinSql,
                                            transactor: Transactor[F]
                                          )(implicit logging: Logging[F]): PasteinStorage[F] =
    new LoggingImpl[F](make(pasteinSql, transactor))
}
