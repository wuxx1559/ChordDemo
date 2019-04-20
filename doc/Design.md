# Design Document

## 0. Requirement

* Seven machines (localhost for Client, one for SuperNode, five for Nodes)	
* thrift 0.9.3 (CSE lab's version)
* vim, Linux command

thrift usage for this project (after generating `.java` file in `gen-java/` directory, please use `mv` command to move them into `src/` directory)

```bash
thrift -r --gen java chord.thrift
```

## 1. Client

The Client will only finish the test function for this project.

First it will open the transport to the SuperNode, and then show a command line like UI to handle the input from user. You can enter `SET`, `GET`, `FILE` and `EXIT` command. If you input `EXIT`, the client will shutdown. If you input wrong command, it will say this is incorrect and ask you input again. Below is an example.

```txt
[output] Please input GET, SET, FILE or EXIT.
[input]  Excited
[output] Wrong Command.
[output] --------------------
[input]  FILE
[output] Please input the file name:
[input]  shakespeares.txt
[output] Set from FILE finished.
[output] --------------------
[input]  SET
[output] Node information: <IP>:<Port>
[output] Please input <Book_title>:<Genre>
[input]  All's Well That Ends Well:Comedies
[output] Set operation [ All's Well That Ends Well:Comedies ]
# if there is no ':' the terminal will give a message "can't find : in string"
[output] --------------------
[input]  GET
[output] Node information: <IP>:<Port>
[output] Please input <Book_title>
[input]  All's Well That Ends Well
[output] The <Genre> of All's Well That Ends Well is Comedies
# if we don't have this book the terminal will say "Sorry we cannot find this book"
[output] --------------------
[input]  EXIT
```

## 2. SuperNode

SuperNode have four functions.

* for Client
    * `String GetNode()`: return a random Node, if there is no Node, return null
* for itself
    * `int hash_node(String raw_info)`: the `raw_info` contains IP, port, and current time, it is a help function to assign a place for each Node
* for Node
    * `int get_node_num()`: return the number of maximum nodes that the SuperNode can handle.
    * `String Join(String IP, int Port)`: Join method, the detail will be shown below
    * `boolean PostJoin(String IP, int Port)`: Node tells SuperNode that it finishes the Join

```Java
public String Join(String IP, int Port) {
    if (running.compareAndSet(false, true)) {
        if (nodes_port.size() >= node_num) {
            log.info("Current node number is maximum, cannot join.");
            return null;
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
        return res;
    }
    else {
        log.info("Node with information IP = " + IP + ", port = " + Port
                + " join failed because another join is running.");
        return "NACK";
    }
}
```

> the join function first uses a `AtomicBoolean` to protect the critical section. When there is a processing is running, other join request will return "NACK". Then it will assign the current Node with a location in the DHT and return the information of the current Node and a random choosen Node.

## 3. Node

* for Client
    * `void Set(String Book_title, String Genre)`: set the book information into the correct node. The function is recursive, which means, if the current node is not the right node to insert <Book_title:Genre>, we can use the function to find suitable 
    * `String Get(String Book_title)`: return the Genre of the Book_title if exists, if not, return "" (which means null). The logic is similar to `Set` function.
* for itself
    * `Info_iip get_pred()`, `Info_iip get_succ()`, `void set_pred(Info_iip iip)`, `void set_succ(Info_iip iip)`: help to maintain the  succ and pred of each Node.
    * `int hash_node(String raw_info)`: we use the `Book_title` as `raw_info`, to get the hash code of the Book. It is a help function  to assign a Node for each Book.
    * `boolean interv_clockwise(int test, int start, int end, int range)`:  return true if the test number between start and end following clockwise direction
    * `print_log(String loc)`: function for print the log information on the terminal. `loc` can be set as the current function's name.
* for other Node
    * `void UpdateDHT(String node_info, String node_IP, int node_Port)`: `node_info` is the information of the random node given by `GetNode()`. The procedure is between join and postjoin. After joining the new node into the DHT, set the predecessor, successor, and finger table of the new node,and update some nodes' whose pred, succ, finger_table are affected.
    * `Info_iip find_ID(int title_nodeID)` : using the the finger table to find the suitable Node for the `title_nodeID`. This function is recursive, details are shown below.
    * `void update_finger_table(Info_iip add_iip, int i)`: the function is also shown below. The idea of this function is trying to update the finger tabel recursively.

```java
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
```

## 4. Useful scripts

### 4.1 `Makefile`

The `Makefile` gives us a easy way to compile the program and clean all the files produced. For the compile part, we can use `make` command to compile Client, SuperNode, and Node. All source files are in `src/` directory while the result will be in `classes/` directory. For cleaning part, using `make clean` can the `classes/` directory.

```Makefile
objects = class_dir Client SuperNode Node
FLAGS = -cp ".:/usr/local/Thrift/*" -d classes -sourcepath src
all: $(objects)
.PHONY : all
class_dir:
	@if [ ! -d classes ]; then mkdir classes; else echo "/classes already exists."; fi
Client:
	javac $(FLAGS) src/Client.java
SuperNode:
	javac $(FLAGS) src/SuperNode.java
Node:
	javac $(FLAGS) src/Node.java

.PHONY : clean
clean:
	@echo "Cleaning up..."
	@rm -rf classes/
```

### 4.2 `test.sh`

```bash
cd classes
error_string="Parameter Error. Using \"./test.sh Client\" , \"./test.sh SuperNode node_size\" or \"./test.sh Node[#]\""
if [[ $# -eq 1 || $# -eq 2 ]]
then
    if [ $1 == 'Client' ]
    then
        java -cp ".:/usr/local/Thrift/*" Client ../config/SuperNode.cfg
    elif [[ $# -eq 2 && $1 == 'SuperNode' ]]
    then
        java -cp ".:/usr/local/Thrift/*" SuperNode $2 ../config/SuperNode.cfg
    elif [ -r ../config/$1.cfg ]
    then
        java -cp ".:/usr/local/Thrift/*" Node ../config/SuperNode.cfg ../config/$1.cfg
    else
        echo $error_string
    fi
else
    echo $error_string
fi
```

The script is shown above. First . And then the script should make sure the number of parameter(s) is 1 or 2. Then run the Client, SuperNode or Node. For the Client, it need to get the IP address and port number of the SuperNode, so its parameter is the confg file of SuperNode. For the SuperNode, it needs the configuration of itself and the number of nodes in DHT (the maximum it can hold). For each Node, it only require the configuration file of itself.

## 5. Configuration

* `SuperNode.cfg` contains two lines: the IP address and the port number.
* `Node[#].cfg` ([#] part replaced by number) includes two lines: the IP address, the port number.
* the usage descriptions are in `4.2`

## 6. Other Information
NULL

