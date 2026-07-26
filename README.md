# Kevin Volkov's Java Chat App

## About The Project
A chat application for message exchange among remote peers that uses TCP sockets in peer connection implementation, handles multiple socket connections, and includes both client-side and server-side code. A command line parameter is taken, which indicates the port on which the process will listen for incoming connections. When launched, the process works like a UNIX shell, accepting incoming connections and at the same time providing a user interface to offer the following command options:
1. <span style="color: blue; font-weight: bold;">help - </span>Display information about the available user interface options.
2. <span style="color: blue; font-weight: bold;">myip - </span>Display the IP address of this process. This IP is not your local address (127.0.0.1), but your computer's actual IP.
3. <span style="color: blue; font-weight: bold;">myport - </span>Display the port on which this process is listening for incoming connections.
4. <span style="color: blue; font-weight: bold;">connect \<destination> \<port no.> - </span>Establish a new TCP connection to the specified \<destination> at the specified \<port no.>. The <destination> is the IP address of the computer. Any attempt to connect to an invalid IP will be rejected and suitable error message will be displayed. Success or failure in connections between two peers will be indicated by both the peers using suitable messages. Self-connections and duplicate connections will be flagged with suitable error messages.
5. <span style="color: blue; font-weight: bold;">list - </span>Display a numbered list of all the connections this process is handling. This numbered list includes connections initiated by this process and connections initiated by other processes. The output displays the IP address and the listening port of all the peers the process is connected to. Below is an example.
    ```bash
    id: IP address      Port No.
     1: 192.168.21.20   4545
     2: 192.168.21.21   5454
     3: 192.168.21.23   5000
     4: 192.168.21.24   5000
    ```
6. <span style="color: blue; font-weight: bold;">terminate \<connection id.> - </span>Terminate the connection listed under the specified number when LIST is used to display all connections. E.g., terminate 2. In this example, the connection with 192.168.21.21 will end. An error message is displayed if a valid connection does not exist as number 2. If a remote machine terminates one of your connections, a message is also displayed.
7. <span style="color: blue; font-weight: bold;">send \<connection id.> \<message> - </span>(For example, send 3 Oh! This project is a piece of cake). This will send the message to the host on the connection that is designated by the number 3 when command "list" is used. The message to be sent can be up-to 100 characters long, including blank spaces. On successfully executing the command, the sender displays "Message sent to <connection id.>" on the screen. On receiving any message from the peer, the receiver displays the received message along with the sender information. Eg. If a process on 192.168.21.20 sends a message to a process on 192.168.21.21 then the output on 192.168.21.21 when receiving a message displays the following:
    ```bash
    Message received from 192.168.21.20
    Sender's Port: <The port no. of the sender>
    Message: <received message>
    ```
8. <span style="color: blue; font-weight: bold;">exit - </span>Close all connections and  terminate this process. The other peers also update their connection listd by removing the peer that exits.

## Getting Started
1. **Make sure you have Java Development Kit (JDK) 8 or higher installed.** If you don't, you can download it at https://www.oracle.com/java/technologies/downloads/. To To verify that Java is installed and check the exact JDK version, you can run the following command.
    ```bash
    java -version
    ```
2. **Clone the repository**
    ```bash
    git clone --branch main --single-branch https://github.com/KevinVolkov24/JavaChatApp.git
    cd JavaChatApp
    ```
3. **Compile and run program.** When running it, specify the number of the port on which you want to listed for incomming connections.
    ```bash
    javac chat.java
    java chat <Port Number>
    ```

## Demo Video
You can watch my video briefly explaining the code and demonstrating the app here: https://www.youtube.com/watch?v=CcRSx-oz2Ws&pp=ygUMa2V2aW4gdm9sa292