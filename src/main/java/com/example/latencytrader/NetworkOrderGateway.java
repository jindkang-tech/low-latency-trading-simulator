package com.example.latencytrader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A network-based order gateway that listens for incoming TCP connections on a
 * specified port.  Each line of text received from a client is parsed as an
 * order command and forwarded to the sequencer.  Commands follow the same
 * CSV format as the console gateway:
 *
 * <pre>
 * NEW,clOrdId=123,side=B,qty=100,px=101.25,acct=ABC,sym=XYZ
 * CXL,clOrdId=123
 * RPL,clOrdId=123,qty=50,px=101.00
 * </pre>
 */
public final class NetworkOrderGateway implements Runnable {
    private final Sequencer sequencer;
    private final int port;
    private volatile boolean running = true;

    // Regex patterns for parsing commands
    private static final Pattern NEW_PATTERN = Pattern.compile(
            "NEW,clOrdId=(\\d+),side=([BS]),qty=(\\d+),px=([0-9.]+),acct=([A-Za-z0-9]+),sym=([A-Za-z0-9]+)");
    private static final Pattern CXL_PATTERN = Pattern.compile(
            "CXL,clOrdId=(\\d+)");
    private static final Pattern RPL_PATTERN = Pattern.compile(
            "RPL,clOrdId=(\\d+),qty=(\\d+),px=([0-9.]+)");

    public NetworkOrderGateway(Sequencer sequencer, int port) {
        this.sequencer = sequencer;
        this.port = port;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("NetworkOrderGateway listening on port " + port);
            while (running) {
                Socket client = server.accept();
                new Thread(() -> handleClient(client), "OrderClientHandler").start();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to start network order gateway", e);
        }
    }

    private void handleClient(Socket client) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    processLine(line);
                } catch (Exception ex) {
                    System.err.println("Invalid message: " + line + "; " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Client disconnected: " + e.getMessage());
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
            String instrument = m.group(6);
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
            throw new IllegalArgumentException("Unrecognized command");
        }
    }
}
