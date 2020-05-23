<h1>Distributed Systems</h1>

This repository contains an example application of a distributed system. <br>
It's based on a message-based client server architecture. <br>
The clients (<i><strong>Aqualife.java</strong></i>) are logically 
structured as a ring and are communicating directly with their left and right neighbor; 
the broker (<i><strong>Broker.java</strong></i>) acts as a server, providing registering clients 
with the address of their left and right neighbor. Simultaneously, said clients are provided the 
address of the newly registered client.<br><br>
Each client represents a part of a common aquarium in which virtual fishes can move freely.
Fishes can swim back and forth between clients. New fishes can be spawned by clicking inside the aquarium.
When a fish hits the border of the client it is in, a hand off request is sent to the corresponding neighbor.
Since the system implements the token ring technology, the fish is only handed off to the client's neighbor 
if the client is currently holding the token. <br><br>
The broker uses a thread pool of constant size provided by the Java Executor Framework for processing requests.<br><br>
The system implements the Chandy-Lamport algorithm, a snapshot algorithmn used for recording a consistent 
global state of an asynchronous system.
