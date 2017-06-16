import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TxHandler {

  private UTXOPool pool;

  /**
   * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
   * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
   * constructor.
   */
  public TxHandler(UTXOPool utxoPool) {
    this.pool = new UTXOPool(utxoPool);
  }

  /**
   * @return true if:
   * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
   * (2) the signatures on each input of {@code tx} are valid,
   * (3) no UTXO is claimed multiple times by {@code tx},
   * (4) all of {@code tx}s output values are non-negative, and
   * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
   *     values; and false otherwise.
   */
  public boolean isValidTx(Transaction tx) {

    // (1) all outputs claimed by {@code tx} are in the current UTXO pool,
    if (!tx.getInputs().stream()
           .map(input -> new UTXO(input.prevTxHash, input.outputIndex))
           .allMatch(utxo -> this.pool.contains(utxo))) {
      return false;
    }

    // (2) the signatures on each input of {@code tx} are valid,
    if (!IntStream.range(0, tx.getInputs().size()).allMatch(i -> {
          Transaction.Output output = this.pool.getTxOutput(new UTXO(tx.getInput(i).prevTxHash, tx.getInput(i).outputIndex));
          return Crypto.verifySignature(output.address, tx.getRawDataToSign(i), tx.getInput(i).signature);
        })) {
      return false;
    }

    // (3) no UTXO is claimed multiple times by {@code tx},
    if (tx.getInputs().stream().map(input -> new UTXO(input.prevTxHash, input.outputIndex)).distinct().count() !=
        tx.getInputs().stream().count()) {
      return false;
    }

    // (4) all of {@code tx}s output values are non-negative, and
    if (!tx.getOutputs().stream().allMatch(output -> output.value >= 0.0)) {
      return false;
    }

    // (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
    return (
      tx.getInputs().stream().mapToDouble(input -> this.pool.getTxOutput(new UTXO(input.prevTxHash, input.outputIndex)).value).sum() >=
      tx.getOutputs().stream().mapToDouble(output -> output.value).sum()
    );
  }

  /**
   * Handles each epoch by receiving an unordered array of proposed transactions, checking each
   * transaction for correctness, returning a mutually valid array of accepted transactions, and
   * updating the current UTXO pool as appropriate.
   */
  public Transaction[] handleTxs(Transaction[] transactions) {
    return Stream.of(transactions).filter(tx -> {
      if (isValidTx(tx)) {
        IntStream.range(0, tx.getInputs().size()).forEach(i -> this.pool.removeUTXO(new UTXO(tx.getInput(i).prevTxHash, tx.getInput(i).outputIndex)));
        IntStream.range(0, tx.getOutputs().size()).forEach(i -> this.pool.addUTXO(new UTXO(tx.getHash(), i), tx.getOutput(i)));
        return true;
      } else {
        return false;
      }
    }).toArray(Transaction[]::new);
  }
}
