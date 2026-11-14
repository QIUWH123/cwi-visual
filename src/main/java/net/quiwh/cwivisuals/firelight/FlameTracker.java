/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight;

import net.quiwh.cwivisuals.firelight.FlameKind;
import net.quiwh.cwivisuals.firelight.Flicker;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

public final class FlameTracker {
   public static final int RANGE = 40;
   private static final int[] NONE = new int[0];
   private static final int CACHE_LIMIT = 2048;
   private static final Long2ObjectOpenHashMap<FlameTracker.Entry> CACHE = new Long2ObjectOpenHashMap();
   private static ClientLevel cachedLevel;
   private static final FlameTracker.Candidates FOUND = new FlameTracker.Candidates();
   private static volatile FlameTracker.Snapshot snapshot = FlameTracker.Snapshot.EMPTY;

   private FlameTracker() {
   }

   public static FlameTracker.Snapshot snapshot() {
      return snapshot;
   }

   public static void blockChanged(BlockPos pos, BlockState before, BlockState after) {
      if (before != after && (Flames.maybeFlame(before) || Flames.maybeFlame(after))) {
         CACHE.remove(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
      }
   }

   public static void chunkChanged(ClientLevel level, int chunkX, int chunkZ) {
      if (level == cachedLevel) {
         for (int sy = Levels.minSectionY(level); sy <= Levels.maxSectionY(level); sy++) {
            CACHE.remove(SectionPos.asLong(chunkX, sy, chunkZ));
         }
      }
   }

   public static void tick(Minecraft mc) {
      ClientLevel level = mc.level;
      if (level != null && FirelightClient.active()) {
         if (level != cachedLevel) {
            forget();
            cachedLevel = level;
         }

         Vec3 camera = Cameras.position(mc);
         int cx = (int)Math.floor(camera.x);
         int cy = (int)Math.floor(camera.y);
         int cz = (int)Math.floor(camera.z);
         FlameTracker.Candidates found = FOUND;
         found.reset();
         int minSy = Math.max(SectionPos.blockToSectionCoord(cy - 40), Levels.minSectionY(level));
         int maxSy = Math.min(SectionPos.blockToSectionCoord(cy + 40), Levels.maxSectionY(level));

         for (int sx = SectionPos.blockToSectionCoord(cx - 40); sx <= SectionPos.blockToSectionCoord(cx + 40); sx++) {
            for (int sz = SectionPos.blockToSectionCoord(cz - 40); sz <= SectionPos.blockToSectionCoord(cz + 40); sz++) {
               ChunkAccess chunk = Levels.chunk(level, sx, sz);
               if (chunk != null) {
                  for (int sy = minSy; sy <= maxSy; sy++) {
                     for (int packed : flames(level, chunk, sx, sy, sz)) {
                        {
                           int x = (sx << 4) + (packed & 15);
                           int y = (sy << 4) + (packed >> 8 & 15);
                           int z = (sz << 4) + (packed >> 4 & 15);
                           int dx = x - cx;
                           int dy = y - cy;
                           int dz = z - cz;
                           int distance = dx * dx + dy * dy + dz * dz;
                           if (distance <= 1600) {
                              found.add(x, y, z, packed, distance);
                           }
                        }
                     }
                  }
               }
            }
         }

         snapshot = found.nearest(64);
      } else {
         snapshot = FlameTracker.Snapshot.EMPTY;
         forget();
         FlickerFrame.clear();
      }
   }

   private static int[] flames(ClientLevel level, ChunkAccess chunk, int sx, int sy, int sz) {
      int sectionIndex = sy - Levels.minSectionY(level);
      LevelChunkSection[] sections = chunk.getSections();
      if (sectionIndex >= 0 && sectionIndex < sections.length) {
         LevelChunkSection section = sections[sectionIndex];
         long key = SectionPos.asLong(sx, sy, sz);
         FlameTracker.Entry entry = (FlameTracker.Entry)CACHE.get(key);
         if (entry != null && entry.section() == section) {
            return entry.flames();
         } else {
            int[] flames = scan(section);
            if (CACHE.size() >= 2048) {
               CACHE.clear();
            }

            CACHE.put(key, new FlameTracker.Entry(section, flames));
            return flames;
         }
      } else {
         return NONE;
      }
   }

   static int[] scan(LevelChunkSection section) {
      if (!Levels.mayHaveFlames(section)) {
         return NONE;
      } else {
         int[] found = new int[8];
         int count = 0;

         for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  BlockState state = section.getBlockState(x, y, z);
                  FlameKind kind = Flames.kind(state);
                  if (kind != null) {
                     int light = Math.min(15, state.getLightEmission());
                     if (light > 0) {
                        if (count == found.length) {
                           found = Arrays.copyOf(found, count * 2);
                        }

                        found[count++] = x | z << 4 | y << 8 | kind.ordinal() << 12 | light << 16;
                     }
                  }
               }
            }
         }

         return Arrays.copyOf(found, count);
      }
   }

   public static void forget() {
      CACHE.clear();
      cachedLevel = null;
      snapshot = FlameTracker.Snapshot.EMPTY;
   }

   /** Retains only the nearest sources; encounter order breaks equal-distance ties. */
   static final class Candidates {
      private static final int CAPACITY = 64;
      private int size;
      private int seen;
      private boolean heapified;
      private final int[] x = new int[CAPACITY];
      private final int[] y = new int[CAPACITY];
      private final int[] z = new int[CAPACITY];
      private final int[] packed = new int[CAPACITY];
      private final int[] seed = new int[CAPACITY];
      private final long[] order = new long[CAPACITY];
      private final long[] sorted = new long[CAPACITY];

      void reset() {
         this.size = 0;
         this.seen = 0;
         this.heapified = false;
      }

      void add(int px, int py, int pz, int flame, int distance) {
         if (this.size < CAPACITY) {
            int index = this.size++;
            this.x[index] = px;
            this.y[index] = py;
            this.z[index] = pz;
            this.packed[index] = flame;
            this.seed[index] = Flicker.seed(px, py, pz);
            this.order[index] = (long)distance << 32 | (long)index;
            return;
         }
         this.offer(px, py, pz, flame, distance);
      }

      private void offer(int px, int py, int pz, int flame, int distance) {
         // Sparse scenes keep the original append-and-sort path. Build a max heap
         // only after overflowing the shader's 64-source capacity.
         if (!this.heapified) {
            for (int i = CAPACITY / 2 - 1; i >= 0; i--) {
               this.siftDown(i);
            }
            this.heapified = true;
            this.seen = CAPACITY;
         }
         long rank = (long)distance << 32 | (long)this.seen++;
         if (rank >= this.order[0]) {
            return;
         }
         this.set(0, px, py, pz, flame, Flicker.seed(px, py, pz), rank);
         this.siftDown(0);
      }

      private void set(int index, int px, int py, int pz, int flame, int flameSeed, long rank) {
         this.x[index] = px;
         this.y[index] = py;
         this.z[index] = pz;
         this.packed[index] = flame;
         this.seed[index] = flameSeed;
         this.order[index] = rank;
      }

      private void siftDown(int index) {
         int px = this.x[index];
         int py = this.y[index];
         int pz = this.z[index];
         int flame = this.packed[index];
         int flameSeed = this.seed[index];
         long rank = this.order[index];
         while (index < this.size / 2) {
            int child = index * 2 + 1;
            if (child + 1 < this.size && this.order[child + 1] > this.order[child]) {
               child++;
            }
            if (rank >= this.order[child]) {
               break;
            }
            this.set(index, this.x[child], this.y[child], this.z[child], this.packed[child], this.seed[child], this.order[child]);
            index = child;
         }
         this.set(index, px, py, pz, flame, flameSeed, rank);
      }

      FlameTracker.Snapshot nearest(int limit) {
         if (this.size == 0) {
            return FlameTracker.Snapshot.EMPTY;
         }
         if (this.heapified) {
            for (int i = 0; i < this.size; i++) {
               // Tracker distances are at most 1600, and a tick visits fewer than a
               // million block positions. Six low bits safely identify the heap slot.
               this.sorted[i] = this.order[i] << 6 | i;
            }
         } else {
            System.arraycopy(this.order, 0, this.sorted, 0, this.size);
         }
         Arrays.sort(this.sorted, 0, this.size);
         int count = Math.min(limit, this.size);
         int[] xs = new int[count];
         int[] ys = new int[count];
         int[] zs = new int[count];
         int[] levels = new int[count];
         int[] seeds = new int[count];
         FlameKind[] kinds = new FlameKind[count];

         for (int i = 0; i < count; i++) {
            int c = this.heapified ? (int)(this.sorted[i] & 63) : (int)this.sorted[i];
            xs[i] = this.x[c];
            ys[i] = this.y[c];
            zs[i] = this.z[c];
            kinds[i] = FlameKind.byIndex(this.packed[c] >>> 12 & 15);
            levels[i] = this.packed[c] >>> 16 & 15;
            seeds[i] = this.seed[c];
         }
         return new FlameTracker.Snapshot(count, xs, ys, zs, levels, seeds, kinds);
      }
   }

   private static record Entry(LevelChunkSection section, int[] flames) {
   }

   public static record Snapshot(int count, int[] x, int[] y, int[] z, int[] levels, int[] seeds, FlameKind[] kinds) {
      static final FlameTracker.Snapshot EMPTY = new FlameTracker.Snapshot(
         0, FlameTracker.NONE, FlameTracker.NONE, FlameTracker.NONE, FlameTracker.NONE, FlameTracker.NONE, new FlameKind[0]
      );
   }
}
