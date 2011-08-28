
Usage examples:

```java
//Retrieve all serial ports currently available on the system.
ISerialPort[] serialPorts = SerialPorts.getAvailableSerialPorts();

//Retrieve a specific serial port.
ISerialPort serialPort = SerialPorts.find("COM2");

//Print out its name.
System.out.println(serialPort.getName());

//Configure the serial port.
serialPort.configure(9600, ISerialPort.DATABITS_8, ISerialPort.STOPBITS_1, ISerialPort.PARITY_NONE);
serialPort.changeFlowControl(ISerialPort.FLOWCONTROL_NONE);

//Create a charset decoder for converting byte buffers into strings.
final CharsetDecoder decoder = ICommPort.DEFAULT_CHARSET_DECODER;

serialPort.addReadListener(new IReadListener() {
	@Override
	public void bytesRead(ByteBuffer buffer, int offset, int bytesRead) {
		//This is called from a thread in a thread pool and could be 
		//competing with other threads.
		synchronized(decoder) {
			CharBuffer cb = CharBuffer.allocate(bytesRead);
			decoder.decode(buffer, cb, true);
			cb.flip();

			String msg = cb.toString();

			System.out.println("Received: " + msg);
		}
	}
});

//Open the serial port.
serialPort.open();

//Write to the serial port.
serialPort.print("Hello world");

//Allow 10 seconds to receive a response.
Thread.sleep(1000 * 10);

//Close the serial port.
serialPort.close();
```