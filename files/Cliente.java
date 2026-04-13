/**
 * Thread que representa um cliente da Barbearia de Hilzer.
 *
 * Ciclo de vida:
 *  1. Tenta entrar (verifica capacidade máxima).
 *  2. Se o sofá estiver cheio, aguarda em pé (FIFO via filaEmPe).
 *  3. Quando há vaga no sofá, senta e aguarda ser o primeiro da fila
 *     E haver cadeira de barbeiro livre.
 *  4. Vai para a cadeira; promove o próximo em pé para o sofá.
 *  5. Após o corte (simulado pelo Barbeiro), paga no caixa (serializado).
 *  6. Libera a cadeira e sai.
 */
public class Cliente extends Thread {

    private final int id;

    public Cliente(int id) {
        this.id = id;
        setName("Cliente-" + id);
    }

    @Override
    public void run() {
        long chegada = System.currentTimeMillis();

        // ── 1. Entrada: verifica capacidade ──────────────────────────────────
        EstadoBarbearia.lock.lock();
        try {
            if (EstadoBarbearia.totalNaBarbearia.get() >= EstadoBarbearia.MAX_CLIENTES) {
                EstadoBarbearia.clientesRejeitados.incrementAndGet();
                EstadoBarbearia.log("Cliente " + id + " REJEITADO (barbearia cheia)");
                return;
            }
            EstadoBarbearia.totalNaBarbearia.incrementAndGet();
            EstadoBarbearia.log("Cliente " + id + " ENTROU  [total=" + EstadoBarbearia.totalNaBarbearia + "]");

            // ── 2. Fase em pé ────────────────────────────────────────────────
            if (EstadoBarbearia.filaSofa.size() >= EstadoBarbearia.CAPACIDADE_SOFA) {
                EstadoBarbearia.filaEmPe.add(id);
                EstadoBarbearia.log("Cliente " + id + " ficou EM PÉ [fila pé=" + EstadoBarbearia.filaEmPe.size() + "]");
                long t0 = System.currentTimeMillis();

                // Aguarda até ser promovido ao sofá (FIFO garantido pela fila)
                while (!EstadoBarbearia.filaSofa.contains(id)) {
                    EstadoBarbearia.podeSentarSofa.await();
                }

                EstadoBarbearia.somaEsperaEmPe += System.currentTimeMillis() - t0;
                EstadoBarbearia.contEmPe++;
            } else {
                // Há vaga no sofá: senta diretamente
                EstadoBarbearia.filaSofa.add(id);
                EstadoBarbearia.log("Cliente " + id + " sentou no SOFÁ [sofá=" + EstadoBarbearia.filaSofa.size() + "]");
            }

            // ── 3. Fase no sofá: aguarda ser o primeiro E haver cadeira livre ─
            long t1 = System.currentTimeMillis();
            while (EstadoBarbearia.filaSofa.peek() != id || EstadoBarbearia.cadeirasLivres == 0) {
                EstadoBarbearia.podeSerAtendido.await();
            }

            // ── 4. Vai para a cadeira ────────────────────────────────────────
            EstadoBarbearia.filaSofa.poll();
            EstadoBarbearia.cadeirasLivres--;

            EstadoBarbearia.somaEsperaSofa  += System.currentTimeMillis() - t1;
            EstadoBarbearia.somaTotalEspera += System.currentTimeMillis() - chegada;
            EstadoBarbearia.contSofa++;
            EstadoBarbearia.log("Cliente " + id + " foi para a CADEIRA [cadeiras livres="
                    + EstadoBarbearia.cadeirasLivres + "]");

            // Promove o próximo em pé para o sofá
            if (!EstadoBarbearia.filaEmPe.isEmpty()) {
                int promovido = EstadoBarbearia.filaEmPe.poll();
                EstadoBarbearia.filaSofa.add(promovido);
                EstadoBarbearia.log("Cliente " + promovido + " promovido EM PÉ → SOFÁ");
                EstadoBarbearia.podeSentarSofa.signalAll();
            }

            // Acorda barbeiros e demais clientes no sofá
            EstadoBarbearia.barbeiroLivre.signal();
            EstadoBarbearia.podeSerAtendido.signalAll();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } finally {
            EstadoBarbearia.lock.unlock();
        }

        // ── 5. Pagamento (fora do lock — caixa serializado por semáforo) ─────
        try {
            EstadoBarbearia.caixa.acquire();
            EstadoBarbearia.log("Cliente " + id + " pagando no CAIXA");
            Thread.sleep((long) (Math.random() * 300 + 100));
            EstadoBarbearia.log("Cliente " + id + " pagou e SAIU");
            EstadoBarbearia.caixa.release();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // ── 6. Libera cadeira e atualiza contadores ──────────────────────────
        EstadoBarbearia.lock.lock();
        try {
            EstadoBarbearia.cadeirasLivres++;
            EstadoBarbearia.totalNaBarbearia.decrementAndGet();
            EstadoBarbearia.clientesAtendidos.incrementAndGet();
            EstadoBarbearia.podeSerAtendido.signalAll();
            EstadoBarbearia.barbeiroLivre.signal();
            EstadoBarbearia.log("Cliente " + id + " liberou cadeira [cadeiras livres="
                    + EstadoBarbearia.cadeirasLivres + ", total=" + EstadoBarbearia.totalNaBarbearia + "]");
        } finally {
            EstadoBarbearia.lock.unlock();
        }
    }
}
