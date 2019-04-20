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
