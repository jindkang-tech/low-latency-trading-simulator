Low‑Latency Trading Simulator (Java)

This project is a minimalistic yet practical trading simulator designed to emulate a single‑instrument exchange with extremely low latency. It was built from the ground up in Java to be deterministic and easy to extend. By default it runs entirely in memory, uses a ring‑buffer sequencer to serialise incoming events, and exposes both console and network gateways for submitting orders.

Features

Event Sequencer: All NEW, CANCEL, REPLACE and market data (MD_TICK) events are placed on a single‑writer ring buffer (implemented with an ArrayBlockingQueue), which is drained by a dedicated thread. This decouples I/O threads from the matching engine and ensures events are processed in a deterministic order.

Matching Engine: A single‑threaded core that maintains price‑level order books (bids and asks) per instrument, matches orders on a price‑time priority basis, and handles order modifications in place whenever possible. Each accepted order receives a server‑assigned ID for robust cancellation and replacement.

Market Data Integration: A simulated MarketDataFeeder publishes top‑of‑book updates via the sequencer. The MatchingEngine updates its view of the best bid and ask and notifies the Publisher.

Network & Console Gateways: Orders can be submitted either via a simple console‑based interface or over a TCP connection. The wire protocol accepts commands like:

NEW,clOrdId=123,side=B,qty=100,px=101.25,acct=ABC,sym=XYZ

CXL,clOrdId=123

RPL,clOrdId=123,qty=50,px=101.50

Risk Management: The RiskManager performs pre‑trade checks on maximum order size and approximates position limits per account. Positions are updated on each fill.

Metrics & Logging: Latency (ingress → acknowledgement and ingress → fill) is measured using HdrHistogram and summarised to CSV (metrics.csv) every five seconds. Counts of acknowledgements, fills and market data ticks are included.

Extensible Design: The project is modular. You can easily extend it to support multiple instruments (a separate book per symbol), add FIX or SBE gateways, plug in your own strategy modules, or persist event logs via libraries like Chronicle Queue.

Requirements

Java 17 or later (Java 21 recommended for records and recent GC improvements).

Maven 3.x.

For network usage, ensure the chosen port (default 9000) is available.

Building the Project

Clone or unpack the repository and build it via Maven:

mvn clean package


The build produces a shaded JAR at target/low-latency-trading-simulator-0.1.0.jar with all dependencies bundled.

Running the Simulator

From the root of the project, run:

java -jar target/low-latency-trading-simulator-0.1.0.jar


By default the application will:

Start the event sequencer thread.

Launch a MarketDataFeeder that emits random top‑of‑book updates for instrument XYZ every second.

Start a network gateway on port 9000 that accepts orders in the CSV format described above.

Launch a console gateway that reads orders from standard input. Enter Ctrl+D (EOF) to stop the console gateway.

Write latency and throughput metrics to metrics.csv every five seconds.

Example Session

Open two terminals. In the first, run the simulator. In the second, connect via nc (netcat) and submit orders:

# Terminal 1
java -jar target/low-latency-trading-simulator-0.1.0.jar

# Terminal 2
nc localhost 9000
NEW,clOrdId=1,side=B,qty=100,px=101.25,acct=ABC,sym=XYZ
NEW,clOrdId=2,side=S,qty=50,px=101.00,acct=ABC,sym=XYZ
CXL,clOrdId=1


You should see acknowledgement (ACK) and fill (FILL) messages on the simulator’s console, along with latency values.

Directory Layout
├── pom.xml               # Maven configuration, including dependencies for Agrona, Disruptor, JUnit and HdrHistogram
├── README.md             # You are here
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com/example/latencytrader
│   │   │       ├── CancelEvent.java
│   │   │       ├── Event.java
│   │   │       ├── EventType.java
│   │   │       ├── Main.java
│   │   │       ├── MarketDataEvent.java
│   │   │       ├── MarketDataFeeder.java
│   │   │       ├── MatchingEngine.java
│   │   │       ├── NetworkOrderGateway.java
│   │   │       ├── Order.java
│   │   │       ├── OrderBook.java
│   │   │       ├── OrderEvent.java
│   │   │       ├── OrderGateway.java
│   │   │       ├── Publisher.java
│   │   │       ├── ReplaceEvent.java
│   │   │       ├── RiskManager.java
│   │   │       ├── Sequencer.java
│   │   │       └── Side.java
│   └── test
│       └── java
│           └── com/example/latencytrader
│               └── MatchingEngineTest.java

Testing

The project includes a small JUnit 5 test suite under src/test/java. Run tests with:

mvn test


This will verify basic scenarios such as order insertion, matching and cancellation. You can add more tests to cover edge cases like partial fills, invalid cancels, or multi‑level sweeps.

Extending the Simulator

This simulator is intentionally simple. Here are some ideas for extension:

Multi‑instrument support: Maintain separate order books for each instrument symbol. The current implementation stores a Map<String, OrderBook> to prepare for this.

FIX/SBE Gateway: Add a proper network protocol, such as FIX 4.4 or Simple Binary Encoding (SBE), to integrate with external systems.

Persistence: Use Chronicle Queue or another event log to persist all inbound and outbound messages for replay and backtesting.

Strategy API: Expose hooks (onTick, onTrade) so that trading strategies can be loaded dynamically and respond to market data and fills.

Advanced Risk Management: Enforce pre‑trade margin requirements, per‑instrument limits, or cross‑product hedging.

Latency Optimisation: Swap the ArrayBlockingQueue with LMAX Disruptor for even lower jitter and integrate CPU pinning or real‑time Java settings.

If you encounter issues or have suggestions, feel free to open an issue or submit a pull request. This project is a learning tool—contributions are welcome!
