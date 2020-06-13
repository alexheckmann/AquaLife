<h1>Distributed Systems</h1>

This repository contains an example application of a distributed system based on a message-based combination of peer-to-peer & client server architecture. <br>
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
The broker uses a thread pool of constant size provided by the Java Executor Framework for the processing of incoming messages. There two ways to shut down the server, either by setting a boolean flag that work is done or by using the poison pill pattern (<i><strong>Poisoner.java</strong></i>), instantly shutting down the server.<br><br>
The system implements the Chandy-Lamport algorithm, a snapshot algorithmn used for recording a consistent 
global state of an asynchronous system.

<h1>Getting started</h1>
To run this application you need to
<ol>
<li>verify that you have at least Java 11 installed</li>
<li>add <i><strong>messaging.jar</strong></i> (located in the <i>lib</i> directory) to your library path</li>
<li>run <i><strong>Broker.java</strong></i> before starting any instance of <i><strong>Aqualife.java</strong></i></li>
<li>run at least one instance of <i><strong>Aqualife.java</strong></i>, but multiple clients are supported</li>
</ol>
