import java.util.*;
import java.io.*;
import java.util.logging.Logger;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

public class SuperNodeHandler implements supernode_interface.Iface {
    private static AtomicBoolean running = new AtomicBoolean(false);
    private static List<String> nodes_IP = new ArrayList<String>();
    private static List<Integer> nodes_port = new ArrayList<Integer>();
    private static List<Integer> nodes_ID = new ArrayList<Integer>();
    private static Logger log = Logger.getLogger("SuperNode");
    private final int node_num;
    private static boolean[] used_node;

    public SuperNodeHandler(int _node) {
        this.node_num = _node;
        used_node = new boolean[node_num];
        for (int i = 0; i < node_num; i++) {
            used_node[i] = false;
        }
    }

    @Override
    public int get_node_num(){
      return this.node_num;
    }

    private int hash_node(String raw_info) {
        int res = 0;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            // select MD5 algorithm, can also use SHA-1 or SHA-2
            byte[] bytes = md.digest(raw_info.getBytes());
            for (int i = 0; i < bytes.length; i++) {
                res += bytes[i] + node_num;
                res %= node_num;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }

    @Override
    public String GetNode() {
        if (nodes_port.size() == 0) {
            return null;
        }
        Random r = new Random();
        int i = r.nextInt(nodes_port.size());
        String res = nodes_IP.get(i) + ":" + nodes_port.get(i);
        log.info("Request from client, return node: " + res);
        return res;
    }

    @Override
    public String Join(String IP, int Port) {
        if (running.compareAndSet(false, true)) {
            if (nodes_port.size() >= node_num) {
                log.info("Current node number is maximum, cannot join.");
                return "NACK";
            }
            String raw_info = IP + Port;
            int k = hash_node(raw_info);
            while (used_node[k]) {
                raw_info = IP + Port + System.currentTimeMillis();
                k = hash_node(raw_info);
            }
            String res = String.valueOf(k);
            if (nodes_port.size() == 0) {
                res += ",null";
            }
            else {
                Random r = new Random();
                int i = r.nextInt(nodes_port.size());
                res += ("," + nodes_IP.get(i) +
                        ":" + nodes_port.get(i) +
                        ":" + nodes_ID.get(i));
            }
            used_node[k] = true;
            nodes_ID.add(k);
            nodes_IP.add(IP);
            nodes_port.add(Port);
            log.info("Join from IP = " + IP + ", port = " + Port + " succeed.");
            return res;
        }
        else {
            log.info("Node with information IP = " + IP + ", port = " + Port
                    + " join failed because another join is running.");
            return "NACK";
        }
    }

    @Override
    public boolean PostJoin(String IP, int Port) {
        if (running.compareAndSet(true, false)) {
            // release, let other process in
            log.info("Postjoin success from IP = " + IP + ", port = " + Port);
            return true;
        }
        else {
            log.info("Postjoin Error: from IP = " + IP + ", port = " + Port);
            return false;
        }
    }
}
