package com.talkingdata.ecommerce.utils;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author wwy
 * @date 2019-08-26
 */
public class ConcurrentReferenceHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {
    private static final int INITIAL_HASH = 7;
    private static final int MULTIPLIER = 31;
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75F;
    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;
    private static final ConcurrentReferenceHashMap.ReferenceType DEFAULT_REFERENCE_TYPE;
    private static final int MAXIMUM_CONCURRENCY_LEVEL = 65536;
    private static final int MAXIMUM_SEGMENT_SIZE = 1073741824;
    private final ConcurrentReferenceHashMap<K, V>.Segment[] segments;
    private final float loadFactor;
    private final ConcurrentReferenceHashMap.ReferenceType referenceType;
    private final int shift;
    private volatile Set<Map.Entry<K, V>> entrySet;

    public ConcurrentReferenceHashMap() {
        this(16, 0.75F, 16, DEFAULT_REFERENCE_TYPE);
    }

    public ConcurrentReferenceHashMap(int initialCapacity) {
        this(initialCapacity, 0.75F, 16, DEFAULT_REFERENCE_TYPE);
    }

    public ConcurrentReferenceHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, 16, DEFAULT_REFERENCE_TYPE);
    }

    public ConcurrentReferenceHashMap(int initialCapacity, int concurrencyLevel) {
        this(initialCapacity, 0.75F, concurrencyLevel, DEFAULT_REFERENCE_TYPE);
    }

    public ConcurrentReferenceHashMap(int initialCapacity, ConcurrentReferenceHashMap.ReferenceType referenceType) {
        this(initialCapacity, 0.75F, 16, referenceType);
    }

    public ConcurrentReferenceHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
        this(initialCapacity, loadFactor, concurrencyLevel, DEFAULT_REFERENCE_TYPE);
    }

    public ConcurrentReferenceHashMap(int initialCapacity, float loadFactor, int concurrencyLevel, ConcurrentReferenceHashMap.ReferenceType referenceType) {
        Assert.isTrue(initialCapacity >= 0, "Initial capacity must not be negative");
        Assert.isTrue(loadFactor > 0.0F, "Load factor must be positive");
        Assert.isTrue(concurrencyLevel > 0, "Concurrency level must be positive");
        Assert.notNull(referenceType, "Reference type must not be null");
        this.loadFactor = loadFactor;
        this.shift = calculateShift(concurrencyLevel, 65536);
        int size = 1 << this.shift;
        this.referenceType = referenceType;
        int roundedUpSegmentCapacity = (int)(((long)(initialCapacity + size) - 1L) / (long)size);
        this.segments = (ConcurrentReferenceHashMap.Segment[])((ConcurrentReferenceHashMap.Segment[]) Array.newInstance(ConcurrentReferenceHashMap.Segment.class, size));

        for(int i = 0; i < this.segments.length; ++i) {
            this.segments[i] = new ConcurrentReferenceHashMap.Segment(roundedUpSegmentCapacity);
        }

    }

    protected final float getLoadFactor() {
        return this.loadFactor;
    }

    protected final int getSegmentsSize() {
        return this.segments.length;
    }

    protected final ConcurrentReferenceHashMap<K, V>.Segment getSegment(int index) {
        return this.segments[index];
    }

    protected ConcurrentReferenceHashMap<K, V>.ReferenceManager createReferenceManager() {
        return new ConcurrentReferenceHashMap.ReferenceManager();
    }

    protected int getHash(Object o) {
        int hash = o != null ? o.hashCode() : 0;
        hash += hash << 15 ^ -12931;
        hash ^= hash >>> 10;
        hash += hash << 3;
        hash ^= hash >>> 6;
        hash += (hash << 2) + (hash << 14);
        hash ^= hash >>> 16;
        return hash;
    }

    @Override
    public V get(Object key) {
        ConcurrentReferenceHashMap.Entry<K, V> entry = this.getEntryIfAvailable(key);
        return entry != null ? entry.getValue() : null;
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        ConcurrentReferenceHashMap.Entry<K, V> entry = this.getEntryIfAvailable(key);
        return entry != null ? entry.getValue() : defaultValue;
    }

    @Override
    public boolean containsKey(Object key) {
        ConcurrentReferenceHashMap.Entry<K, V> entry = this.getEntryIfAvailable(key);
        return entry != null && nullSafeEquals(entry.getKey(), key);
    }

    private ConcurrentReferenceHashMap.Entry<K, V> getEntryIfAvailable(Object key) {
        ConcurrentReferenceHashMap.Reference<K, V> ref = this.getReference(key, ConcurrentReferenceHashMap.Restructure.WHEN_NECESSARY);
        return ref != null ? ref.get() : null;
    }

    protected final ConcurrentReferenceHashMap.Reference<K, V> getReference(Object key, ConcurrentReferenceHashMap.Restructure restructure) {
        int hash = this.getHash(key);
        return this.getSegmentForHash(hash).getReference(key, hash, restructure);
    }

    @Override
    public V put(K key, V value) {
        return this.put(key, value, true);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return this.put(key, value, false);
    }

    private V put(K key, final V value, final boolean overwriteExisting) {
        return this.doTask(key, new ConcurrentReferenceHashMap<K, V>.Task<V>(new ConcurrentReferenceHashMap.TaskOption[]{ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_BEFORE, ConcurrentReferenceHashMap.TaskOption.RESIZE}) {
            @Override
            protected V execute(ConcurrentReferenceHashMap.Reference<K, V> ref, ConcurrentReferenceHashMap.Entry<K, V> entry, ConcurrentReferenceHashMap<K, V>.Entries entries) {
                if (entry != null) {
                    V oldValue = entry.getValue();
                    if (overwriteExisting) {
                        entry.setValue(value);
                    }

                    return oldValue;
                } else {
                    entries.add(value);
                    return null;
                }
            }
        });
    }

    @Override
    public V remove(Object key) {
        return this.doTask(key, new ConcurrentReferenceHashMap<K, V>.Task<V>(new ConcurrentReferenceHashMap.TaskOption[]{ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_AFTER, ConcurrentReferenceHashMap.TaskOption.SKIP_IF_EMPTY}) {
            @Override
            protected V execute(ConcurrentReferenceHashMap.Reference<K, V> ref, ConcurrentReferenceHashMap.Entry<K, V> entry) {
                if (entry != null) {
                    ref.release();
                    return entry.value;
                } else {
                    return null;
                }
            }
        });
    }

    @Override
    public boolean remove(Object key, final Object value) {
        return (Boolean)this.doTask(key, new ConcurrentReferenceHashMap<K, V>.Task<Boolean>(new ConcurrentReferenceHashMap.TaskOption[]{ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_AFTER, ConcurrentReferenceHashMap.TaskOption.SKIP_IF_EMPTY}) {
            @Override
            protected Boolean execute(ConcurrentReferenceHashMap.Reference<K, V> ref, ConcurrentReferenceHashMap.Entry<K, V> entry) {
                if (entry != null && nullSafeEquals(entry.getValue(), value)) {
                    ref.release();
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    @Override
    public boolean replace(K key, final V oldValue, final V newValue) {
        return (Boolean)this.doTask(key, new ConcurrentReferenceHashMap<K, V>.Task<Boolean>(new ConcurrentReferenceHashMap.TaskOption[]{ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_BEFORE, ConcurrentReferenceHashMap.TaskOption.SKIP_IF_EMPTY}) {
            @Override
            protected Boolean execute(ConcurrentReferenceHashMap.Reference<K, V> ref, ConcurrentReferenceHashMap.Entry<K, V> entry) {
                if (entry != null && nullSafeEquals(entry.getValue(), oldValue)) {
                    entry.setValue(newValue);
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    @Override
    public V replace(K key, final V value) {
        return this.doTask(key, new ConcurrentReferenceHashMap<K, V>.Task<V>(new ConcurrentReferenceHashMap.TaskOption[]{ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_BEFORE, ConcurrentReferenceHashMap.TaskOption.SKIP_IF_EMPTY}) {
            @Override
            protected V execute(ConcurrentReferenceHashMap.Reference<K, V> ref, ConcurrentReferenceHashMap.Entry<K, V> entry) {
                if (entry != null) {
                    V oldValue = entry.getValue();
                    entry.setValue(value);
                    return oldValue;
                } else {
                    return null;
                }
            }
        });
    }

    @Override
    public void clear() {
        ConcurrentReferenceHashMap.Segment[] var1 = this.segments;
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            ConcurrentReferenceHashMap<K, V>.Segment segment = var1[var3];
            segment.clear();
        }

    }

    public void purgeUnreferencedEntries() {
        ConcurrentReferenceHashMap.Segment[] var1 = this.segments;
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            ConcurrentReferenceHashMap<K, V>.Segment segment = var1[var3];
            segment.restructureIfNecessary(false);
        }

    }

    @Override
    public int size() {
        int size = 0;
        ConcurrentReferenceHashMap.Segment[] var2 = this.segments;
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            ConcurrentReferenceHashMap<K, V>.Segment segment = var2[var4];
            size += segment.getCount();
        }

        return size;
    }

    @Override
    public boolean isEmpty() {
        ConcurrentReferenceHashMap.Segment[] var1 = this.segments;
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            ConcurrentReferenceHashMap<K, V>.Segment segment = var1[var3];
            if (segment.getCount() > 0) {
                return false;
            }
        }

        return true;
    }

    public Set<java.util.Map.Entry<K, V>> entrySet() {
        Set<java.util.Map.Entry<K, V>> entrySet = this.entrySet;
        if (entrySet == null) {
            entrySet = new ConcurrentReferenceHashMap.EntrySet();
            this.entrySet = (Set)entrySet;
        }

        return (Set)entrySet;
    }

    private <T> T doTask(Object key, ConcurrentReferenceHashMap<K, V>.Task<T> task) {
        int hash = this.getHash(key);
        return this.getSegmentForHash(hash).doTask(hash, key, task);
    }

    private ConcurrentReferenceHashMap<K, V>.Segment getSegmentForHash(int hash) {
        return this.segments[hash >>> 32 - this.shift & this.segments.length - 1];
    }

    protected static int calculateShift(int minimumValue, int maximumValue) {
        int shift = 0;

        for(int value = 1; value < minimumValue && value < maximumValue; ++shift) {
            value <<= 1;
        }

        return shift;
    }

    static {
        DEFAULT_REFERENCE_TYPE = ConcurrentReferenceHashMap.ReferenceType.SOFT;
    }

    private static final class WeakEntryReference<K, V> extends WeakReference<Entry<K, V>> implements ConcurrentReferenceHashMap.Reference<K, V> {
        private final int hash;
        private final ConcurrentReferenceHashMap.Reference<K, V> nextReference;

        public WeakEntryReference(ConcurrentReferenceHashMap.Entry<K, V> entry, int hash, ConcurrentReferenceHashMap.Reference<K, V> next, ReferenceQueue<Entry<K, V>> queue) {
            super(entry, queue);
            this.hash = hash;
            this.nextReference = next;
        }

        @Override
        public int getHash() {
            return this.hash;
        }

        @Override
        public ConcurrentReferenceHashMap.Reference<K, V> getNext() {
            return this.nextReference;
        }

        @Override
        public void release() {
            this.enqueue();
            this.clear();
        }
    }

    private static final class SoftEntryReference<K, V> extends SoftReference<Entry<K, V>> implements ConcurrentReferenceHashMap.Reference<K, V> {
        private final int hash;
        private final ConcurrentReferenceHashMap.Reference<K, V> nextReference;

        public SoftEntryReference(ConcurrentReferenceHashMap.Entry<K, V> entry, int hash, ConcurrentReferenceHashMap.Reference<K, V> next, ReferenceQueue<ConcurrentReferenceHashMap.Entry<K, V>> queue) {
            super(entry, queue);
            this.hash = hash;
            this.nextReference = next;
        }

        @Override
        public int getHash() {
            return this.hash;
        }

        @Override
        public ConcurrentReferenceHashMap.Reference<K, V> getNext() {
            return this.nextReference;
        }

        @Override
        public void release() {
            this.enqueue();
            this.clear();
        }
    }

    protected class ReferenceManager {
        private final ReferenceQueue<ConcurrentReferenceHashMap.Entry<K, V>> queue = new ReferenceQueue();

        protected ReferenceManager() {
        }

        public ConcurrentReferenceHashMap.Reference<K, V> createReference(ConcurrentReferenceHashMap.Entry<K, V> entry, int hash, ConcurrentReferenceHashMap.Reference<K, V> next) {
            return (ConcurrentReferenceHashMap.Reference)(ConcurrentReferenceHashMap.this.referenceType == ConcurrentReferenceHashMap.ReferenceType.WEAK ? new ConcurrentReferenceHashMap.WeakEntryReference(entry, hash, next, this.queue) : new ConcurrentReferenceHashMap.SoftEntryReference(entry, hash, next, this.queue));
        }

        public ConcurrentReferenceHashMap.Reference<K, V> pollForPurge() {
            return (ConcurrentReferenceHashMap.Reference)this.queue.poll();
        }
    }

    protected static enum Restructure {
        WHEN_NECESSARY,
        NEVER;

        private Restructure() {
        }
    }

    private class EntryIterator implements Iterator<Map.Entry<K, V>> {
        private int segmentIndex;
        private int referenceIndex;
        private ConcurrentReferenceHashMap.Reference<K, V>[] references;
        private ConcurrentReferenceHashMap.Reference<K, V> reference;
        private ConcurrentReferenceHashMap.Entry<K, V> next;
        private ConcurrentReferenceHashMap.Entry<K, V> last;

        public EntryIterator() {
            this.moveToNextSegment();
        }

        @Override
        public boolean hasNext() {
            this.getNextIfNecessary();
            return this.next != null;
        }

        @Override
        public ConcurrentReferenceHashMap.Entry<K, V> next() {
            this.getNextIfNecessary();
            if (this.next == null) {
                throw new NoSuchElementException();
            } else {
                this.last = this.next;
                this.next = null;
                return this.last;
            }
        }

        private void getNextIfNecessary() {
            while(this.next == null) {
                this.moveToNextReference();
                if (this.reference == null) {
                    return;
                }

                this.next = this.reference.get();
            }

        }

        private void moveToNextReference() {
            if (this.reference != null) {
                this.reference = this.reference.getNext();
            }

            while(this.reference == null && this.references != null) {
                if (this.referenceIndex >= this.references.length) {
                    this.moveToNextSegment();
                    this.referenceIndex = 0;
                } else {
                    this.reference = this.references[this.referenceIndex];
                    ++this.referenceIndex;
                }
            }

        }

        private void moveToNextSegment() {
            this.reference = null;
            this.references = null;
            if (this.segmentIndex < ConcurrentReferenceHashMap.this.segments.length) {
                this.references = ConcurrentReferenceHashMap.this.segments[this.segmentIndex].references;
                ++this.segmentIndex;
            }

        }

        @Override
        public void remove() {
            Assert.state(this.last != null, "No element to remove");
            ConcurrentReferenceHashMap.this.remove(this.last.getKey());
        }
    }

    private class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        private EntrySet() {
        }

        @Override
        public Iterator<java.util.Map.Entry<K, V>> iterator() {
            return ConcurrentReferenceHashMap.this.new EntryIterator();
        }

        @Override
        public boolean contains(Object o) {
            if (o instanceof java.util.Map.Entry) {
                java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry)o;
                ConcurrentReferenceHashMap.Reference<K, V> ref = ConcurrentReferenceHashMap.this.getReference(entry.getKey(), ConcurrentReferenceHashMap.Restructure.NEVER);
                ConcurrentReferenceHashMap.Entry<K, V> otherEntry = ref != null ? ref.get() : null;
                if (otherEntry != null) {
                    return nullSafeEquals(otherEntry.getValue(), otherEntry.getValue());
                }
            }

            return false;
        }

        @Override
        public boolean remove(Object o) {
            if (o instanceof java.util.Map.Entry) {
                java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry)o;
                return ConcurrentReferenceHashMap.this.remove(entry.getKey(), entry.getValue());
            } else {
                return false;
            }
        }

        @Override
        public int size() {
            return ConcurrentReferenceHashMap.this.size();
        }

        @Override
        public void clear() {
            ConcurrentReferenceHashMap.this.clear();
        }
    }

    private abstract class Entries {
        private Entries() {
        }

        public abstract void add(V var1);
    }

    private static enum TaskOption {
        RESTRUCTURE_BEFORE,
        RESTRUCTURE_AFTER,
        SKIP_IF_EMPTY,
        RESIZE;

        private TaskOption() {
        }
    }

    private abstract class Task<T> {
        private final EnumSet<TaskOption> options;

        public Task(ConcurrentReferenceHashMap.TaskOption... options) {
            this.options = options.length == 0 ? EnumSet.noneOf(ConcurrentReferenceHashMap.TaskOption.class) : EnumSet.of(options[0], options);
        }

        public boolean hasOption(ConcurrentReferenceHashMap.TaskOption option) {
            return this.options.contains(option);
        }

        protected T execute(ConcurrentReferenceHashMap.Reference<K, V> ref, ConcurrentReferenceHashMap.Entry<K, V> entry, ConcurrentReferenceHashMap<K, V>.Entries entries) {
            return this.execute(ref, entry);
        }

        protected T execute(ConcurrentReferenceHashMap.Reference<K, V> ref, ConcurrentReferenceHashMap.Entry<K, V> entry) {
            return null;
        }
    }

    protected static final class Entry<K, V> implements java.util.Map.Entry<K, V> {
        private final K key;
        private volatile V value;

        public Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return this.key;
        }

        @Override
        public V getValue() {
            return this.value;
        }

        @Override
        public V setValue(V value) {
            V previous = this.value;
            this.value = value;
            return previous;
        }

        @Override
        public String toString() {
            return this.key + "=" + this.value;
        }

        @Override
        public final boolean equals(Object other) {
            if (this == other) {
                return true;
            } else if (!(other instanceof java.util.Map.Entry)) {
                return false;
            } else {
                java.util.Map.Entry otherEntry = (java.util.Map.Entry)other;
                return nullSafeEquals(this.getKey(), otherEntry.getKey()) && nullSafeEquals(this.getValue(), otherEntry.getValue());
            }
        }

        @Override
        public final int hashCode() {
            return nullSafeHashCode(this.key) ^ nullSafeHashCode(this.value);
        }
    }

    protected interface Reference<K, V> {
        ConcurrentReferenceHashMap.Entry<K, V> get();

        int getHash();

        ConcurrentReferenceHashMap.Reference<K, V> getNext();

        void release();
    }

    protected final class Segment extends ReentrantLock {
        private final ConcurrentReferenceHashMap<K, V>.ReferenceManager referenceManager = ConcurrentReferenceHashMap.this.createReferenceManager();
        private final int initialSize;
        private volatile ConcurrentReferenceHashMap.Reference<K, V>[] references;
        private volatile int count = 0;
        private int resizeThreshold;

        public Segment(int initialCapacity) {
            this.initialSize = 1 << ConcurrentReferenceHashMap.calculateShift(initialCapacity, 1073741824);
            this.setReferences(this.createReferenceArray(this.initialSize));
        }

        public ConcurrentReferenceHashMap.Reference<K, V> getReference(Object key, int hash, ConcurrentReferenceHashMap.Restructure restructure) {
            if (restructure == ConcurrentReferenceHashMap.Restructure.WHEN_NECESSARY) {
                this.restructureIfNecessary(false);
            }

            if (this.count == 0) {
                return null;
            } else {
                ConcurrentReferenceHashMap.Reference<K, V>[] references = this.references;
                int index = this.getIndex(hash, references);
                ConcurrentReferenceHashMap.Reference<K, V> head = references[index];
                return this.findInChain(head, key, hash);
            }
        }

        public <T> T doTask(final int hash, final Object key, ConcurrentReferenceHashMap<K, V>.Task<T> task) {
            boolean resize = task.hasOption(ConcurrentReferenceHashMap.TaskOption.RESIZE);
            if (task.hasOption(ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_BEFORE)) {
                this.restructureIfNecessary(resize);
            }

            if (task.hasOption(ConcurrentReferenceHashMap.TaskOption.SKIP_IF_EMPTY) && this.count == 0) {
                return (T) task.execute((ConcurrentReferenceHashMap.Reference)null, (ConcurrentReferenceHashMap.Entry)null, (ConcurrentReferenceHashMap.Entries)null);
            } else {
                this.lock();

                Object var10;
                try {
                    final int index = this.getIndex(hash, this.references);
                    final ConcurrentReferenceHashMap.Reference<K, V> head = this.references[index];
                    ConcurrentReferenceHashMap.Reference<K, V> ref = this.findInChain(head, key, hash);
                    ConcurrentReferenceHashMap.Entry<K, V> entry = ref != null ? ref.get() : null;
                    ConcurrentReferenceHashMap<K, V>.Entries entries = new ConcurrentReferenceHashMap<K, V>.Entries() {
                        @Override
                        public void add(V value) {
                            ConcurrentReferenceHashMap.Entry<K, V> newEntry = new ConcurrentReferenceHashMap.Entry(key, value);
                            ConcurrentReferenceHashMap.Reference<K, V> newReference = Segment.this.referenceManager.createReference(newEntry, hash, head);
                            Segment.this.references[index] = newReference;
                            Segment.this.count++;
                        }
                    };
                    var10 = task.execute(ref, entry, entries);
                } finally {
                    this.unlock();
                    if (task.hasOption(ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_AFTER)) {
                        this.restructureIfNecessary(resize);
                    }

                }

                return (T) var10;
            }
        }

        public void clear() {
            if (this.count != 0) {
                this.lock();

                try {
                    this.setReferences(this.createReferenceArray(this.initialSize));
                    this.count = 0;
                } finally {
                    this.unlock();
                }

            }
        }

        protected final void restructureIfNecessary(boolean allowResize) {
            boolean needsResize = this.count > 0 && this.count >= this.resizeThreshold;
            ConcurrentReferenceHashMap.Reference<K, V> ref = this.referenceManager.pollForPurge();
            if (ref != null || needsResize && allowResize) {
                this.lock();

                try {
                    int countAfterRestructure = this.count;
                    Set<ConcurrentReferenceHashMap.Reference<K, V>> toPurge = Collections.emptySet();
                    if (ref != null) {
                        for(toPurge = new HashSet(); ref != null; ref = this.referenceManager.pollForPurge()) {
                            ((Set)toPurge).add(ref);
                        }
                    }

                    countAfterRestructure -= ((Set)toPurge).size();
                    needsResize = countAfterRestructure > 0 && countAfterRestructure >= this.resizeThreshold;
                    boolean resizing = false;
                    int restructureSize = this.references.length;
                    if (allowResize && needsResize && restructureSize < 1073741824) {
                        restructureSize <<= 1;
                        resizing = true;
                    }

                    ConcurrentReferenceHashMap.Reference<K, V>[] restructured = resizing ? this.createReferenceArray(restructureSize) : this.references;

                    for(int i = 0; i < this.references.length; ++i) {
                        ref = this.references[i];
                        if (!resizing) {
                            restructured[i] = null;
                        }

                        for(; ref != null; ref = ref.getNext()) {
                            if (!((Set)toPurge).contains(ref) && ref.get() != null) {
                                int index = this.getIndex(ref.getHash(), restructured);
                                restructured[index] = this.referenceManager.createReference(ref.get(), ref.getHash(), restructured[index]);
                            }
                        }
                    }

                    if (resizing) {
                        this.setReferences(restructured);
                    }

                    this.count = Math.max(countAfterRestructure, 0);
                } finally {
                    this.unlock();
                }
            }

        }

        private ConcurrentReferenceHashMap.Reference<K, V> findInChain(ConcurrentReferenceHashMap.Reference<K, V> ref, Object key, int hash) {
            for(ConcurrentReferenceHashMap.Reference currRef = ref; currRef != null; currRef = currRef.getNext()) {
                if (currRef.getHash() == hash) {
                    ConcurrentReferenceHashMap.Entry<K, V> entry = currRef.get();
                    if (entry != null) {
                        K entryKey = entry.getKey();
                        if (nullSafeEquals(entryKey, key)) {
                            return currRef;
                        }
                    }
                }
            }

            return null;
        }

        private ConcurrentReferenceHashMap.Reference<K, V>[] createReferenceArray(int size) {
            return new ConcurrentReferenceHashMap.Reference[size];
        }

        private int getIndex(int hash, ConcurrentReferenceHashMap.Reference<K, V>[] references) {
            return hash & references.length - 1;
        }

        private void setReferences(ConcurrentReferenceHashMap.Reference<K, V>[] references) {
            this.references = references;
            this.resizeThreshold = (int)((float)references.length * ConcurrentReferenceHashMap.this.getLoadFactor());
        }

        public final int getSize() {
            return this.references.length;
        }

        public final int getCount() {
            return this.count;
        }
    }

    public static enum ReferenceType {
        SOFT,
        WEAK;

        private ReferenceType() {
        }
    }

    //---------------------------------------------------------------------
    // Convenience methods for content-based equality/hash-code handling
    //---------------------------------------------------------------------

    /**
     * Determine if the given objects are equal, returning {@code true} if
     * both are {@code null} or {@code false} if only one is {@code null}.
     * <p>Compares arrays with {@code Arrays.equals}, performing an equality
     * check based on the array elements rather than the array reference.
     * @param o1 first Object to compare
     * @param o2 second Object to compare
     * @return whether the given objects are equal
     * @see Object#equals(Object)
     * @see java.util.Arrays#equals
     */
    public static boolean nullSafeEquals(Object o1, Object o2) {
        if (o1 == o2) {
            return true;
        }
        if (o1 == null || o2 == null) {
            return false;
        }
        if (o1.equals(o2)) {
            return true;
        }
        if (o1.getClass().isArray() && o2.getClass().isArray()) {
            return arrayEquals(o1, o2);
        }
        return false;
    }

    /**
     * Compare the given arrays with {@code Arrays.equals}, performing an equality
     * check based on the array elements rather than the array reference.
     * @param o1 first array to compare
     * @param o2 second array to compare
     * @return whether the given objects are equal
     * @see #nullSafeEquals(Object, Object)
     * @see java.util.Arrays#equals
     */
    private static boolean arrayEquals(Object o1, Object o2) {
        if (o1 instanceof Object[] && o2 instanceof Object[]) {
            return Arrays.equals((Object[]) o1, (Object[]) o2);
        }
        if (o1 instanceof boolean[] && o2 instanceof boolean[]) {
            return Arrays.equals((boolean[]) o1, (boolean[]) o2);
        }
        if (o1 instanceof byte[] && o2 instanceof byte[]) {
            return Arrays.equals((byte[]) o1, (byte[]) o2);
        }
        if (o1 instanceof char[] && o2 instanceof char[]) {
            return Arrays.equals((char[]) o1, (char[]) o2);
        }
        if (o1 instanceof double[] && o2 instanceof double[]) {
            return Arrays.equals((double[]) o1, (double[]) o2);
        }
        if (o1 instanceof float[] && o2 instanceof float[]) {
            return Arrays.equals((float[]) o1, (float[]) o2);
        }
        if (o1 instanceof int[] && o2 instanceof int[]) {
            return Arrays.equals((int[]) o1, (int[]) o2);
        }
        if (o1 instanceof long[] && o2 instanceof long[]) {
            return Arrays.equals((long[]) o1, (long[]) o2);
        }
        if (o1 instanceof short[] && o2 instanceof short[]) {
            return Arrays.equals((short[]) o1, (short[]) o2);
        }
        return false;
    }

    /**
     * Return as hash code for the given object; typically the value of
     * {@code Object#hashCode()}}. If the object is an array,
     * this method will delegate to any of the {@code nullSafeHashCode}
     * methods for arrays in this class. If the object is {@code null},
     * this method returns 0.
     * @see Object#hashCode()
     * @see #nullSafeHashCode(Object[])
     * @see #nullSafeHashCode(boolean[])
     * @see #nullSafeHashCode(byte[])
     * @see #nullSafeHashCode(char[])
     * @see #nullSafeHashCode(double[])
     * @see #nullSafeHashCode(float[])
     * @see #nullSafeHashCode(int[])
     * @see #nullSafeHashCode(long[])
     * @see #nullSafeHashCode(short[])
     */
    public static int nullSafeHashCode(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj.getClass().isArray()) {
            if (obj instanceof Object[]) {
                return nullSafeHashCode((Object[]) obj);
            }
            if (obj instanceof boolean[]) {
                return nullSafeHashCode((boolean[]) obj);
            }
            if (obj instanceof byte[]) {
                return nullSafeHashCode((byte[]) obj);
            }
            if (obj instanceof char[]) {
                return nullSafeHashCode((char[]) obj);
            }
            if (obj instanceof double[]) {
                return nullSafeHashCode((double[]) obj);
            }
            if (obj instanceof float[]) {
                return nullSafeHashCode((float[]) obj);
            }
            if (obj instanceof int[]) {
                return nullSafeHashCode((int[]) obj);
            }
            if (obj instanceof long[]) {
                return nullSafeHashCode((long[]) obj);
            }
            if (obj instanceof short[]) {
                return nullSafeHashCode((short[]) obj);
            }
        }
        return obj.hashCode();
    }

    /**
     * Return a hash code based on the contents of the specified array.
     * If {@code array} is {@code null}, this method returns 0.
     */
    public static int nullSafeHashCode(Object[] array) {
        if (array == null) {
            return 0;
        }
        int hash = INITIAL_HASH;
        for (Object element : array) {
            hash = MULTIPLIER * hash + nullSafeHashCode(element);
        }
        return hash;
    }

    /**
     * Return a hash code based on the contents of the specified array.
     * If {@code array} is {@code null}, this method returns 0.
     */
    public static int nullSafeHashCode(boolean[] array) {
        if (array == null) {
            return 0;
        }
        int hash = INITIAL_HASH;
        for (boolean element : array) {
            hash = MULTIPLIER * hash + hashCode(element);
        }
        return hash;
    }

    /**
     * Return a hash code based on the contents of the specified array.
     * If {@code array} is {@code null}, this method returns 0.
     */
    public static int nullSafeHashCode(byte[] array) {
        if (array == null) {
            return 0;
        }
        int hash = INITIAL_HASH;
        for (byte element : array) {
            hash = MULTIPLIER * hash + element;
        }
        return hash;
    }

    /**
     * Return a hash code based on the contents of the specified array.
     * If {@code array} is {@code null}, this method returns 0.
     */
    public static int nullSafeHashCode(char[] array) {
        if (array == null) {
            return 0;
        }
        int hash = INITIAL_HASH;
        for (char element : array) {
            hash = MULTIPLIER * hash + element;
        }
        return hash;
    }

    /**
     * Return a hash code based on the contents of the specified array.
     * If {@code array} is {@code null}, this method returns 0.
     */
    public static int nullSafeHashCode(double[] array) {
        if (array == null) {
            return 0;
        }
        int hash = INITIAL_HASH;
        for (double element : array) {
            hash = MULTIPLIER * hash + hashCode(element);
        }
        return hash;
    }

    /**
     * Return a hash code based on the contents of the specified array.
     * If {@code array} is {@code null}, this method returns 0.
     */
    public static int nullSafeHashCode(float[] array) {
        if (array == null) {
            return 0;
        }
        int hash = INITIAL_HASH;
        for (float element : array) {
            hash = MULTIPLIER * hash + hashCode(element);
        }
        return hash;
    }

    /**
     * Return a hash code based on the contents of the specified array.
     * If {@code array} is {@code null}, this method returns 0.
     */
    public static int nullSafeHashCode(int[] array) {
        if (array == null) {
            return 0;
        }
        int hash = INITIAL_HASH;
        for (int element : array) {
            hash = MULTIPLIER * hash + element;
        }
        return hash;
    }

    /**
     * Return a hash code based on the contents of the specified array.
     * If {@code array} is {@code null}, this method returns 0.
     */
    public static int nullSafeHashCode(long[] array) {
        if (array == null) {
            return 0;
        }
        int hash = INITIAL_HASH;
        for (long element : array) {
            hash = MULTIPLIER * hash + hashCode(element);
        }
        return hash;
    }

    /**
     * Return a hash code based on the contents of the specified array.
     * If {@code array} is {@code null}, this method returns 0.
     */
    public static int nullSafeHashCode(short[] array) {
        if (array == null) {
            return 0;
        }
        int hash = INITIAL_HASH;
        for (short element : array) {
            hash = MULTIPLIER * hash + element;
        }
        return hash;
    }

    /**
     * Return the same value as {@link Boolean#hashCode()}}.
     * @see Boolean#hashCode()
     */
    public static int hashCode(boolean bool) {
        return (bool ? 1231 : 1237);
    }

    /**
     * Return the same value as {@link Double#hashCode()}}.
     * @see Double#hashCode()
     */
    public static int hashCode(double dbl) {
        return hashCode(Double.doubleToLongBits(dbl));
    }

    /**
     * Return the same value as {@link Float#hashCode()}}.
     * @see Float#hashCode()
     */
    public static int hashCode(float flt) {
        return Float.floatToIntBits(flt);
    }

    /**
     * Return the same value as {@link Long#hashCode()}}.
     * @see Long#hashCode()
     */
    public static int hashCode(long lng) {
        return (int) (lng ^ (lng >>> 32));
    }

}
