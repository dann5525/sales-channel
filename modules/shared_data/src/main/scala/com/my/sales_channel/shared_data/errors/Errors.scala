package com.my.sales_channel.shared_data.errors

import cats.implicits.catsSyntaxValidatedIdBinCompat0
import org.tessellation.currency.dataApplication.DataApplicationValidationError
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr

object Errors {
  type DataApplicationValidationType = DataApplicationValidationErrorOr[Unit]

  val valid: DataApplicationValidationType = ().validNec[DataApplicationValidationError]

  implicit class DataApplicationValidationTypeOps[E <: DataApplicationValidationError](err: E) {
    def invalid: DataApplicationValidationType = err.invalidNec[Unit]

    def unlessA(cond: Boolean): DataApplicationValidationType = if (cond) valid else invalid

    def whenA(cond: Boolean): DataApplicationValidationType = if (cond) invalid else valid
  }

  case object SalesChannelAlreadyExists extends DataApplicationValidationError {
    val message = "SalesChannel already exists"
  }

  case object InvalidOwner extends DataApplicationValidationError {
    val message = "Not a Owner"
  }
  

  case object SalesChannelDoesNotExists extends DataApplicationValidationError {
    val message = "SalesChannel does not exists"
  }

  case object ProductAlreadyExists extends DataApplicationValidationError{
    val message = "Product already exists"
  }
  case object  SellerAlreadyExists extends DataApplicationValidationError{
    val message = "Seller already exists"
  }

  case object InvalidAddress extends DataApplicationValidationError {
    val message = "Provided address different than proof"
  }

  case object RepeatedSale extends DataApplicationValidationError {
    val message = "This user already voted!"
  }

  case object Repeated extends DataApplicationValidationError {
    val message = "This is repeated request"
  }

  case object MissingInventory extends DataApplicationValidationError {
    val message = "Not enough Inventory of the sender"
  }

  case object InvalidSeller extends DataApplicationValidationError {
    val message = "Not a Seller"
  }
  

  case object InvalidEndSnapshot extends DataApplicationValidationError {
    val message = "Provided end snapshot ordinal lower than current snapshot!"
  }

  case object CouldNotGetLatestCurrencySnapshot extends DataApplicationValidationError {
    val message = "Could not get latest currency snapshot!"
  }

  case object CouldNotGetLatestState extends DataApplicationValidationError {
    val message = "Could not get latest state!"
  }

  case object ClosedPool extends DataApplicationValidationError {
    val message = "Pool is closed"
  }

  case object NotEnoughWalletBalance extends DataApplicationValidationError {
    val message = "Not enough wallet balance"
  }
}

