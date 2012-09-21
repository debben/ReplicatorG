package replicatorg.app.util.serial;

public interface SerialInterface {

	public abstract String getName();

	/**
	 * Unregister and close the port.
	 */
	public abstract void dispose();

	/**
	 * Briefly pulse the RTS line low.  On most arduino-based boards, this will hard reset the
	 * device.
	 */
	public abstract void pulseRTSLow();

	/**
	 * Attempt to read a single byte.
	 * @return the byte read, or -1 to indicate a timeout.
	 */
	public abstract int read();

	/**
	 * Attempt to fill the given buffer.  This method blocks until input data is available, 
	 * end of file is detected, or an exception is thrown.  It is meant to emulate the
	 * behavior of the call of the same signature on InputStream, with the significant
	 * difference that it will terminate when the timeout is exceeded.
	 * @param bytes The buffer to fill with as much data as is available.
	 * @return the number of characters read.
	 */
	public abstract int read(byte bytes[]);

	public abstract void write(byte bytes[]);

	/**
	 * Write a String to the output. Note that this doesn't account for Unicode
	 * (two bytes per char), nor will it send UTF8 characters.. It assumes that
	 * you mean to send a byte buffer (most often the case for networking and
	 * serial i/o) and will only use the bottom 8 bits of each char in the
	 * string. (Meaning that internally it uses String.getBytes)
	 * 
	 * If you want to move Unicode data, you can first convert the String to a
	 * byte stream in the representation of your choice (i.e. UTF8 or two-byte
	 * Unicode data), and send it as a byte array.
	 */
	public abstract void write(String what);

	/**
	 * Set the amount of time we're willing to wait for a read to timeout.
	 */
	public abstract void setTimeout(int timeoutMillis);

	public abstract void clear();

	/**
	 * Indicates if we've received 
	 */
	public abstract boolean isConnected();

}