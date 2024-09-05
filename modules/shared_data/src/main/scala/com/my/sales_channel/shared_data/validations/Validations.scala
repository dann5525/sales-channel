package com.my.sales_channel.shared_data.validations

import cats.data.NonEmptySet
import cats.effect.Async
import cats.syntax.all._
import com.my.sales_channel.shared_data.errors.Errors.valid
import com.my.sales_channel.shared_data.serializers.Serializers
import com.my.sales_channel.shared_data.types.Types.{CreateSalesChannel, SalesChannelCalculatedState, Sale, SalesChannelStateOnChain, AddProducts, AddSeller, AddInventory, MoveInventory}
import com.my.sales_channel.shared_data.validations.TypeValidators._
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.address.Address
import org.tessellation.security.SecurityProvider
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.signature.SignatureProof

object Validations {
  def CreateSalesChannelValidations[F[_] : Async](update: CreateSalesChannel, maybeState: Option[DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]], lastSnapshotOrdinal: Option[SnapshotOrdinal]): F[DataApplicationValidationErrorOr[Unit]] = Async[F].delay {
    val validatedCreateSalesChannelSnapshot = lastSnapshotOrdinal match {
      case Some(value) => validateSnapshotCreateSalesChannel(value, update)
      case None => valid
    }

    maybeState match {
      case Some(state) =>
        val channelId = Hash.fromBytes(Serializers.serializeUpdate(update))
        val validatedSalesChannel = validateIfSalesChannelAlreadyExists(state, channelId.toString)
        validatedCreateSalesChannelSnapshot.productR(validatedSalesChannel)
      case None => validatedCreateSalesChannelSnapshot
    }
  }

  def addProductValidation[F[_] : Async](update: AddProducts, maybeState: Option[DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]]): F[DataApplicationValidationErrorOr[Unit]] = Async[F].delay {
    
    maybeState match {
      case Some(state) =>
        val product = validateIfProductAlreadyExists(state, update)
        val owner = validateOwnerAddres(state, update.channelId, update.address)

        product.productR(owner)
      
      case None => valid
    }
    
  }

  def addSellerValidation[F[_] : Async](update: AddSeller, maybeState: Option[DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]]): F[DataApplicationValidationErrorOr[Unit]] = Async[F].delay {
    
    maybeState match {
      case Some(state) =>
        val seller = validateIfSellerAlreadyExists(state, update)
        val owner = validateOwnerAddres(state, update.channelId, update.address)

        seller.productR(owner)
      case None => valid
    }
    
  }

  def addInventoryValidation[F[_] : Async](update: AddInventory, maybeState: Option[DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]]): F[DataApplicationValidationErrorOr[Unit]] = Async[F].delay {
    
    maybeState match {
      case Some(state) =>
        val inventory = validatedInventoryAdd(state, update)
        val owener = validateOwnerAddres(state, update.channelId, update.address)

        inventory.productR(owener)
      case None => valid
    }
    
  }

  def moveInventoryValidation[F[_] : Async](update: MoveInventory, maybeState: Option[DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]]): F[DataApplicationValidationErrorOr[Unit]] = Async[F].delay {
    
    maybeState match {
      case Some(state) =>
        val move = validatedMoveInventory(state, update)
        val inventory = validatedInventory(state, update)

        move.productR(inventory)

      case None => valid
    }
    
  }

  def SaleValidations[F[_] : Async](update: Sale, maybeState: Option[DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]], lastSnapshotOrdinal: Option[SnapshotOrdinal]): F[DataApplicationValidationErrorOr[Unit]] = Async[F].delay {
  // Validate the wallet balance
  
  maybeState match {
    case Some(state) =>
      // Validate the snapshot interval if provided
      val validatedSnapshotInterval = lastSnapshotOrdinal match {
        case Some(value) => validateSalesChannelSnapshotInterval(value, state, update)
        case None => valid
      }

      // Validate the existence of the channel
      val validatedSalesChannel = validateIfSalesChannelExists(state, update.channelId)


      // Validate that the user hasn't already voted
      val validateSale = validateIfNewSale(state, update)

      // Validate that the address is part of the sellers list
      val validateSeller = validateSellerInSalesChannel(state, update.channelId, update.address)

      // Combine all validations
      validatedSnapshotInterval
        .productR(validatedSalesChannel)
        .productR(validateSale)
        .productR(validateSeller)
        

    case None => valid
    }
  }


  private def extractAddresses[F[_] : Async : SecurityProvider](proofs: NonEmptySet[SignatureProof]): F[List[Address]] = {
    proofs
      .map(_.id)
      .toList
      .traverse(_.toAddress[F])
  }

  def CreateSalesChannelValidationsWithSignature[F[_] : Async](update: CreateSalesChannel, proofs: NonEmptySet[SignatureProof], state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState])(implicit sp: SecurityProvider[F]): F[DataApplicationValidationErrorOr[Unit]] = {
    for {
      addresses <- extractAddresses(proofs)
      validatedAddress = validateProvidedAddress(addresses, update.owner)
      validatedSalesChannel <- CreateSalesChannelValidations(update, state.some, None)
    } yield validatedAddress.productR(validatedSalesChannel)
  }

  def SaleValidationsWithSignature[F[_] : Async](update: Sale, proofs: NonEmptySet[SignatureProof], state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState])(implicit sp: SecurityProvider[F]): F[DataApplicationValidationErrorOr[Unit]] = {
    for {
      addresses <- extractAddresses(proofs)
      validatedAddress = validateProvidedAddress(addresses, update.address)
      validatedSalesChannel <- SaleValidations(update, state.some, None)
    } yield validatedAddress.productR(validatedSalesChannel)
  }

  def addProductValidationsWithSignature[F[_] : Async](update: AddProducts, proofs: NonEmptySet[SignatureProof], state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState])(implicit sp: SecurityProvider[F]): F[DataApplicationValidationErrorOr[Unit]] = {
    for {
      addresses <- extractAddresses(proofs)
      validatedAddress = validateProvidedAddress(addresses, update.address)
      validatedProduct <- addProductValidation(update, state.some)
    } yield validatedAddress.productR(validatedProduct)
  }
  def addSellerValidationWithSignature[F[_] : Async](update: AddSeller, proofs: NonEmptySet[SignatureProof], state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState])(implicit sp: SecurityProvider[F]): F[DataApplicationValidationErrorOr[Unit]] = {
    for {
      addresses <- extractAddresses(proofs)
      validatedAddress = validateProvidedAddress(addresses, update.address)
      validatedSeller <- addSellerValidation(update, state.some)
    } yield validatedAddress.productR(validatedSeller)
  }

  def addInventoryValidationWithSignature[F[_] : Async](update: AddInventory, proofs: NonEmptySet[SignatureProof], state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState])(implicit sp: SecurityProvider[F]): F[DataApplicationValidationErrorOr[Unit]] = {
    for {
      addresses <- extractAddresses(proofs)
      validatedAddress = validateProvidedAddress(addresses, update.address)
      validatedInventoryAdd <- addInventoryValidation(update, state.some)
    } yield validatedAddress.productR(validatedInventoryAdd)
  }

  def moveInventoryValidationWithSignature[F[_] : Async](update: MoveInventory, proofs: NonEmptySet[SignatureProof], state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState])(implicit sp: SecurityProvider[F]): F[DataApplicationValidationErrorOr[Unit]] = {
    for {
      addresses <- extractAddresses(proofs)
      validatedAddress = validateProvidedAddress(addresses, update.address)
      validatedMoveInventory <- moveInventoryValidation(update, state.some)
    } yield validatedAddress.productR(validatedMoveInventory)
  }


}

