/*
 MakerBot Ethernet Bridge
 
 Connects a MakerBot to an Ethernet network through the use of an Arduino
 
 created 3 Sep 2012
 by Eric Barch
 
 */

#include <SPI.h>
#include <Ethernet.h>

// network config
byte mac[] = { 0xDE, 0xAD, 0xBE, 0xEF, 0xFE, 0xED };
IPAddress ip( 192, 168, 0, 200 );


// makerbot server on port 8225
EthernetServer server(8225);

void setup() {
  // initialize the ethernet device
  Ethernet.begin(mac, ip);
  // start listening for clients
  server.begin();
  // Open serial communications
  Serial.begin(38400);
}

void loop() {
  // wait for a new client:
  EthernetClient client = server.available();

  // loop while we've got a connection
  while (client.connected()) {
    
    if (client.available() > 0) {
      // read the bytes incoming from the client
      char thisChar = client.read();
      // echo the bytes to the makerbot over serial
      Serial.write(thisChar);
    }
  
    // is there data available back from the makerbot?
    while (Serial.available() > 0) {
      char inChar = Serial.read();
      if (client.connected()) {
        client.write(inChar); 
      }
    }
  }
}
