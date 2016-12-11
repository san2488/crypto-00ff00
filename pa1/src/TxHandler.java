import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TxHandler {

    private final UTXOPool pool;

    public TxHandler(UTXOPool utxoPool) {
        pool = new UTXOPool(utxoPool);
    }


    public boolean isValidTx(Transaction tx) {
        final List<UTXO> claimedUtxos = tx.getInputs().stream().map(i -> new UTXO(i.prevTxHash, i.outputIndex))
                .collect(Collectors.toList());
        return tx.getInputs().stream().allMatch(i -> pool.contains(new UTXO(i.prevTxHash, i.outputIndex)))
                &&
        IntStream.range(0, tx.numInputs()).boxed()
                .allMatch(i -> Crypto.verifySignature(
                  pool.getTxOutput(new UTXO(tx.getInput(i).prevTxHash, tx.getInput(i).outputIndex)).address,
                    tx.getRawDataToSign(i),
                    tx.getInput(i).signature
                ))
                &&
        pool.getAllUTXO().stream().noneMatch(u -> Collections.frequency(claimedUtxos, u) > 1)
                &&
        tx.getOutputs().stream().noneMatch(o -> o.value < 0)
                &&
        tx.getInputs().stream().map(i -> pool.getTxOutput(new UTXO(i.prevTxHash, i.outputIndex)))
                .mapToDouble(o -> o.value).sum() >= tx.getOutputs().stream().mapToDouble(o -> o.value).sum();
    }

    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        final Map<byte[], Transaction> hashToPossibleTx = Stream.of(possibleTxs)
                .collect(Collectors.toMap(Transaction::getHash, Function.identity()));
        return Stream.of(possibleTxs)
                .map(tx -> handleTx(tx, hashToPossibleTx))
                .distinct()
                .toArray(size -> new Transaction[possibleTxs.length]);
    }

    private Set<Transaction> handleTx(final Transaction tx, final Map<byte[], Transaction> hashToPossibleTx) {
        final Set<Transaction> acceptedTransactions = new HashSet<>();
        return handleTx(tx, hashToPossibleTx, acceptedTransactions) ? acceptedTransactions : new HashSet<>();
    }
    private boolean handleTx(final Transaction tx, final Map<byte[], Transaction> hashToPossibleTx,
                             final Set<Transaction> validTransactions) {
        final List<Transaction> depTxs = tx.getInputs().stream().map(i -> hashToPossibleTx.get(i.prevTxHash))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        final boolean isTransactionChainValid
                = isValidTx(tx) && depTxs.stream().allMatch(t -> this.handleTx(t, hashToPossibleTx, validTransactions));
        if (isTransactionChainValid) {
            validTransactions.addAll(depTxs);
            validTransactions.add(tx);
            return true;
        } else {
            return false;
        }
    }
}
