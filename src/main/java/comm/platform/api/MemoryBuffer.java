
package comm.platform.api;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author David Hoyt <dhoyt@hoytsoft.org>
 */
public class MemoryBuffer extends Structure {
	private static Map<Pointer, MemoryBuffer> refs = new ConcurrentHashMap<Pointer, MemoryBuffer>();

	public int reference_count;
	public long size;
	public Pointer buffer;
	
	public MemoryBuffer() {
	}
	
	@SuppressWarnings("LeakingThisInConstructor")
	public MemoryBuffer(long size) {
		if (size <= 0L)
			return;
		
		Pointer ptr = getPointer();
		this.size = size;
		this.reference_count = 1;
		this.buffer = new FreeableMemory(size);
		
		refs.put(buffer, this);
	}
	
	@SuppressWarnings("OverridableMethodCallInConstructor")
	public MemoryBuffer(Pointer ptr) {
		useMemory(ptr);
		read();
		refresh(ptr);
	}
	
	public void refresh() {
		refresh(buffer);
	}
	
	public void refresh(Pointer ptr) {
		synchronized(this) {
			MemoryBuffer mb = refs.get(ptr);
			if (mb != null) {
				this.buffer = null;
				this.buffer = mb.buffer;
			}
		}
	}
	
	public int reference() {
		return ++reference_count;
	}
	
	public int unreference() {
		int count = reference_count - 1;
		if (count < 0)
			return 0;
		--reference_count;
		if (count <= 0)
			dispose();
		return count;
	}
	
	public void dispose() {
		synchronized(this) {
			Pointer ptr = getBufferPointer();
			if (!refs.containsKey(ptr))
				return;
			refs.remove(ptr);
			if (buffer instanceof FreeableMemory)
				((FreeableMemory)buffer).dispose();
		}
	}

	@Override
	@SuppressWarnings("FinalizeDeclaration")
	protected void finalize() throws Throwable {
		dispose();
	}
	
	public ByteBuffer getByteBuffer(long offset, long length) {
		return buffer.getByteBuffer(offset, length);
	}
	
	public Pointer getBufferPointer() {
		return buffer;
	}
	
	public long getBufferSize() {
		return size;
	}
	
	public int getReferenceCount() {
		return reference_count;
	}
	
	public static int getGlobalInstanceCount() {
		return refs.size();
	}
}
