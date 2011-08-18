/*
 * Copyright (c) 2011 David Hoyt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, 
 * are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list 
 * of conditions and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright notice, this 
 * list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 *  
 * The names of any contributors may not be used to endorse or promote products derived 
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 */
package comm;

import comm.platform.Sys;
import comm.platform.UnsupportedPlatformException;
import comm.util.StringUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides access to system serial ports.
 * 
 * @author David Hoyt <dhoyt@hoytsoft.org>
 */
public final class SerialPorts {
	//<editor-fold defaultstate="collapsed" desc="Variables">
	private static final Implementation impl;
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Initialization">
	static {
		Implementation osImpl = null;
		switch(Sys.getOSFamily()) {
			case Windows:
				osImpl = new comm.platform.dev.win32.APISerialPortImplementation();
				break;
			default:
				osImpl = null;
				break;
		}
		
		//Double check that the current platform is supported before we 
		//allow serial ports to be discovered, opened, and used.
		if (osImpl != null && !osImpl.isPlatformSupported())
			osImpl = null;
		impl = osImpl;
	}
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Interfaces">
	/**
	 * Used as a guide for platform-specific implementations.
	 */
	public static interface Implementation {
		int getDefaultBaudRate();
		
		boolean isPlatformSupported();
		int[] getPlatformBaudRateOptions();
		DataBits[] getPlatformDataBitsOptions();
		StopBits[] getPlatformStopBitsOptions();
		FlowControl[] getPlatformFlowControlOptions();
		Parity[] getPlatformParityOptions();
		
		void visitAvailableSerialPorts(IVisitor visitor);
		void addPlatformHint(final String name, final Object value);
		<T> T findPlatformHint(final String name);
	}
	
	/**
	 * Used as a callback into the discovered list of ports.
	 */
	public static interface IVisitor {
		/**
		 * Called for every port found.
		 * 
		 * @param SerialPort The associated found port.
		 * @return True if you want the callbacks to continue. False to short circuit the callback.
		 */
		boolean visit(ISerialPort SerialPort);
	}
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Public Static Methods">
	/**
	 * Examine each found port via the {@link IVisitor#visit(comm.ISerialPort) visit()} callback.
	 * @param visitor The object that will handle the callback.
	 */
	public static void visitAvailableSerialPorts(IVisitor visitor) {
		if (impl == null)
			throw new UnsupportedPlatformException();
		if (visitor == null)
			return;
		impl.visitAvailableSerialPorts(visitor);
	}
	
	/**
	 * Retrieves the list of available ports by visiting each one and accumulating references.
	 * 
	 * @return An array of valid, available ports.
	 */
	public static ISerialPort[] getAvailableSerialPorts() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		
		final List<ISerialPort> lst = new ArrayList<ISerialPort>(4);
		visitAvailableSerialPorts(new IVisitor() {
			@Override
			public boolean visit(ISerialPort SerialPort) {
				lst.add(SerialPort);
				return true;
			}
		});
		
		if (!lst.isEmpty())
			return lst.toArray(new ISerialPort[lst.size()]);
		else
			return ISerialPort.EMPTY_SERIAL_PORTS;
	}
	
	/**
	 * Searches through the ports looking for one with the given name.
	 * 
	 * @param name The name of the port such as "COM1" or "LPT1".
	 * @return The instance of the port if found, null otherwise.
	 */
	public static ISerialPort find(final String name) {
		if (StringUtil.isNullOrEmpty(name))
			return null;
		final ISerialPort[] finder = new ISerialPort[1];
		visitAvailableSerialPorts(new IVisitor() {
			@Override
			public boolean visit(ISerialPort SerialPort) {
				if (name.equalsIgnoreCase(SerialPort.getName())) {
					finder[0] = SerialPort;
					//We can exit since we found what we were looking for.
					return false;
				}
				return true;
			}
		});
		//Will return null if it couldn't find the serial port.
		return finder[0];
	}
	
	/**
	 * Provides a way to augment/modify platform-specific behavior such as the 
	 * number of IO completion port threads on Windows.
	 * 
	 * @param name The name of the hint.
	 * @param value The value to assign the hint.
	 */
	public static void addPlatformHint(final String name, final Object value) {
		if (impl == null)
			throw new UnsupportedPlatformException();
		impl.addPlatformHint(name, value);
	}
	
	/**
	 * Locate a specific platform hint and return its value.
	 * 
	 * @param name The name of the hint.
	 * @return The value of the requested platform hint if it exists. Null otherwise.
	 */
	public static <T> T findPlatformHint(final String name) {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return impl.findPlatformHint(name);
	}
	
	/**
	 * Determines if the current platform is supported by this API.
	 * 
	 * @return True if this API can be used on the current platform/system.
	 */
	public static boolean isPlatformSupported() {
		if (impl == null)
			return false;
		return impl.isPlatformSupported();
	}
	
	/**
	 * Gets the platform default baud rate.
	 * 
	 * @return An integer representing the platform default baud rate.
	 */
	public static int getPlatformDefaultBaudRate() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return impl.getDefaultBaudRate();
	}
	
	/**
	 * Gets the platform standard length of data bits per byte.
	 * 
	 * @return The default value for the platform.
	 */
	public static DataBits getPlatformDefaultDataBits() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return DataBits.getDefault();
	}
	
	/**
	 * Gets the platform standard flow control methods.
	 * 
	 * @return The default value for the platform.
	 */
	public static FlowControl[] getPlatformDefaultFlowControl() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return FlowControl.getDefault();
	}
	
	/**
	 * Gets the platform standard stop bits.
	 * 
	 * @return The default value for the platform.
	 */
	public static StopBits getPlatformDefaultStopBits() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return StopBits.getDefault();
	}
	
	/**
	 * Gets the platform standard parity.
	 * 
	 * @return The default value for the platform.
	 */
	public static Parity getPlatformDefaultParity() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return Parity.getDefault();
	}
	
	/**
	 * Requests a list of valid baud rates that the platform allows.
	 * 
	 * @return An integer array containing valid platform baud rates.
	 */
	public static int[] getPlatformBaudRateOptions() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return impl.getPlatformBaudRateOptions();
	}
	
	/**
	 * Requests a list of valid data bit values that the platform allows.
	 * 
	 * @return The default value for the platform.
	 */
	public static DataBits[] getPlatformDataBitsOptions() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return impl.getPlatformDataBitsOptions();
	}
	
	/**
	 * Requests a list of valid stop bit values that the platform allows.
	 * 
	 * @return The default value for the platform.
	 */
	public static StopBits[] getPlatformStopBitsOptions() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return impl.getPlatformStopBitsOptions();
	}
	
	/**
	 * Requests a list of valid flow control values that the platform allows.
	 * 
	 * @return The default value for the platform.
	 */
	public static FlowControl[] getPlatformFlowControlOptions() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return impl.getPlatformFlowControlOptions();
	}
	
	/**
	 * Requests a list of valid parity values that the platform allows.
	 * 
	 * @return The default value for the platform.
	 */
	public static Parity[] getPlatformParityOptions() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return impl.getPlatformParityOptions();
	}
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Shutdown">
	public static synchronized void shutdown() {
		visitAvailableSerialPorts(new IVisitor() {
			@Override
			public boolean visit(ISerialPort SerialPort) {
				SerialPort.close();
				return true;
			}
		});
		Sys.gc();
		System.runFinalization();
	}
	//</editor-fold>
}
