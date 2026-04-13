import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Estado compartilhado da Barbearia de Hilzer.
 *
 * Centraliza todas as variáveis, primitivas de sincronização e
 * estatísticas acessadas pelas threads Cliente e Barbeiro.
 *
 * Regra de acesso: toda leitura/escrita aos campos não-atômicos
 * deve ocorrer com o {@link #lock} adquirido.
 */
public class EstadoBarbearia {

    // ── Capacidades ──────────────────────────────────────────────────────────
    public static final int MAX_CLIENTES    = 20;
    public static final int CAPACIDADE_SOFA = 4;
    public static final int NUM_BARBEIROS   = 3;

    // ── Lock global (fair=true evita starvation) ─────────────────────────────
    public static final ReentrantLock lock = new ReentrantLock(true);

    // ── Conditions (todas derivadas do mesmo lock) ───────────────────────────
    /** Clientes em pé aguardam aqui até serem promovidos ao sofá. */
    public static final Condition podeSentarSofa  = lock.newCondition();

    /** Clientes no sofá aguardam aqui até serem o primeiro E haver cadeira livre. */
    public static final Condition podeSerAtendido = lock.newCondition();

    /** Barbeiros dormem aqui enquanto não há cliente em cadeira. */
    public static final Condition barbeiroLivre   = lock.newCondition();

    // ── Filas FIFO explícitas ─────────────────────────────────────────────────
    /** IDs dos clientes no sofá em ordem de chegada. */
    public static final Queue<Integer> filaSofa = new LinkedList<>();

    /** IDs dos clientes aguardando em pé em ordem de chegada. */
    public static final Queue<Integer> filaEmPe = new LinkedList<>();

    // ── Contadores ────────────────────────────────────────────────────────────
    /** Número total de clientes atualmente dentro da barbearia. */
    public static final AtomicInteger totalNaBarbearia = new AtomicInteger(0);

    /** Número de cadeiras de barbeiro livres (protegido pelo lock). */
    public static int cadeirasLivres = NUM_BARBEIROS;

    // ── Caixa (pagamento serializado) ─────────────────────────────────────────
    /** Semáforo binário justo: garante que apenas um cliente paga por vez, em FIFO. */
    public static final Semaphore caixa = new Semaphore(1, true);

    // ── Estatísticas (protegidas pelo lock, exceto AtomicIntegers) ───────────
    public static final AtomicInteger clientesAtendidos  = new AtomicInteger(0);
    public static final AtomicInteger clientesRejeitados = new AtomicInteger(0);

    public static long somaTotalEspera = 0;
    public static long somaEsperaSofa  = 0;
    public static long somaEsperaEmPe  = 0;
    public static int  contSofa        = 0;
    public static int  contEmPe        = 0;

    // ── Utilitário de log ─────────────────────────────────────────────────────
    public static void log(String msg) {
        System.out.printf("[%5dms] %s%n", System.currentTimeMillis() % 100_000, msg);
    }

    // Classe utilitária — não instanciável
    private EstadoBarbearia() {}
}
