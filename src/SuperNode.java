import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.server.TThreadPoolServer.Args;
import org.apache.thrift.transport.TTransportFactory;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TSSLTransportFactory.TSSLTransportParameters;

import java.io.*;
import java.util.*;

public class SuperNode {
    public static SuperNodeHandler handler;
    public static supernode_interface.Processor<SuperNodeHandler> processor;
    private static String host_add;
    private static int port_num, node_num;

    public static void main(String[] args) {
        // assume the command is: Java Sever num_of_nodes supernode_file
        int argc = args.length;
        if (argc != 2) {
            System.out.println("Wrong number of arguments!");
            return;
        }

        try {
            BufferedReader input = new BufferedReader(new FileReader(args[1]));
            host_add = input.readLine();
            port_num = Integer.parseInt(input.readLine());
            input.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // config file

        int node_num = Integer.parseInt(args[0]);
        //supernode start to receive request
        try {
            handler = new SuperNodeHandler(node_num);
            processor = new supernode_interface.Processor<SuperNodeHandler>(handler);
            Runnable hold_supernode = new Runnable() {
                public void run() {
                    supernode_service(processor);
                    //host_add seems to be useless
                }
            };
            new Thread(hold_supernode).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void supernode_service(supernode_interface.Processor<SuperNodeHandler> processor) {
        try {
            //Create Thrift server socket
            TServerTransport ST = new TServerSocket(port_num);
            TTransportFactory factory = new TFramedTransport.Factory();
            TThreadPoolServer.Args args = new TThreadPoolServer.Args(ST);
            args.processor(processor);  //Set handler
            args.transportFactory(factory); //Set FramedTransport (for performance)
            //Thread Server to process the request form client
            TServer server = new TThreadPoolServer(args);
            server.serve();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
