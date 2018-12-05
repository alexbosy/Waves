package com.wavesplatform.api.http

import cats.implicits._
import com.wavesplatform.account.{Address, PublicKeyAccount}
import com.wavesplatform.lang.v1.FunctionHeader
import com.wavesplatform.lang.v1.compiler.Terms._
import com.wavesplatform.state.{BinaryDataEntry, BooleanDataEntry, DataEntry, IntegerDataEntry, StringDataEntry}
import com.wavesplatform.transaction.smart.ContractInvocationTransaction
import com.wavesplatform.transaction.{DataTransaction, Proofs, ValidationError}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import play.api.libs.json.Json
import scodec.bits.ByteVector

import scala.annotation.meta.field

object ContractInvocationRequest {
  implicit val unsignedContractInvocationRequestReads = Json.reads[ContractInvocationRequest]
  implicit val unsignedContractInvocationRequestReads = Json.reads[SignedContractInvocationRequest]

  def buildFunctionCall(f: String, args: List[DataEntry[_]]): FUNCTION_CALL =
    FUNCTION_CALL(
      FunctionHeader.User(f),
      args.map {
        case BooleanDataEntry(_, b) => CONST_BOOLEAN(b)
        case StringDataEntry(_, b)  => CONST_STRING(b)
        case IntegerDataEntry(_, b) => CONST_LONG(b)
        case BinaryDataEntry(_, b)  => CONST_BYTEVECTOR(ByteVector(b.arr))
      }
    )
}

case class ContractInvocationRequest(
    @(ApiModelProperty @field)(required = true, dataType = "java.lang.Integer", value = "1", allowableValues = "1") version: Byte,
    sender: String,
    @(ApiModelProperty @field)(required = true) args: List[DataEntry[_]],
    @(ApiModelProperty @field)(required = true, value = "1000") fee: Long,
    @(ApiModelProperty @field)(required = true, value = "foo") function: String,
    @(ApiModelProperty @field)(dataType = "string", example = "3Mciuup51AxRrpSz7XhutnQYTkNT9691HAk") contractAddress: String,
    timestamp: Option[Long] = None)

@ApiModel(value = "Signed Data transaction")
case class SignedContractInvocationRequest(
    @(ApiModelProperty @field)(required = true, dataType = "java.lang.Integer", value = "1", allowableValues = "1")
    version: Byte,
    @(ApiModelProperty @field)(value = "Base58 encoded sender public key", required = true) senderPublicKey: String,
    @(ApiModelProperty @field)(value = "Data to put into blockchain", required = true) args: List[DataEntry[_]],
    @(ApiModelProperty @field)(required = true) fee: Long,
    @(ApiModelProperty @field)(required = true, value = "foo") function: String,
    @(ApiModelProperty @field)(dataType = "string", example = "3Mciuup51AxRrpSz7XhutnQYTkNT9691HAk") contractAddress: String,
    @(ApiModelProperty @field)(required = true, value = "1000") timestamp: Long,
    @(ApiModelProperty @field)(required = true) proofs: List[String])
    extends BroadcastRequest {
  def toTx: Either[ValidationError, DataTransaction] =
    for {
      _sender          <- PublicKeyAccount.fromBase58String(senderPublicKey)
      _contractAddress <- Address.fromString(contractAddress)
      _proofBytes      <- proofs.traverse(s => parseBase58(s, "invalid proof", Proofs.MaxProofStringSize))
      _proofs          <- Proofs.create(_proofBytes)
      t <- ContractInvocationTransaction.create(
        version,
        _sender,
        _contractAddress,
        ContractInvocationRequest.buildFunctionCall(function, args),
        fee,
        timestamp,
        _proofs
      )
    } yield t
}