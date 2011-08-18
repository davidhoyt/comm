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
package comm.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread factory for creating threads associated with comm activities 
 * such as IO completion port worker threads under Windows.
 * 
 * @author David Hoyt <dhoyt@hoytsoft.org>
 */
public class CommThreadFactory implements ThreadFactory {
	//<editor-fold defaultstate="collapsed" desc="Constants">
	private static final String
		  DEFAULT_THREAD_GROUP_NAME     = "comm thread pool"
		, DEFAULT_THREAD_PREFIX         = "comm-"
	;
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Variables">
	protected ThreadGroup threadGroup;
	protected String threadNamePrefix;
	protected final AtomicInteger threadNumber = new AtomicInteger(1);
	//</editor-fold>

	//<editor-fold defaultstate="collapsed" desc="Init">
	public CommThreadFactory() {
		SecurityManager sm = System.getSecurityManager();
		init(
			(sm != null ? sm.getThreadGroup() : null),
			DEFAULT_THREAD_GROUP_NAME
		);
	}
	
	public CommThreadFactory(ThreadGroup parentThreadGroup) {
		init(parentThreadGroup, DEFAULT_THREAD_GROUP_NAME);
	}
	
	public CommThreadFactory(ThreadGroup parentThreadGroup, String threadGroupName) {
		init(parentThreadGroup, threadGroupName);
	}
	
	private void init(ThreadGroup parentThreadGroup, String threadGroupName) {
		parentThreadGroup = (parentThreadGroup != null ? parentThreadGroup : Thread.currentThread().getThreadGroup());
		this.threadGroup = (threadGroupName != null ? new ThreadGroup(parentThreadGroup, DEFAULT_THREAD_GROUP_NAME) : parentThreadGroup);
		this.threadNamePrefix = DEFAULT_THREAD_PREFIX;
	}
	//</editor-fold>
	
	//<editor-fold defaultstate="collapsed" desc="Getters">
	public ThreadGroup getThreadGroup() {
		return threadGroup;
	}
	
	public String getThreadNamePrefix() {
		return threadNamePrefix;
	}
	
	public int getThreadNumber() {
		return threadNumber.intValue();
	}
	//</editor-fold>

	@Override
	public Thread newThread(Runnable r) {
		Thread t = new Thread(threadGroup, r, threadNamePrefix + threadNumber.getAndIncrement(), 0);
		if (t.isDaemon())
			t.setDaemon(false);
		if (t.getPriority() != Thread.NORM_PRIORITY)
			t.setPriority(Thread.NORM_PRIORITY);
		return configureThread(t);
	}
	
	protected Thread configureThread(Thread t) {
		return t;
	}
}
