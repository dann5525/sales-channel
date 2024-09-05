package com.my.sales_channel.shared_data.calculated_state

import com.my.sales_channel.shared_data.types.Types.SalesChannelCalculatedState
import eu.timepit.refined.types.all.NonNegLong
import org.tessellation.schema.SnapshotOrdinal

case class CalculatedState(ordinal: SnapshotOrdinal, state: SalesChannelCalculatedState)

object CalculatedState {
  def empty: CalculatedState =
    CalculatedState(SnapshotOrdinal(NonNegLong.MinValue), SalesChannelCalculatedState(Map.empty))
}