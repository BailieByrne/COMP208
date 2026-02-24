This is the client for the game itself

-----------------------------------------------------------

Running instructions:
I have added a ps1 file to smooth the running process 
So simply running it will automatically compile and run the java code
Warning: this uses relative pathing so changing the file locations will fuck it up

Quick use:
Get path of run.ps1 (by right clicking in vscode and selecting 'copy path')
Paste that into terminal and run

-----------------------------------------------------------

clientv1 is the first iteration - 
as we are reading from a JSON file which isnt native to java we are using a library which is compiled automatically (through the ps1 file) when running so the user shouldnt need any extra files

if the file looks like a mess in your IDE its because of the libraries - trust me it works (at least on mine it does)

The Json file currently being use is a test file just giving an example - this is not the final product, just a proof of concept to show that data can be stored and access in a 'config' format using a Json file for persistent data storafe

the file itself has a lot of printing as it is essentially the flow of the game so its tracking when events are starting / stopping - you can deactive this by setting debug to false (at the top of client.java). If anyone is to work on this use the built in print function for the debug messages (not mandatory but helpful) and use system.out.println() for essential messages
