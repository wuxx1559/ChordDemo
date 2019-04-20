import java.util.*;
import java.io.*;

import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import java.lang.Math;
import java.lang.Object;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.security.MessageDigest;

public class NodeHandler implements node_interface.Iface {
    private static int id;
    private static int key_range;
    private static Info_iip pred;
    private static Info_iip succ;
    private static Map<String, String> title_genre_map;
    private static Map<Integer, Info_iip> finger_table;
    private static Logger log = Logger.getLogger("Node");
    private final String node_host_add, sn_host_add;
    private final int node_port_num, sn_port_num;


    public NodeHandler(String sn_IP, int sn_Port, String node_IP, int node_Port) {
      this.sn_host_add = sn_IP;
      this.sn_port_num = sn_Port;
      this.node_host_add = node_IP;
      this.node_port_num = node_Port;

      // act as a client to "supernode"
      try {
        TTransport transport = new TSocket(sn_IP, sn_Port);
        // host_add and port_num of the SuperNode
        TProtocol protocol = new TBinaryProtocol(new TFramedTransport(transport));
        supernode_interface.Client client = new supernode_interface.Client(protocol);
        transport.open();

        String node_info = client.Join(node_IP, node_Port);
        // node_info: i.e. 5,IP:PORT:ID(random existing node)
        //                 some nodes may still wait to be added
        if (node_info.equals("NACK")) {
            log.warning("Sorry, the supernode is busy or full.");
            return;
        }
        int pos = node_info.indexOf(",");
        this.id = Integer.parseInt(node_info.substring(0, pos));
        this.key_range = client.get_node_num();
        // check the node_num in SN, whether it is 2^n !!!!!
        this.title_genre_map = new ConcurrentHashMap<>();
        this.finger_table = new ConcurrentHashMap<>();
        String rd_node_info = node_info.substring(pos+1);
        log.info("Return from Join: " + node_info);

        // updata this.pred + this.succ + this.finger_table
        // update succ.prev + prev.secc + all_prev.finger_table
        // act as a client to other "node"
        this.UpdateDHT(rd_node_info, node_IP, node_Port);
        client.PostJoin(node_IP, node_Port);
        transport.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
      print_log("In construction\n");
    }

    public static boolean interv_clockwise(int test, int start, int end, int range) {
      // exclude start == test case here
      // if end == test, true
      // if (start == end)  && they are not equal to test return false
      int temp = start;
      while (temp != test) {
        temp++;
        temp %= range;
        if(temp == end) {
          return false;
        }
      }
      return true;
    }

    @Override
    public Info_iip get_pred() {
      return this.pred;
    }
    public Info_iip get_succ() {
      return this.succ;
    }

    @Override
    public void set_pred(Info_iip iip) {
      this.pred = iip;
      //print_log("In set_pred\n");
    }

    @Override
    public void set_succ(Info_iip iip) {
      this.succ = iip;
      //print_log("In ser_succ\n");
    }

    private void print_log(String loc) {
      log.info("======================================\n" +
              loc + "id = " + id + "   " +
              "key_range = " + key_range + "   " +
              "number of books stored = " + title_genre_map.size() + "\n" +
              "pred = " + pred + "\n" +
              "succ = " + succ + "\n" +
              "books list = " + title_genre_map + "\n" +
              "finger table = " + finger_table + "\n" +
              "=======================================");
    }

    public void UpdateDHT(String node_info, String node_IP, int node_Port){
      int m = (int)Math.round(Math.log(this.key_range)/Math.log(2));
      // number of entries in finger table
      Info_iip iip = new Info_iip(this.id, node_IP, node_Port);

      if(node_info.equals("null")) {
        this.pred = iip;
        this.succ = iip;
        for(int i = 1; i <= m; i++) {
          this.finger_table.put(i, iip);
        }
      }
      else {
        int pos_host = node_info.indexOf(":");
        String rd_host_add = node_info.substring(0, pos_host);
        int pos_port = node_info.indexOf(":", pos_host + 1);
        int rd_port_num = Integer.parseInt(node_info.substring(pos_host + 1, pos_port));
        int rd_id = Integer.parseInt(node_info.substring(pos_port + 1));

        // act as a client to "node"
        // means this current node is a client to rd_node
        try {
          TTransport rd_TS = new TSocket(rd_host_add, rd_port_num);
          TProtocol rd_protocol = new TBinaryProtocol(new TFramedTransport(rd_TS));
          node_interface.Client rd_node = new node_interface.Client(rd_protocol);
          rd_TS.open();
          // find the location for node uding rd_node.finger_table
          Info_iip next_iip = rd_node.find_ID(this.id);
          rd_TS.close();

          this.succ = next_iip;
          TTransport next_TS = new TSocket(next_iip.IP, next_iip.Port);
          TProtocol next_protocol = new TBinaryProtocol(new TFramedTransport(next_TS));
          node_interface.Client next_node = new node_interface.Client(next_protocol);
          next_TS.open();
          Info_iip prev_iip = next_node.get_pred();
          next_node.set_pred(iip);

          this.pred = prev_iip;
          log.info("connect prev IP = " + next_iip.IP + ", port = " + next_iip.Port);
          TTransport prev_TS = new TSocket(prev_iip.IP, prev_iip.Port);
          TProtocol prev_protocol = new TBinaryProtocol(new TFramedTransport(prev_TS));
          node_interface.Client prev_node = new node_interface.Client(prev_protocol);
          prev_TS.open();
          prev_node.set_succ(iip);
          prev_TS.close();

          this.finger_table.put(1, this.succ);
          for(int i = 2; i <= m; i++) {
            int test = this.id + (int)Math.round(Math.pow(2, i - 1));
            test %= this.key_range;
            Info_iip last_iip = this.finger_table.get(i-1);
            if (interv_clockwise(test, this.id, last_iip.ID, this.key_range)) {
              this.finger_table.put(i, last_iip);
            }
            else {
              this.finger_table.put(i, next_node.find_ID(test));
            }
          }
          next_TS.close();
          // 1: update this new node's finger above
          // 2: updata all pred asscoiated nodes' finger table following
          // upate the pred with the new ID

          for(int i = 1; i <= m; i++) {
            int succ_id = this.id - (int)Math.round(Math.pow(2, i - 1)) + this.key_range;
            succ_id %= this.key_range;
            Info_iip p_succ_iip = this.find_ID(succ_id);

            Info_iip p_iip = new Info_iip(-1, null, -1);
            if (p_succ_iip.ID == this.id) {
              // avoid connect to itself case
              p_iip = this.pred;
            }
            else {
              TTransport p_succ_TS = new TSocket(p_succ_iip.IP, p_succ_iip.Port);
              TProtocol p_succ_protocol = new TBinaryProtocol(new TFramedTransport(p_succ_TS));
              node_interface.Client p_succ_node = new node_interface.Client(p_succ_protocol);
              p_succ_TS.open();
              p_iip = p_succ_node.get_pred();
              p_succ_TS.close();
            }

            if (p_iip.ID != this.id) {
              // avoid connect to itself case
              TTransport p_TS = new TSocket(p_iip.IP, p_iip.Port);
              TProtocol p_protocol = new TBinaryProtocol(new TFramedTransport(p_TS));
              node_interface.Client p_node = new node_interface.Client(p_protocol);
              p_TS.open();
              p_node.update_finger_table(iip, i);
              p_TS.close();
            }
          }
        } catch (Exception e) {
            e.printStackTrace();
        }
      }
    }

    @Override
    public void update_finger_table(Info_iip add_iip, int i) {
      Info_iip next_iip = this.finger_table.get(i);
      int next_id = next_iip.ID;
      int add_id = add_iip.ID;
      if(interv_clockwise(add_id, this.id, next_id, this.key_range)) {
        this.finger_table.put(i, add_iip);
        Info_iip newp_iip = this.pred;
        if (newp_iip.ID != add_id) { //this.succ.ID change to add_id
                                     //causing no need to update the new added node
          try {
            int newp_id = newp_iip.ID;
            // why ID here
            TTransport newp_TS = new TSocket(newp_iip.IP, newp_iip.Port);
            TProtocol newp_protocol = new TBinaryProtocol(new TFramedTransport(newp_TS));
            node_interface.Client newp_node = new node_interface.Client(newp_protocol);
            newp_TS.open();
            newp_node.update_finger_table(add_iip, i);
            newp_TS.close();
          } catch (Exception e) {
              e.printStackTrace();
          }
        }
      }
      print_log("In update_finger_table\n");
    }

    private int hash_node(String raw_info) {
        int res = 0;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            // select MD5 algorithm, can also use SHA-1 or SHA-2
            byte[] bytes = md.digest(raw_info.getBytes());
            for (int i = 0; i < bytes.length; i++) {
                res += bytes[i] + this.key_range;
                res %= this.key_range;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }


    @Override
    public Info_iip find_ID(int title_nodeID) {
      // use finger table to recursively connect and find the location
      Info_iip next_iip = new Info_iip(-1, null, -1);
      int m = (int)Math.round(Math.log(this.key_range)/Math.log(2));
      int i;
      for(i = 1; i <= m; i++) {
        next_iip = this.finger_table.get(i);
        if(interv_clockwise(title_nodeID, this.id, next_iip.ID, this.key_range)) {
          break;
        }
      }

      Info_iip prev_iip = new Info_iip(-1, null, -1);
      Info_iip dest_iip = new Info_iip(-1, null, -1);
      if (i == 1) {
        // avoid searching in infinite loop
        if (next_iip.ID == this.id) {
          return this.succ;
        }
        return this.succ; // next_iip may haven't been updated yet
                          // debug from succ_id + p_succ_iip.ID
      }
      else if(i <= m) {
        prev_iip = this.finger_table.get(i-1);
      }
      else {
        prev_iip = this.finger_table.get(m);
      }
      try {
        TTransport prev_TS = new TSocket(prev_iip.IP, prev_iip.Port);
        TProtocol prev_protocol = new TBinaryProtocol(new TFramedTransport(prev_TS));
        node_interface.Client prev_node = new node_interface.Client(prev_protocol);
        prev_TS.open();
        dest_iip = prev_node.find_ID(title_nodeID);

        prev_TS.close();
      } catch (Exception e) {
          e.printStackTrace();
      }
      return dest_iip;
    }

    @Override
    public void Set(String Book_title, String Genre) {
      int title_nodeID = hash_node(Book_title);
      log.info("The hash code of " + Book_title + " is " + title_nodeID);
      if(interv_clockwise(title_nodeID, this.pred.ID, this.id, this.key_range)) {
        // start == test case
        this.title_genre_map.put(Book_title, Genre);
        print_log("In Set\n");
      }
      else {
        try {
          Info_iip dest_iip = this.find_ID(title_nodeID);
          TTransport dest_TS = new TSocket(dest_iip.IP, dest_iip.Port);
          TProtocol dest_protocol = new TBinaryProtocol(new TFramedTransport(dest_TS));
          node_interface.Client dest_node = new node_interface.Client(dest_protocol);
          dest_TS.open();
          dest_node.Set(Book_title, Genre);
          dest_TS.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
      }
    }

    @Override
    public String Get(String Book_title) {
        int title_nodeID = hash_node(Book_title);
        String type = null;
        if(interv_clockwise(title_nodeID, this.pred.ID, this.id, this.key_range) || title_genre_map.containsKey(Book_title)) {
          type = this.title_genre_map.get(Book_title);
          if (null == type) {
            log.info("Sorry, " + Book_title + " not exist.");
            return "";
          }
          else {
            log.info("find " + Book_title + " here. Genre = " + type);
            return type;
          }
        }
        else {
          try {
            Info_iip dest_iip = this.find_ID(title_nodeID);
            log.info(Book_title + " not find in this Node. Try to connect " + dest_iip);
            TTransport dest_TS = new TSocket(dest_iip.IP, dest_iip.Port);
            TProtocol dest_protocol = new TBinaryProtocol(new TFramedTransport(dest_TS));
            node_interface.Client dest_node = new node_interface.Client(dest_protocol);
            dest_TS.open();
            type = dest_node.Get(Book_title);
            dest_TS.close();
            return type;
          } catch (Exception e) {
              e.printStackTrace();
          }
        }
        return type;
    }
}
