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
 * Provides access to system parallel ports.
 * 
 * @author David Hoyt <dhoyt@hoytsoft.org>
 */
public final class ParallelPorts {
	//<editor-fold defaultstate="collapsed" desc="Variables">
	private static final Implementation impl;
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Initialization">
	static {
		Implementation osImpl = null;
		switch(Sys.getOSFamily()) {
			case Windows:
				osImpl = new comm.platform.dev.win32.APIParallelPortImplementation();
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
		int[] getBaudRates();
		void visitAvailableParallelPorts(IVisitor visitor);
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
		 * @param ParallelPort The associated found port.
		 * @return True if you want the callbacks to continue. False to short circuit the callback.
		 */
		boolean visit(IParallelPort ParallelPort);
	}
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Public Static Methods">
	/**
	 * Examine each found port via the {@link IVisitor#visit(comm.IParallelPort) visit()} callback.
	 * @param visitor The object that will handle the callback.
	 */
	public static void visitAvailableParallelPorts(IVisitor visitor) {
		if (visitor == null)
			return;
		if (impl == null)
			throw new UnsupportedPlatformException();
		impl.visitAvailableParallelPorts(visitor);
	}
	
	/**
	 * Retrieves the list of available ports by visiting each one and accumulating references.
	 * 
	 * @return An array of valid, available ports.
	 */
	public static IParallelPort[] getAvailableParallelPorts() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		
		final List<IParallelPort> lst = new ArrayList<IParallelPort>(4);
		visitAvailableParallelPorts(new IVisitor() {
			@Override
			public boolean visit(IParallelPort ParallelPort) {
				lst.add(ParallelPort);
				return true;
			}
		});
		
		if (!lst.isEmpty())
			return lst.toArray(new IParallelPort[lst.size()]);
		else
			return IParallelPort.EMPTY_PARALLEL_PORTS;
	}
	
	/**
	 * Searches through the ports looking for one with the given name.
	 * 
	 * @param name The name of the port such as "COM1" or "LPT1".
	 * @return The instance of the port if found, null otherwise.
	 */
	public static IParallelPort find(final String name) {
		if (StringUtil.isNullOrEmpty(name))
			return null;
		final IParallelPort[] finder = new IParallelPort[1];
		visitAvailableParallelPorts(new IVisitor() {
			@Override
			public boolean visit(IParallelPort ParallelPort) {
				if (name.equalsIgnoreCase(ParallelPort.getName())) {
					finder[0] = ParallelPort;
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
	 * Requests a list of valid baud rates that the system allows.
	 * 
	 * @return An integer array containing valid system baud rates.
	 */
	public static int[] getBaudRates() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return impl.getBaudRates();
	}
	
	/**
	 * Gets the system default baud rate.
	 * 
	 * @return An integer representing the system default baud rate.
	 */
	public static int getDefaultBaudRate() {
		if (impl == null)
			throw new UnsupportedPlatformException();
		return impl.getDefaultBaudRate();
	}
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Shutdown">
	public static synchronized void shutdown() {
		visitAvailableParallelPorts(new IVisitor() {
			@Override
			public boolean visit(IParallelPort ParallelPort) {
				ParallelPort.close();
				return true;
			}
		});
		Sys.gc();
		System.runFinalization();
	}
	//</editor-fold>
}
