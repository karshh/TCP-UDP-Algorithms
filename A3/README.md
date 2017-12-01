CS 456 Assignment 3
By: Utkarsh SHarma

This assignment demonstrates packet transfer from one client to another through a server.

First, run make.
To run the server, execute the following:
./server

To run the client, execute the following:
./client <host> <port> {F/P/G}<code> <filename> <time>

The filename variable is required for P and G code. The time variable is required for the P code. 

NOTABLE FILES

Client.java:
	This file contains the implementation for the client program.
Server.java:
	This file contains the implementation for the server program, whos job is to just transmit data between clients.
client:
	This is a bash script which executes Client.
server:
	This is a bash script which executes Server.



