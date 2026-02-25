This is the client for the game itself

-----------------------------------------------------------

Running instructions:
I have added a ps1 file to smooth the running process 
So simply running it will automatically compile and run the java code
Warning: this uses relative pathing so changing the file locations will fuck it up

Quick use:
Get path of runV1.ps1 (by right clicking in vscode and selecting 'copy path')
Paste that into terminal and run

-----------------------------------------------------------
general

clientv1 is the first iteration

if the file looks like a mess in your IDE its because of the libraries - trust me it works (at least on mine it does)

the file itself has a lot of printing as it is essentially the flow of the game so its tracking when events are starting / stopping - you can deactive this by setting debug to false (at the top of client.java). If anyone is to work on this use the built in print function for the debug messages (not mandatory but helpful) and use system.out.println() for essential messages

-----------------------------------------------------------
storage

as we are reading from a JSON file which isnt native to java we are using a library which is compiled automatically (through the ps1 file) when running so the user shouldnt need any extra files

The Json file currently being use is a test file just giving an example - this is not the final product, just a proof of concept to show that data can be stored and access in a 'config' format using a Json file for persistent data storage. Its fairly malleable right now as long as the config class in Client.java matches the format of the configs.json file

The client assumes that the config data is not null - in theory it should never be empty and the JSON by default will have default values - changing these in settings will change the JSON file

It does this by mostly handling the data client side (within the config class)
NOTE:
updateConfig() and saveConfigs() are very different

updateConfig() takes in a field name and value then searches for that field name in the class and updates if it its valid and the data type is valid

saveConfigs() updates the actual values in the JSON file by using pattern matching 

on the user side they are essentially the same thing but the distinction is very important particularly when accessing this data - the data stored in the config class is always prioritised as it is faster and generally more consistent, reading and writing from the JSON file takes time 

Essentially:

game loads -> get data from JSON
change data -> update local values -> update JSON to match local values
read data -> read local values

----------------------
Architecture 

So what is the client architecture - essentially there are 'levels' to each loop, think of it as as how a file system would work. Right at the top we have we have the main loop Init(). From there it can then initiate game states such as the main menu or the actual game - this in turn begins its own state loop in which things can happen - when traversing between these loops we use return allowing information to be passed between which most importantly allows for transitions between states. Furthermore we can inbed loops so within loops such that for example the game can have different fases that play in a specific order and are controlled by the main overall game loop. If you actually read this I will be very suprised. More importantly it definitevly ends a process when not being used so going from the game to the main menu will end the game loop (and thus reset it). This can of course be changed and probably will be in future
