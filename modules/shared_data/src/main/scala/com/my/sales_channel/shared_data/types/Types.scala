package com.my.sales_channel.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.tessellation.currency.dataApplication.{DataCalculatedState, DataOnChainState, DataUpdate}
import org.tessellation.schema.address.Address

object Types {
  @derive(decoder, encoder)
  sealed trait SalesUpdate extends DataUpdate

  @derive(decoder, encoder)
  case class CreateSalesChannel(name: String, owner: Address,  products: List[(String, Long)], startSnapshotOrdinal: Long, endSnapshotOrdinal: Long) extends SalesUpdate

  @derive(decoder, encoder)
  case class Sale(channelId: String, address: Address, sale: List[(String, Long)], timestamp: String) extends SalesUpdate

  @derive(decoder, encoder)
  case class AddProducts(channelId: String, address: Address, products: List[(String, Long)]) extends SalesUpdate

  @derive(decoder, encoder)
  case class AddSeller(channelId: String, address: Address, seller: Address) extends SalesUpdate

  @derive(decoder, encoder)
  case class AddInventory(channelId: String, address: Address, product: String, amount: Long, timestamp: String) extends SalesUpdate

  @derive(decoder, encoder)
  case class MoveInventory(channelId: String, address: Address, toAddress: Address, product: String, amount: Long, timestamp: String) extends SalesUpdate

  @derive(decoder, encoder)
  case class SalesChannel(id: String, name: String, owner: Address, products: Map[String, Long], sellers: List[Address], sales: Map[String, Map[Address, Map[String, Long]]] , startSnapshotOrdinal: Long, endSnapshotOrdinal: Long, inventory: Map[Address, Map[String, Long]])

  @derive(decoder, encoder)
  case class SalesChannelStateOnChain(updates: List[SalesUpdate]) extends DataOnChainState

  @derive(decoder, encoder)
  case class SalesChannelCalculatedState(channels: Map[String, SalesChannel]) extends DataCalculatedState
}
