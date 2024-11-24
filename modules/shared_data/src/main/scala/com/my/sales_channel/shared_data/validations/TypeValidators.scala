package com.my.sales_channel.shared_data.validations

import com.my.sales_channel.shared_data.errors.Errors._
import com.my.sales_channel.shared_data.types.Types.{CreateSalesChannel, SalesChannelCalculatedState, Sale, SalesChannelStateOnChain, AddProducts, AddSeller, AddInventory, MoveInventory}
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.address.Address

object TypeValidators {
  def validateIfSalesChannelAlreadyExists(state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], channelId: String): DataApplicationValidationType =
    SalesChannelAlreadyExists.whenA(state.calculated.channels.contains(channelId))

  def validateIfProductAlreadyExists(state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], newProduct: AddProducts): DataApplicationValidationType =
     ProductAlreadyExists.whenA(state.calculated.channels.get(newProduct.channelId).exists { channel =>
        newProduct.products.exists { case (productName, _) =>
        channel.products.contains(productName)}})

  def validateIfSellerAlreadyExists(state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], newSeller: AddSeller): DataApplicationValidationType =
     SellerAlreadyExists.whenA(state.calculated.channels.get(newSeller.channelId).exists(_.sellers.contains(newSeller.seller)))      

  def validateIfSalesChannelExists(state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], channelId: String): DataApplicationValidationType =
    SalesChannelDoesNotExists.unlessA(state.calculated.channels.contains(channelId))

  def validateProvidedAddress(proofAddresses: List[Address], address: Address): DataApplicationValidationType =
    InvalidAddress.unlessA(proofAddresses.contains(address))

  def validateIfNewSale(state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], sale: Sale): DataApplicationValidationType = {
  // Check if the on-chain state contains a matching Sale update
  val isDublicateSale = state.onChain.updates.exists {
    case existingSale: Sale =>
      existingSale.channelId == sale.channelId &&
      existingSale.address == sale.address &&
      existingSale.sale == sale.sale &&
      existingSale.timestamp == sale.timestamp
    case _ => false
    }

  // Return validation result
  RepeatedSale.whenA(isDublicateSale)
  }
  
  def validateSellerInSalesChannel(state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], channelId: String, address: Address): DataApplicationValidationType = {
  // Check if the channel exists and if the address is in the sellers list
  InvalidSeller.whenA {
    !state.calculated.channels.get(channelId).exists { channel =>
      channel.sellers.contains(address)
    }
  }
}


  def validatedInventoryAdd(state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], addInventory: AddInventory): DataApplicationValidationType = {
  // Check if the on-chain state contains a matching Sale update
  val isDuplicate = state.onChain.updates.exists {
    case existingInventory: AddInventory =>
      existingInventory.channelId == addInventory.channelId &&
      existingInventory.timestamp == addInventory.timestamp &&
      existingInventory.address == addInventory.address
    case _ => false
    }

    // Return validation result
    Repeated.whenA(isDuplicate)
  }

  def validateOwnerAddres(state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], channelId: String, address: Address): DataApplicationValidationType = {
  // Check if the channel exists and if the address is in the sellers list
  InvalidOwner.whenA {
    !state.calculated.channels.get(channelId).exists { channel =>
      channel.owner == address
      }
    }
  }


  def validatedMoveInventory(state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], moveInventory: MoveInventory): DataApplicationValidationType = {
  // Check if the on-chain state contains a matching Sale update
  val isDuplicate = state.onChain.updates.exists {
    case existingInventory: MoveInventory =>
      existingInventory.channelId == moveInventory.channelId &&
      existingInventory.timestamp == moveInventory.timestamp &&
      existingInventory.address == moveInventory.address
    case _ => false
    }

    // Return validation result
    Repeated.whenA(isDuplicate)
    }

    def validatedInventory(state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], moveInventory: MoveInventory): DataApplicationValidationType = {

    MissingInventory.whenA {
    !state.calculated.channels.get(moveInventory.channelId).exists { channel =>
      channel.inventory.get(moveInventory.fromStation).exists { inventory =>
        inventory.get(moveInventory.product).exists(_ >= moveInventory.amount)
          }
        }
      } 
    }


  def validateSnapshotCreateSalesChannel(snapshotOrdinal: SnapshotOrdinal, update: CreateSalesChannel): DataApplicationValidationType =
    InvalidEndSnapshot.whenA(update.endSnapshotOrdinal < snapshotOrdinal.value.value)

  def validateSalesChannelSnapshotInterval(lastSnapshotOrdinal: SnapshotOrdinal, state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], sale: Sale): DataApplicationValidationType =
    state.calculated.channels
      .get(sale.channelId)
      .map(value => ClosedPool.whenA(value.endSnapshotOrdinal < lastSnapshotOrdinal.value.value))
      .getOrElse(SalesChannelDoesNotExists.invalid)
}

