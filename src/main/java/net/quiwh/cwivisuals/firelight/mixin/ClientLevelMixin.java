/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight.mixin;

import net.quiwh.cwivisuals.firelight.FlameTracker;
import net.quiwh.cwivisuals.firelight.Levels;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientLevel.class})
abstract class ClientLevelMixin {
   @Inject(
      method = {"sendBlockUpdated"},
      at = {@At("HEAD")}
   )
   private void cwivisuals$blockUpdated(BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo ci) {
      FlameTracker.blockChanged(pos, before, after);
   }

   @Inject(
      method = {"onChunkLoaded"},
      at = {@At("HEAD")}
   )
   private void cwivisuals$chunkLoaded(ChunkPos pos, CallbackInfo ci) {
      FlameTracker.chunkChanged((ClientLevel)(Object)this, Levels.chunkX(pos), Levels.chunkZ(pos));
   }

   @Inject(
      method = {"unload"},
      at = {@At("HEAD")}
   )
   private void cwivisuals$chunkUnloaded(LevelChunk chunk, CallbackInfo ci) {
      FlameTracker.chunkChanged((ClientLevel)(Object)this, Levels.chunkX(chunk.getPos()), Levels.chunkZ(chunk.getPos()));
   }
}
