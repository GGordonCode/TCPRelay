package org.gordon.relay;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * The Client Listener is the main point of processing for requests made by
 * clients.
 * 
 * Clients connect to the advertised host/port. Behind the scenes the daemon
 * (i.e. server) is provided a server socket to connect to so the input and
 * output from the client can be processed by the actual server on the backend.
 * 
 * For each request, a ClientRequestProcessor is run in a separate thread to handle
 * the request.
 * 
 * @author Gary
 *
 */
public class ClientListener implements Runnable {

    private String serviceName;
    private ServerSocket listener;
    private Socket daemonChannel;
    private Thread listenerThread;
    private Logger logger;

    // These are used for line-oriented character communications over the
    // control channel
    // for the server.
    private PrintWriter pw;
    private BufferedReader br;

    /**
     * Creates a new ClientListener to handle
     * 
     * @param daemonChannel
     *            the control channel for the backend server
     * @throws IOException
     *             if the accept() socket cannot be created, or there are issues
     *             with the control channel socket.
     */
    public ClientListener(Socket daemonChannel) throws IOException {
        listener = new ServerSocket(0);
        this.daemonChannel = daemonChannel;
        InputStream is = daemonChannel.getInputStream();
        OutputStream os = daemonChannel.getOutputStream();
        this.pw = new PrintWriter(new OutputStreamWriter(os));
        this.br = new BufferedReader(new InputStreamReader(is));
        this.logger = Relay.logger;
    }

    /**
     * Gets the name of the service the Listener is functioning on behalf of.
     * @return the service name.
     */
    public String getserviceName() {
        return serviceName;
    }

    /**
     * Starts the listener loop for client requests for this service, and
     * advertises the host/port combination for clients to use.
     * @throws IOException if various socket-y things go wrong
     */
    public void start() throws IOException {
        listenerThread = new Thread(this);
        listenerThread.start();
        
        // Block until the server-side syncs up to receive the host:port combo.
        logger.info("Client Listener: awaiting registration from server ...");
        String serviceName = br.readLine();
        if (serviceName == null) {
            logger.severe("service name not read from server!");
            throw new IOException("Server connection not established for client request.");
        }
        this.serviceName = serviceName;
        
        // Provide the server with its host and port.
        pw.println(daemonChannel.getInetAddress().getHostName() + ":" + listener.getLocalPort());
        pw.flush();

        // The ServerSocket lists it's address as 0.0.0.0, which is a sort of wildcard,
        // but we need to give the client a valid IP address, so we use the actual one
        // the server has connected to.
        logger.info(() -> "Service '" + serviceName + "' has established relay address: "
                + daemonChannel.getLocalAddress().getHostName() + ":" + listener.getLocalPort());
    }

    /**
     * Attempt at orderly cleanup. Close the socket and interrupt the thread.
     * @throws IOException for either of the above
     */
    public void shutdown() throws IOException {
        listener.close();
        listenerThread.interrupt();
    }

    /**
     * The run loop listens for client requests, and when it receives one, it creates
     * a ServerSocket for the server to connect to, so it can receive and process I/O
     * from the client.
     */
    @Override
    public void run() {
        try {
            while (true) {
                ServerSocket serverListener = null;
                try {
                    logger.info(() -> "Client Listener accepting client requests on port "
                            + listener.getLocalPort() + "...");
                    Socket socket = listener.accept();
                    serverListener = null;

                    // There is a very small (should be effectively negligible), but
                    // required bottleneck as we get the ACK from the server that it
                    // has received the new request to process. This must happen here,
                    // and not in the individual processing threads to prevent intermixed
                    // writes to the control socket.  If the server goes away, the read
                    // should throw an error, as TCP is stateful.
                    serverListener = new ServerSocket(0);
                    pw.println(daemonChannel.getLocalAddress().getHostName() + ":" + serverListener.getLocalPort());
                    pw.flush();
                    String serviceName = br.readLine();
                    if (serviceName == null) {
                        logger.severe(() -> String.format("ACK for client request not read from server '%s'",
                                getserviceName()));
                        // We really don't want to kill the server due to one failure.  In real
                        // life we might consider doing so if the problem persists.
                        continue;
                    } else {
                        logger.info("read ACK, server has connected for request processing");
                    }

                    Socket daemonSocket = serverListener.accept();
                    logger.info(() -> String.format("server '%s' has connected to client request", serviceName));

                    // Now we can launch the request processing in a new thread.
                    ClientRequestProcessor proc = new ClientRequestProcessor(socket, daemonSocket);
                    proc.start();
                } catch (IOException e) {
                    logger.severe(() -> "handling client request: " + e.getMessage());
                } finally {
                    try {
                        if (serverListener != null) {
                            serverListener.close();
                        }
                    } catch (IOException e) {
                        logger.warning(() -> "cannot close listener socket: " + e.getMessage());
                    }
                }
            }
        } finally {
            try {
                listener.close();
            } catch (IOException e) {
                logger.warning(() -> "cannot close client listener socket: " + e.getMessage());
            }
        }
    }
}
