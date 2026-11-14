/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class Levels {
   private Levels() {
   }

   public static int minSectionY(LevelHeightAccessor level) {
      return level.getMinSection();
   }

   public static int maxSectionY(LevelHeightAccessor level) {
      return level.getMaxSection() - 1;
   }

   public static ChunkAccess chunk(ClientLevel level, int x, int z) {
      return level.getChunk(x, z, ChunkStatus.FULL, false);
   }

   public static int chunkX(ChunkPos pos) {
      return pos.x;
   }

   public static int chunkZ(ChunkPos pos) {
      return pos.z;
   }

   public static boolean mayHaveFlames(LevelChunkSection section) {
      return section != null && !section.hasOnlyAir() && section.maybeHas(Flames::maybeFlame);
   }
}
