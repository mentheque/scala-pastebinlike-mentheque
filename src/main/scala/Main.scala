import cats.Id
import cats.data.ReaderT
import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits.toSemigroupKOps
import config.AppConfig
import controller.PasteinController
import dao.PasteinSql
import doobie.util.transactor.Transactor
import com.comcast.ip4s._
import eu.timepit.refined.string.IPv4
import com.comcast.ip4s._
import domain.RequestContext
import domain.RequestContext.ContextualIO
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import service.PasteinStorage
import service.PasteinStorage.LoggingImpl
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import tofu.logging.Logging

object Main extends IOApp {

  private val mainLogs =
    Logging.Make.plain[IO].byName("Main")

  override def run(args: List[String]): IO[ExitCode] =
    (for {
      _ <- Resource.eval(mainLogs.info("Starting pasteins service...."))
      config <- Resource.eval(AppConfig.load)
      transactor = Transactor.fromDriverManager[ContextualIO](
        config.db.driver,
        config.db.url,
        config.db.user,
        config.db.password
      )
      sql = PasteinSql.make
      storage: PasteinStorage[ContextualIO] = PasteinStorage.make[ContextualIO](sql, transactor)
      controller: PasteinController[IO] = PasteinController.make(storage)
      docsEpt = SwaggerInterpreter().fromEndpoints[IO](controller.all.map(_.endpoint), "Backend", "1.0")
      routes = Http4sServerInterpreter[IO]().toRoutes(controller.all <+> docsEpt)
      httpApp = Router("/" -> routes).orNotFound
      _ <- EmberServerBuilder
        .default[IO]
        .withHost(
          Ipv4Address.fromString(config.server.host).getOrElse(ipv4"0.0.0.0")
        )
        .withPort(Port.fromInt(config.server.port).getOrElse(port"80"))
        .withHttpApp(httpApp)
        .build
    } yield ()).useForever.as(ExitCode.Success)
}

