package org.gordon.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * An interactive test client for the echo server. You will br prompted at the command line to
 * type something, and it will be echoed back.  The loop continues until you type an EOF for input.
 * @author Gary
 */

public class EchoClient {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: echoclient <host> <port number>");
            System.exit(1);
        }

        Socket sock = null;
        try {
            sock = new Socket(args[0], Integer.parseInt(args[1]));
            System.out.println("created request socket: " + sock);
            InputStream isc = sock.getInputStream();
            OutputStream osc = sock.getOutputStream();
            loopInput(isc, osc);
        } finally {
            if (sock != null) {
                sock.close();
            }
        }  
    }
    
    public static void loopInput(InputStream is, OutputStream os) throws IOException {
        BufferedReader brl = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        
        while (true) {
            System.out.print("Enter an expression: ");
            String line = brl.readLine();
            if (line == null) {
                break;
            }
            pw.println(line);
            pw.flush();
            System.out.println(br.readLine());
        }   
    }

}
