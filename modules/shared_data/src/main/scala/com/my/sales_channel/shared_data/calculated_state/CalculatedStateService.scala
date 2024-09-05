package com.my.sales_channel.shared_data.calculated_state

import cats.effect.Ref
import cats.effect.kernel.Async
import cats.syntax.applicative._
import cats.syntax.functor._
import com.my.sales_channel.shared_data.types.Types.SalesChannelCalculatedState
import io.circe.syntax.EncoderOps
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.hash.Hash

import java.nio.charset.StandardCharsets

trait CalculatedStateService[F[_]] {
  def getCalculatedState: F[CalculatedState]

  def setCalculatedState(
    snapshotOrdinal: SnapshotOrdinal,
    state          : SalesChannelCalculatedState
  ): F[Boolean]

  def hashCalculatedState(
    state: SalesChannelCalculatedState
  ): F[Hash]
}

object CalculatedStateService {
  def make[F[_] : Async]: F[CalculatedStateService[F]] = {
    Ref.of[F, CalculatedState](CalculatedState.empty).map { stateRef =>
      new CalculatedStateService[F] {
        override def getCalculatedState: F[CalculatedState] = stateRef.get

        override def setCalculatedState(
          snapshotOrdinal: SnapshotOrdinal,
          state          : SalesChannelCalculatedState
        ): F[Boolean] =
          stateRef.update { currentState =>
            val currentSalesChannelCalculatedState = currentState.state
            val updatedDevices = state.channels.foldLeft(currentSalesChannelCalculatedState.channels) {
              case (acc, (address, value)) =>
                acc.updated(address, value)
            }

            CalculatedState(snapshotOrdinal, SalesChannelCalculatedState(updatedDevices))
          }.as(true)

        override def hashCalculatedState(
          state: SalesChannelCalculatedState
        ): F[Hash] = {
          val jsonState = state.asJson.deepDropNullValues.noSpaces
          Hash.fromBytes(jsonState.getBytes(StandardCharsets.UTF_8)).pure[F]
        }
      }
    }
  }
}
