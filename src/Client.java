import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;

import java.util.*;
import java.io.*;

public class Client {
    public static void main(String[] args) {
        // assume the command is: Jave Client supernode_cfg_file
        // host_name: local host --- test 1st
        int argc = args.length;
        if (argc < 1) {
            System.out.println("Wrong number of arguments!");
            return;
        }

        String cfg_file = args[0];
        String host_add = null;
        int port_num = -1;
        try {
            BufferedReader input = new BufferedReader(new FileReader(cfg_file));
            host_add = input.readLine();
            port_num = Integer.parseInt(input.readLine());
            input.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Create client connect.
        try {
            TTransport transport = new TSocket(host_add, port_num);
            // host_add and port_num of the server
            TProtocol protocol = new TBinaryProtocol(new TFramedTransport(transport));
            supernode_interface.Client client = new supernode_interface.Client(protocol);
            //Try to connect
            transport.open();
            client_test(client);
            transport.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void client_test(supernode_interface.Client client) {
        try {
            while (true) {
                System.out.println("Please input GET, SET, FILE or EXIT.");
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String input = reader.readLine();
                if (input.equals("EXIT")) {
                    return;
                }
                if ((!input.equals("GET")) && (!input.equals("SET")) && (!input.equals("FILE"))) {
                    System.out.println("Wrong Command.");
                    System.out.println("--------------------");
                    continue;
                }

                String node_info = client.GetNode();
                System.out.println("Node information: " + node_info);
                int pos = node_info.indexOf(":");
                String host_ = node_info.substring(0, pos);
                int port_ = Integer.parseInt(node_info.substring(pos + 1));
                TTransport TS = new TSocket(host_, port_);
                // host_ and port_ of the node
                TProtocol protocol = new TBinaryProtocol(new TFramedTransport(TS));
                node_interface.Client node = new node_interface.Client(protocol);
                TS.open();

                if (input.equals("SET")) {
                    System.out.println("Please input <Book_title>:<Genre>");
                    input = reader.readLine();
                    pos = input.indexOf(":");
                    if (-1 == pos) {
                        System.out.println("can't find : in string:" + input);
                        TS.close();
                        continue;
                    }
                    String book = input.substring(0, pos);
                    String type = input.substring(pos + 1);
                    // extract from pos to the end
                    node.Set(book, type);
                    System.out.println("Set operation [ " + book +  ":" + type + " ]");
                }
                else if (input.equals("GET")) {
                    System.out.println("Please input <Book_title>");
                    input = reader.readLine();
                    String type = "";
                    type = node.Get(input);
                    if (type.equals("")) {
                        System.out.println("Sorry we cannot find this book");
                    }
                    else {
                        System.out.println("The <Genre> of " + input + " is " + type);
                    }
                }
                else {
                    // input equals "FILE"
                    System.out.println("Please input the file name:");
                    String input_file = "../" + reader.readLine();
                    File file = new File(input_file);
                    if (!file.exists()) {
                        System.out.println("Sorry, file " + input_file + " not exists.");
                        continue;
                    }
                    BufferedReader input_stream = new BufferedReader(new FileReader(input_file));
                    String line = null;
                    while (null != (line = input_stream.readLine())) {
                        pos = line.indexOf(":");
                        if (-1 == pos) {
                            System.out.println("can't find : in string:" + line);
                            continue;
                        }
                        String book = line.substring(0, pos);
                        String type = line.substring(pos + 1);
                        // extract from pos to the end
                        node.Set(book,type);
                    }
                    input_stream.close();
                    System.out.println("Set from FILE finished.");
                }
                System.out.println("--------------------");
                TS.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
