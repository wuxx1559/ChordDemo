import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.server.TThreadPoolServer.Args;
import org.apache.thrift.transport.TTransportFactory;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;

import java.io.*;
import java.util.*;

public class Node {
    public static NodeHandler handler;
    public static node_interface.Processor<NodeHandler> processor;

    public static String node_host_add = null, sn_host_add = null;
    public static int node_port_num = -1, sn_port_num = -1;

    public static void main(String[] args) {
        // java Node supernode_cfg node_cfg
        int argc = args.length;
        if (argc < 2) {
            System.out.println("Wrong number of arguments!");
            return;
        }

        String node_cfg = args[1];
        try {
            BufferedReader input = new BufferedReader(new FileReader(node_cfg));
            node_host_add = input.readLine();
            node_port_num = Integer.parseInt(input.readLine());
            input.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // get Node's IP address and port number

        String sn_cfg = args[0];
        try {
            BufferedReader input = new BufferedReader(new FileReader(sn_cfg));
            sn_host_add = input.readLine();
            sn_port_num = Integer.parseInt(input.readLine());
            input.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // get supernode's information

        // act as a client to supernode
        // move to NodeHandler.java any more procedure??

        // act as a server to "client"
        try {
            handler = new NodeHandler(sn_host_add, sn_port_num, node_host_add, node_port_num);
            processor = new node_interface.Processor<NodeHandler>(handler);
            Runnable hold_node = new Runnable() {
                public void run() {
                    node_service(processor);
                }
            };
            new Thread(hold_node).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void node_service(node_interface.Processor<NodeHandler> processor) {
        try {
            TServerTransport ST = new TServerSocket(node_port_num);
            TTransportFactory factory = new TFramedTransport.Factory();
            TThreadPoolServer.Args args = new TThreadPoolServer.Args(ST);
            args.processor(processor);
            args.transportFactory(factory);
            TServer server = new TThreadPoolServer(args);
            server.serve();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
