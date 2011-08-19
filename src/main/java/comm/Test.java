/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package comm;

import comm.ICommPort.IReadListener;
import comm.ICommPort.IWriteListener;
import comm.platform.api.MemoryBuffer;
import comm.util.StringUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;

/**
 *
 * @author hoyt6
 */
class Test {
	public static void main(String[] args) throws InterruptedException, IOException {
		final CharsetDecoder decoder = ICommPort.DEFAULT_CHARSET_DECODER;
		
		//SerialPorts.addPlatformHint(PlatformHint.IOCompletionPortNumberOfConcurrentThreads, 1);

		ISerialPort serialPort = comm.SerialPorts.find("COM2");
		if (serialPort == null)
			serialPort = comm.SerialPorts.getAvailableSerialPorts()[0];
		
		serialPort.configure(921600, ISerialPort.DATABITS_8, ISerialPort.STOPBITS_1, ISerialPort.PARITY_NONE);
		serialPort.changeFlowControl(ISerialPort.FLOWCONTROL_NONE);
		
		//SerialPorts.addPlatformHint(PlatformHint.IOCompletionPortNumberOfConcurrentThreads, 2);
		
		System.out.println("Opening " + serialPort.getName() + " [" + serialPort.getTitle() + "]");
		
		serialPort.addReadListener(new IReadListener() {
			@Override
			public void bytesRead(ByteBuffer buffer, int offset, int bytesRead) {
				try {
					synchronized(decoder) {
						CharBuffer cb = CharBuffer.allocate(bytesRead);
						decoder.decode(buffer, cb, true);
						cb.flip();
					
						String msg = cb.toString();
						
						System.out.println(Thread.currentThread().getName() +  ", recv: " + msg);
					}
				} catch(Throwable t) {
				}
			}
		});
		
		final StringBuilder output = new StringBuilder(4096);
		final int[] output_size = new int[1];
		final int[] msg_count = new int[1];
		
		serialPort.addWriteListener(new IWriteListener() {
			@Override
			@SuppressWarnings("CallToThreadDumpStack")
			public void bytesWritten(ByteBuffer buffer, int offset, int length) {
				try {
					//CharBuffer cb = CharBuffer.allocate(length);
					//decoder.decode(buffer, cb, true);
					//String msg = cb.toString();
					//System.out.println(Thread.currentThread().getName() +  ", sent partial: " + msg);
				} catch(Throwable t) {
					//t.printStackTrace();
				}
			}

			@Override
			@SuppressWarnings("CallToThreadDumpStack")
			public void writeComplete(ByteBuffer buffer, int length) {
				try {
					synchronized(output_size) {
						++msg_count[0];
						output_size[0] += length;
					}
					
					synchronized(decoder) {
						CharBuffer cb = CharBuffer.allocate(length);
						decoder.decode(buffer, cb, true);
						cb.flip();
					
						String msg = cb.toString();

						output.append(msg);
					}
				} catch(Throwable t) {
					t.printStackTrace();
				}
			}
		});
		
		final String TEST_START = "----TEST START----";
		final String TEST_END = "----TEST END----";
		final String NEWLINE = StringUtil.newLine;
		final int TEST_START_LEN = TEST_START.length();
		final int TEST_END_LEN = TEST_END.length();
		final int NEWLINE_LEN = NEWLINE.length();
		
		String num = StringUtil.empty;
		int expectedMsgLength = 0;
		int expectedMsgCount = 0;
		
		serialPort.open();
		
		serialPort.print(TEST_START);
		expectedMsgLength += TEST_START_LEN;
		++expectedMsgCount;
		serialPort.print(NEWLINE);
		expectedMsgLength += NEWLINE_LEN;
		++expectedMsgCount;
		
		for(int i = 0; i < 500; ++i) {
			for(int j = 100; j <= 115; ++j) {
				num = j + ".";
				serialPort.print(num);
				expectedMsgLength += num.length();
				++expectedMsgCount;
			}
			serialPort.print(NEWLINE);
			expectedMsgLength += NEWLINE_LEN;
			++expectedMsgCount;
		}
		serialPort.print(TEST_END);
		expectedMsgLength += TEST_END_LEN;
		++expectedMsgCount;
		serialPort.print(NEWLINE);
		expectedMsgLength += NEWLINE_LEN;
		++expectedMsgCount;
		
		while(output_size[0] < expectedMsgLength) {
			System.out.println("MSG COUNT: " + msg_count[0] + ", EXPECTING: " + expectedMsgCount);
			Thread.sleep(1000);
		}
		System.out.println("EXPECTED: " + expectedMsgLength);
		System.out.println("SIZE: " + output.length());
		System.out.println("OUTPUT SIZE: " + output_size[0]);
		//System.out.println("OUTPUT:");
		//System.out.print(output.toString());
		
		//Thread.sleep(30000 * 1);
		
		serialPort.close();
		
//		System.gc();
//		System.gc();
//		System.gc();
//		System.gc();
		
		System.out.println("MEM BUFFER INSTANCE COUNT: " + MemoryBuffer.getGlobalInstanceCount());
		
		System.out.print("Opening and closing (attempt ");
		for(int i = 1; i <= 50; ++i) {
			System.out.print(i + " ");
			serialPort.open();
			serialPort.close();
		}
		System.out.println(")...");
		//System.exit(0);
		//System.runFinalization();
		//System.in.read();
		CommPorts.shutdown();
	}
}
