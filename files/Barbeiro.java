/**
 * Thread que representa um barbeiro da Barbearia de Hilzer.
 *
 * Ciclo de vida (loop contínuo até ser interrompido):
 *  1. Dorme (await) enquanto todas as cadeiras estão livres.
 *  2. Ao ser acordado por um cliente, realiza o corte (sleep fora do lock).
 *  3. Coleta estatísticas de tempo ocupado e clientes atendidos.
 */
public class Barbeiro extends Thread {

    final int id;

    /** Número de clientes atendidos por este barbeiro. */
    public int clientesAtendidos = 0;

    /** Tempo total em ms que este barbeiro ficou cortando cabelo. */
    public long tempoOcupado = 0;

    public Barbeiro(int id) {
        this.id = id;
        setName("Barbeiro-" + id);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {

            // ── 1. Aguarda cliente na cadeira ────────────────────────────────
            EstadoBarbearia.lock.lock();
            try {
                // Loop while (não if) para tratar spurious wakeups
                while (EstadoBarbearia.cadeirasLivres == EstadoBarbearia.NUM_BARBEIROS) {
                    EstadoBarbearia.log("Barbeiro " + id + " está DORMINDO");
                    EstadoBarbearia.barbeiroLivre.await();
                }
                EstadoBarbearia.log("Barbeiro " + id + " começou a CORTAR");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                EstadoBarbearia.lock.unlock();
            }

            // ── 2. Realiza o corte (fora do lock — sem busy-wait) ────────────
            try {
                long inicio = System.currentTimeMillis();
                Thread.sleep((long) (Math.random() * 800 + 400));
                tempoOcupado += System.currentTimeMillis() - inicio;
                clientesAtendidos++;
                EstadoBarbearia.log("Barbeiro " + id + " TERMINOU corte [atendidos=" + clientesAtendidos + "]");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
