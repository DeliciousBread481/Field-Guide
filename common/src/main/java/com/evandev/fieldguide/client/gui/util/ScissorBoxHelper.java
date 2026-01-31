package com.evandev.fieldguide.client.gui.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class ScissorBoxHelper {

    public static ScissorBox calculateScissor(PoseStack poseStack, double limitY) {
        Minecraft mc = Minecraft.getInstance();
        Matrix4f modelView = poseStack.last().pose();
        Matrix4f projection = RenderSystem.getProjectionMatrix();

        Vector4f bottomPos = new Vector4f(0, 0, 0, 1.0f);
        Vector4f topPos = new Vector4f(0, (float) limitY, 0, 1.0f);

        bottomPos.mul(modelView);
        bottomPos.mul(projection);

        topPos.mul(modelView);
        topPos.mul(projection);

        if (bottomPos.w() <= 0 && topPos.w() <= 0) return null;

        Vector3f ndcTop = new Vector3f(topPos.x() / topPos.w(), topPos.y() / topPos.w(), topPos.z() / topPos.w());

        int winWidth = mc.getWindow().getWidth();
        int winHeight = mc.getWindow().getHeight();

        int yEnd = (int) ((ndcTop.y() + 1) * 0.5f * winHeight);
        int scissorHeight = Math.max(0, yEnd);

        if (scissorHeight > winHeight) scissorHeight = winHeight;

        return new ScissorBox(0, 0, winWidth, scissorHeight);
    }
}
