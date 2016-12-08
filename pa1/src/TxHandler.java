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
        final Set<Transaction> acceptedTransactions = new HashSet<>();
        final Map<byte[], Transaction> hashToTx = Stream.of(possibleTxs)
                .collect(Collectors.toMap(Transaction::getHash, Function.identity()));
        Stream.of(possibleTxs).forEach(t -> {
            if (this.isValidTx(t)) {
                acceptedTransactions.add(t);
                t.getInputs().stream().forEach(i -> pool.removeUTXO(new UTXO(i.prevTxHash, i.outputIndex)));
            }
        });
        return acceptedTransactions.toArray(new Transaction[acceptedTransactions.size()]);
    }

    private Set<Transaction> handleTx(final Transaction tx, final Map<byte[], Transaction> hashToTx) {
        final List<Transaction> depTxs = tx.getInputs().stream().map(i -> hashToTx.get(i.prevTxHash))
                .filter(t -> t != null)
                .collect(Collectors.toList());
        if (depTxs.isEmpty()) {
            if (this.isValidTx(tx)) {
                final Set<Transaction> validTxs = new HashSet<>();
                validTxs.add(tx);
                return validTxs;
            } else {
                return new HashSet<>();
            }
        }
        return this.handleTx(tx, hashToTx);
    }
}
