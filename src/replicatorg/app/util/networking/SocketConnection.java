package replicatorg.app.util.networking;

import com.sun.org.apache.xml.internal.security.Init;

import replicatorg.app.Base;
import replicatorg.app.util.serial.SerialInterface;

/* simple Java serial port <-> tcp proxy */

import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;



//<NETWORK PORT CODE> 
public class SocketConnection implements SerialInterface {

	
	
	/** True if the device is connected **/
	private AtomicBoolean connected = new AtomicBoolean(false);
	
	private String name;
	private int rate;
	private int parity;
	private int data;
	private int stop;
	private Socket connection = null;	
	private InputStream input = null;
	private OutputStream output = null;

	private int timeoutMillis;
	
	public SocketConnection(String portName, int rate, char parity, int dataBits, int stopBits, String host, int port) throws Exception {
		init(portName, rate, parity, dataBits, (float)stopBits,host,port);
	}

	private void init(String name, int rate, char parity, int data, float stop, String host, int port) throws Exception{
		this.name = name;
		this.rate = rate;
		this.parity = parity;
		this.data = data;
		this.stop = (int)stop;
		
		try
		{
			connection = new Socket(host,port);
			input = connection.getInputStream();
			output = connection.getOutputStream();
			
			output.write(new String("<<<" + rate + ":" + parity + ":" + data + ":" + stop + ">>>").getBytes());
		}
		catch(UnknownHostException ex){
			throw new Exception("Couldn't find host " + host);			
		}
		
		connected.set(true);
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public void dispose() {
		try{
			if(input != null)
				input.close();
			if(output != null)
				output.close();
			if(connection != null)
				connection.close();			
		}
		catch(IOException ex){
			Base.logger.warning("Error closing network socket " + ex.getMessage());
		}

	}

	@Override
	public void pulseRTSLow() {
		PrintWriter writter = new PrintWriter(output);
		writter.write("<<<DTR:FALSE>>>");
		writter.write("<<<RTS:FALSE>>>");
		try {
			Thread.sleep(100);
		} catch (java.lang.InterruptedException ie) {
		}
		writter.write("<<<DTR:TRUE>>>");
		writter.write("<<<RTS:TRUE>>>");
	}

	@Override
	public int read() {
		try{
			return input.read();
		}
		catch(IOException ex){
			Base.logger.finest("Attempt to read from socket with no data");
			return 0;
		}
		
	}

	@Override
	public int read(byte[] bytes) {
		if(connected.get()){
			try{
				return input.read(bytes);
			}
			catch(IOException ex){
				Base.logger.severe("Failed to get bytes from socket " + ex.getMessage());				
			}
		}
		return 0;
	}

	@Override
	public void write(byte[] bytes) {
		if (!connected.get()) {
			Base.logger.severe("socket disconnected");
			return;
		}
		
		try {
			output.write(bytes);
			output.flush(); // Reconsider?

		} catch (Exception e) { // null pointer or serial port dead
			Base.logger.severe( "socket error: \n" + e.getMessage() );
		}	
	}

	@Override
	public void write(String what) {
		write(what.getBytes());
	}

	@Override
	public void setTimeout(int timeoutMillis) {
		this.timeoutMillis = timeoutMillis;
	}

	@Override
	public void clear() {
		//TODO: for now, do nothing

	}

	@Override
	public boolean isConnected() {
		return connected.get();
	}

}
