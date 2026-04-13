import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Problema da Barbearia de Hilzer
 * - 3 barbeiros, 3 cadeiras de corte
 * - Sofá com 4 lugares (FIFO)
 * - Fila em pé (FIFO)
 * - Capacidade total: 20 clientes
 * - Pagamento serializado (1 caixa)
 */
public class Barbearia {

    // ── Capacidades ──────────────────────────────────────────────────────────
    static final int MAX_CLIENTES    = 20;
    static final int CAPACIDADE_SOFA = 4;
    static final int NUM_BARBEIROS   = 3;

    // ── Lock global da barbearia ─────────────────────────────────────────────
    static final ReentrantLock lock = new ReentrantLock(true); // fair lock

    // ── Conditions ───────────────────────────────────────────────────────────
    static final Condition podeSentarSofa  = lock.newCondition(); // clientes em pé esperam aqui
    static final Condition podeSerAtendido = lock.newCondition(); // clientes no sofá esperam aqui
    static final Condition barbeiroLivre   = lock.newCondition(); // barbeiros esperam clientes

    // ── Filas explícitas FIFO ────────────────────────────────────────────────
    static final Queue<Integer> filaSofa   = new LinkedList<>();  // IDs em ordem de chegada
    static final Queue<Integer> filaEmPe   = new LinkedList<>();  // IDs em ordem de chegada

    // ── Contadores compartilhados ────────────────────────────────────────────
    static final AtomicInteger totalNaBarbearia = new AtomicInteger(0);
    static int cadeirasLivres = NUM_BARBEIROS;

    // ── Semáforo do caixa (pagamento serializado) ────────────────────────────
    static final Semaphore caixa = new Semaphore(1, true); // binário e justo

    // ── Estatísticas ─────────────────────────────────────────────────────────
    static final AtomicInteger clientesAtendidos  = new AtomicInteger(0);
    static final AtomicInteger clientesRejeitados = new AtomicInteger(0);
    static long somaTotalEspera   = 0; // protegido pelo lock
    static long somaEsperaSofa    = 0;
    static long somaEsperaEmPe    = 0;
    static int  contEmPe          = 0;
    static int  contSofa          = 0;

    // ── Log ──────────────────────────────────────────────────────────────────
    static void log(String msg) {
        System.out.printf("[%5dms] %s%n",
                System.currentTimeMillis() % 100_000, msg);
    }

    // =========================================================================
    // Thread: Cliente
    // =========================================================================
    static class Cliente extends Thread {
        final int id;
        Cliente(int id) { this.id = id; setName("Cliente-" + id); }

        @Override
        public void run() {
            long chegada = System.currentTimeMillis();
            lock.lock();
            try {
                // Verifica capacidade
                if (totalNaBarbearia.get() >= MAX_CLIENTES) {
                    clientesRejeitados.incrementAndGet();
                    log("Cliente " + id + " REJEITADO (barbearia cheia)");
                    return;
                }
                totalNaBarbearia.incrementAndGet();
                log("Cliente " + id + " ENTROU  [total=" + totalNaBarbearia + "]");

                long entradaSofa;

                // ── Fase em pé ────────────────────────────────────────────
                if (filaSofa.size() >= CAPACIDADE_SOFA) {
                    filaEmPe.add(id);
                    log("Cliente " + id + " ficou EM PÉ [fila pé=" + filaEmPe.size() + "]");
                    long t0 = System.currentTimeMillis();
                    // Espera até ser promovido ao sofá
                    while (!filaSofa.contains(id)) {
                        podeSentarSofa.await();
                    }
                    long espera = System.currentTimeMillis() - t0;
                    lock.lock(); // reacquire após await (já está no bloco, mas await libera e readquire)
                    somaEsperaEmPe += espera;
                    contEmPe++;
                    entradaSofa = System.currentTimeMillis();
                } else {
                    filaSofa.add(id);
                    log("Cliente " + id + " sentou no SOFÁ [sofá=" + filaSofa.size() + "]");
                    entradaSofa = System.currentTimeMillis();
                }

                // ── Fase no sofá ──────────────────────────────────────────
                long t1 = System.currentTimeMillis();
                while (filaSofa.peek() != id || cadeirasLivres == 0) {
                    podeSerAtendido.await();
                }
                // Sou o primeiro do sofá E há cadeira livre → sentar na cadeira
                filaSofa.poll(); // remove do sofá
                cadeirasLivres--;
                long esperaSofa = System.currentTimeMillis() - t1;
                somaTotalEspera += (System.currentTimeMillis() - chegada);
                somaEsperaSofa  += esperaSofa;
                contSofa++;
                log("Cliente " + id + " foi para a CADEIRA [cadeiras livres=" + cadeirasLivres + "]");

                // Promover quem estava em pé → sofá
                if (!filaEmPe.isEmpty()) {
                    int promovido = filaEmPe.poll();
                    filaSofa.add(promovido);
                    log("Cliente " + promovido + " promovido EM PÉ → SOFÁ");
                    podeSentarSofa.signalAll();
                }
                // Acorda barbeiros e outros clientes no sofá
                barbeiroLivre.signal();
                podeSerAtendido.signalAll();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }

            // ── Fase de corte (fora do lock) ──────────────────────────────
            // O corte é simulado pela thread do Barbeiro; o cliente aguarda o semáforo do caixa
            try {
                // Pagamento serializado via semáforo binário
                caixa.acquire();
                log("Cliente " + id + " pagando no CAIXA");
                Thread.sleep((long)(Math.random() * 300 + 100));
                log("Cliente " + id + " pagou e SAIU");
                caixa.release();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            lock.lock();
            try {
                cadeirasLivres++;
                totalNaBarbearia.decrementAndGet();
                clientesAtendidos.incrementAndGet();
                podeSerAtendido.signalAll();
                barbeiroLivre.signal();
                log("Cliente " + id + " liberou cadeira [cadeiras livres=" + cadeirasLivres +
                    ", total=" + totalNaBarbearia + "]");
            } finally {
                lock.unlock();
            }
        }
    }

    // =========================================================================
    // Thread: Barbeiro
    // =========================================================================
    static class Barbeiro extends Thread {
        final int id;
        int clientesAtendidos = 0;
        long tempoOcupado = 0;

        Barbeiro(int id) { this.id = id; setName("Barbeiro-" + id); }

        @Override
        public void run() {
            long inicio = System.currentTimeMillis();
            while (!Thread.currentThread().isInterrupted()) {
                lock.lock();
                try {
                    // Dorme enquanto não há cliente na cadeira disponível
                    while (cadeirasLivres == NUM_BARBEIROS) {
                        log("Barbeiro " + id + " está DORMINDO");
                        barbeiroLivre.await();
                    }
                    log("Barbeiro " + id + " começou a CORTAR");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } finally {
                    lock.unlock();
                }

                // Simula corte (fora do lock — sem busy-wait)
                try {
                    long t = System.currentTimeMillis();
                    Thread.sleep((long)(Math.random() * 800 + 400));
                    tempoOcupado += System.currentTimeMillis() - t;
                    clientesAtendidos++;
                    log("Barbeiro " + id + " TERMINOU corte [atendidos=" + clientesAtendidos + "]");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    // =========================================================================
    // main
    // =========================================================================
    public static void main(String[] args) throws InterruptedException {
        final int NUM_CLIENTES = 40;

        log("=== Barbearia de Hilzer INICIADA ===");
        long inicio = System.currentTimeMillis();

        // Cria e inicia barbeiros
        Barbeiro[] barbeiros = new Barbeiro[NUM_BARBEIROS];
        for (int i = 0; i < NUM_BARBEIROS; i++) {
            barbeiros[i] = new Barbeiro(i + 1);
            barbeiros[i].setDaemon(true);
            barbeiros[i].start();
        }

        // Cria e inicia clientes com chegadas espaçadas aleatoriamente
        Cliente[] clientes = new Cliente[NUM_CLIENTES];
        for (int i = 0; i < NUM_CLIENTES; i++) {
            clientes[i] = new Cliente(i + 1);
            clientes[i].start();
            Thread.sleep((long)(Math.random() * 400 + 50));
        }

        // Aguarda todos os clientes terminarem
        for (Cliente c : clientes) c.join();

        long duracao = System.currentTimeMillis() - inicio;

        // Encerra barbeiros
        for (Barbeiro b : barbeiros) b.interrupt();
        for (Barbeiro b : barbeiros) b.join(500);

        // ── Estatísticas ──────────────────────────────────────────────────
        log("\n=== ESTATÍSTICAS ===");
        System.out.println("Clientes gerados   : " + NUM_CLIENTES);
        System.out.println("Clientes atendidos : " + clientesAtendidos.get());
        System.out.println("Clientes rejeitados: " + clientesRejeitados.get());
        System.out.printf ("Tempo médio total  : %.0f ms%n",
                clientesAtendidos.get() > 0 ? (double) somaTotalEspera / clientesAtendidos.get() : 0);
        System.out.printf ("Tempo médio sofá   : %.0f ms%n",
                contSofa > 0 ? (double) somaEsperaSofa / contSofa : 0);
        System.out.printf ("Tempo médio em pé  : %.0f ms%n",
                contEmPe > 0 ? (double) somaEsperaEmPe / contEmPe : 0);
        System.out.println("Duração total      : " + duracao + " ms");

        System.out.println("\n-- Utilização dos Barbeiros --");
        for (Barbeiro b : barbeiros) {
            double util = duracao > 0 ? 100.0 * b.tempoOcupado / duracao : 0;
            System.out.printf("Barbeiro %d: %d clientes, %.1f%% utilização%n",
                    b.id, b.clientesAtendidos, util);
        }
    }
}
