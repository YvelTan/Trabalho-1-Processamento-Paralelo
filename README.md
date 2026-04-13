# Barbearia de Hilzer — Trabalho Prático I

## Compilar e Executar

### Requisitos
- Java 11+ (JDK — inclui javac)

### Compilar
```bash
mkdir -p out
javac src/Barbearia.java -d out/
```

### Executar
```bash
java -cp out Barbearia
```

### Saída esperada
```
[  123ms] === Barbearia de Hilzer INICIADA ===
[  125ms] Barbeiro 1 está DORMINDO
[  125ms] Barbeiro 2 está DORMINDO
[  125ms] Barbeiro 3 está DORMINDO
[  200ms] Cliente 1 ENTROU  [total=1]
[  200ms] Cliente 1 sentou no SOFÁ [sofá=1]
...
=== ESTATÍSTICAS ===
Clientes gerados   : 40
Clientes atendidos : 35
Clientes rejeitados: 5
Tempo médio total  : 1823 ms
Tempo médio sofá   : 620 ms
Tempo médio em pé  : 940 ms
Duração total      : 12450 ms

-- Utilização dos Barbeiros --
Barbeiro 1: 12 clientes, 78.3% utilização
Barbeiro 2: 11 clientes, 72.1% utilização
Barbeiro 3: 12 clientes, 75.9% utilização
```

## Estrutura do Projeto
```
barbearia/
├── src/
│   └── Barbearia.java   ← código-fonte completo
├── out/                 ← bytecode compilado
└── README.md
```

## Arquitetura Resumida

| Primitiva           | Uso                                              |
|---------------------|--------------------------------------------------|
| `ReentrantLock`     | Lock global justo; garante exclusão mútua        |
| `Condition` (sofá)  | Clientes em pé aguardam promoção                 |
| `Condition` (atend) | Clientes no sofá aguardam cadeira livre          |
| `Condition` (barb)  | Barbeiros aguardam chegada de cliente            |
| `Semaphore(1,true)` | Serializa pagamento no caixa (FIFO garantido)    |
| `Queue<Integer>`    | Filas FIFO explícitas para sofá e fila em pé     |
| `AtomicInteger`     | Contadores de estatísticas sem lock              |
