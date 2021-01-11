package vsys.blockchain.state.contract.voption

import com.google.common.primitives.{Ints, Longs}
import org.scalacheck.{Gen, Shrink}
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import vsys.account.ContractAccount.tokenIdFromBytes
import vsys.blockchain.block.TestBlock
import vsys.blockchain.contract.ContractGenHelper.basicContractTestGen
import vsys.blockchain.contract.{DataEntry, DataType}
import vsys.blockchain.contract.token.SystemContractGen
import vsys.blockchain.contract.voption.{VOptionContractGen, VOptionFunctionHelperGen}
import vsys.blockchain.state._
import vsys.blockchain.state.diffs._
import vsys.blockchain.transaction.contract.{RegisterContractTransaction => RC, ExecuteContractFunctionTransaction => EC}
import vsys.blockchain.transaction.{GenesisTransaction, TransactionGen, TransactionStatus}


class ExecuteVOptionValidDiffTest extends PropSpec
  with PropertyChecks
  with GeneratorDrivenPropertyChecks
  with Matchers
  with TransactionGen
  with SystemContractGen
  with VOptionContractGen
  with VOptionFunctionHelperGen {

  private implicit def noShrink[A]: Shrink[A] = Shrink(_ => Stream.empty)

  val preconditionsAndVOptionDepositBaseToken: Gen[(GenesisTransaction, RC,
    RC, RC, RC, RC, EC, EC, EC, EC)] = for {
    // Generates 4 register token contract transactions and a register contract tx for V Option
    // Also generates 4 deposit functions, proof and option token deposits the entire supply, deposit amount for base and target tokens can be selected
    (genesis, genesis2, master, user, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract,
    issueBaseToken, issueTargetToken, issueOptionToken, issueProofToken, depositBaseToken, depositTargetToken, depositOptionToken , depositProofToken, fee, ts, attach) <-
      createBaseTargetOptionProofTokenAndInitVOption(
        1000L, // baseTotalSupply
        1L, // baseUnity
        1000L, // baseIssueAmount
        1000L, // targetTotalSupply
        1L, // targetUnity
        1000L, // targetIssueAmount
        1000L, // optionTotalSupply
        1L, // optionUnity
        1000L, // proofTotalSupply
        1L, // proofUnity
        100L, // baseTokenDepositAmount
        500L) // targetTokenDepositAmount

  } yield (genesis, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract, issueBaseToken,
    issueTargetToken, depositBaseToken, depositTargetToken)

  property("vOption able to deposit") {
    forAll(preconditionsAndVOptionDepositBaseToken) { case (genesis: GenesisTransaction, registerBase: RC,
      registerTarget: RC, registerOption: RC, registerProof: RC,
      registerVOption: RC, issueBase: EC, issueTarget: EC,
      depositBase: EC, depositTarget: EC) =>
      assertDiffAndStateCorrectBlockTime(Seq(TestBlock.create(genesis.timestamp, Seq(genesis)),
        TestBlock.create(registerVOption.timestamp, Seq(registerBase, registerTarget, registerOption, registerProof, registerVOption, issueBase, issueTarget,
          depositBase))),
        TestBlock.createWithTxStatus(depositTarget.timestamp, Seq(depositTarget), TransactionStatus.Success)) { (blockDiff, newState) =>
        blockDiff.txsDiff.txStatus shouldBe TransactionStatus.Success

        val master = registerVOption.proofs.firstCurveProof.explicitGet().publicKey

        val (contractBaseTokenBalanceKey, contractTargetTokenBalanceKey, _, _) = getOptionContractTokenBalanceKeys(registerBase.contractId.bytes.arr,
          registerTarget.contractId.bytes.arr, registerOption.contractId.bytes.arr,
          registerProof.contractId.bytes.arr, registerVOption.contractId.bytes.arr)

        val (masterBaseTokenBalanceKey, masterTargetTokenBalanceKey, _, _) = getOptionUserTokenBalanceKeys(registerBase.contractId.bytes.arr,
          registerTarget.contractId.bytes.arr, registerOption.contractId.bytes.arr,
          registerProof.contractId.bytes.arr, master)

        newState.tokenAccountBalance(masterBaseTokenBalanceKey) shouldBe 900L
        newState.tokenAccountBalance(contractBaseTokenBalanceKey) shouldBe 100L

        newState.tokenAccountBalance(masterTargetTokenBalanceKey) shouldBe 500L
        newState.tokenAccountBalance(contractTargetTokenBalanceKey) shouldBe 500L
      }
    }
  }

  val preconditionsAndVoptionWithdrawBaseToken: Gen[(GenesisTransaction, RC,
    RC, RC, RC, RC, EC, EC, EC)] = for {
    (genesis, _, master, _, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract,
    issueBaseToken, _, _, _, depositBaseToken, _, _ , _, fee, ts, _) <-
      createBaseTargetOptionProofTokenAndInitVOption(
        1000L, // baseTotalSupply
        1L, // baseUnity
        1000L, // baseIssueAmount
        1000L, // targetTotalSupply
        1L, // targetUnity
        1000L, // targetIssueAmount
        1000L, // optionTotalSupply
        1L, // optionUnity
        1000L, // proofTotalSupply
        1L, // proofUnity
        1000L, // baseTokenDepositAmount
        1000L) // targetTokenDepositAmount

    withdrawBaseToken <- withdrawToken(master, regBaseTokenContract.contractId, regVOptionContract.contractId.bytes.arr, master.toAddress.bytes.arr, 100L, fee, ts + 13)

  } yield (genesis, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract, issueBaseToken, depositBaseToken,
    withdrawBaseToken)

  property("vOption able to withdraw") {
    forAll(preconditionsAndVoptionWithdrawBaseToken) { case (genesis: GenesisTransaction, registerBase: RC,
    registerTarget: RC, registerOption: RC, registerProof: RC,
    registerVOption: RC, issueBase: EC, depositBase: EC,
    withdrawBase: EC) =>
      assertDiffAndStateCorrectBlockTime(Seq(TestBlock.create(genesis.timestamp, Seq(genesis)),
        TestBlock.create(registerVOption.timestamp, Seq(registerBase, registerTarget, registerOption, registerProof, registerVOption, issueBase, depositBase))),
        TestBlock.createWithTxStatus(withdrawBase.timestamp, Seq(withdrawBase), TransactionStatus.Success)) { (blockDiff, newState) =>
        blockDiff.txsDiff.txStatus shouldBe TransactionStatus.Success

        val master = registerVOption.proofs.firstCurveProof.explicitGet().publicKey

        val (contractBaseTokenBalanceKey, _, _, _) = getOptionContractTokenBalanceKeys(registerBase.contractId.bytes.arr,
          registerTarget.contractId.bytes.arr, registerOption.contractId.bytes.arr,
          registerProof.contractId.bytes.arr, registerVOption.contractId.bytes.arr)

        val (masterBaseTokenBalanceKey, _, _, _) = getOptionUserTokenBalanceKeys(registerBase.contractId.bytes.arr,
          registerTarget.contractId.bytes.arr, registerOption.contractId.bytes.arr,
          registerProof.contractId.bytes.arr, master)

        newState.tokenAccountBalance(masterBaseTokenBalanceKey) shouldBe 100L
        newState.tokenAccountBalance(contractBaseTokenBalanceKey) shouldBe 900L

      }
    }
  }

  val preconditionsAndVOptionSupersedeActivate: Gen[(GenesisTransaction, GenesisTransaction, RC,
    RC, RC, RC, RC, EC, EC, EC, EC, EC, EC, EC, EC, EC, EC)] = for {

    (master, ts, fee) <- basicContractTestGen()

    genesis <- genesisVOptionGen(master, ts)
    user <- accountGen
    genesis2 <- genesisVOptionGen(user, ts)
    vOptionContract <- vOptionContractGen()

    // register base token
    regBaseTokenContract <- registerToken(user, 1000L, 1L, "init", fee + 10000000000L, ts)
    baseTokenContractId = regBaseTokenContract.contractId
    baseTokenId = tokenIdFromBytes(baseTokenContractId.bytes.arr, Ints.toByteArray(0)).explicitGet()
    // register target token
    regTargetTokenContract <- registerToken(user, 1000L, 1L, "init", fee + 10000000000L, ts + 1)
    targetTokenContractId = regTargetTokenContract.contractId
    targetTokenId = tokenIdFromBytes(targetTokenContractId.bytes.arr, Ints.toByteArray(0)).explicitGet()
    // register option token
    regOptionTokenContract <- registerToken(user, 1000L, 1L, "init", fee + 10000000000L, ts + 2)
    optionTokenContractId = regOptionTokenContract.contractId
    optionTokenId = tokenIdFromBytes(optionTokenContractId.bytes.arr, Ints.toByteArray(0)).explicitGet()
    // register proof token
    regProofTokenContract <- registerToken(user, 1000L, 1L, "init", fee + 10000000000L, ts + 3)
    proofTokenContractId = regProofTokenContract.contractId
    proofTokenId = tokenIdFromBytes(proofTokenContractId.bytes.arr, Ints.toByteArray(0)).explicitGet()

    // register VSwap contract
    description <- validDescStringGen
    initVOptionDataStack: Seq[DataEntry] <- initVOptionDataStackGen(baseTokenId.arr, targetTokenId.arr, optionTokenId.arr, proofTokenId.arr, ts + 100, ts + 200)
    regVOptionContract <- registerVOptionGen(master, vOptionContract, initVOptionDataStack, description, fee + 10000000000L, ts + 4)
    vOptionContractId = regVOptionContract.contractId

    // issue base token
    attach <- genBoundedString(2, EC.MaxDescriptionSize)
    issueBaseToken <- issueToken(user, baseTokenContractId, 1000L, fee, ts + 5)
    // issue target token
    issueTargetToken <- issueToken(user, targetTokenContractId, 1000L, fee, ts + 6)
    // issue option token, issue the entire supply of option tokens
    issueOptionToken <- issueToken(user, optionTokenContractId, 1000L, fee, ts + 7)
    // issue proof token, issue the entire supply of proof tokens
    issueProofToken <- issueToken(user, proofTokenContractId, 1000L, fee, ts + 8)


    depositBaseToken <- depositToken(user, baseTokenContractId, user.toAddress.bytes.arr, vOptionContractId.bytes.arr, 1000L, fee + 10000000000L, ts + 9)
    depositTargetToken <- depositToken(user, targetTokenContractId, user.toAddress.bytes.arr, vOptionContractId.bytes.arr, 1000L, fee + 10000000000L, ts + 10)
    depositOptionToken <- depositToken(user, optionTokenContractId, user.toAddress.bytes.arr, vOptionContractId.bytes.arr, 1000L, fee + 10000000000L, ts + 11)
    depositProofToken <- depositToken(user, proofTokenContractId, user.toAddress.bytes.arr, vOptionContractId.bytes.arr, 1000L, fee + 10000000000L, ts + 12)

    supersedeOption <- supersedeVOptionGen(master, regVOptionContract.contractId, user.toAddress, attach, fee, ts + 13)
    activateOption <- activateVOptionGen(user, regVOptionContract.contractId, 1000L, 10L, 10L, attach, fee, ts + 14)
  } yield (genesis, genesis2, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract, issueBaseToken, issueTargetToken,
    issueOptionToken, issueProofToken, depositBaseToken, depositTargetToken, depositOptionToken, depositProofToken, supersedeOption, activateOption)

  property("vOption able to supersede and activate") {
    forAll(preconditionsAndVOptionSupersedeActivate) { case (genesis: GenesisTransaction, genesis2: GenesisTransaction, registerBase: RC, registerTarget: RC,
      registerOption: RC, registerProof: RC, registerVOption: RC, issueBase: EC,
      issueTarget: EC, issueOption: EC, issueProof: EC, depositBase: EC,
      depositTarget: EC, depositOption: EC, depositProof: EC,
      supersede: EC, activate: EC) =>
      assertDiffAndStateCorrectBlockTime(Seq(TestBlock.create(genesis.timestamp, Seq(genesis, genesis2)),
        TestBlock.create(registerVOption.timestamp, Seq(registerBase, registerTarget, registerOption, registerProof, registerVOption, issueBase, issueTarget,
          issueOption, issueProof, depositBase, depositTarget, depositOption, depositProof))),
        TestBlock.createWithTxStatus(activate.timestamp, Seq(supersede, activate), TransactionStatus.Success)) { (blockDiff, newState) =>
        blockDiff.txsDiff.txStatus shouldBe TransactionStatus.Success

        val user = registerBase.proofs.firstCurveProof.explicitGet().publicKey
        val vOptionContractId = registerVOption.contractId.bytes.arr

        val (optionStatusKey, maxIssueNumKey, reservedOptionKey,
          reservedProofKey, priceKey, priceUnitKey, tokenLockedKey, tokenCollectedKey) = getOptionContractStateVarKeys(vOptionContractId)

        val (userStateMapBaseTokenBalanceKey, userStateMapTargetTokenBalanceKey,
        userStateMapOptionTokenBalanceKey, userStateMapProofTokenBalanceKey) = getOptionContractStateMapKeys(vOptionContractId, user)

        newState.contractInfo(optionStatusKey) shouldBe Some(DataEntry(Array(1.toByte), DataType.Boolean))
        newState.contractInfo(maxIssueNumKey) shouldBe Some(DataEntry(Longs.toByteArray(1000L), DataType.Amount))
        newState.contractNumInfo(reservedOptionKey) shouldBe 1000L
        newState.contractNumInfo(reservedProofKey) shouldBe 1000L
        newState.contractInfo(priceKey) shouldBe Some(DataEntry(Longs.toByteArray(10L), DataType.Amount))
        newState.contractInfo(priceUnitKey) shouldBe Some(DataEntry(Longs.toByteArray(10L), DataType.Amount))
        newState.contractNumInfo(tokenLockedKey) shouldBe 0L
      }
    }
  }

  val preconditionsAndVOptionMint: Gen[(GenesisTransaction, RC,
    RC, RC, RC, RC, EC, EC, EC, EC, EC, EC, EC, EC,
    EC, EC)] = for {
    (genesis, _, master, _, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract,
    issueBaseToken, issueTargetToken, issueOptionToken, issueProofToken, depositBaseToken, depositTargetToken, depositOptionToken, depositProofToken, fee, ts, attach) <-
      createBaseTargetOptionProofTokenAndInitVOption(
        1000L,
        1L,
        1000L,
        1000L,
        1L,
        1000L,
        1000L,
        1L,
        1000L,
        1L,
        1000L,
        1000L)

    activateOption <- activateVOptionGen(master, regVOptionContract.contractId, 1000L, 10L, 10L, attach, fee, ts + 13)
    mintOption <- mintVOptionGen(master, regVOptionContract.contractId, 500L, attach, fee, ts + 14)
  } yield (genesis, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract, issueBaseToken, issueTargetToken,
    issueOptionToken, issueProofToken, depositBaseToken, depositTargetToken, depositOptionToken, depositProofToken, activateOption, mintOption)

  property("vOption able to mint") {
    forAll(preconditionsAndVOptionMint) { case (genesis: GenesisTransaction, registerBase: RC, registerTarget: RC,
    registerOption: RC, registerProof: RC, registerVOption: RC, issueBase: EC,
    issueTarget: EC, issueOption: EC, issueProof: EC, depositBase: EC,
    depositTarget: EC, depositOption: EC, depositProof: EC, activate: EC,
      mint: EC) =>
      assertDiffAndStateCorrectBlockTime(Seq(TestBlock.create(genesis.timestamp, Seq(genesis)),
        TestBlock.create(registerVOption.timestamp, Seq(registerBase, registerTarget, registerOption, registerProof, registerVOption, issueBase, issueTarget,
          issueOption, issueProof, depositBase, depositTarget, depositOption, depositProof, activate))),
        TestBlock.createWithTxStatus(mint.timestamp, Seq(mint), TransactionStatus.Success)) { (blockDiff, newState) =>
        blockDiff.txsDiff.txStatus shouldBe TransactionStatus.Success

        val user = registerBase.proofs.firstCurveProof.explicitGet().publicKey
        val vOptionContractId = registerVOption.contractId.bytes.arr

        val (optionStatusKey, maxIssueNumKey, reservedOptionKey,
        reservedProofKey, priceKey, priceUnitKey, tokenLockedKey, tokenCollectedKey) = getOptionContractStateVarKeys(vOptionContractId)

        val (userStateMapBaseTokenBalanceKey, userStateMapTargetTokenBalanceKey,
        userStateMapOptionTokenBalanceKey, userStateMapProofTokenBalanceKey) = getOptionContractStateMapKeys(vOptionContractId, user)

        newState.contractInfo(optionStatusKey) shouldBe Some(DataEntry(Array(1.toByte), DataType.Boolean))
        newState.contractInfo(maxIssueNumKey) shouldBe Some(DataEntry(Longs.toByteArray(1000L), DataType.Amount))
        newState.contractNumInfo(reservedOptionKey) shouldBe 500L
        newState.contractNumInfo(reservedProofKey) shouldBe 500L
        newState.contractInfo(priceKey) shouldBe Some(DataEntry(Longs.toByteArray(10L), DataType.Amount))
        newState.contractInfo(priceUnitKey) shouldBe Some(DataEntry(Longs.toByteArray(10L), DataType.Amount))
        newState.contractNumInfo(tokenLockedKey) shouldBe 500L

      }
    }
  }

  val preconditionsAndVOptionUnlock: Gen[(GenesisTransaction, RC,
    RC, RC, RC, RC, EC, EC, EC, EC, EC, EC, EC, EC, EC, EC, EC)] = for {
    (genesis, _, master, _, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract,
    issueBaseToken, issueTargetToken, issueOptionToken, issueProofToken, depositBaseToken, depositTargetToken, depositOptionToken, depositProofToken, fee, ts, attach) <-
      createBaseTargetOptionProofTokenAndInitVOption(
        1000L,
        1L,
        1000L,
        1000L,
        1L,
        1000L,
        1000L,
        1L,
        1000L,
        1L,
        1000L,
        1000L)

    activateOption <- activateVOptionGen(master, regVOptionContract.contractId, 1000L, 10L, 10L, attach, fee, ts + 13)
    mintOption <- mintVOptionGen(master, regVOptionContract.contractId, 10L, attach, fee, ts + 14)
    unlockOption <- unlockVOptionGen(master, regVOptionContract.contractId, 10L, attach, fee, ts + 15)
  } yield (genesis, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract, issueBaseToken, issueTargetToken,
    issueOptionToken, issueProofToken, depositBaseToken, depositTargetToken, depositOptionToken, depositProofToken, activateOption, mintOption, unlockOption)

  property("vOption able to unlock") {
    forAll(preconditionsAndVOptionUnlock) { case (genesis: GenesisTransaction, registerBase: RC, registerTarget: RC,
    registerOption: RC, registerProof: RC, registerVOption: RC, issueBase: EC,
    issueTarget: EC, issueOption: EC, issueProof: EC, depositBase: EC,
    depositTarget: EC, depositOption: EC, depositProof: EC, activate: EC,
    mint: EC, unlock: EC) =>
      assertDiffAndStateCorrectBlockTime(Seq(TestBlock.create(genesis.timestamp, Seq(genesis)),
        TestBlock.create(registerVOption.timestamp, Seq(registerBase, registerTarget, registerOption, registerProof, registerVOption, issueBase, issueTarget,
          issueOption, issueProof, depositBase, depositTarget, depositOption, depositProof, activate, mint))),
        TestBlock.createWithTxStatus(unlock.timestamp, Seq(unlock), TransactionStatus.Success)) { (blockDiff, newState) =>
        blockDiff.txsDiff.txStatus shouldBe TransactionStatus.Success

        val user = registerBase.proofs.firstCurveProof.explicitGet().publicKey
        val vOptionContractId = registerVOption.contractId.bytes.arr

        val (optionStatusKey, maxIssueNumKey, reservedOptionKey,
        reservedProofKey, priceKey, priceUnitKey, tokenLockedKey, tokenCollectedKey) = getOptionContractStateVarKeys(vOptionContractId)

        val (userStateMapBaseTokenBalanceKey, userStateMapTargetTokenBalanceKey,
        userStateMapOptionTokenBalanceKey, userStateMapProofTokenBalanceKey) = getOptionContractStateMapKeys(vOptionContractId, user)

        newState.contractInfo(optionStatusKey) shouldBe Some(DataEntry(Array(1.toByte), DataType.Boolean))
        newState.contractInfo(maxIssueNumKey) shouldBe Some(DataEntry(Longs.toByteArray(1000L), DataType.Amount))
        newState.contractNumInfo(reservedOptionKey) shouldBe 1000L
        newState.contractNumInfo(reservedProofKey) shouldBe 1000L
        newState.contractInfo(priceKey) shouldBe Some(DataEntry(Longs.toByteArray(10L), DataType.Amount))
        newState.contractInfo(priceUnitKey) shouldBe Some(DataEntry(Longs.toByteArray(10L), DataType.Amount))
        newState.contractNumInfo(tokenLockedKey) shouldBe 0L
      }
    }
  }

  val preconditionsAndVOptionExecute: Gen[(GenesisTransaction, RC,
    RC, RC, RC, RC, EC, EC, EC, EC, EC, EC, EC, EC, EC, EC, EC)] = for {
    (genesis, _, master, _, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract,
    issueBaseToken, issueTargetToken, issueOptionToken, issueProofToken, depositBaseToken, depositTargetToken, depositOptionToken, depositProofToken, fee, ts, attach) <-
      createBaseTargetOptionProofTokenAndInitVOption(
        1000L,
        1L,
        1000L,
        1000L,
        1L,
        1000L,
        1000L,
        1L,
        1000L,
        1L,
        1000L,
        1000L)

    activateOption <- activateVOptionGen(master, regVOptionContract.contractId, 1000L, 10L, 1L, attach, fee, ts + 13)
    mintOption <- mintVOptionGen(master, regVOptionContract.contractId, 100L, attach, fee, ts + 14)
    executeOption <- executeVOptionGen(master, regVOptionContract.contractId, 10L, attach, fee, ts + 101)
  } yield (genesis, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract, issueBaseToken, issueTargetToken,
    issueOptionToken, issueProofToken, depositBaseToken, depositTargetToken, depositOptionToken, depositProofToken, activateOption, mintOption, executeOption)

  property("vOption able to execute") {
    forAll(preconditionsAndVOptionExecute) { case (genesis: GenesisTransaction, registerBase: RC, registerTarget: RC,
    registerOption: RC, registerProof: RC, registerVOption: RC, issueBase: EC,
    issueTarget: EC, issueOption: EC, issueProof: EC, depositBase: EC,
    depositTarget: EC, depositOption: EC, depositProof: EC, activate: EC,
    mint: EC, execute: EC) =>
      assertDiffAndStateCorrectBlockTime(Seq(TestBlock.create(genesis.timestamp, Seq(genesis)),
        TestBlock.create(registerVOption.timestamp, Seq(registerBase, registerTarget, registerOption, registerProof, registerVOption, issueBase, issueTarget,
          issueOption, issueProof, depositBase, depositTarget, depositOption, depositProof, activate, mint))),
        TestBlock.createWithTxStatus(execute.timestamp, Seq(execute), TransactionStatus.Success)) { (blockDiff, newState) =>
        blockDiff.txsDiff.txStatus shouldBe TransactionStatus.Success

        val user = registerBase.proofs.firstCurveProof.explicitGet().publicKey
        val vOptionContractId = registerVOption.contractId.bytes.arr

        val (optionStatusKey, maxIssueNumKey, reservedOptionKey,
        reservedProofKey, priceKey, priceUnitKey, tokenLockedKey, tokenCollectedKey) = getOptionContractStateVarKeys(vOptionContractId)

        val (userStateMapBaseTokenBalanceKey, userStateMapTargetTokenBalanceKey,
        userStateMapOptionTokenBalanceKey, userStateMapProofTokenBalanceKey) = getOptionContractStateMapKeys(vOptionContractId, user)

        newState.contractInfo(optionStatusKey) shouldBe Some(DataEntry(Array(1.toByte), DataType.Boolean))
        newState.contractInfo(maxIssueNumKey) shouldBe Some(DataEntry(Longs.toByteArray(1000L), DataType.Amount))
        newState.contractNumInfo(reservedOptionKey) shouldBe 910L
        newState.contractNumInfo(reservedProofKey) shouldBe 900L
        newState.contractInfo(priceKey) shouldBe Some(DataEntry(Longs.toByteArray(10L), DataType.Amount))
        newState.contractInfo(priceUnitKey) shouldBe Some(DataEntry(Longs.toByteArray(1L), DataType.Amount))
        newState.contractNumInfo(tokenLockedKey) shouldBe 90L
      }
    }
  }

  val preconditionsAndVOptionCollect: Gen[(GenesisTransaction, RC,
    RC, RC, RC, RC, EC, EC, EC, EC, EC, EC, EC, EC, EC, EC, EC, EC)] = for {
    (genesis, _, master, _, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract,
    issueBaseToken, issueTargetToken, issueOptionToken, issueProofToken, depositBaseToken, depositTargetToken, depositOptionToken, depositProofToken, fee, ts, attach) <-
      createBaseTargetOptionProofTokenAndInitVOption(
        1000L,
        1L,
        1000L,
        1000L,
        1L,
        1000L,
        1000L,
        1L,
        1000L,
        1L,
        1000L,
        1000L)

    activateOption <- activateVOptionGen(master, regVOptionContract.contractId, 1000L, 10L, 1L, attach, fee, ts + 13)
    mintOption <- mintVOptionGen(master, regVOptionContract.contractId, 100L, attach, fee, ts + 14)
    executeOption <- executeVOptionGen(master, regVOptionContract.contractId, 10L, attach, fee, ts + 101)
    collectOption <- collectVOptionGen(master, regVOptionContract.contractId, 100L, attach, fee, ts + 202)

  } yield (genesis, regBaseTokenContract, regTargetTokenContract, regOptionTokenContract, regProofTokenContract, regVOptionContract, issueBaseToken, issueTargetToken,
    issueOptionToken, issueProofToken, depositBaseToken, depositTargetToken, depositOptionToken, depositProofToken, activateOption, mintOption, executeOption, collectOption)

  property("vOption able to collect") {
    forAll(preconditionsAndVOptionCollect) { case (genesis: GenesisTransaction, registerBase: RC, registerTarget: RC,
    registerOption: RC, registerProof: RC, registerVOption: RC, issueBase: EC,
    issueTarget: EC, issueOption: EC, issueProof: EC, depositBase: EC,
    depositTarget: EC, depositOption: EC, depositProof: EC, activate: EC,
    mint: EC, execute: EC, collect: EC) =>
      assertDiffAndStateCorrectBlockTime(Seq(TestBlock.create(genesis.timestamp, Seq(genesis)),
        TestBlock.create(registerVOption.timestamp, Seq(registerBase, registerTarget, registerOption, registerProof, registerVOption, issueBase, issueTarget,
          issueOption, issueProof, depositBase, depositTarget, depositOption, depositProof, activate, mint)), TestBlock.create(execute.timestamp, Seq()), // empty block since transactions look at previous block timestamp
        TestBlock.create(execute.timestamp + 1, Seq(execute))),
        TestBlock.createWithTxStatus(collect.timestamp, Seq(collect), TransactionStatus.Success)) { (blockDiff, newState) =>
        blockDiff.txsDiff.txStatus shouldBe TransactionStatus.Success

        val user = registerBase.proofs.firstCurveProof.explicitGet().publicKey
        val vOptionContractId = registerVOption.contractId.bytes.arr

        val (optionStatusKey, maxIssueNumKey, reservedOptionKey,
        reservedProofKey, priceKey, priceUnitKey, tokenLockedKey, tokenCollectedKey) = getOptionContractStateVarKeys(vOptionContractId)

        val (userStateMapBaseTokenBalanceKey, userStateMapTargetTokenBalanceKey,
        userStateMapOptionTokenBalanceKey, userStateMapProofTokenBalanceKey) = getOptionContractStateMapKeys(vOptionContractId, user)

        newState.contractInfo(optionStatusKey) shouldBe Some(DataEntry(Array(1.toByte), DataType.Boolean))
        newState.contractInfo(maxIssueNumKey) shouldBe Some(DataEntry(Longs.toByteArray(1000L), DataType.Amount))
        newState.contractNumInfo(reservedOptionKey) shouldBe 910L
        newState.contractNumInfo(reservedProofKey) shouldBe 1000L
        newState.contractInfo(priceKey) shouldBe Some(DataEntry(Longs.toByteArray(10L), DataType.Amount))
        newState.contractInfo(priceUnitKey) shouldBe Some(DataEntry(Longs.toByteArray(1L), DataType.Amount))
        newState.contractNumInfo(tokenLockedKey) shouldBe 0L
      }
    }
  }
}
