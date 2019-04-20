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
