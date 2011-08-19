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
package comm.platform.dev;

import comm.PortType;
import comm.util.StringUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author David Hoyt <dhoyt@hoytsoft.org>
 */
public abstract class CommPort extends DisposableObject implements comm.CommPort {
	//<editor-fold defaultstate="collapsed" desc="Variables">
	protected final Object commLock = new Object();
	protected String name, title, description, owner;
	protected boolean opened, owned, available;
	protected PortType portType;
	
	protected final Object readListenerLock = new Object();
	protected final List<IReadListener> readListeners = new CopyOnWriteArrayList<IReadListener>();
	
	protected final Object writeListenerLock = new Object();
	protected final List<IWriteListener> writeListeners = new CopyOnWriteArrayList<IWriteListener>();
	
	protected final Object errorListenerLock = new Object();
	protected final List<IErrorListener> errorListeners = new CopyOnWriteArrayList<IErrorListener>();
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Init">
	protected CommPort(String name, String title, String description, PortType portType) {
		init(name, title, description, portType);
	}
	
	protected CommPort() {
		init(StringUtil.empty, StringUtil.empty, StringUtil.empty, PortType.UNKNOWN);
	}
	
	private void init(String name, String title, String description, PortType portType) {
		this.name = name;
		this.title = title;
		this.portType = portType;
		this.description = description;
	}
	//</editor-fold>

	//<editor-fold defaultstate="collapsed" desc="Getters">
	@Override
	public final Object getLock() {
		return commLock;
	}
	
	@Override
	public final String getName() {
		return name;
	}
	
	@Override
	public final String getTitle() {
		return title;
	}
	
	@Override
	public final String getDescription() {
		return description;
	}
	
	@Override
	public final boolean isOpen() {
		return opened;
	}
	
	@Override
	public final boolean isOwned() {
		return owned;
	}
	
	@Override
	public final String getOwner() {
		return owner;
	}
	
	@Override
	public final boolean isAvailable() {
		return available;
	}
	
	@Override
	public final PortType getPortType() {
		return portType;
	}
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Helper Methods">
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Listeners">
	//<editor-fold defaultstate="collapsed" desc="Read Listener">
	@Override
	public final boolean hasReadListeners() {
		return !readListeners.isEmpty();
	}
	
	@Override
	public final boolean hasReadListener(IReadListener listener) {
		if (listener == null)
			return false;
		synchronized(readListenerLock) {
			return readListeners.contains(listener);
		}
	}
	
	@Override
	public final boolean addReadListener(IReadListener listener) {
		if (listener == null)
			return false;
		synchronized(readListenerLock) {
			readListeners.add(listener);
		}
		return true;
	}
	
	@Override
	public final boolean removeReadListener(IReadListener listener) {
		if (listener == null)
			return true;
		synchronized(readListenerLock) {
			return readListeners.remove(listener);
		}
	}
	
	@Override
	public final boolean clearReadListeners() {
		synchronized(readListenerLock) {
			if (readListeners.isEmpty())
				return true;
			readListeners.clear();
			return readListeners.isEmpty();
		}
	}
	
	public final void notifyReadListenersBytesRead(ByteBuffer buffer, int offset, int length) {
		for(IReadListener listener : readListeners)
			listener.bytesRead(buffer, offset, length);
	}
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Write Listener">
	@Override
	public final boolean hasWriteListeners() {
		return !writeListeners.isEmpty();
	}
	
	@Override
	public final boolean hasWriteListener(IWriteListener listener) {
		if (listener == null)
			return false;
		synchronized(writeListenerLock) {
			return writeListeners.contains(listener);
		}
	}
	
	@Override
	public final boolean addWriteListener(IWriteListener listener) {
		if (listener == null)
			return false;
		synchronized(writeListenerLock) {
			writeListeners.add(listener);
		}
		return true;
	}
	@Override
	public final boolean removeWriteListener(IWriteListener listener) {
		if (listener == null)
			return true;
		synchronized(writeListenerLock) {
			return writeListeners.remove(listener);
		}
	}
	@Override
	public final boolean clearWriteListeners() {
		synchronized(writeListenerLock) {
			if (writeListeners.isEmpty())
				return true;
			writeListeners.clear();
			return writeListeners.isEmpty();
		}
	}
	
	public final void notifyWriteListenersBytesWritten(ByteBuffer buffer, int offset, int length) {
		for(IWriteListener listener : writeListeners)
			listener.bytesWritten(buffer, offset, length);
	}
	
	public final void notifyWriteListenersWriteComplete(ByteBuffer buffer, int length) {
		for(IWriteListener listener : writeListeners)
			listener.writeComplete(buffer, length);
	}
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Error Listener">
	@Override
	public final boolean hasErrorListeners() {
		return !errorListeners.isEmpty();
	}
	
	@Override
	public final boolean hasErrorListener(IErrorListener listener) {
		if (listener == null)
			return false;
		synchronized(errorListenerLock) {
			return errorListeners.contains(listener);
		}
	}
	
	@Override
	public final boolean addErrorListener(IErrorListener listener) {
		if (listener == null)
			return false;
		synchronized(errorListenerLock) {
			errorListeners.add(listener);
		}
		return true;
	}
	
	@Override
	public final boolean removeErrorListener(IErrorListener listener) {
		if (listener == null)
			return true;
		synchronized(errorListenerLock) {
			return errorListeners.remove(listener);
		}
	}
	
	@Override
	public final boolean clearErrorListeners() {
		synchronized(errorListenerLock) {
			if (errorListeners.isEmpty())
				return true;
			errorListeners.clear();
			return errorListeners.isEmpty();
		}
	}
	
	public final void notifyErrorListenersExceptionCaught(Throwable exc) {
		for(IErrorListener listener : errorListeners)
			listener.exceptionCaught(exc);
	}
	//</editor-fold>
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Public Methods">
	@Override
	public final boolean open() {
		return open(DEFAULT_READ_BUFFER_SIZE, DEFAULT_WRITE_BUFFER_SIZE);
	}
	
	@Override
	public final boolean write(byte value) {
		return writeSystem(ByteBuffer.allocate(BYTE_SIZE).put(value), 0, BYTE_SIZE);
	}
	
	@Override
	public final boolean write(short value) {
		return writeSystem(ByteBuffer.allocate(SHORT_SIZE).putShort(value), 0, SHORT_SIZE);
	}
	
	@Override
	public final boolean write(int value) {
		return writeSystem(ByteBuffer.allocate(INTEGER_SIZE).putInt(value), 0, INTEGER_SIZE);
	}
	
	@Override
	public final boolean write(long value) {
		return writeSystem(ByteBuffer.allocate(LONG_SIZE).putLong(value), 0, LONG_SIZE);
	}
	
	@Override
	public final boolean write(float value) {
		return writeSystem(ByteBuffer.allocate(FLOAT_SIZE).putFloat(value), 0, FLOAT_SIZE);
	}
	
	@Override
	public final boolean write(double value) {
		return writeSystem(ByteBuffer.allocate(DOUBLE_SIZE).putDouble(value), 0, DOUBLE_SIZE);
	}
	
	@Override
	public final boolean write(char value) {
		return writeSystem(ByteBuffer.allocate(CHAR_SIZE).putChar(value), 0, CHAR_SIZE);
	}
	
	@Override
	public final boolean write(byte[] buffer, int offset, int length) {
		return writeSystem(ByteBuffer.wrap(buffer), offset, length);
	}
	
	@Override
	public final boolean write(short value, ByteOrder order) {
		return writeSystem(ByteBuffer.allocate(SHORT_SIZE).order(order).putShort(value), 0, SHORT_SIZE);
	}
	
	@Override
	public final boolean write(int value, ByteOrder order) {
		return writeSystem(ByteBuffer.allocate(INTEGER_SIZE).order(order).putInt(value), 0, INTEGER_SIZE);
	}
	
	@Override
	public final boolean write(long value, ByteOrder order) {
		return writeSystem(ByteBuffer.allocate(LONG_SIZE).order(order).putLong(value), 0, LONG_SIZE);
	}
	
	@Override
	public final boolean write(float value, ByteOrder order) {
		return writeSystem(ByteBuffer.allocate(FLOAT_SIZE).order(order).putFloat(value), 0, FLOAT_SIZE);
	}
	
	@Override
	public final boolean write(double value, ByteOrder order) {
		return writeSystem(ByteBuffer.allocate(DOUBLE_SIZE).order(order).putDouble(value), 0, DOUBLE_SIZE);
	}
	
	@Override
	public final boolean write(char value, ByteOrder order) {
		return writeSystem(ByteBuffer.allocate(CHAR_SIZE).order(order).putChar(value), 0, CHAR_SIZE);
	}
	
	@Override
	public final boolean write(byte[] buffer, int offset, int length, ByteOrder order) {
		return writeSystem(ByteBuffer.wrap(buffer).order(order), offset, length);
	}

	@Override
	public final boolean write(ByteBuffer buffer, int offset, int length) {
		return writeSystem(buffer, offset, length);
	}
	
	@Override
	public final boolean println() {
		return print("\n", DEFAULT_CHARSET_ENCODER);
	}
	
	@Override
	public final boolean println(CharSequence value) {
		return print(value + "\n", DEFAULT_CHARSET_ENCODER);
	}
	
	@Override
	public final boolean print(CharSequence value) {
		return print(value, DEFAULT_CHARSET_ENCODER);
	}
	
	@Override
	public final boolean print(CharSequence value, Charset charset) {
		if (value == null || charset == null)
			return false;
		return write(CharBuffer.wrap(value), charset);
	}
	
	@Override
	public final boolean print(CharSequence value, CharsetEncoder encoder) {
		if (value == null || encoder == null)
			return false;
		return write(CharBuffer.wrap(value), encoder);
	}
	
	@Override
	public final boolean write(CharBuffer value, Charset charset) {
		if (value == null || charset == null)
			return false;
		return write(
			value, 
			charset.newEncoder()
				.onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE)
		);
	}
	
	@Override
	public final boolean write(CharBuffer value, CharsetEncoder encoder) {
		if (value == null || encoder == null)
			return false;
		
		ByteBuffer bb;
		try {
			bb = encoder.encode(value);
		} catch(CharacterCodingException cce) {
			return false;
		}
		
		return writeSystem(bb, 0, bb.limit());
	}
	//</editor-fold>
	
	protected abstract boolean writeSystem(ByteBuffer buffer, int offset, int length);
}
