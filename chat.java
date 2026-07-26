/*Programmer:  Kevin Volkov, student COMP 429, class #16541
  Program:     Project Assignment
  File:        chat.java
  Description: A chat application for message exchange among remote peers that uses TCP sockets in
               peer connection implementation, handles multiple socket connections, and includes
			   both client-side and server-side code. A command line parameter is taken, which 
			   indicates the port on which the process will listen for incoming connections. When
			   launched, the process works like a UNIX shell, accepting incoming connections and at
			   the same time providing a user interface to offer the following command options:
			       1. help: Display info about the available user interface options
				   2. myip: Display the IP address of this process
				   3. myport: Display the number of the port listening for incomming connections
				   4. connect <destination> <port no>: Establish a new TCP connection
				   5. list: Display a list of all connections this process is handling
				   6. terminate <connection id.>: end the specified connection
				   7. send: <connection id.> <message>: send a message through specified connection
			       8. exit: Close all connections and terminate this process */

import java.net.Socket;                          //for working with sockets in Java        
import java.net.ServerSocket;                    //for creating our server socket
import java.net.BindException;                   //for catching an exception if port already in use
import java.net.ConnectException;                //for catching exception if port is not listening
import java.io.IOException;                      //for catching other socket-related exceptions
import java.io.OutputStream;                     //for sending messages over a connection
import java.io.InputStream;                      //for recieving messages over a connection
import java.net.InetAddress;                     //for getting the IP address of a host
import java.net.UnknownHostException;            //for catching the exception if a host is unknown
import java.util.concurrent.atomic.AtomicBoolean;//for declaring multithread-safe bool variables
import java.util.concurrent.atomic.AtomicInteger;//for declaring multithread-safe int variables
import java.util.concurrent.CopyOnWriteArrayList;//for declaring a multithread-safe array list
import java.util.Scanner;                        //for reading user input
import java.lang.ArrayIndexOutOfBoundsException; //for catching exception if argument is unentered
import java.lang.NumberFormatException;          //for catching exception if argument isn't integer

public class chat //the class should be named the same as this file
{
	//I am using the ANSI color codes to print colored text. The below declared strings allow to
	//refer to these codes by a simple variable name instead of a string of random characters. I
	//learned about these ANSI color codes here:
	//https://www.geeksforgeeks.org/how-to-print-colored-text-in-java-console/
	static final String RED = "\u001B[31m";    //for red text
	static final String CYAN = "\u001b[36m";   //for cyan text
	static final String DEFAULT = "\u001B[0m"; //to reset to default color (white)
	
	static int servPortNum = 0; //to hold the user-entered number that indicates our listening port
	
	static ServerSocket myServSocket = null; //to hold the server socket itself
	
	//This boolean flag indicates whether the port number was entered following "connect" is equal
	//to this process's listening server port number. If so, the program then needs to check if the
	//IP addresses of the connected processes is equal as well, so that a self-connection can be 
	//detected. This is an atomic variable, which means that it can be modified in any thread in
	//the program. I learned about atomic variables here:
	//https://www.geeksforgeeks.org/atomic-variables-in-java-with-examples/
	static AtomicBoolean equalPortNums = new AtomicBoolean(false);
	
	//To store the list of Sockets that correspond to all connections this process is currently 
	//handling. An array list is used so that our list can grow and shrink as elements are added and
	//removed. A CopyOnWriteArrayList is a special type of ArrayList that is thread-safe. An atomic
	//ArrayList is also safe to modify in multiple threads, but its individual elements are not.
	static CopyOnWriteArrayList<Socket> mySockets = new CopyOnWriteArrayList<>();
	
	//Another atomic variable to indicate the position in the above array list of the connection
	//that is being closed in the main thread. This is needed so that the thread in which this
	//this connection is listening knows that this closure isn't an error.
	static AtomicInteger closingConnection = new AtomicInteger(-1);
	
	/*Method: main
	  Input: String[] args - array of string arguments entered by the user in the command prompt
	                         when running this program
	  Description: The main method is the starting point of any Java program. Here, its job is to
	               call the method that starts the server, accept user input, and call the correct
				   method for each user-entered command. */
	public static void main(String[] args)
	{
		startServer(args); //call the method to start our server on the user-entered port number
		
		//From here, the server socket is now listening for TCP requests in a seperate thread
		Scanner myScan = new Scanner(System.in); //Scanner object for reading user input
		String inputStr; //to hold the entire input string entered by the user
		String[] splitInputStr; //to hold array of strings created by seperating the input string
		
		//inform the user what to do
		System.out.println("Welcome to my chat program: Enter a command.");
		System.out.println("To find out what commands are accepted, type \"help\".\n");
		
		while(true) //"endless" loop to take commands until the user types "exit"
		{	
			System.out.print("Chat>"); //print the prompt string
			inputStr = myScan.nextLine().trim();//get user input, remove leading/trailing spaces
			
            //This line splits the input string into an array array of up to 3 strings, using the
            //space characters in the origional string as markers for where to make the splits			
			splitInputStr = inputStr.split("\\s+", 3);
			
			if(splitInputStr[0] == "") //if user just pressed enter or typed only spaces
				continue; //continue to the next iteration
			
			//if the array's length is < 3, increase it's length, inititalize empty elements to ""
			if(splitInputStr.length == 1) //if the has only one element (only command, no args)
				splitInputStr = new String[] {splitInputStr[0], "", ""}; //add 2 new "" elements
			else if(splitInputStr.length == 2) //if array has 2 elements (1 command, 1 arg)
				splitInputStr = new String[] {splitInputStr[0], splitInputStr[1], ""};//add 1 new
			                                                                          //"" element
			switch(splitInputStr[0])//switch statement to call correct method for entered command 
			{
				case "help": //if the user entered "help"
				    showHelp(); //call the method that shows the help text
					break; //exit the switch statement
				case "myip": //if the user entered "myip"
				    showMyIP(); //call the method that shows this host's IP address
					break; //exit the switch statement
				case "myport": //if the user entered "myport"
				    showMyPort(); //call the method that shows our listening server's port number
					break; //exit the switch statement
				case "connect": //if the user entered "connect"
				    connectTo(splitInputStr[1], splitInputStr[2].split("\\s+",2)[0]); //connect to
                    break; //exit the switch statement					
				case "list": //if the user entered "list"                             //a server
				    showList(); //call the method that shows the list of connections
					break; //exit the switch statement
				case "terminate": //if the user entered "terminate":
				    closeConnection(splitInputStr[1]); //call method to close specified connection
					break; //exit the switch statement
				case "send": //if the user entered "send"
				    sendMessage(splitInputStr[1], splitInputStr[2]); //call method to send message
					break; //exit the switch statement
				case "exit": //if the user entered "exit"
				    closeAllAndExit(); //call the method to close all connections and exit program
				default: //if the user entered an unrecognized command, inform them
				    System.out.println(RED + splitInputStr[0] + " is an unrecognized command.");
					System.out.println("Please enter one of this program's accepted commands.\n" + 
					                   DEFAULT);
			}//end switch statement
		}//end while loop
	}//end method main
	
    /*Method: startServer
	  Input: String[] servPortStr - array of strings entered by the user in the command prompt when
	                                running this program that were passed from main. Its first
									element will be used as our server socket's port number.
	  Description: This method validates the user-entered port number, crates the server socket,
	               and starts the thread in which this server socket listens for TCP requests. */
	static void startServer(String[] servPortStr)
	{		
		try //try block in case something goes wrong while reading the user-entered port number
		{ 
		    servPortNum = Integer.parseInt(servPortStr[0]); //convert user-entered number into int
		}//end try block
		catch(ArrayIndexOutOfBoundsException e)
		{//if here, servPortStr[0] is empty, meaning no input was given: inform the user and exit
			System.out.println(RED + "You did not enter a parameter.");
			System.out.println("Please enter a port number next time you run this program.");
			System.out.println("Exiting program. Goodbye!" + DEFAULT);
			System.exit(0); //exit the program
		}//end catch block
		catch(NumberFormatException e)
		{//if here, servPortStr[0] is not an integer and can't be used: inform the user and exit
		    System.out.println(RED + "Your input can't be used as a port number.");
			System.out.println("Please enter an integer next time you run this program.");
			System.out.println("Exiting program. Goodbye!" + DEFAULT);
			System.exit(0); //exit the program	
		}//end catch block
		
		//Port numbers are in range 0 - 65535, but ports 0 - 1023 are well-known ports (like port
		//80 for HTTP and 443 for HTTPS), which require special privaleges to use that this program
        //doesn't provide. Ports 49152-65535 should also be avoided because these are ephemeral
        //ports that are dynamically selected by the operating system to make outgoing requests
		if(servPortNum < 1024 || servPortNum > 49151)
		{// if here, port number is not from 1024 to 49151: inform the user and exit
            System.out.println(RED + "The port number you entered is not from 1024 to 49151.");
			System.out.println("Please enter a number from this range next time you run this " +
			                   "program.");
			System.out.println("Exiting program. Goodbye!" + DEFAULT);
			System.exit(0); //exit the program	
		}//end if statement
		
		try //try block in case something goes wrong while creating our server socket
		{
			myServSocket = new ServerSocket(servPortNum); //create the server socket
		}//end try block
		catch(BindException e)
	    {//if here, the indicated port is already in use: inform the user and exit
			System.out.println(RED + "Port " + servPortNum + " is already in use.");
			System.out.println("Please free up port " + servPortNum + " or try a different " +
				               "port next time you run this program.");
		    System.out.println("Exiting program. Goodbye!" + DEFAULT);
			System.exit(0); //exit the program
	    }//end catch block
		catch(IOException e)
		{//if here, something else went wrong in creating the server socket: inform user and exit
		    System.out.println(RED + "Couldn't create your server socket." + DEFAULT);
		    System.out.println("Exiting program. Goodbye!" + DEFAULT);
			System.exit(0); //exit the program
        }//end catch block
		
		//start a new thread to listen for incomming TCP requests so that this is done AT THE SAME
		//TIME as sccepting user commands in the main thread per assignment requirements.
		new Thread(() ->
		{
			while(true) //"endless" loop to continue listening until this process has exited
			{
				try //try block in case something goes wrong in recievong a TCP request
				{
					//This line waits until the server recieves a TCP request and creates the 
					//corresponding client socket when this happends.
					Socket connection = myServSocket.accept(); //accept the TCP request
					
					//check for a self-connection by checking if our server port and the client
					//that connected to it have both equal ports and equal IPs. If so, this is a
					//self-connection and we need to end it.
					if(equalPortNums.get() && 
					   connection.getInetAddress().equals(connection.getLocalAddress()))
					{
						connection.close(); //close the connection to the client
						continue; //scontinue to the next loop iteration   
					}//end if statement				
			    					
				    mySockets.add(connection); //add this connection to our array list
					
				    //inform the user of the newly connected client and print its IP and port
				    System.out.println("\nA new client has connected to your server socket.");
				    System.out.println("Its IP address is " + CYAN + 
					                   connection.getInetAddress().getHostAddress() + DEFAULT +
					                   " and its port number is " + CYAN + connection.getPort() +
								       DEFAULT + ".\n");
									   
				    System.out.print("Chat>"); //print this again	
					
					//create another new thread, in which the method handleConnection is excecuted 
				    //to handle the individual connection with this client
                    new Thread(() -> handleConnection(connection)).start();
			    }//end try block
				catch(IOException e)
				{//if here, something went wrong in accepting a request or closing the socket
					continue; //continue to the next iteration
				}//end catch block
			}//end while loop
			
		}).start(); //start the new thread we just declared
	}//end method startServer
	
	/*Method: handleConnection
	  Input: Socket connection - socket that was created when a connection was established
	  Description: Called when either the server accepts a TCP request or the client has
	               sucessfully connected a server. Its job is to listen for incomming messages
				   and inform the user if the connection was closed form the other peer. */
	static void handleConnection(Socket connection)
	{
		try //try block in case something goes wrong while listening for incomming messages
		{
			InputStream iStream = connection.getInputStream(); //input stream from the socket
		    byte[] buffer = new byte[100]; //to recieve messages <= 100 chars (100 bytes in UTF-8)
		
		    while(true)//"endless" loop to keep listening until the connection is closed
		    {
				//This line pauses until a message is recieved. When this finally happends, it
				//counts the number of bytes that the message contains.
				int bytesRead = iStream.read(buffer);
				
				if(bytesRead == -1)
				{//if here, -1 bytes means that the remote client closed the connection
					System.out.println("\nConnection " + CYAN + (mySockets.indexOf(connection)+1) +
                                       DEFAULT + " of your list has been closed by the remote " +
									   "client.\n");
				    break; //exit the loop
				}//end if statement
				
				//convert the recieved message into a string with UTF-8, where 1 byte = 1 char
				String receivedMessage = new String(buffer, 0, bytesRead, "UTF-8");
                
				//print the message as instructed for the assignment
				System.out.println("\nMessage received from " + CYAN + 
				                   connection.getInetAddress().getHostAddress() + DEFAULT);
                System.out.println("Sender's Port: " + CYAN + connection.getPort() + DEFAULT);
                System.out.println("Message: \"" + CYAN + receivedMessage + DEFAULT + "\"\n");
				System.out.print("Chat>");//print this again
		    }//end while loop
		}//end try block
		catch(IOException e)
		{//if here, something went wrong with the connection: inform the user
		    if(closingConnection.get() != mySockets.indexOf(connection) && 
			   closingConnection.get() != -2)//if this was unintentional
			    System.out.println(RED + "\nAn error occured with connection " + 
			                       (mySockets.indexOf(connection) + 1) + " of your list.\n" +
 								   DEFAULT);
		}//end catch block
		finally //finally block excecutes if end of try block was reached or an exception occured
		{
			if(!connection.isClosed()) //check if the connection is closed
			{
				try //try block in case something goes wrong while closing the connection
				{
				    connection.close(); //close the connection
				}//end try block
				catch(IOException e)
				{//if here, there was an error in closing the connection
					System.out.println(RED + "Could not close connection " + 
					                   (mySockets.indexOf(connection)+1) + "of your list.\n" + 
									   DEFAULT);
				}//end catch block
			}//end if statement
          
			if(closingConnection.get() == mySockets.indexOf(connection) ||
			   closingConnection.get() == -2)//if closure intentional
			{
			    if(closingConnection.get() != -2)
					closingConnection.set(-1); //reset this variable
			}//end if block
			else //else the closure was unintentional
				System.out.print("Chat>"); //print this again
			
			mySockets.remove(connection); //remove the socket from our array list
		}//end finally block
	}//end method handleConnection
	
	/*Method: showHelp
	  Description: Called when the user enters "help" and displays the list of commands accepted by
	               this program along with an explaination of each. */
	static void showHelp()
    {   //Print the command information to the user
	    System.out.println("The following commands are accepted by this program:");
		System.out.println("1. " + CYAN + "help" + DEFAULT + ": Display information about the " +
                           "available user interface options.");
		System.out.println("2. " + CYAN + "myip" + DEFAULT + ": Display the IP address of this " +
		                   "process. This IP is not your local address (127.0.0.1), but");
        System.out.println("   your computer's actual IP.");
		System.out.println("3. " + CYAN + "myport" + DEFAULT + ": Display the port on which " +
		                   "this process is listening for incoming connections.");
	    System.out.println("4. " + CYAN + "connect <destination> <port no>" + DEFAULT + ": " +
		                   "Establish a new TCP connection to the specified <destination>");
	    System.out.println("   at the specified <port no>. The <destination> is the IP address " +
		                   "of the computer. Any attempt to");
		System.out.println("   connect to an invalid IP will be rejected and suitable error " +
		                   "message will be displayed. Success");
		System.out.println("   or failure in connections between two peers will be indicated by " +
                           "both the peers using suitable");
		System.out.println("   messages. Self-connections and duplicate connections will be " +
		                   "flagged with suitable error");
		System.out.println("   messages.");
		System.out.println("5. " + CYAN + "list" + DEFAULT + ": Display a numbered list of all " +
		                   "the connections this process is handling. This numbered list");
		System.out.println("   includes connections initiated by this process and connections " +
		                   "initiated by other processes. The");
		System.out.println("   output displays the IP address and the listening port of all the " +
		                   "peers the process is connected"); 
		System.out.println("   to.");
	    System.out.println("   E.g., id: IP address      Port No.");
        System.out.println("          1: 192.168.21.20   4545");
        System.out.println("          2: 192.168.21.21   5454");
        System.out.println("          3: 192.168.21.23   5000");
        System.out.println("          4: 192.168.21.24   5000");
		System.out.println("6. " + CYAN + "terminate <connection id.>" + DEFAULT + ": Terminate " +
		                   "the connection listed under the specified number when LIST");
	    System.out.println("   is used to display all connections. E.g., terminate 2. In this " +
		                   "example, the connection with");
		System.out.println("   192.168.21.21 will end. An error message is displayed if a valid " +
		                   "connection does not exist as");
		System.out.println("   number 2. If a remote machine terminates one of your " +
		                   "connections, a message is also displayed.");
		System.out.println("7. " + CYAN + "send <connection id.> <message>" + DEFAULT + ": (For " +
		                   "example, send 3 Oh! This project is a piece of cake). This");
		System.out.println("   will send the message to the host on the connection that is " +
		                   "designated by the number 3 when");
		System.out.println("   command \"list\" is used. The message to be sent can be up-to " +
		                   "100 characters long, including");
		System.out.println("   blank spaces. On successfully executing the command, the sender " +
		                   "displays \"Message sent to");
		System.out.println("   <connection id>\" on the screen. On receiving any message from " +
		                   "the peer, the receiver displays");
		System.out.println("   the received message along with the sender information. Eg. If a " +
		                   "process on 192.168.21.20 sends");
		System.out.println("   a message to a process on 192.168.21.21 then the output on " +
		                   "192.168.21.21 when receiving a ");
		System.out.println("   message displays the following:");
		System.out.println("       Message received from 192.168.21.20");
		System.out.println("       Sender’s Port: <The port no. of the sender>");
		System.out.println("       Message: \"<received message>\"");
        System.out.println("8. " + CYAN + "exit" + DEFAULT + ": Close all connections and " +
		                   " terminate this process. The other peers also update their");
        System.out.println("   connection listd by removing the peer that exits.\n");						   
    }//end method showHelp
	
	/*Method: showMyIP
	  Description: Called when the user enters "myip" and displays IP address of this process. */
    static void showMyIP()
    {
		try//try block in case this host is unrecognized
		{
			InetAddress myHost = InetAddress.getLocalHost(); //define a variable to refer to host
			String myIp = myHost.getHostAddress(); //extract the IP address of the host
			System.out.println("This host's IP address is " + CYAN + myIp + DEFAULT +".\n");//print
		}//end try block                                                                    //IP
		
		catch(UnknownHostException e)
		{//if here, the host was unrecognized: Inform the user: shouldn't happen if server created
			System.out.println(RED + "Could not get IP address because this host is " +
			                   "unrecognized." + DEFAULT);
		}//end catch block
    }//end method showMyIP
	
	/*Method: showMyPort
	  Description: Called when the user enters "myport" and displays their entered port number. */
    static void showMyPort()
    {
	    System.out.println("Incomming connections are being listened for on port " + CYAN + 
		                   servPortNum + DEFAULT + ".\n"); //print the port number
    }//end method showMyPort
	
	/*Method: connectTo
	  Input: String destIP - IP address of target host passed as a string from main
	         String destPortStr - port number of target passed as a string from main
	  Description: Called when the user enters "connect" and sends a TCP request to the specified
	               destination IP and port. */
    static void connectTo(String destIP, String destPortStr)
    {
		if(destIP.equals("") || destPortStr.equals(""))//if either IP or port number not entered
		{
			//inform the user their arguments are invalid
	        System.out.println(RED + "Not enough arguments: You must enter both the IP address " +
			                   "and port number of a server to connect to.\n" + DEFAULT);
			return; //return back to main method
		}//end if statement
		
		//regular expression representing IPv4 format, which the entered IP will be checked against
		String IPformat = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[" +
		                  "01]?[0-9][0-9]?)$";
						  
		if(!destIP.matches(IPformat)) //if IP string doesn't matches IPv4 pattern X.X.X.X, where
		{                             //each X is an int from 0 to 255
		    //inform user IP address is invalid
		    System.out.println(RED + destIP + " is not a valid IPv4 address.");
			System.out.println("A valid IPv4 address is in the form X.X.X.X, where each X is an " +
			                   "integer from 0 to 255.\n" + DEFAULT);
			return; //return back to main method
		}//end if statement
			
		int destPortNum = 0; //to hold the destination port in numerical form
		
		try //try block in case error occurs in converting port string to int
		{
			destPortNum = Integer.parseInt(destPortStr); //convert port number from string to int
		}//end try block
		catch(NumberFormatException e)
		{//if here, the user did not enter an integer as the port number: inform them
			System.out.println(RED + "Invalid port number: You must enter an integer to represent "
			                         + "a port number of the server.\n" + DEFAULT);
			return; //return back to main method
		}//end catch block
		
		//server port we connect to should be in range 1024 - 49151
		if(destPortNum < 1024 || destPortNum > 49151)
		{// if here, destination port number is not from 1024 to 49151: inform user
            System.out.println(RED + "Invalid port number: You must enter a port number from " +
			                         "1024 to 49151 to connect to.\n" + DEFAULT);
			return; //return back to main method
		}//end if statement
		
		if(destPortNum == servPortNum)//if user-entered port is same as this process's server port
		    equalPortNums.set(true); //set the flag to check for self-connections
		
        //check for self-connections (ones that already exist		
		for(Socket s: mySockets) //loop through our list of sockets
		{
		    if(destPortNum == s.getPort() && destIP.equals(s.getInetAddress().getHostAddress()))
			{//if here, duplicate connection detected: inform user
				System.out.println(RED +"Invalid input: This process is already connected to "+
                                   destIP + ", port " + destPortNum + ".");
			    System.out.println("Duplicate connections are not accepted.\n" + DEFAULT);
				 return; //return back to main method
			}//end if statement
	    }//end for loop
		
		try //try block in case something goes wrong while establishing a connection
		{
			Socket connection = new Socket(destIP, destPortNum); //send TCP request
			
			//check for self-connections by comparing ports and IP addresses
			if(equalPortNums.get() && 
			   connection.getInetAddress().equals(connection.getLocalAddress()))
			{//if here, self-connection detected: inform use and close it
				System.out.println(RED + "Invalid input: Self-connections are not accepted.");
				System.out.println("You must enter the IP address and port number of a " +
				                   "different process.\n" + DEFAULT);
				connection.close(); //close the connection
				return; //return back to main method
			}//end if statement
			
			//check for duplicate connections again in case entered IP mapped to already connected
		    for(Socket s: mySockets) //loop through our list of sockets
		    {
		       if(destPortNum == s.getPort() && 
			      connection.getInetAddress().equals(s.getInetAddress()))
			    {//if here, duplicate connection detected: inform user
				    System.out.println(RED +"Invalid input: This process is already connected to "+
                                   connection.getInetAddress().getHostAddress() + ", port " + 
								   destPortNum + ".");
				    System.out.println("Duplicate connections are not accepted.\n" + DEFAULT);
					connection.close(); //close the connection
				    return; //return back to main method
			    }//end if statement
		   }//end for loop
			
			mySockets.add(connection); //add this connection to our list
			
			//inform the user that the connection was sucessful
		    System.out.println("Sucessfully connected to the Server with IP address " + CYAN +
			                   connection.getInetAddress().getHostAddress() + DEFAULT + 
							   " and port number " + CYAN + destPortNum + DEFAULT + ".\n");
							   				 
			new Thread(() -> handleConnection(connection)).start(); //handle connection in a
		}//end try block	                                        //seperate thread                                                
		catch(ConnectException e)
		{//if here, the entered IP and port is unreachable (no active server socket)
	        System.out.println(RED + destIP + " does not have a server socket running on port " +
                               destPortNum + ".\n" + DEFAULT);
		}//end catch block
		catch(IOException e) 
		{//if here, some other error occured in creating the connection
            System.out.println(RED + "Failed to connect to " + destIP + " through port " +
			                   destPortNum + ".\n" + DEFAULT );
		}//end catch block
		finally//finally block is excuted no matter what
		{
		    equalPortNums.set(false); //set this flag back to false
		}//end finally block
    }//end method connectTo
	
	/*Method: showList
	  Description: Called when the user enters "list" and displays the list of all connections this
	               process is currently handling. */
    static void showList()
    {
		if(mySockets.isEmpty()) //check if our list is currently empty
		{//if here, our list is empty (no active connections): inform user
			System.out.println(RED + "This process is not currently handling any connections.");
		    System.out.println("Once a connection involving this process is established, you " +
			                   "will be able to see it here.\n" + DEFAULT);
		}//end if statement
		else
		{//if here, our list is not currently empty: display its contents
	        System.out.println("id: IP address      Port No.");
			for(int i=0; i<mySockets.size(); i++)
				System.out.printf("%2d: " + CYAN + "%-15s %d%n" + DEFAULT, i + 1, 
			                      mySockets.get(i).getInetAddress().getHostAddress(),
								  mySockets.get(i).getPort());
								  
			System.out.print("\n" + DEFAULT); //skip a line
		}//end else block
    }//end method showList
	
	/*Method: closeConnection
	  Input: String idStr - The ID (in our list) of the connection that the user wants to close 
	  Description: Called when the user enters "terminate", and closes the connection in our list
	               specified by the entered id. */
    static void closeConnection(String idStr)
    {
		if(mySockets.size() == 0)
		{//if here, there are no connections to terminate: inform the user
			System.out.println(RED + "This process is not currently handling any connections.");
			System.out.println("You must have a connection established before you can terminate " +
			                   "it.\n" + DEFAULT);
			return; //return back to the main method
		}//end if statement
		
		if(idStr == "") //check if the id string is empty
		{//if here, an id was not entered :inform the user
			System.out.println(RED + "You did not enter the ID of a connection to terminate.\n" +
			                   DEFAULT);
			return; //return back to main method
		}//end if statement
		
		int idNum = 0; //to hold the user-entered ID in numerical form
		
		try //try block in case something goes wrong while reading the user-entered id
		{ 
		    idNum = Integer.parseInt(idStr); //convert user-entered number into int
		}//end try block
		catch(NumberFormatException e)
		{//if here, idStr is not an integer and can't be used: inform the user
		    System.out.println(RED + "Invalid ID: You must enter an integer number to represent " +
			                   "the connection you want to terminate.\n" + DEFAULT);
			return; //return back to main method
		}//end catch block
		
		if(idNum<=0 || idNum>mySockets.size()) //check if the specified connection id exists
		{//if here, a number not from 1 to mySockets.size() was entered: inform the user 
			System.out.println(RED + "Invalid ID: None of your connections are represented by " +
			                   idNum + ".");
			System.out.println("This process is currently handling " + mySockets.size() +
			                   " connections.");
			System.out.println("That means you must enter a positive ID number of at most " + 
			                   mySockets.size() + ".\n" + DEFAULT);
			return; //return back to main method
		}//end if statement
		
		//ID validated: now we can close the corresonding connection
		try //try block in case closing the connection goes wrong
		{
			closingConnection.set(idNum - 1); //so the other thread knows the closure isn't an error
			mySockets.get(idNum - 1).close(); //close the connection
			System.out.println("Connection " + CYAN + idNum + DEFAULT + " has been sucessfully " +
			                   "closed.\n");
		}//end try block
		catch(IOException e)
		{//if here, there was an error on closing the connection
			System.out.println(RED + "Connection " + idNum + "could not be closed.\n" + DEFAULT);
		}//end try block
    }//end method closeConnection
	
	/*Method: sendMessage
	  Input: String idStr - The ID (in our list) of the connection through which the user wants to
                            send a message
             String message - the message that the user wants to send							
	  Description: Called when the user enters "send", and sends the message specified by the user
                   through the connection specified by the entered id number. */
    static void sendMessage(String idStr, String message)
    {
		if(mySockets.size() == 0)
		{//if here, there are no connections to send messages through: inform the user
			System.out.println(RED + "This process is not currently handling any connections.");
			System.out.println("You must have a connection established before you can send " +
			                   "messages.\n" + DEFAULT);
			return; //return back to the main method
		}//end if statement
		
		if(idStr.equals("") || message.equals(""))
		{//if here, the user did not enter enough parameters: inform them
			System.out.println(RED + "Not enough arguments: You must enter both a connection ID " +
			                   "and a message to send.\n" + DEFAULT);
			return; //return back to the main method
		}//end if statement
		
		int idNum; //to hold the user-entered ID in numerical format
		
    	try //try block in case something goes wrong while reading the user-entered id
		{ 
		    idNum = Integer.parseInt(idStr); //convert user-entered number into int
		}//end try block
		catch(NumberFormatException e)
		{//if here, idStr is not an integer and can't be used: inform the user
		    System.out.println(RED + "Invalid ID: You must enter an integer to represent " +
			                   "the connection through which to send the message.\n" + DEFAULT);
			return; //return back to main method
		}//end catch block
		
		if(idNum<=0 || idNum>mySockets.size()) //check if the specified connection id exists
		{//if here, a number not from 1 to mySockets.size() was entered: inform the user 
			System.out.println(RED + "Invalid ID: None of your connections are represented by " +
			                   idNum + ".");
			System.out.println("This process is currently handling " + mySockets.size() +
			                   " connections.");
			System.out.println("That means you must enter a positive ID number of at most " + 
			                   mySockets.size() + ".\n" + DEFAULT);
			return; //return back to main method
		}//end if statement
		
		if(message.length() > 100) //check if the message is greater than 100 characters
		{//if here, the message length is over 100 characters: inform user
			System.out.println(RED + "Invalid message: your message contains more than 100 " +
			                   "characters.");
			System.out.println("Please enter a shorter message.\n" + DEFAULT);
			return; //return to main method
		}//end if statement
		
		//arguments validated: now send the message
		try //try block in case something goes wrong while sending the message
		{
		    OutputStream oStream = mySockets.get(idNum-1).getOutputStream(); //get output stream
		    oStream.write(message.getBytes("UTF-8")); //send the message in UTF-8 (1 byte per char)
		    oStream.flush(); //flush the output stream immediatly after sending the message
		    System.out.println("Your message has been sucessfully sent through connection " + 
			                   CYAN + idNum + DEFAULT + ".\n");
		}//end try block
		catch(IOException e)
		{//if here, something went wrong in sending the message: inform the user
			System.out.println(RED + "There was an error in sending the message." + DEFAULT);
		}//end catch block
    }//end method sendMessage
	
	/*Method: closeAllAndExit			
	  Description: Called when the user enters "exit", and closes all of the connections that are
	               that thos process is currently handling. */
	static void closeAllAndExit()
	{
		if(!myServSocket.isClosed())
		{//if here, the server socket is not closed: close it.
			try //try block in case something goes wrong while closing the server socket
			{
				myServSocket.close(); //close the server socket
				System.out.println("Your server Socket has been closed.");//inform about closure
			}//end try block
			catch(IOException e)
			{//if here, there was an error in closing the server socket: inform the user
				System.out.println(RED + "Could not close your server socket." + DEFAULT);
			}//end catch block
		}//end if block
		
		closingConnection.set(-2); //so that other threads know this closure is intentional
		
		try //try block in case something goes wrong while closing the sockets
		{
		    for(Socket s: mySockets) //loop through the list of sockets to close them all
		        if(!s.isClosed()) //check if socket s is not closed
		            s.close(); //if so, close it
			System.out.println("All your connections have been closed."); //inform of closure
		}
		catch(IOException e)
		{//if here, one or more of the sockets were not closed: inform the user.
		    System.out.println(RED + "Could not close one or more of your connections." + DEFAULT);
	    }//end catch block

		System.out.println("Exiting Program, Goodbye!"); //inform that the program exiting
		System.exit(0); //exit the program
	}//end method closeAllAndExit
}//end class chat