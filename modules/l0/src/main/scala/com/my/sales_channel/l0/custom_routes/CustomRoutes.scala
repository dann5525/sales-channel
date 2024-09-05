package com.my.sales_channel.l0.custom_routes

import cats.effect.Async
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.my.sales_channel.shared_data.calculated_state.CalculatedStateService
import com.my.sales_channel.shared_data.types.Types._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.auto._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.CORS
import org.http4s.{HttpRoutes, Response}
import org.tessellation.routes.internal.{InternalUrlPrefix, PublicRoutes}
import org.tessellation.schema.address.Address
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

case class CustomRoutes[F[_] : Async](calculatedStateService: CalculatedStateService[F]) extends Http4sDsl[F] with PublicRoutes[F] {
  implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

  @derive(decoder, encoder)
  case class SalesChannelResponse(id: String, name: String, owner: Address,  sellers: List[Address], products: Map[String, Long], sales: Map[String, Map[Address, Map[String, Long]]], inventory: Map[Address, Map[String, Long]], startSnapshotOrdinal: Long, endSnapshotOrdinal: Long, status: String)

  private def formatSalesChannel(channel: SalesChannel, lastOrdinal: Long): SalesChannelResponse = {
    if (channel.endSnapshotOrdinal < lastOrdinal) {
      SalesChannelResponse(channel.id, channel.name, channel.owner,  channel.sellers, channel.products, channel.sales, channel.inventory,  channel.startSnapshotOrdinal, channel.endSnapshotOrdinal, "Closed")
    } else {
      SalesChannelResponse(channel.id, channel.name, channel.owner,  channel.sellers, channel.products, channel.sales, channel.inventory,  channel.startSnapshotOrdinal, channel.endSnapshotOrdinal, "Open")
    }
  }

  private def getAllSalesChannels: F[Response[F]] = {
    calculatedStateService.getCalculatedState
      .map(v => (v.ordinal, v.state))
      .map { case (ord, state) => state.channels.view.mapValues(formatSalesChannel(_, ord.value.value)).toList }
      .flatMap(Ok(_))
      .handleErrorWith { e =>
        val message = s"An error occurred when getAllSalesChannels: ${e.getMessage}"
        logger.error(message) >> new Exception(message).raiseError[F, Response[F]]
      }
  }

  private def getSalesChannelById(channelId: String): F[Response[F]] = {
    calculatedStateService.getCalculatedState
      .map(v => (v.ordinal, v.state))
      .map { case (ord, state) => state.channels.get(channelId).map(formatSalesChannel(_, ord.value.value)) }
      .flatMap(_.fold(NotFound())(Ok(_)))
      .handleErrorWith { e =>
        val message = s"An error occurred when getSalesChannelById: ${e.getMessage}"
        logger.error(message) >> new Exception(message).raiseError[F, Response[F]]
      }

  }

  private val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "channels" => getAllSalesChannels
    case GET -> Root / "channels" / poolId => getSalesChannelById(poolId)
  }

  val public: HttpRoutes[F] =
    CORS
      .policy
      .withAllowCredentials(false)
      .httpRoutes(routes)

  override protected def prefixPath: InternalUrlPrefix = "/"
}
