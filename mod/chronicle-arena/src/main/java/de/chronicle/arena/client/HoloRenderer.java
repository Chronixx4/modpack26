package de.chronicle.arena.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.chronicle.arena.ArenaShopBlock;
import de.chronicle.arena.HoloBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/** Schwebendes Holo-Fenster ueber Terminal und Shop: leuchtender Rahmen, Scanlinie, kreisende Gegenstaende. */
public class HoloRenderer implements BlockEntityRenderer<HoloBlockEntity> {
    private static final ItemStack[] TERMINAL_ITEMS = {new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.SHIELD),
            new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.BOW)};
    private static final ItemStack[] SHOP_ITEMS = {new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.EMERALD),
            new ItemStack(Items.DIAMOND), new ItemStack(Items.ENCHANTED_BOOK)};
    private static final ItemStack TERMINAL_TOP = new ItemStack(Items.NETHER_STAR);
    private static final ItemStack SHOP_TOP = new ItemStack(Items.GOLD_BLOCK);

    private static final int HALF_W = 52;
    private static final int HALF_H = 29;

    @Override
    public void render(HoloBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean shop = be.getBlockState().getBlock() instanceof ArenaShopBlock;
        float time = mc.level.getGameTime() + partial;
        int accent = shop ? 0xFFD36B : 0x9B5CFF;
        double bob = Math.sin(time * 0.06) * 0.06;

        // Fenster, immer zum Spieler gedreht.
        pose.pushPose();
        pose.translate(0.5, 2.3 + bob, 0.5);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        pose.scale(-0.02F, -0.02F, 0.02F);
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.gui());
        float pulse = 0.75F + 0.25F * (float) Math.sin(time * 0.15);
        int glowA = (int) (0x30 * pulse);
        quad(vc, m, -HALF_W - 5, -HALF_H - 5, HALF_W + 5, HALF_H + 5, argb(glowA, accent), 0.0F);
        quad(vc, m, -HALF_W - 2, -HALF_H - 2, HALF_W + 2, HALF_H + 2, argb((int) (0x70 * pulse), accent), 0.0F);
        quad(vc, m, -HALF_W, -HALF_H, HALF_W, 0, 0xD8201638, 0.0F);
        quad(vc, m, -HALF_W, 0, HALF_W, HALF_H, 0xD80B0914, 0.0F);
        quad(vc, m, -HALF_W, -HALF_H, HALF_W, -HALF_H + 12, shop ? 0xF06A4A10 : 0xF03A2370, 0.0F);
        quad(vc, m, -HALF_W, -HALF_H + 12, HALF_W, -HALF_H + 13, 0xFFFFD36B, 0.0F);
        // Scanlinie.
        float sy = -HALF_H + 13 + (time * 0.7F) % (HALF_H * 2 - 13);
        quad(vc, m, -HALF_W, sy, HALF_W, sy + 1.5F, 0x38FFFFFF, 0.0F);
        // Ecken.
        for (int sx : new int[]{-1, 1}) for (int sy2 : new int[]{-1, 1}) {
            int cx = sx * HALF_W;
            int cy = sy2 * HALF_H;
            quad(vc, m, cx - 3, cy - 3, cx + 3, cy + 3, 0xFFFFD36B, 0.0F);
        }

        Font font = mc.font;
        String title = shop ? "ARENA-SHOP" : "ARENA-TERMINAL";
        text(font, buffers, m, title, 0, -HALF_H + 2, 0xFFFFE9A8, 1.0F);
        String l1 = shop ? "Items, Titel, Spuren" : "Wellen oder Boss";
        String l2 = shop ? "Bezahlt mit Coins" : "Stufen und Modifikatoren";
        text(font, buffers, m, l1, 0, -8, 0xFFFFFFFF, 0.9F);
        text(font, buffers, m, l2, 0, 3, 0xFFB8A8E8, 0.8F);
        boolean blink = ((int) (time / 20)) % 2 == 0;
        text(font, buffers, m, blink ? "> Rechtsklick <" : "  Rechtsklick  ", 0, 17, shop ? 0xFFFFD700 : 0xFF88DDFF, 0.9F);
        pose.popPose();

        // Kreisende Gegenstaende um das Fenster.
        ItemStack[] items = shop ? SHOP_ITEMS : TERMINAL_ITEMS;
        for (int i = 0; i < items.length; i++) {
            double a = time * 0.04 + Math.PI * 2.0 * i / items.length;
            pose.pushPose();
            pose.translate(0.5 + Math.cos(a) * 1.5, 2.3 + bob + Math.sin(a * 2.0) * 0.12, 0.5 + Math.sin(a) * 1.5);
            pose.mulPose(Axis.YP.rotationDegrees(time * 4.0F + i * 90.0F));
            pose.scale(0.5F, 0.5F, 0.5F);
            mc.getItemRenderer().renderStatic(items[i], ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, i);
            pose.popPose();
        }
        // Grosses Symbol ueber dem Fenster.
        pose.pushPose();
        pose.translate(0.5, 3.35 + bob + Math.sin(time * 0.08) * 0.05, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * 3.0F));
        pose.scale(0.8F, 0.8F, 0.8F);
        mc.getItemRenderer().renderStatic(shop ? SHOP_TOP : TERMINAL_TOP, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 9);
        pose.popPose();
    }

    private static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Rechteck in beiden Umlaufrichtungen, damit es von vorn sichtbar ist, egal wie Culling steht. */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int color, float z) {
        vc.addVertex(m, x0, y0, z).setColor(color);
        vc.addVertex(m, x0, y1, z).setColor(color);
        vc.addVertex(m, x1, y1, z).setColor(color);
        vc.addVertex(m, x1, y0, z).setColor(color);
        vc.addVertex(m, x1, y0, z).setColor(color);
        vc.addVertex(m, x1, y1, z).setColor(color);
        vc.addVertex(m, x0, y1, z).setColor(color);
        vc.addVertex(m, x0, y0, z).setColor(color);
    }

    private static void text(Font font, MultiBufferSource buffers, Matrix4f base, String s, float cx, float y, int color, float scale) {
        Matrix4f m = new Matrix4f(base).translate(0.0F, 0.0F, -0.05F).scale(scale, scale, 1.0F);
        float x = cx / scale - font.width(s) / 2.0F;
        font.drawInBatch(s, x, y / scale, color, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
    }

    @Override
    public AABB getRenderBoundingBox(HoloBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2.5, 0.0, 2.5).expandTowards(0.0, 4.0, 0.0);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
