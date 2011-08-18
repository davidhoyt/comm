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
package comm.platform.dev.win32;

import com.sun.jna.LastErrorException;
import java.nio.ByteBuffer;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import comm.ICommPort;
import comm.platform.api.MemoryBuffer;
import comm.platform.api.win32.CommAPI;
import comm.platform.api.win32.IOComPortsAPI;
import comm.platform.dev.CommPort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static comm.platform.api.win32.API.*;
import static comm.platform.api.win32.IOComPortsAPI.*;

/**
 * Manages our global IO completion port.
 * 
 * @author David Hoyt <dhoyt@hoytsoft.org>
 */
class IOComPort {
	private static final Object portLock = new Object();
	private static final AtomicInteger portCount = new AtomicInteger(0);
	private static final Map<HANDLE, PortInfo> ports = new ConcurrentHashMap<HANDLE, PortInfo>(8);
	private static HANDLE ioCompletionPort = INVALID_HANDLE_VALUE;
	private static List<ThreadInfo> ioCompletionPortServiceThreads = null;
	
	private static class PortInfo {
		HANDLE port;
		Memory readBuffer;
		ByteBuffer writeBuffer;
		int readBufferSize;
		int writeBufferSize;
		IntByReference pBytesRead;
		IntByReference pEventMask;
		OVERLAPPED_EX waitCommOverlapped;
		Pointer pWaitCommOverlapped;
		OVERLAPPED_EX waitCommImmediateOverlapped;
		Pointer pWaitCommImmediateOverlapped;
		OVERLAPPED_EX readOverlapped;
		Pointer pReadOverlapped;
		CommPort commPort;
		
		Map<Pointer, ByteBuffer> pendingWrites;
		
		public PortInfo(HANDLE port, CommPort commPort, int readBufferSize, int writeBufferSize) {
			this.port = port;
			this.commPort = commPort;
			this.readBufferSize = (readBufferSize > 0 ? readBufferSize : ICommPort.DEFAULT_READ_BUFFER_SIZE);
			this.writeBufferSize = (writeBufferSize > 0 ? writeBufferSize : ICommPort.DEFAULT_WRITE_BUFFER_SIZE);
			this.pEventMask = new IntByReference();
			this.pBytesRead = new IntByReference();
			this.readBuffer = new Memory(this.readBufferSize);
			
			this.pendingWrites = new HashMap<Pointer, ByteBuffer>(128, 0.78125f);
			
			//<editor-fold defaultstate="collapsed" desc="Read">
			this.readOverlapped = new OVERLAPPED_EX();
			this.readOverlapped.op = OVERLAPPED_EX.OP_READ;
			this.readOverlapped.write();
			this.pReadOverlapped = this.readOverlapped.getPointer();
			//</editor-fold>
			
			//<editor-fold defaultstate="collapsed" desc="WaitComm">
			this.waitCommOverlapped = new OVERLAPPED_EX();
			this.waitCommOverlapped.op = OVERLAPPED_EX.OP_WAITCOMMEVENT;
			this.waitCommOverlapped.write();
			this.pWaitCommOverlapped = this.waitCommOverlapped.getPointer();
			//</editor-fold>
			
			//<editor-fold defaultstate="collapsed" desc="WaitCommImmediate">
			this.waitCommImmediateOverlapped = new OVERLAPPED_EX();
			this.waitCommImmediateOverlapped.op = OVERLAPPED_EX.OP_WAITCOMMEVENT_IMMEDIATE;
			this.waitCommImmediateOverlapped.ev = IOComPortsAPI.INSTANCE.CreateEvent(null, false, false, null);
			this.waitCommImmediateOverlapped.write();
			this.pWaitCommImmediateOverlapped = this.waitCommImmediateOverlapped.getPointer();
			//</editor-fold>
		}
		
		public Object getLock() {
			return this;
		}
		
		public boolean dispose() {
			synchronized(getLock()) {
				//Clean up native resources.
				if (this.waitCommImmediateOverlapped.ev != null && this.waitCommImmediateOverlapped.ev != INVALID_HANDLE_VALUE) {
					IOComPortsAPI.INSTANCE.CloseHandle(this.waitCommImmediateOverlapped.ev);
					this.waitCommImmediateOverlapped.ev = null;
				}

				pendingWrites.clear();
			}
			return true;
		}
	}
	
	private static class ThreadInfo {
		public Thread thread;
		public HANDLE completionPort;
		public boolean pleaseExit = false;
		public CountDownLatch threadExited = new CountDownLatch(1);
		
		public ThreadInfo(Thread thread, HANDLE completionPort) {
			this.thread = thread;
			this.completionPort = completionPort;
		}
	}
	
	public static boolean associateCommPort(HANDLE port, CommPort commPort, int readBufferSize, int writeBufferSize) {
		synchronized(portLock) {
			//Verify that we don't already hold this port.
			if (!ports.containsKey(port)) {
				//If this is the first port we're adding, we'll need to create an unassociated IO completion port.
				if (portCount.incrementAndGet() == 1) {
					//Determine the number of concurrent threads that IOCP will use. Typically it's best to actually 
					//create twice as many as the value passed to CreateIoCompletionPort().
					Integer concurrentThreadCount = PlatformHint.hint(PlatformHint.IOCompletionPortNumberOfConcurrentThreads);
					int threadCount = (concurrentThreadCount == null || concurrentThreadCount < 0 ? Math.max(1, ICommPort.DEFAULT_THREAD_POOL_SIZE / 2) : concurrentThreadCount.intValue());
					ThreadFactory threadFactory = PlatformHint.hint(PlatformHint.IOCompletionPortThreadFactory);
					if (threadFactory == null)
						threadFactory = Executors.defaultThreadFactory();
					
					//Create the completion port.
					HANDLE completionPort = ioCompletionPort = IOComPortsAPI.Util.CreateUnassociatedIoCompletionPort();
					 
					//Spin up each service thread.
					//The call will block until all the threads have started.
					ioCompletionPortServiceThreads = launchServiceThreads(threadCount, threadFactory, completionPort);
				}
				
				//Now associate our open file handle with the IO completion port.
				if (!IOComPortsAPI.Util.AssociateHandleWithIoCompletionPort(ioCompletionPort, port, port.getPointer())) {
					portCount.decrementAndGet();
					return false;
				}
				
				//Add our port to the list.
				IOComPortsAPI API = IOComPortsAPI.INSTANCE;
				PortInfo pi = new PortInfo(port, commPort, readBufferSize, writeBufferSize);
				ports.put(port, pi);
				
				//Specify which events we're interested in knowing about.
				API.SetCommMask(port, CommAPI.EV_RXCHAR);
				
				waitCommEvent(API, port, pi, false);
				return true;
			}
		}
		return false;
	}
	
	private static List<ThreadInfo> launchServiceThreads(final int threadCount, final ThreadFactory threadFactory, final HANDLE completionPort) {
		//Create a pool of threads and keep hold of them.
		final CountDownLatch counter = new CountDownLatch(threadCount);
		final List<ThreadInfo> serviceThreads = new CopyOnWriteArrayList<ThreadInfo>();
		
		try {
			for(int i = 0; i < threadCount; ++i) {
				//Create a new thread and leave it up and running.
				Thread t = threadFactory.newThread(new Runnable() {
					@Override
					public void run() {
						ThreadInfo ti = null;
						try {
							if (false)
								throw new InterruptedException();
							ti = new ThreadInfo(Thread.currentThread(), completionPort);
							serviceThreads.add(ti);
							counter.countDown();
							serviceThread(ti);
						} catch(Throwable t) {
							//t.printStackTrace();
						} finally {
							if (ti != null) {
								serviceThreads.remove(ti);
								ti.threadExited.countDown();
							}
						}
					}
				});
				t.start();
			}
			counter.await();
			return serviceThreads;
		} catch(Throwable t) {
			//Destroy running threads
			shutdownServiceThreads(serviceThreads);
			return null;
		}
	}
	
	private static void shutdownServiceThreads(List<ThreadInfo> serviceThreads) {
		//<editor-fold defaultstate="collapsed" desc="Setup params">
		if (serviceThreads == null || serviceThreads.isEmpty())
			return;
		IOComPortsAPI API = IOComPortsAPI.INSTANCE;
		OVERLAPPED_EX post = new OVERLAPPED_EX();
		post.op = OVERLAPPED_EX.OP_EXITTHREAD;
		//</editor-fold>

		//TODO: Find a more intelligent way to do this that does not require pounding on the worker 
		//      threads until they see that they should exit.
		for(ThreadInfo ti : serviceThreads) {
			if (ti == null || !ti.thread.isAlive())
				continue;
			
			//Ask the thread to exit nicely.
			ti.pleaseExit = true;
			
			//Post message to thread.
			API.PostQueuedCompletionStatus(ioCompletionPort, 0, null, post);
		}
		
		boolean allDone;
		do {
			allDone = true;
			//This list will get gradually smaller and smaller since as threads exit 
			//they remove themselves from the list.
			for(ThreadInfo ti : serviceThreads) {
				//If the thread hasn't died yet, then forcefully remove it.
				try {
					//Ensure that the thread has exited.
					if (ti.thread.isAlive() && !ti.threadExited.await(1L, TimeUnit.MILLISECONDS)) {
						//Post message again.
						API.PostQueuedCompletionStatus(ioCompletionPort, 0, null, post);
						allDone = false;
					}
				} catch(Throwable w) {
				}
			}
		} while(!allDone);
		
		serviceThreads.clear();
	}
	
	private static void serviceThread(ThreadInfo ti) throws Throwable {
		IOComPortsAPI API = IOComPortsAPI.INSTANCE;
		HANDLE completionPort = ti.completionPort;
		HANDLE port = new HANDLE();
		OVERLAPPED_EX overlapped = new OVERLAPPED_EX();
		IntByReference pBytesTransferred = new IntByReference();
		PointerByReference ppOverlapped = new PointerByReference();
		PointerByReference pCompletionKey = new PointerByReference();
		int bytesTransferred;
		Pointer pOverlapped;
		PortInfo pi;
		boolean isImmediate;
		
		while(!ti.pleaseExit) {
			//Retrieve the queued event and then examine it.
			if (!API.GetQueuedCompletionStatus(completionPort, pBytesTransferred, pCompletionKey, ppOverlapped, INFINITE)) 
				return;
			
			//If no OVERLAPPED/OVERLAPPEDEX instance is specified, then there's 
			//something wrong and we need to exit.
			if (ppOverlapped == null || (pOverlapped = ppOverlapped.getValue()) == null || pOverlapped == Pointer.NULL)
				return;
			
			//Retrieve data from the event.
			overlapped.reuse(pOverlapped);
			//overlapped = new OVERLAPPED_EX(pOverlapped);
			port.reuse(pCompletionKey.getValue());
			//port = new HANDLE(pCompletionKey.getValue());
			bytesTransferred = pBytesTransferred.getValue();
			
			//If we've received a message asking to break out of the thread, then 
			//loop back and around and check that our flag has been set. If so, 
			//then it's time to go!
			if (overlapped.op == OVERLAPPED_EX.OP_EXITTHREAD)
				continue;
			
			//If, for some unknown reason, we are processing an event for a port we 
			//haven't seen before, then go ahead and ignore it.
			if ((pi = ports.get(port)) == null)
				continue;
			
			switch(overlapped.op) {
				case OVERLAPPED_EX.OP_WAITCOMMEVENT:
					if (!API.GetOverlappedResult(port, pOverlapped, pBytesTransferred, false))
						continue;
					evaluateCommEvent(API, port, pi, pi.pEventMask.getValue());
					break;
				case OVERLAPPED_EX.OP_WAITCOMMEVENT_IMMEDIATE:
					//The events found (event mask) were placed in .ex so it needs to be 
					//evaluated and then the calling thread that's currently blocking waiting 
					//for this to complete can continue.
					evaluateCommEvent(API, port, pi, overlapped.ex);
					API.SetEvent(overlapped.ev);
					break;
				case OVERLAPPED_EX.OP_READ:
					if (!API.GetOverlappedResult(port, pOverlapped, pBytesTransferred, false))
						continue;
					
					if (bytesTransferred > 0) {
						if (pi.commPort.hasReadListeners()) {
							//Notify application that data has arrived.
							try {
								pi.commPort.notifyReadListenersBytesRead(pi.readBuffer.getByteBuffer(0, bytesTransferred), 0, bytesTransferred);
							} catch(Throwable t) {
								if (pi.commPort.hasErrorListeners())
									pi.commPort.notifyErrorListenersExceptionCaught(t);
							}
						}
					}
					
					//Read again if necessary. Otherwise wait for another comm event.
					if (bytesTransferred <= 0 || !read(API, port, pi))
						waitCommEvent(API, port, pi, true);
					break;
				case OVERLAPPED_EX.OP_WRITE:
				case OVERLAPPED_EX.OP_WRITE_IMMEDIATE:
					isImmediate = (overlapped.op == OVERLAPPED_EX.OP_WRITE_IMMEDIATE);
					bytesTransferred = (!isImmediate ? pBytesTransferred.getValue() : overlapped.ex);
					
					if (!API.GetOverlappedResult(port, pOverlapped, pBytesTransferred, false) && !isImmediate)
						continue;
					
					
					//Requests that the memorybuffer instance find itself in its list of references and 
					//then update private variables as appropriate. This should be called any time 
					//JNA constructs an instance on its own.
					overlapped.memBuffer.refresh();
					ByteBuffer bb = overlapped.memBuffer.getByteBuffer(0L, overlapped.memBuffer.size);

					if (pi.commPort.hasWriteListeners()) {
						ByteBuffer slice;
						
						if (bytesTransferred > 0) {
							slice = bb.slice();
							if (bytesTransferred < slice.limit())
								slice.limit(bytesTransferred);
							try {
								pi.commPort.notifyWriteListenersBytesWritten(slice, 0, slice.remaining());
							} catch(Throwable t) {
								if (pi.commPort.hasErrorListeners())
									pi.commPort.notifyErrorListenersExceptionCaught(t);
							} finally {
							}
						}

						if (bytesTransferred >= bb.remaining()) {
							bb.position(0);
							slice = bb.slice();
							try {
								pi.commPort.notifyWriteListenersWriteComplete(slice, slice.remaining());
							} catch(Throwable t) {
								if (pi.commPort.hasErrorListeners())
									pi.commPort.notifyErrorListenersExceptionCaught(t);
							} finally {
							}
						}
					}

					bb.position(Math.min(bb.limit(), bb.position() + bytesTransferred));

					//Clean up memory we're holding onto so we don't crash.
					if (!bb.hasRemaining()) {
						//Clean up memory.
						overlapped.memBuffer.dispose();
					} else {
						writeFile(API, pi, port, overlapped.memBuffer.getBufferPointer(), bb.remaining(), overlapped);
					}
					break;
				default:
					break;
			}
		}
	}
	
	private static void waitCommEvent(IOComPortsAPI API, HANDLE port, PortInfo pi, boolean iocpThread) {
		//This will typically return false and GetLastError() should return ERROR_IO_PENDING.
		//If it's successful, then there are events to be evaluated right away. Go ahead and 
		//post them to the IOCP but then block until they've been processed. That way, we 
		//can ensure that all our event firing, reading, and writing are always done from the 
		//worker threads. It's just nicer for consistency's sake.
		while (API.WaitCommEvent(port, pi.pEventMask, pi.waitCommOverlapped) && !iocpThread) {
			//Set the event mask so we can pick it up inside the IOCP worker thread.
			pi.waitCommImmediateOverlapped.ex = pi.pEventMask.getValue();
			//Post the event.
			API.PostQueuedCompletionStatus(ioCompletionPort, Native.POINTER_SIZE, port.getPointer(), pi.waitCommImmediateOverlapped);
			//Wait for the IOCP worker thread to signal that it's done processing it.
			API.WaitForSingleObject(pi.waitCommImmediateOverlapped.ev, INFINITE);
			//evaluateCommEvent(pi.eventMask.getValue());
		}
	}
	
	private static void evaluateCommEvent(IOComPortsAPI API, HANDLE port, PortInfo pi, int eventMask) {
		if ((eventMask & CommAPI.EV_RXCHAR) == CommAPI.EV_RXCHAR)
			read(API, port, pi);
	}
	
	private static boolean read(IOComPortsAPI API, HANDLE port, PortInfo pi) {
		return API.ReadFile(port, pi.readBuffer, pi.readBufferSize, pi.pBytesRead, pi.pReadOverlapped);
	}
	
	static boolean write(IOComPortsAPI API, HANDLE port, ByteBuffer buffer, int offset, int length) {
		PortInfo pi = ports.get(port);
		if (pi == null)
			return false;

		OVERLAPPED_EX ovl;
		ByteBuffer direct_buffer;
		try {
			ovl = new OVERLAPPED_EX();
			ovl.op = OVERLAPPED_EX.OP_WRITE;
			
			buffer = buffer.slice();
			buffer.position(offset);
			length = Math.min(buffer.remaining(), length);
			
			ovl.memBuffer = new MemoryBuffer(length);
			
			direct_buffer = ovl.memBuffer.getByteBuffer(0L, length);
			direct_buffer.put(buffer);
			direct_buffer.flip();
			
			ovl.write();
		} catch(OutOfMemoryError oome) {
			return false;
		}
		
		//This is only to hold onto the buffer so that the memory 
		//isn't automatically reclaimed.
		//pi.pendingWrites.put(ovl.getPointer(), direct_buffer);
		
		return writeFile(API, pi, port, ovl.memBuffer.getBufferPointer(), length, ovl);
	}
	
	private static boolean writeFile(IOComPortsAPI API, PortInfo pi, HANDLE port, Pointer buffer, int length, OVERLAPPED_EX ovl) {
		if (ovl == null || buffer == null || length <= 0)
			return true;
		
		boolean api_result = false;
		int err = ERROR_SUCCESS;
		
		try {
			api_result = API.WriteFile(port, buffer, length, null, ovl);
		} catch(LastErrorException lee) {
			err = lee.getErrorCode();
		}
		
		switch(err) {
			case ERROR_IO_PENDING:
				return !api_result;
			case ERROR_SUCCESS:
				ovl.op = OVERLAPPED_EX.OP_WRITE_IMMEDIATE;
				ovl.ex = length;
				ovl.write();
				return API.PostQueuedCompletionStatus(ioCompletionPort, Native.POINTER_SIZE, port.getPointer(), ovl);
			default:
				return false;
		}
	}
	
	public static boolean unassociateCommPort(HANDLE port) {
		PortInfo pi;
		synchronized(portLock) {
			//Verify that this port is actually managed by this IOCP and if so, then 
			//clean up any native resources before we unassociate it.
			if (ports.containsKey(port) && (pi = ports.remove(port)) != null && pi.dispose()) {
				//Unassociate this port from the IO completion port.
				
				if (portCount.decrementAndGet() == 0) {
					//Stop the threads in the pool.
					shutdownServiceThreads(ioCompletionPortServiceThreads);
					
					if (ioCompletionPort != INVALID_HANDLE_VALUE) {
						IOComPortsAPI.INSTANCE.CloseHandle(ioCompletionPort);
						ioCompletionPort = INVALID_HANDLE_VALUE;
					}
				}
				
				return true;
			}
		}
		return false;
	}
}
