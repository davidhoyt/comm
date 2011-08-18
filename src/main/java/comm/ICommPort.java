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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

/**
 * Provides generic access to system communication ports.
 * 
 * @author David Hoyt <dhoyt@hoytsoft.org>
 */
public interface ICommPort extends IDisposable {
	//<editor-fold defaultstate="collapsed" desc="Constants">
	/**
	 * Describes a state where no communication ports are available on the system.
	 */
	public static final ICommPort[] EMPTY = { };
	
	public static final int 
		  DEFAULT_READ_BUFFER_SIZE     = 2048
		, DEFAULT_WRITE_BUFFER_SIZE    = 2048
	;
	
	public static final int 
		  DEFAULT_THREAD_POOL_SIZE = Math.max(1, Runtime.getRuntime().availableProcessors()) * 2
	;
	
	public static final Charset
		  DEFAULT_CHARSET = Charset.forName("ASCII") 
	;
	
	public static final CharsetEncoder 
		  DEFAULT_CHARSET_ENCODER = (
			DEFAULT_CHARSET.newEncoder()
				.onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE)
		)
	;
	
	public static final CharsetDecoder 
		  DEFAULT_CHARSET_DECODER = (
			DEFAULT_CHARSET.newDecoder()
				.onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE)
	);
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Interfaces">
	public static interface IReadListener {
		void bytesRead(ByteBuffer buffer, int offset, int length);
	}
	
	public static interface IWriteListener {
		void bytesWritten(ByteBuffer buffer, int offset, int length);
		void writeComplete(ByteBuffer buffer, int length);
	}
	
	public static interface IErrorListener {
		void exceptionCaught(Throwable exc);
	}
	
	public static abstract class ReadListenerAdapter implements IReadListener {
		@Override
		public void bytesRead(ByteBuffer buffer, int offset, int length) {
		}
	}
	
	public static abstract class WriteListenerAdapter implements IWriteListener {
		@Override
		public void bytesWritten(ByteBuffer buffer, int offset, int length) {
		}

		@Override
		public void writeComplete(ByteBuffer buffer, int length) {
		}
	}
	
	public static abstract class ErrorListenerAdapter implements IErrorListener {
		@Override
		public void exceptionCaught(Throwable exc) {
		}
	}
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Listeners">
	boolean hasReadListeners();
	boolean hasReadListener(IReadListener listener);
	boolean addReadListener(IReadListener listener);
	boolean removeReadListener(IReadListener listener);
	boolean clearReadListeners();
	
	boolean hasWriteListeners();
	boolean hasWriteListener(IWriteListener listener);
	boolean addWriteListener(IWriteListener listener);
	boolean removeWriteListener(IWriteListener listener);
	boolean clearWriteListeners();
	
	boolean hasErrorListeners();
	boolean hasErrorListener(IErrorListener listener);
	boolean addErrorListener(IErrorListener listener);
	boolean removeErrorListener(IErrorListener listener);
	boolean clearErrorListeners();
	//</editor-fold>
	
	String getName();
	String getTitle();
	String getDescription();
	boolean isOpen();
	boolean isOwned();
	String getOwner();
	boolean isAvailable();
	PortType getPortType();
	Object getLock();
	
	boolean println();
	boolean println(CharSequence value);
	boolean print(CharSequence value);
	boolean print(CharSequence value, Charset charset);
	boolean print(CharSequence value, CharsetEncoder encoder);
	boolean write(CharBuffer value, Charset charset);
	boolean write(CharBuffer value, CharsetEncoder encoder);
	boolean write(byte[] buffer, int offset, int length);
	boolean write(ByteBuffer buffer, int offset, int length);
	
	boolean open(int readBufferSize, int writeBufferSize);
	boolean open();
	boolean updateConfiguration();
	boolean close();
}
