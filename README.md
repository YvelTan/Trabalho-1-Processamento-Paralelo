# Barbearia de Hilzer — Trabalho Prático I

## Estrutura dos arquivos

| Arquivo              | Responsabilidade                                          |
|----------------------|-----------------------------------------------------------|
| `EstadoBarbearia.java` | Estado compartilhado: lock, conditions, filas, semáforo, estatísticas |
| `Cliente.java`       | Thread do cliente: entrada, fila em pé, sofá, cadeira, pagamento |
| `Barbeiro.java`      | Thread do barbeiro: dorme, corta, coleta métricas         |
| `Main.java`          | Ponto de entrada: cria threads, aguarda e exibe estatísticas |

## Primitivas de sincronização

| Primitiva            | Objeto               | Papel                                      |
|----------------------|----------------------|--------------------------------------------|
| `ReentrantLock(true)` | `lock`              | Lock global justo; garante exclusão mútua  |
| `Condition`          | `podeSentarSofa`     | Clientes em pé aguardam promoção ao sofá   |
| `Condition`          | `podeSerAtendido`    | Clientes no sofá aguardam cadeira livre    |
| `Condition`          | `barbeiroLivre`      | Barbeiros dormem aguardando clientes       |
| `Semaphore(1, true)` | `caixa`             | Serializa pagamento em FIFO                |
| `Queue<Integer>`     | `filaSofa/filaEmPe`  | Filas FIFO explícitas de clientes          |
| `AtomicInteger`      | `totalNaBarbearia`   | Contagem thread-safe sem lock adicional    |
