
package comm.platform.api;

/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */


import com.sun.jna.Memory;

public class FreeableMemory extends Memory {

	public FreeableMemory(long size) {
		super(size);
	}
	
	@Override
	@SuppressWarnings("FinalizeCalledExplicitly")
	public void dispose() {
		synchronized(this) {
			if (peer == 0L)
				return;
			super.dispose();
			super.finalize();
			peer = 0L;
		}
	}

	/**
	 * avoid re-freeing already-freed memory
	 */
	@Override
	@SuppressWarnings({"FinalizeDeclaration", "FinalizeDoesntCallSuperFinalize"})
	protected void finalize() {
		dispose();
	}
}