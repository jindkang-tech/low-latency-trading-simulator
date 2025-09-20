Low‑Latency Trading Simulator (Java)

This project is a minimalistic yet practical trading simulator designed to emulate a single‑instrument exchange with extremely low latency. It was built from the ground up in Java to be deterministic and easy to extend. By default it runs entirely in memory, uses a ring‑buffer sequencer to serialise incoming events, and exposes both console and network gateways for submitting orders.

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://travis-ci.com)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.1.0-orange.svg)](https://github.com/example/low-latency-trading-simulator/releases)

Features

- **Event Sequencer**: All NEW, CANCEL, REPLACE and market data (MD_TICK) events are placed on a single‑writer ring buffer (implemented with an ArrayBlockingQueue), which is drained by a dedicated thread. This decouples I/O threads from the matching engine and ensures events are processed in a deterministic order.
- **Matching Engine**: A single‑threaded core that maintains price‑level order books (bids and asks) per instrument, matches orders on a price‑time priority basis, and handles order modifications in place whenever possible. Each accepted order receives a server‑assigned ID for robust cancellation and replacement.
- **Market Data Integration**: A simulated MarketDataFeeder publishes top‑of‑book updates via the sequencer. The MatchingEngine updates its view of the best bid and ask and notifies the Publisher.
- **Network & Console Gateways**: Orders can be submitted either via a simple console‑based interface or over a TCP connection. The wire protocol accepts commands such as:
  - `NEW,clOrdId=123,side=B,qty=100,px=101.25,acct=ABC,sym=XYZ`
  - `CXL,clOrdId=123`
  - `RPL,clOrdId=123,qty=50,px=101.50`
- **Risk Management**: The RiskManager performs pre‑trade checks on maximum order size and approximates position limits per account. Positions are updated on each fill.
- **Metrics & Logging**: Latency (ingress → acknowledgement and ingress → fill) is measured using HdrHistogram and summarised to CSV (metrics.csv) every five seconds. Counts of acknowledgements, fills and market data ticks are included.
- **Extensible Design**: The project is modular. You can easily extend it to support multiple instruments (a separate book per symbol), add FIX or SBE gateways, plug in your own strategy modules, or persist event logs via libraries like Chronicle Queue.

## Installation

### Requirements
- **Java**: 17 or later (Java 21 recommended for records and recent GC improvements).
- **Maven**: 3.x.
- **Network**: For network usage, ensure the chosen port (default 9000) is available.

## Run Locally

### Building the Project

Clone or unpack the repository and build it via Maven:
```bash
mvn clean package
```

The build produces a shaded JAR at `target/low-latency-trading-simulator-0.1.0.jar` with all dependencies bundled.

## Usage/Examples

### Running the Simulator

From the root of the project, run:
```bash
java -jar target/low-latency-trading-simulator-0.1.0.jar
```

By default the application will:
- Start the event sequencer thread.
- Launch a MarketDataFeeder that emits random top‑of‑book updates for instrument XYZ every second.
- Start a network gateway on port 9000 that accepts orders in the CSV format described above.
- Launch a console gateway that reads orders from standard input. Enter Ctrl+D (EOF) to stop the console gateway.
- Write latency and throughput metrics to `metrics.csv` every five seconds.

## Running Tests

The project includes a small JUnit 5 test suite under `src/test/java`. Run tests with:
```bash
mvn test
```

This will verify basic scenarios such as order insertion, matching and cancellation. You can add more tests to cover edge cases like partial fills, invalid cancels, or multi‑level sweeps.

## Contributing

This simulator is intentionally simple. Here are some ideas for extension:
- **Multi‑instrument support**: Maintain separate order books for each instrument symbol. The current implementation stores a `Map<String, OrderBook>` to prepare for this.
- **FIX/SBE Gateway**: Add a proper network protocol, such as FIX 4.4 or Simple Binary Encoding (SBE), to integrate with external systems.
- **Persistence**: Use Chronicle Queue or another event log to persist all inbound and outbound messages for replay and backtesting.
- **Strategy API**: Expose hooks (onTick, onTrade) so that trading strategies can be loaded dynamically and respond to market data and fills.
- **Advanced Risk Management**: Enforce pre‑trade margin requirements, per‑instrument limits, or cross‑product hedging.
- **Latency Optimisation**: Swap the `ArrayBlockingQueue` with LMAX Disruptor for even lower jitter and integrate CPU pinning or real‑time Java settings.

If you encounter issues or have suggestions, feel free to open an issue or submit a pull request. This project is a learning tool—contributions are welcome!

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for more details.
