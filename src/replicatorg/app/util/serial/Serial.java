/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Serial - serial port wrapper

 Copyright (c) 2004-05 Ben Fry & Casey Reas
 Copyright (c) 2010 Adam Mayer

 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.

 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General
 Public License along with this library; if not, write to the
 Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 Boston, MA  02111-1307  USA
 */

package replicatorg.app.util.serial;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.RXTXVersion;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import replicatorg.app.Base;
import replicatorg.app.exceptions.SerialException;
import replicatorg.app.exceptions.UnknownSerialPortException;


public class Serial implements SerialPortEventListener, SerialInterface {

	/**
	 * We maintain our own set of ports in current use, because RXTX can't be trusted.
	 * (NB: may be obsoleted at some point on some platforms?)
	 */
	private static Set<Serial> portsInUse = new HashSet<Serial>();

	
	/** True if the device is connected **/
	private AtomicBoolean connected = new AtomicBoolean(false);
	
	// Properties can be passed in for default values.
	// Otherwise, we default to 9600 N81
	private SerialPort port;
	private String name;
	private int rate;
	private int parity;
	private int data;
	private int stop;
	
	/**
	 * The amount of time we're willing to wait for a read to timeout.  Defaults to 500ms.
	 */
    private static int TIMEOUT_DEFAULT = 500; 
	private int timeoutMillis = TIMEOUT_DEFAULT;            
	
	private ByteFifo readFifo = new ByteFifo();
	
	public final AtomicReference<SerialFifoEventListener> listener =
		new AtomicReference<SerialFifoEventListener>();
	
	private InputStream input;
	private OutputStream output;
	
	/**
	 * Scan the port ids for a list of potential serial ports that we can use.
	 * @return A vector of serial port names and availability information.
	 */
	public static Vector<Name> scanSerialNames()
	{
		
		Vector<Name> v = new Vector<Name>();
		try {
			Enumeration<?> portList = CommPortIdentifier.getPortIdentifiers();
			while (portList.hasMoreElements()) {
				CommPortIdentifier portId = (CommPortIdentifier) portList.nextElement();
				if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
					if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
						Name sn = new Name(portId.getName(),!portId.isCurrentlyOwned());
						v.add(sn);
					}
				}
			}
		} catch (Exception e) {
			Base.logger.fine("problem scanning SerialPorts: " + e);
		}
		Base.logger.fine("RXTX Version" + RXTXVersion.getVersion());
		
		
		// In-use ports may not end up in the enumeration (thanks, RXTX), so
		// we'll scan for them, and insert them if necessary.  (The app wants
		// to display in-use ports to reduce user confusion.)
		for (SerialInterface port: portsInUse) {
			Name n = new Name(port.getName(),false);
			boolean contains = false;
			for (Name vi : v) { // vector.contains doesn't use comparable.
				if (vi.compareTo(n) == 0) {
					contains = true; 
					break; 
				}
			}
			if (!contains) { v.add(n); }
		}

		// Linux: scan the by-id directory and see if we can find the ids of the cables.
		if (Base.isLinux()) {
			Pattern idPattern = Pattern.compile("(FTDI_TTL232R_|usb-Arduino__www.arduino.cc__Arduino_Uno_)([^-]*)");
			File portDir = new File("/dev/serial/by-id/");
			if (portDir.exists() && portDir.isDirectory()) {
				for (File file : portDir.listFiles()) {
					Matcher match = idPattern.matcher(file.getPath());
					if (match.find()) try {
						String canonical = file.getCanonicalFile().getPath();
						for (Name m : v) {
							if (m.getName().equals(canonical)) {
								m.setAlias(match.group(2));
							}
						}
					} catch (IOException ioe) {
						// pass 
					}
				}
			}
			/// scan on linux the 'By-Id' value, and try to verify by ID if we have a MakerBot Inutries USB device
			Pattern makerBotByIdPattern = Pattern.compile("usb-MakerBot_Industries_(MightyBoard)_([^-]*)");
			if (portDir.exists() && portDir.isDirectory()) {
				for (File file : portDir.listFiles()) {
					/// try to match our ex
					Matcher match = makerBotByIdPattern.matcher(file.getPath());
					if ( match.find() ) 
					{
						try {
							String canonical = file.getCanonicalFile().getPath();
							for (Name m : v) {
								/// if any name matchines, we can verify we have a verified MBI device.
								if (m.getName().equals(canonical)) {
									m.setAlias("'MightyBoard " + match.group(2));
									m.setHardwareId(UsbHardwareId.MIGHTY_BOARD);
								}
							}
						} catch (IOException ioe) {
							// pass 
						}
					}
				}
			}
		}
		
		//<NETWORK PORT CODE>
		//lets add an item which points to the network port
		Name n = new Name("192.168.0.200:8225", "printer@192.168.0.200", true);
		v.add(n);

		return v;
	}

	public Serial(String portName, int baudRate, char parity, int dataBits, int stopBits) throws SerialException {
		init(portName, baudRate, parity, dataBits, stopBits);
	}
	
	public Serial(String name) throws SerialException {
		init(name,38400,'N',8,1);
	}
	
	/* (non-Javadoc)
	 * @see replicatorg.app.util.serial.SerialInterface#getName()
	 */
	public String getName() { return name; }
	

	private CommPortIdentifier findPortIdentifier(String name) {
		Enumeration<?> portList = CommPortIdentifier.getPortIdentifiers();
		while (portList.hasMoreElements()) {
			CommPortIdentifier id = (CommPortIdentifier)portList.nextElement();
			if (id.getPortType() == CommPortIdentifier.PORT_SERIAL && 
					id.getName().equals(name)) {
				return id;
			}
		}
		return null;
	}
	

	private void init(String name, int rate, char parity, int data, float stop) throws SerialException {
		// Prepare parameters
		this.name = name;
		this.rate = rate;
		this.parity = SerialPort.PARITY_NONE;
		if (parity == 'E')
			this.parity = SerialPort.PARITY_EVEN;
		if (parity == 'O')
			this.parity = SerialPort.PARITY_ODD;
		this.data = data;
		this.stop = (int)stop;
		if (stop == 1.5f)
			this.stop = SerialPort.STOPBITS_1_5;
		if (stop == 2)
			this.stop = SerialPort.STOPBITS_2;
		// Attempt to find the port identifier for the designated name
		CommPortIdentifier portId = findPortIdentifier(name);
		if (portId == null) {
			throw new UnknownSerialPortException(name);
		}
		// Attempt to open the given port
		try {
			port = (SerialPort)portId.open("replicatorG", 2000);
			port.setSerialPortParams(this.rate, this.data, this.stop, this.parity);

			input = port.getInputStream();
			output = port.getOutputStream();
			port.addEventListener(this);
			port.notifyOnDataAvailable(true);
		} catch (PortInUseException e) {
			throw new SerialException(
					"Serial port '" + name
					+ "' already in use.  Try quiting any programs that may be using it.");
		} catch (Exception e) {
			throw new SerialException("Error opening serial port '" + name + "'.", e);
		}
		portsInUse.add(this);
		
		connected.set(true);
	}

	
	/* (non-Javadoc)
	 * @see replicatorg.app.util.serial.SerialInterface#dispose()
	 */
	public synchronized void dispose() {
		connected.set(false);
		
		if (port != null) {
			port.removeEventListener();
		}
		
		if (input != null) {
			try {
				input.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			input = null;
		}
		
		if (output != null) {
			try {
				output.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			output = null;
		}
		
		if (port != null) {
			port.close();
			port = null;
		}
		
		portsInUse.remove(this);
	}

	/* (non-Javadoc)
	 * @see replicatorg.app.util.serial.SerialInterface#pulseRTSLow()
	 */
	public void pulseRTSLow() {
		port.setDTR(false);
		port.setRTS(false);
		try {
			Thread.sleep(100);
		} catch (java.lang.InterruptedException ie) {
		}
		port.setDTR(true);
		port.setRTS(true);
	}
	
	/**
	 * polls the readFifo for new bytes. If numberOfBytes bytes are received or the 
	 * wait times out the method returns zero. If a interrupt exception is thrown 
	 * the method returns -1.
	 * @param numberOfBytes
	 * @return
	 */
	private int waitForBytes(int numberOfBytes)
	{
		try {
			long to = System.currentTimeMillis() + timeoutMillis;
			while (System.currentTimeMillis() < to && readFifo.size() < numberOfBytes)
			{
				/*
				 * Wait until we timeout or a byte is received (which will notify this 
				 * method). readFifo notifies for each byte received.
				 */
				synchronized (readFifo) {
					readFifo.wait(timeoutMillis);
				}
			}
		} catch (InterruptedException e) {
			// We are most likely amidst a shutdown.  Propagate the interrupt
			// status.
			Thread.currentThread().interrupt();
			return -1;
		}
		return 0;
	}
	
	/* (non-Javadoc)
	 * @see replicatorg.app.util.serial.SerialInterface#read()
	 */
	public int read() {
 		//wait for the fifo to fill
		if (waitForBytes(1) == -1) return -1;
		//read the fifo
		synchronized(readFifo) {
			if (readFifo.size() > 0) {
				byte b = readFifo.dequeue();
				return b & 0xff; 
			} else {
				// dial down timeout error reporting if timeoutMillis is set
				// below the default.  In this case, the packets will time out
				// frequently and messaging will flood the console
				if(timeoutMillis < TIMEOUT_DEFAULT){
					Base.logger.finest("Read timed out.");
				}
				else{
					Base.logger.warning("Read timed out.");
				}
				return -1;
			}
		}
	}

	/* (non-Javadoc)
	 * @see replicatorg.app.util.serial.SerialInterface#read(byte[])
	 */
 	public int read(byte bytes[]) {
 		//wait for the fifo to fill
		if (waitForBytes(bytes.length) == -1) return -1;
		//read the fifo
		synchronized(readFifo) {
			int idx = 0;
			while (readFifo.size() > 0 && idx < bytes.length) {
				bytes[idx++] = readFifo.dequeue();
			}
			return idx;
		}
	}

	/* (non-Javadoc)
	 * @see replicatorg.app.util.serial.SerialInterface#write(byte[])
	 */
	public void write(byte bytes[]) {
		if (!connected.get()) {
			Base.logger.severe("serial disconnected");
			return;
		}
		
		try {
			output.write(bytes);
			output.flush(); // Reconsider?

		} catch (Exception e) { // null pointer or serial port dead
			Base.logger.severe( "serial error: \n" + e.getMessage() );
		}
	}

	/* (non-Javadoc)
	 * @see replicatorg.app.util.serial.SerialInterface#write(java.lang.String)
	 */
	public void write(String what) {
		write(what.getBytes());
	}


	/* (non-Javadoc)
	 * @see replicatorg.app.util.serial.SerialInterface#setTimeout(int)
	 */
	public void setTimeout(int timeoutMillis) {
		this.timeoutMillis = timeoutMillis;
	}


	/* (non-Javadoc)
	 * @see replicatorg.app.util.serial.SerialInterface#clear()
	 */
	public void clear() {
		synchronized (readFifo) {
			// If we're eating more than 255 characters, then there's a serious error:
			// Either the machine is jabbering, or there's a problem with our serial
			// connection.
			int maxEats = 255;
			
			try {
				while (input.available() > 0 && maxEats > 0) {
					input.read();
					Thread.sleep(1);
					maxEats--;
				}
			} catch (IOException e) {
				// Error condition
				// e.printStackTrace();
				// An unplugged connection will just flood the console with
				// stack traces, and give us zero useful information.  Until
				// we have a plan for how to respond to the user when the
				// connection drops, we'll just let this silently fail, and set
				// a fail bit.
				connected.set(false);
			} catch (InterruptedException e) {
			}
			readFifo.clear();
			readFifo.notifyAll();
			if (maxEats == 0) {
				throw new RuntimeException("Much more data than expected; check your serial line and reset your machine!");
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see replicatorg.app.util.serial.SerialInterface#isConnected()
	 */
	public boolean isConnected() { return (connected.get()); }

	public void serialEvent(SerialPortEvent event) {
		if (event.getEventType() != SerialPortEvent.DATA_AVAILABLE) return;
		synchronized (readFifo) {
			try {
				while (true) {
					synchronized(input)
					{
						if (input.available() == 0)
						{
							return;
						}
					}

					int b = input.read();
					if (b >= 0) {
						readFifo.enqueue((byte)b);
						//notify each byte received
						readFifo.notifyAll();
						SerialFifoEventListener l = listener.get();
						if (l != null)
							l.serialByteReceivedEvent(readFifo);
					}
				}
			} catch (IOException e) {
				// Error condition
				// e.printStackTrace();
				// An unplugged connection will just flood the console with
				// stack traces, and give us zero useful information.  Until
				// we have a plan for how to respond to the user when the
				// connection drops, we'll just let this silently fail, and set
				// a fail bit.
				if (connected.get()) {
					Base.logger.severe("Serial IO exception:" + event.toString() + ". Printer communication may be disrupted.");
//					connected.set(false);
					// TODO: How do we tell rxtx that we're done using this port?
					dispose();
				}
			}
		}
	}
}
