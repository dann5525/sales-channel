package com.my.sales_channel.shared_data

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.applicative._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import com.my.sales_channel.shared_data.Utils.getLastMetagraphIncrementalSnapshotInfo
import com.my.sales_channel.shared_data.combiners.Combiners.{combineCreateSalesChannel, combineSale, combineAddProduct, combineAddSeller, combineAddInventory, combineMoveInventory}
import com.my.sales_channel.shared_data.errors.Errors.{CouldNotGetLatestCurrencySnapshot, DataApplicationValidationTypeOps}
import com.my.sales_channel.shared_data.types.Types._
import com.my.sales_channel.shared_data.validations.Validations.{CreateSalesChannelValidations, CreateSalesChannelValidationsWithSignature, SaleValidations, SaleValidationsWithSignature, addProductValidation, addProductValidationsWithSignature, addSellerValidation, addSellerValidationWithSignature, addInventoryValidation, addInventoryValidationWithSignature, moveInventoryValidation, moveInventoryValidationWithSignature}
import org.slf4j.LoggerFactory
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.currency.dataApplication.{DataState, L0NodeContext, L1NodeContext}
import org.tessellation.security.SecurityProvider
import org.tessellation.security.signature.Signed

object LifecycleSharedFunctions {

  private val logger = LoggerFactory.getLogger("Data")

  def validateUpdate[F[_] : Async](update: SalesUpdate)(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] = {
    context
      .getLastCurrencySnapshot
      .map(_.get.ordinal)
      .flatMap { lastSnapshotOrdinal =>
        update match {
          case channel: CreateSalesChannel => CreateSalesChannelValidations(channel, none, lastSnapshotOrdinal.some)
          case sale: Sale =>
            getLastMetagraphIncrementalSnapshotInfo(context.asRight[L0NodeContext[F]])
              .flatMap {
                case Some(_) => SaleValidations(sale, none, lastSnapshotOrdinal.some)
                case None => CouldNotGetLatestCurrencySnapshot.invalid.pure[F]
              }
          case newProduct: AddProducts => addProductValidation(newProduct, none)
          case newSeller: AddSeller => addSellerValidation(newSeller,none)
          case newInventory: AddInventory => addInventoryValidation(newInventory, none)
          case moveInventory: MoveInventory => moveInventoryValidation(moveInventory, none)
        }
      }
  }

  def validateData[F[_] : Async](state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], updates: NonEmptyList[Signed[SalesUpdate]])(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] = {
    implicit val sp: SecurityProvider[F] = context.securityProvider
    updates.traverse { signedUpdate =>
      signedUpdate.value match {
        case channel: CreateSalesChannel =>
          CreateSalesChannelValidationsWithSignature(channel, signedUpdate.proofs, state)
        case sale: Sale =>
          getLastMetagraphIncrementalSnapshotInfo(context.asLeft[L1NodeContext[F]]).flatMap {
            case Some(_) => SaleValidationsWithSignature(sale, signedUpdate.proofs, state)
            case None => CouldNotGetLatestCurrencySnapshot.invalid.pure[F]
          }
        case newProduct: AddProducts => 
          addProductValidationsWithSignature(newProduct, signedUpdate.proofs, state)  
        case newSeller: AddSeller =>
          addSellerValidationWithSignature(newSeller, signedUpdate.proofs, state)  
        case newInventory: AddInventory =>
          addInventoryValidationWithSignature(newInventory, signedUpdate.proofs, state)  
        case moveInventory: MoveInventory =>
          moveInventoryValidationWithSignature(moveInventory, signedUpdate.proofs, state)
      }
    }.map(_.reduce)
  }

  def combine[F[_] : Async](state: DataState[SalesChannelStateOnChain, SalesChannelCalculatedState], updates: List[Signed[SalesUpdate]])(implicit context: L0NodeContext[F]): F[DataState[SalesChannelStateOnChain, SalesChannelCalculatedState]] = {
    val newStateF = DataState(SalesChannelStateOnChain(List.empty), state.calculated).pure

    if (updates.isEmpty) {
      logger.info("Snapshot without any update, updating the state to empty updates")
      newStateF
    } else {
      getLastMetagraphIncrementalSnapshotInfo(Left(context)).flatMap {
        case None =>
          logger.info("Could not get lastMetagraphIncrementalSnapshotInfo, keeping current state")
          state.pure
        case Some(_) =>
          newStateF.flatMap(newState => {
            val updatedState = updates.foldLeft(newState) { (acc, signedUpdate) => {
              val update = signedUpdate.value
              update match {
                case channel: CreateSalesChannel =>
                  combineCreateSalesChannel(channel, acc)
                case sale: Sale =>
                  combineSale(sale, acc)
                case newProduct: AddProducts =>
                  combineAddProduct(newProduct, acc)
                case newSeller: AddSeller =>
                  combineAddSeller(newSeller, acc) 
                case newInventory: AddInventory =>
                  combineAddInventory(newInventory, acc) 
                case moveInventory: MoveInventory =>
                  combineMoveInventory(moveInventory, acc) 
              }
            }
            }
            updatedState.pure
          })
      }
    }
  }
}