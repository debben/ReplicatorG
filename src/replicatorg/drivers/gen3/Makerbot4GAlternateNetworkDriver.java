package replicatorg.drivers.gen3;

import replicatorg.app.Base;
import replicatorg.app.exceptions.SerialException;
import replicatorg.app.util.networking.SocketConnection;
import replicatorg.app.util.serial.Serial;
import replicatorg.app.util.serial.SerialFifoEventListener;

/* simple Java serial port <-> tcp proxy */
import java.net.ServerSocket;
import java.net.Socket;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import java.io.InputStream;
import java.io.OutputStream;

public class Makerbot4GAlternateNetworkDriver extends Makerbot4GAlternateDriver {

	@Override
	public synchronized void openSerial(String portName) {
		// Grab a lock
		serialLock.writeLock().lock();
		
		//let's try to parse out the desired ip and port
		String[] details = portName.split(":");
		String ip = details[0];
		int port = Integer.parseInt(details[1]);
		
		// Now, try to create the new serial device
		SocketConnection newConnection = null;
		try {

			Base.logger.info("Attempting to connect to machine using tcp: " + portName);
			newConnection = new SocketConnection(portName, getRate(), getParity(), getDataBits(), (int)getStopBits(),ip,port);
		} catch (Exception e) {
			String msg = e.getMessage();
			Base.logger.severe("Connection error: " + msg);
			setError("Connection error: " + msg);
		}

		if (newConnection != null) {
			// TODO: Do we need to explicitly dispose this?
			if (this.serial != null) {
				synchronized(this.serial) {
					this.serial.dispose();
					this.serial = null;
				}
			}
			
			// Finally, set the new serial port
			setInitialized(false);
			this.serial = newConnection;

			//TODO: Figure out how to do the socket equivilent. I think it irrelevant given 
			//the inputstream object's buffer.
			
			// asynch option: the serial port forwards all received data in FIFO format via 
			// serialByteReceivedEvent if the driver implements SerialFifoEventListener.
			//if (this instanceof SerialFifoEventListener && serial != null) {
			//	serial.listener.set( (SerialFifoEventListener) this );
			//}
		}
		serialLock.writeLock().unlock();
	}
	
	// TODO: Move all of this to a new object that causes this when it is destroyed.
	@Override
	public void closeSerial() {
		serialLock.writeLock().lock();
		if (serial != null)
			serial.dispose();
		serial = null;
		serialLock.writeLock().unlock();
	}

	public boolean isConnected() {
		return (this.serial != null && this.serial.isConnected());
	}
	
	public void dispose() {
		closeSerial();
		super.dispose();
	}
}
