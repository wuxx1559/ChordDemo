struct Info_iip {
    1: i32 ID;
    2: string IP;
    3: i32 Port; 
}

service supernode_interface {
    i32 get_node_num();
    string Join(1:string IP, 2:i32 Port);
    bool PostJoin(1:string IP, 2:i32 Port);
    string GetNode();
}

service node_interface {
    Info_iip find_ID(1:i32 title_nodeID);
    Info_iip get_pred();
    Info_iip get_succ();
    void set_pred(1:Info_iip iip);
    void set_succ(1:Info_iip iip);

    void Set(1:string Book_title, 2:string Genre);
    string Get(1:string Book_title);
    void update_finger_table(1:Info_iip add_iip, 2:i32 i);
}
