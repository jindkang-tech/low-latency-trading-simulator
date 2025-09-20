package com.example.latencytrader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple gateway that reads orders from standard input and forwards them to
 * the matching engine.  Accepted commands are:
 *
 * <pre>
 * NEW,clOrdId=123,side=B,qty=100,px=101.25,acct=ABC
 * CXL,clOrdId=123
 * RPL,clOrdId=123,qty=50,px=101.00
 * </pre>
 *
 * The gateway runs on its own thread and continues until EOF is reached on
 * standard input.  Parsing errors are logged but do not terminate the loop.
 */
public final class OrderGateway implements Runnable {
    private final Sequencer sequencer;
    private final String defaultInstrument;

    // Regex patterns for parsing simple CSV commands
    private static final Pattern NEW_PATTERN = Pattern.compile(
            "NEW,clOrdId=(\\d+),side=([BS]),qty=(\\d+),px=([0-9.]+),acct=([A-Za-z0-9]+)(,sym=([A-Za-z0-9]+))?");
    private static final Pattern CXL_PATTERN = Pattern.compile(
            "CXL,clOrdId=(\\d+)");
    private static final Pattern RPL_PATTERN = Pattern.compile(
            "RPL,clOrdId=(\\d+),qty=(\\d+),px=([0-9.]+)");

    public OrderGateway(Sequencer sequencer, String defaultInstrument) {
        this.sequencer = sequencer;
        this.defaultInstrument = defaultInstrument;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    processLine(line);
                } catch (Exception e) {
                    System.err.println("Failed to process input: " + line + "; " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processLine(String line) {
        Matcher m;
        if ((m = NEW_PATTERN.matcher(line)).matches()) {
            long clOrdId = Long.parseLong(m.group(1));
            Side side = m.group(2).equals("B") ? Side.BUY : Side.SELL;
            int qty = Integer.parseInt(m.group(3));
            double px = Double.parseDouble(m.group(4));
            long priceTicks = (long) Math.round(px * 100);
            String account = m.group(5);
            String instrument = m.group(7);
            if (instrument == null) {
                instrument = defaultInstrument;
            }
            long seq = System.nanoTime();
            long tsIn = seq;
            OrderEvent evt = new OrderEvent(seq, tsIn, clOrdId, side, qty, priceTicks, account, instrument);
            sequencer.publish(evt);
        } else if ((m = CXL_PATTERN.matcher(line)).matches()) {
            long clOrdId = Long.parseLong(m.group(1));
            long seq = System.nanoTime();
            long tsIn = seq;
            CancelEvent evt = new CancelEvent(seq, tsIn, clOrdId);
            sequencer.publish(evt);
        } else if ((m = RPL_PATTERN.matcher(line)).matches()) {
            long clOrdId = Long.parseLong(m.group(1));
            int qty = Integer.parseInt(m.group(2));
            double px = Double.parseDouble(m.group(3));
            long priceTicks = (long) Math.round(px * 100);
            long seq = System.nanoTime();
            long tsIn = seq;
            ReplaceEvent evt = new ReplaceEvent(seq, tsIn, clOrdId, qty, priceTicks, null);
            sequencer.publish(evt);
        } else {
            System.err.println("Unrecognized command: " + line);
        }
    }
}
