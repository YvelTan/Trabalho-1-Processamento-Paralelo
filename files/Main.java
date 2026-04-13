/**
 * Ponto de entrada da simulação da Barbearia de Hilzer.
 *
 * Cria e gerencia as threads de Barbeiro e Cliente, aguarda
 * o término de todos os clientes e exibe as estatísticas finais.
 *
 * Compile:  javac src/*.java -d out/
 * Execute:  java -cp out Main
 */
public class Main {

    private static final int NUM_CLIENTES = 40;

    public static void main(String[] args) throws InterruptedException {
        EstadoBarbearia.log("=== Barbearia de Hilzer INICIADA ===");
        long inicio = System.currentTimeMillis();

        // ── Inicia os barbeiros (threads daemon: encerram com a JVM) ─────────
        Barbeiro[] barbeiros = new Barbeiro[EstadoBarbearia.NUM_BARBEIROS];
        for (int i = 0; i < EstadoBarbearia.NUM_BARBEIROS; i++) {
            barbeiros[i] = new Barbeiro(i + 1);
            barbeiros[i].setDaemon(true);
            barbeiros[i].start();
        }

        // ── Inicia os clientes com chegadas aleatórias espaçadas ─────────────
        Cliente[] clientes = new Cliente[NUM_CLIENTES];
        for (int i = 0; i < NUM_CLIENTES; i++) {
            clientes[i] = new Cliente(i + 1);
            clientes[i].start();
            Thread.sleep((long) (Math.random() * 400 + 50)); // intervalo 50–450 ms
        }

        // ── Aguarda todos os clientes concluírem ─────────────────────────────
        for (Cliente c : clientes) {
            c.join();
        }

        long duracao = System.currentTimeMillis() - inicio;

        // ── Encerra os barbeiros graciosamente ───────────────────────────────
        for (Barbeiro b : barbeiros) b.interrupt();
        for (Barbeiro b : barbeiros) b.join(500);

        // ── Exibe estatísticas ───────────────────────────────────────────────
        System.out.println();
        System.out.println("=== ESTATÍSTICAS ===");
        System.out.println("Clientes gerados   : " + NUM_CLIENTES);
        System.out.println("Clientes atendidos : " + EstadoBarbearia.clientesAtendidos.get());
        System.out.println("Clientes rejeitados: " + EstadoBarbearia.clientesRejeitados.get());
        System.out.printf("Tempo médio total  : %.0f ms%n",
                EstadoBarbearia.clientesAtendidos.get() > 0
                        ? (double) EstadoBarbearia.somaTotalEspera / EstadoBarbearia.clientesAtendidos.get()
                        : 0);
        System.out.printf("Tempo médio sofá   : %.0f ms%n",
                EstadoBarbearia.contSofa > 0
                        ? (double) EstadoBarbearia.somaEsperaSofa / EstadoBarbearia.contSofa
                        : 0);
        System.out.printf("Tempo médio em pé  : %.0f ms%n",
                EstadoBarbearia.contEmPe > 0
                        ? (double) EstadoBarbearia.somaEsperaEmPe / EstadoBarbearia.contEmPe
                        : 0);
        System.out.println("Duração total      : " + duracao + " ms");

        System.out.println();
        System.out.println("-- Utilização dos Barbeiros --");
        for (Barbeiro b : barbeiros) {
            double util = duracao > 0 ? 100.0 * b.tempoOcupado / duracao : 0;
            System.out.printf("Barbeiro %d: %d clientes atendidos, %.1f%% utilização%n",
                    b.id, b.clientesAtendidos, util);
        }
    }
}
