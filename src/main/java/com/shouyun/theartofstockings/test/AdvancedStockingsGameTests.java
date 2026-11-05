package com.shouyun.theartofstockings.test;

import com.shouyun.theartofstockings.*;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TheArtofStockings.MODID)
@PrefixGameTestTemplate(false)
public final class AdvancedStockingsGameTests {
    private static String texture() throws Exception {
        try(var stream=AdvancedStockingsGameTests.class.getResourceAsStream("/assets/theartofstockings/textures/entity/custom_stockings.png")) {
            return java.util.Base64.getEncoder().encodeToString(stream.readAllBytes());
        }
    }
    private static ItemStack craft(GameTestHelper h,ItemStack... stacks) {
        var input=CraftingInput.of(stacks.length,1,List.of(stacks));
        return h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow()
                .value().assemble(input,h.getLevel().registryAccess());
    }
    private static String largeArtwork() throws Exception {
        var image=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var random=new java.util.Random(195);
        for(int y=0;y<256;y++)for(int x=0;x<256;x++)image.setRGB(x,y,random.nextInt());
        return CustomStockingsTexture.encode(image);
    }
    @GameTest(template="empty") public static void largeArtworkChunkUploadAndDiskPersistence(GameTestHelper h) throws Exception {
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.PALETTE.toStack());
        var pair=TheArtofStockings.CUSTOM.toStack();player.setItemInHand(InteractionHand.OFF_HAND,pair);
        String artwork=largeArtwork();byte[] bytes=java.util.Base64.getDecoder().decode(artwork);
        h.assertTrue(bytes.length>65535,"Artwork exceeds the old file and NBT UTF limits");
        var token=PaletteEdits.open(player,40,pair);int total=(bytes.length+PaletteEdits.CHUNK_BYTES-1)/PaletteEdits.CHUNK_BYTES;
        for(int i=0;i<total;i++) {
            var chunk=new StockingsNetwork.PaletteChunk(token,0xFFFFFF,0,63,i,total,
                    java.util.Arrays.copyOfRange(bytes,i*PaletteEdits.CHUNK_BYTES,Math.min(bytes.length,(i+1)*PaletteEdits.CHUNK_BYTES)));
            var buf=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try {
                StockingsNetwork.PaletteChunk.CODEC.encode(buf,chunk);h.assertTrue(buf.readableBytes()<32767,"Every chunk fits the wire payload limit");
                var decoded=StockingsNetwork.PaletteChunk.CODEC.decode(buf);
                var result=PaletteEdits.receive(player,decoded);
                h.assertTrue(result==(i==total-1?PaletteEdits.Result.SUCCESS:PaletteEdits.Result.PENDING),"Upload commits atomically after the final chunk");
                if(i<total-1)h.assertTrue(!pair.has(TheArtofStockings.CUSTOM_TEXTURE.get()),"Partial images never overwrite an item");
            } finally { buf.release(); }
        }
        h.assertTrue(artwork.equals(pair.get(TheArtofStockings.CUSTOM_TEXTURE.get())) && StockingsItem.opacity(pair)==63,"All uploaded pixels and opacity survive");
        var tag=(net.minecraft.nbt.CompoundTag)pair.save(h.getLevel().registryAccess());
        var disk=new java.io.ByteArrayOutputStream();net.minecraft.nbt.NbtIo.write(tag,new java.io.DataOutputStream(disk));
        var reloaded=net.minecraft.nbt.NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(disk.toByteArray())),net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        h.assertTrue(ItemStack.matches(ItemStack.parseOptional(h.getLevel().registryAccess(),reloaded),pair),"A large image survives real NBT disk encoding and decoding");
        var network=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(network,pair);
            h.assertTrue(ItemStack.matches(ItemStack.STREAM_CODEC.decode(network),pair),"A large image survives item stack network sync");
        } finally { network.release(); }
        var wearBuffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            var request=new StockingsNetwork.WearRequest(WearSlot.HEAD.ordinal(),InteractionHand.MAIN_HAND,true,pair);
            StockingsNetwork.WearRequest.CODEC.encode(wearBuffer,request);
            h.assertTrue(wearBuffer.readableBytes()<2048,"Head/hand requests fingerprint large artwork instead of resending PNGs above the payload limit");
            var decoded=StockingsNetwork.WearRequest.CODEC.decode(wearBuffer);
            player.setItemInHand(InteractionHand.MAIN_HAND,pair.copy());decoded.apply(player);
            h.assertTrue(StockingsActions.busy(player),"A compact request still validates the complete server-owned artwork");StockingsActions.cancel(player);
            player.getMainHandItem().remove(TheArtofStockings.CUSTOM_TEXTURE.get());decoded.apply(player);
            h.assertTrue(!StockingsActions.busy(player),"Changed artwork rejects a stale clothing request");
        } finally { wearBuffer.release(); }
        h.succeed();
    }
    @GameTest(template="empty") public static void rejectBrokenUploadsAndChangedTargets(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.PALETTE.toStack());
        var pair=TheArtofStockings.CUSTOM.toStack();player.setItemInHand(InteractionHand.OFF_HAND,pair);
        var token=PaletteEdits.open(player,40,pair);
        h.assertTrue(PaletteEdits.receive(player,new StockingsNetwork.PaletteChunk(token,0,0,100,1,2,new byte[4]))==PaletteEdits.Result.REJECTED,"Reject missing first chunks");
        token=PaletteEdits.open(player,40,pair);
        h.assertTrue(PaletteEdits.receive(player,new StockingsNetwork.PaletteChunk(token,0,0,100,0,PaletteEdits.MAX_CHUNKS+1,new byte[4]))==PaletteEdits.Result.REJECTED,"Bound upload allocation");
        token=PaletteEdits.open(player,40,pair);
        h.assertTrue(PaletteEdits.receive(player,new StockingsNetwork.PaletteChunk(token,0,0,100,0,2,new byte[4]))==PaletteEdits.Result.PENDING,"First valid chunk begins a draft");
        var replacement=TheArtofStockings.CUSTOM.toStack();replacement.set(TheArtofStockings.OPACITY.get(),25);player.setItemInHand(InteractionHand.OFF_HAND,replacement);
        h.assertTrue(PaletteEdits.receive(player,new StockingsNetwork.PaletteChunk(token,0,0,100,1,2,new byte[4]))==PaletteEdits.Result.REJECTED,"Changing the target during upload rejects the commit");
        h.assertTrue(StockingsItem.opacity(replacement)==25 && !replacement.has(TheArtofStockings.CUSTOM_TEXTURE.get()),"A rejected upload leaves the new item untouched");
        h.succeed();
    }
    @GameTest(template="empty") public static void newStylesCraftAndRetainComponents(GameTestHelper h) {
        var materials=List.of(Items.WHITE_WOOL,Items.RABBIT_HIDE,Items.INK_SAC,Items.SWEET_BERRIES,Items.PAPER);
        var targets=List.of(TheArtofStockings.STRIPED,TheArtofStockings.CAT_PAW,TheArtofStockings.MATTE,TheArtofStockings.FOX,TheArtofStockings.CUSTOM);
        for(int i=0;i<materials.size();i++) {
            var source=TheArtofStockings.STOCKINGS.toStack();
            source.set(TheArtofStockings.OPACITY.get(),37);
            source.set(TheArtofStockings.STINKY.get(),net.minecraft.util.Unit.INSTANCE);
            source.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("My pair"));
            var pair=craft(h,source,new ItemStack(materials.get(i)));
            h.assertTrue(pair.is(targets.get(i).get()) && StockingsItem.opacity(pair)==37 && pair.has(TheArtofStockings.STINKY.get()),"Tailoring preserves opacity and odor");
            h.assertTrue(pair.getHoverName().getString().contains("My pair"),"Tailoring preserves custom name");
            var redyed=craft(h,pair,new ItemStack(Items.RED_DYE),new ItemStack(Items.BLUE_DYE));
            h.assertTrue(redyed.is(pair.getItem()),"All new styles can be redyed");
            if(StockingsItem.twoColors(pair)) {
                h.assertTrue(StockingsItem.color(redyed)==net.minecraft.world.item.DyeColor.RED.getTextureDiffuseColor(),"First dye sets fabric");
                h.assertTrue(StockingsItem.secondaryColor(redyed)==net.minecraft.world.item.DyeColor.BLUE.getTextureDiffuseColor(),"Second dye sets pattern");
            }
        }
        var fox=TheArtofStockings.FOX.toStack();
        h.assertTrue((StockingsItem.color(fox)&0xFFFFFF)==0xB84B16 && (StockingsItem.secondaryColor(fox)&0xFFFFFF)==0x180E0B,"Fox defaults are deep orange and brown-black");
        h.succeed();
    }
    @GameTest(template="empty") public static void customTextureOpacitySaveAndSync(GameTestHelper h) throws Exception {
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.PALETTE.toStack());
        var pair=TheArtofStockings.CUSTOM.toStack();player.setItemInHand(InteractionHand.OFF_HAND,pair);
        String png=texture();var expected=pair.copy();
        h.assertTrue(PaletteItem.apply(player,40,expected,0xFFFFFF,0,42,png),"Valid custom PNG and opacity apply together");
        h.assertTrue(StockingsItem.opacity(pair)==42 && pair.get(TheArtofStockings.CUSTOM_TEXTURE.get()).equals(png),"Both edits are stored on item");
        var loaded=ItemStack.parseOptional(h.getLevel().registryAccess(),(net.minecraft.nbt.CompoundTag)pair.save(h.getLevel().registryAccess()));
        h.assertTrue(ItemStack.matches(loaded,pair),"PNG and opacity survive save/reload");
        var buf=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            var token=PaletteEdits.open(player,40,pair);
            var packet=new StockingsNetwork.PaletteChunk(token,0x123456,0,0,0,1,java.util.Base64.getDecoder().decode(png));
            StockingsNetwork.PaletteChunk.CODEC.encode(buf,packet);
            h.assertTrue(buf.readableBytes()<32767,"Each upload chunk fits the serverbound payload");
            var decoded=StockingsNetwork.PaletteChunk.CODEC.decode(buf);
            h.assertTrue(decoded.opacity()==0 && java.util.Arrays.equals(decoded.bytes(),packet.bytes()) && decoded.session().equals(token),"Packet preserves edit token, pixels and zero opacity");
            PaletteEdits.close(player);
        } finally { buf.release(); }
        h.assertTrue(!PaletteItem.apply(player,40,expected,0,0,99,""),"Stale editor cannot replace newer texture");
        h.assertTrue(PaletteItem.apply(player,40,pair.copy(),0xFFFFFF,0,100,""),"Texture can be cleared and opacity restored");
        h.assertTrue(!pair.has(TheArtofStockings.CUSTOM_TEXTURE.get()),"Clear removes custom texture component");
        h.succeed();
    }
    @GameTest(template="empty") public static void rejectInvalidAppearanceWithoutMutation(GameTestHelper h) throws Exception {
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.PALETTE.toStack());
        var pair=TheArtofStockings.CUSTOM.toStack();player.setItemInHand(InteractionHand.OFF_HAND,pair);var before=pair.copy();
        for(int alpha:List.of(-1,101,Integer.MAX_VALUE))
            h.assertTrue(!PaletteItem.apply(player,40,before,0,0,alpha,""),"Reject opacity outside 0..100");
        for(String invalid:List.of("not png", "a".repeat(CustomStockingsTexture.MAX_ENCODED_LENGTH+1), java.util.Base64.getEncoder().encodeToString(new byte[40])))
            h.assertTrue(!PaletteItem.apply(player,40,before,0,0,50,invalid),"Reject invalid images");
        h.assertTrue(ItemStack.matches(pair,before),"Invalid edits are atomic");
        player.setItemInHand(InteractionHand.OFF_HAND,TheArtofStockings.FOX.toStack());
        h.assertTrue(!PaletteItem.apply(player,40,player.getOffhandItem().copy(),0,0,50,texture()),"Only the custom item accepts PNG data");
        h.succeed();
    }
    @GameTest(template="empty") public static void stripesHaveHardBoundariesAndPawSoles(GameTestHelper h) throws Exception {
        try(var stream=AdvancedStockingsGameTests.class.getResourceAsStream("/assets/theartofstockings/geo/striped_stockings.geo.json")) {
            var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for(var bone:json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
                var b=bone.getAsJsonObject();if(!b.get("name").getAsString().endsWith("_pull"))continue;
                for(var cube:b.getAsJsonArray("cubes")) {
                    var c=cube.getAsJsonObject();float low=c.getAsJsonArray("origin").get(1).getAsFloat();
                    float high=low+c.getAsJsonArray("size").get(1).getAsFloat();
                    h.assertTrue((int)Math.floor((low+.001)/1.3)==(int)Math.floor((high-.001)/1.3),"No stripe face interpolates across two colors");
                }
            }
        }
        try(var stream=AdvancedStockingsGameTests.class.getResourceAsStream("/assets/theartofstockings/textures/entity/cat_paw_overlay.png")) {
            var image=javax.imageio.ImageIO.read(stream);
            h.assertTrue((image.getRGB(24,238)>>>24)>0 && (image.getRGB(100,100)>>>24)==0,"Paw pad occupies sole UV and leaves fabric clear");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void optionalAccessoryStorageAndMigration(GameTestHelper h) {
        if(!StockingsWear.curiosLoaded() && !StockingsWear.accessoriesLoaded()) { h.succeed();return; }
        var player=ModdedTestPlayers.create(h);
        h.assertTrue(StockingsWear.accessoryAvailable(player),"Installed accessory mod assigns a real stockings slot to players");
        var pair=TheArtofStockings.FOX.toStack();pair.set(TheArtofStockings.OPACITY.get(),62);
        player.setData(TheArtofStockings.WORN_STOCKINGS,pair.copy());
        StockingsWear.migrateLegacy(player);StockingsWear.migrateLegacy(player);
        h.assertTrue(StockingsWear.stored(player).isEmpty() && ItemStack.matches(StockingsWear.get(player),pair),"Legacy attachment migrates once into native accessory storage");
        h.assertTrue(player.getInventory().countItem(pair.getItem())==0,"Migration never duplicates the worn pair");
        player.setItemInHand(InteractionHand.MAIN_HAND,TheArtofStockings.PALETTE.toStack());
        h.assertTrue(PaletteItem.apply(player,PaletteItem.WORN_SLOT,pair.copy(),0xE27722,0x221911,25,""),"Palette writes native accessory slot");
        h.assertTrue(StockingsItem.opacity(StockingsWear.get(player))==25 && StockingsWear.stored(player).isEmpty(),"Palette never creates shadow attachment");
        var worn=StockingsWear.get(player).copy();
        if(StockingsWear.accessoriesLoaded()) AccessoriesVisualChecks.verify(h,player,worn);
        var loaded=ModdedTestPlayers.create(h);loaded.load(player.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(ItemStack.matches(StockingsWear.get(loaded),worn),"Native accessory storage persists all item components");
        StockingsWear.set(player,ItemStack.EMPTY);StockingsWear.set(loaded,ItemStack.EMPTY);
        h.getLevel().getServer().getPlayerList().remove(player);h.getLevel().getServer().getPlayerList().remove(loaded);
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=180) public static void nativeAccessoryAnimatedRoundTrip(GameTestHelper h) {
        if(!StockingsWear.curiosLoaded() && !StockingsWear.accessoriesLoaded()) { h.succeed();return; }
        var player=ModdedTestPlayers.create(h);var pair=TheArtofStockings.CAT_PAW.toStack();
        pair.set(TheArtofStockings.OPACITY.get(),53);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));
        player.setItemInHand(InteractionHand.MAIN_HAND,pair.copy());
        StockingsActions.startEquip(player,InteractionHand.MAIN_HAND);
        h.assertTrue(StockingsWear.get(player).isEmpty(),"Native accessory equipment waits for the animation");
        h.runAfterDelay(73,()->{
            StockingsActions.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player));
            StockingsHistory.mark(pair);
            h.assertTrue(ItemStack.matches(StockingsWear.get(player),pair) && StockingsWear.stored(player).isEmpty(),"Animated equip writes exactly one native accessory");
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(FootOdor.EFFECT,1200));
            player.setShiftKeyDown(true);StockingsActions.requestRemoval(player);
        });
        h.runAfterDelay(146,()->{
            StockingsActions.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player));
            h.assertTrue(StockingsWear.get(player).isEmpty() && player.getInventory().countItem(pair.getItem())==1,"Native animated removal returns exactly one pair");
            h.assertTrue(player.getInventory().items.stream().anyMatch(stack->stack.is(pair.getItem())
                    && stack.has(TheArtofStockings.STINKY.get()) && StockingsItem.opacity(stack)==53),"Scented removal keeps odor and opacity without cancelling the transaction");
            h.assertTrue(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS),"Native accessory dressing preserves armor");
            h.getLevel().getServer().getPlayerList().remove(player);h.succeed();
        });
    }
    @GameTest(template="empty") public static void nativeAccessoryDeathAndKeepInventory(GameTestHelper h) {
        if(!StockingsWear.curiosLoaded() && !StockingsWear.accessoriesLoaded()) { h.succeed();return; }
        var rule=h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        boolean before=rule.get();var player=ModdedTestPlayers.create(h);
        player.setPos(h.absolutePos(new net.minecraft.core.BlockPos(1,1,1)).getCenter());
        var pair=TheArtofStockings.MATTE.toStack();pair.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Native death test"));
        try {
            rule.set(false,h.getLevel().getServer());StockingsWear.set(player,pair);player.die(player.damageSources().generic());
            h.assertTrue(StockingsWear.get(player).isEmpty(),"Accessory owner clears the pair on normal death");
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,player.getBoundingBox().inflate(3),e->ItemStack.matches(e.getItem(),pair));
            h.assertTrue(drops.size()==1,"Accessory death drops exactly one pair, without a shadow attachment drop");
            rule.set(true,h.getLevel().getServer());var kept=ModdedTestPlayers.create(h);
            try {
                StockingsWear.set(kept,pair);kept.die(kept.damageSources().generic());
                h.assertTrue(ItemStack.matches(StockingsWear.get(kept),pair),"Accessory owner honors keepInventory");
            } finally { h.getLevel().getServer().getPlayerList().remove(kept); }
        } finally { rule.set(before,h.getLevel().getServer());h.getLevel().getServer().getPlayerList().remove(player); }
        h.succeed();
    }
    private static final class AccessoriesVisualChecks {
        static void verify(GameTestHelper h,net.minecraft.world.entity.player.Player player,ItemStack functional) {
            var container=io.wispforest.accessories.api.AccessoriesCapability.get(player).getContainers().get("stockings");
            var cosmetic=TheArtofStockings.MATTE.toStack();
            container.getCosmeticAccessories().setItem(0,cosmetic.copy());
            try {
                h.assertTrue(ItemStack.matches(StockingsWear.visual(player),cosmetic),"Accessories cosmetic slot controls appearance");
                h.assertTrue(ItemStack.matches(StockingsWear.get(player),functional),"Cosmetic stockings never replace functional storage");
                container.renderOptions().set(0,false);
                h.assertTrue(StockingsWear.visual(player).isEmpty() && ItemStack.matches(StockingsWear.get(player),functional),"Hiding appearance preserves equipped gameplay");
            } finally { container.renderOptions().set(0,true);container.getCosmeticAccessories().setItem(0,ItemStack.EMPTY); }
        }
    }
}
