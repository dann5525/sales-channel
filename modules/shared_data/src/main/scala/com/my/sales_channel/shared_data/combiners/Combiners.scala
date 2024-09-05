package com.my.sales_channel.shared_data.combiners

import com.my.sales_channel.shared_data.serializers.Serializers
import com.my.sales_channel.shared_data.types.Types._
import monocle.Monocle.toAppliedFocusOps
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.security.hash.Hash

object Combiners {
  def combineCreateSalesChannel(CreateSalesChannel: CreateSalesChannel, state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]): DataState[SalesChannelStateOnChain, SalesChannelCalculatedState] = {
    val channelId = Hash.fromBytes(Serializers.serializeUpdate(CreateSalesChannel)).toString
    
    val newState = SalesChannel(channelId, CreateSalesChannel.name, CreateSalesChannel.owner,  CreateSalesChannel.products.toMap, List.empty, Map.empty, CreateSalesChannel.startSnapshotOrdinal, CreateSalesChannel.endSnapshotOrdinal, Map.empty)

    val newOnChain = SalesChannelStateOnChain(state.onChain.updates :+ CreateSalesChannel)
    val newCalculatedState = state.calculated.focus(_.channels).modify(_.updated(channelId, newState))

    DataState(newOnChain, newCalculatedState)
  }

 def combineSale(sale: Sale, state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]): DataState[SalesChannelStateOnChain, SalesChannelCalculatedState] = {
  val currentState = state.calculated.channels(sale.channelId)
  

  // Define the current seller's inventory
  val sellerInventory = currentState.inventory.getOrElse(sale.address, Map())

  // Calculate the updated inventory for each product in the sale
  val updatedSellerInventory = sale.sale.foldLeft(sellerInventory) {
    case (inventoryAcc, (product, amountSold)) =>
      val currentQuantity = inventoryAcc.getOrElse(product, 0L)
      val newQuantity = currentQuantity - amountSold
      inventoryAcc.updated(product, newQuantity)
  }

 

  

    // Update the channel state with the calculated values
    val updatedState = currentState
      .focus(_.sales)
      .modify(_.updated(sale.timestamp, Map(sale.address -> sale.sale.toMap)))
      .focus(_.inventory)
      .modify(_.updated(sale.address, updatedSellerInventory))

    // Prepare the new states
    val newOnChain = SalesChannelStateOnChain(state.onChain.updates :+ sale)
    val newCalculatedState = state.calculated.focus(_.channels).modify(_.updated(sale.channelId, updatedState))

    DataState(newOnChain, newCalculatedState)

    
}



  def combineAddProduct(newProduct: AddProducts, state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]) : DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]= {
    val currentState = state.calculated.channels(newProduct.channelId)
    val newProductsMap = newProduct.products.toMap
    val newState = currentState
    .focus(_.products)
    .modify { products =>
      newProductsMap.foldLeft(products) {
        case (acc, (key, value)) => acc.updated(key, value)
      }
    }

  // Update the calculated state with the new channel state
  val newCalculatedState = state.calculated.focus(_.channels).modify(_.updated(newProduct.channelId, newState))

  // Return the new DataState with the updated on-chain state
  DataState(state.onChain, newCalculatedState)
  }

  def combineAddSeller(newSeller: AddSeller, state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]) : DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]= {
    val currentState = state.calculated.channels(newSeller.channelId)
    val newState = currentState
      .focus(_.sellers)
      .modify(_ :+ newSeller.seller)

  // Update the calculated state with the new channel state
  val newCalculatedState = state.calculated.focus(_.channels).modify(_.updated(newSeller.channelId, newState))

  // Return the new DataState with the updated on-chain state
  DataState(state.onChain, newCalculatedState)
  }

  def combineAddInventory(newInventory: AddInventory, state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]): DataState[SalesChannelStateOnChain, SalesChannelCalculatedState] = {
  // Retrieve the current state
  val currentState = state.calculated.channels(newInventory.channelId)

  // Define the current product quantity for the seller
  val currentProductQuantity = currentState.inventory
    .getOrElse(newInventory.address, Map())
    .getOrElse(newInventory.product, 0L)

  // Calculate the updated product quantity by adding the new amount
  val updatedProductQuantity = currentProductQuantity + newInventory.amount

  // Update the inventory for the specific product
  val updatedState = currentState
    .focus(_.inventory)
    .modify { inventory =>
      inventory.updated(
        newInventory.address,
        inventory.getOrElse(newInventory.address, Map()).updated(
          newInventory.product,
          updatedProductQuantity
        )
      )
    }

  // Update the calculated state with the new channel state
  val newCalculatedState = state.calculated.focus(_.channels).modify(_.updated(newInventory.channelId, updatedState))

  // Update the on-chain state by adding this new inventory action to the updates
  val newOnChain = state.onChain.copy(updates = state.onChain.updates :+ newInventory)

  // Return the new DataState with the updated on-chain state
  DataState(newOnChain, newCalculatedState)
  }

  def combineMoveInventory(newInventory: MoveInventory, state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]): DataState[SalesChannelStateOnChain, SalesChannelCalculatedState] = {
  // Retrieve the current state
  val currentState = state.calculated.channels(newInventory.channelId)

  // Retrieve the current product quantities for the fromAddress and toAddress
  val currentProductQuantityFrom = currentState.inventory
    .getOrElse(newInventory.address, Map())
    .getOrElse(newInventory.product, 0L)

  val currentProductQuantityTo = currentState.inventory
    .getOrElse(newInventory.toAddress, Map())
    .getOrElse(newInventory.product, 0L)

  // Calculate the updated product quantities
  val updatedProductQuantityFrom = currentProductQuantityFrom - newInventory.amount
  val updatedProductQuantityTo = currentProductQuantityTo + newInventory.amount

  // Update the inventory for the specific product
  val updatedState = currentState
    .focus(_.inventory)
    .modify { inventory =>
      inventory.updated(
        newInventory.address,
        inventory.getOrElse(newInventory.address, Map()).updated(
          newInventory.product,
          updatedProductQuantityFrom
        )
      ).updated(
        newInventory.toAddress,
        inventory.getOrElse(newInventory.toAddress, Map()).updated(
          newInventory.product,
          updatedProductQuantityTo
        )
      )
    }

  // Update the calculated state with the new channel state
  val newCalculatedState = state.calculated.focus(_.channels).modify(_.updated(newInventory.channelId, updatedState))

  // Update the on-chain state by adding this new inventory action to the updates
  val newOnChain = state.onChain.copy(updates = state.onChain.updates :+ newInventory)

  // Return the new DataState with the updated on-chain state
  DataState(newOnChain, newCalculatedState)
  }

  
  
}

// yarn metagraph-transaction:send --seed="drift doll absurd cost upon magic plate often actor decade obscure smooth" --transaction='{"destination": "DAG118xcqyJ1pKKbxNqBuqjP9w12exFuKc2zPk4g","amount":99, "fee":0}'
