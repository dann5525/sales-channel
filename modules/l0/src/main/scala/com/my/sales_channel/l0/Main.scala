package com.my.sales_channel.l0

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.applicative._
import cats.syntax.option.catsSyntaxOptionId
import cats.syntax.validated._
import com.my.sales_channel.l0.custom_routes.CustomRoutes
import com.my.sales_channel.shared_data.LifecycleSharedFunctions
import com.my.sales_channel.shared_data.calculated_state.CalculatedStateService
import com.my.sales_channel.shared_data.deserializers.Deserializers
import com.my.sales_channel.shared_data.serializers.Serializers
import com.my.sales_channel.shared_data.types.Types._
import io.circe.{Decoder, Encoder}
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import org.tessellation.BuildInfo
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.currency.l0.CurrencyL0App
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

import java.util.UUID

object Main extends CurrencyL0App(
  "currency-l0",
  "currency L0 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
  tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
) {

  private def makeBaseDataApplicationL0Service(
    calculatedStateService: CalculatedStateService[IO]
  ): BaseDataApplicationL0Service[IO] =
    BaseDataApplicationL0Service(new DataApplicationL0Service[IO, SalesUpdate, SalesChannelStateOnChain, SalesChannelCalculatedState] {
      override def genesis: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState] = DataState(SalesChannelStateOnChain(List.empty), SalesChannelCalculatedState(Map.empty))

      override def validateUpdate(update: SalesUpdate)(implicit context: L0NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] = ().validNec.pure[IO]

      override def validateData(state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], updates: NonEmptyList[Signed[SalesUpdate]])(implicit context: L0NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] = LifecycleSharedFunctions.validateData[IO](state, updates)

      override def combine(state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], updates: List[Signed[SalesUpdate]])(implicit context: L0NodeContext[IO]): IO[DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]] = LifecycleSharedFunctions.combine[IO](state, updates)

      override def serializeState(state: SalesChannelStateOnChain): IO[Array[Byte]] = IO(Serializers.serializeState(state))

      override def serializeUpdate(update: SalesUpdate): IO[Array[Byte]] = IO(Serializers.serializeUpdate(update))

      override def serializeBlock(block: Signed[DataApplicationBlock]): IO[Array[Byte]] = IO(Serializers.serializeBlock(block)(dataEncoder.asInstanceOf[Encoder[DataUpdate]]))

      override def deserializeState(bytes: Array[Byte]): IO[Either[Throwable, SalesChannelStateOnChain]] = IO(Deserializers.deserializeState(bytes))

      override def deserializeUpdate(bytes: Array[Byte]): IO[Either[Throwable, SalesUpdate]] = IO(Deserializers.deserializeUpdate(bytes))

      override def deserializeBlock(bytes: Array[Byte]): IO[Either[Throwable, Signed[DataApplicationBlock]]] = IO(Deserializers.deserializeBlock(bytes)(dataDecoder.asInstanceOf[Decoder[DataUpdate]]))

      override def dataEncoder: Encoder[SalesUpdate] = implicitly[Encoder[SalesUpdate]]

      override def dataDecoder: Decoder[SalesUpdate] = implicitly[Decoder[SalesUpdate]]

      override def routes(implicit context: L0NodeContext[IO]): HttpRoutes[IO] = CustomRoutes[IO](calculatedStateService).public

      override def signedDataEntityDecoder: EntityDecoder[IO, Signed[SalesUpdate]] = circeEntityDecoder

      override def calculatedStateEncoder: Encoder[SalesChannelCalculatedState] = implicitly[Encoder[SalesChannelCalculatedState]]

      override def calculatedStateDecoder: Decoder[SalesChannelCalculatedState] = implicitly[Decoder[SalesChannelCalculatedState]]

      override def getCalculatedState(implicit context: L0NodeContext[IO]): IO[(SnapshotOrdinal, SalesChannelCalculatedState)] = calculatedStateService.getCalculatedState.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

      override def setCalculatedState(ordinal: SnapshotOrdinal, state: SalesChannelCalculatedState)(implicit context: L0NodeContext[IO]): IO[Boolean] = calculatedStateService.setCalculatedState(ordinal, state)

      override def hashCalculatedState(state: SalesChannelCalculatedState)(implicit context: L0NodeContext[IO]): IO[Hash] = calculatedStateService.hashCalculatedState(state)

      override def serializeCalculatedState(state: SalesChannelCalculatedState): IO[Array[Byte]] = IO(Serializers.serializeCalculatedState(state))

      override def deserializeCalculatedState(bytes: Array[Byte]): IO[Either[Throwable, SalesChannelCalculatedState]] = IO(Deserializers.deserializeCalculatedState(bytes))
    })

  private def makeL0Service: IO[BaseDataApplicationL0Service[IO]] = {
    for {
      calculatedStateService <- CalculatedStateService.make[IO]
      dataApplicationL0Service = makeBaseDataApplicationL0Service(calculatedStateService)
    } yield dataApplicationL0Service
  }

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL0Service[IO]]] =
    makeL0Service.asResource.some
}
