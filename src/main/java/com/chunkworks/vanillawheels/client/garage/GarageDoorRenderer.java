/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.client.garage;

import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.garage.GarageDoorBlockEntity;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** One renderer per assembled shutter. Steel slats rise into a rotating roll,
 * with fixed side tracks and end bearings. Geometry shares the collision's
 * half-block header and eighth-block travel; followers emit no geometry. */
public final class GarageDoorRenderer implements BlockEntityRenderer<GarageDoorBlockEntity> {
    private static final ResourceLocation STEEL = VanillaWheels.id("textures/block/mechanic_lift.png");
    /** requires: renderer context; effects: creates stateless renderer; throws: none. */
    public GarageDoorRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(GarageDoorBlockEntity door, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!door.renders() || door.getLevel() == null) return;
        // Sample exposed air in front of the housing, not the opaque lintel above it.
        light = LevelRenderer.getLightColor(door.getLevel(),door.origin().above(door.height()-1)
                .relative(door.axis()==Direction.Axis.X ? Direction.NORTH : Direction.WEST));
        pose.pushPose();
        if (!door.formed()) {
            var pieces = buffers.getBuffer(RenderType.entityCutoutNoCull(STEEL));
            final int sampledLight = light;
            door.shape().forAllBoxes((x0,y0,z0,x1,y1,z1) -> box(pose,pieces,
                    (float)x0,(float)y0,(float)z0,(float)x1,(float)y1,(float)z1,0xffbdc6cc,sampledLight));
            pose.popPose(); return;
        }
        if (door.axis() == Direction.Axis.Z) { pose.translate(1,0,0); pose.mulPose(Axis.YP.rotationDegrees(-90)); }
        float w=door.width(), h=door.height(), cap=h-.5f;
        float raised=Math.min(cap,Math.max(0,door.clearance(partial)));
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(STEEL));
        // Tracks stay inside the cells; the frame never steals an adjacent building block.
        box(pose,out,0,0,.35f,.1f,cap,.65f,0xff68727a,light);
        box(pose,out,w-.1f,0,.35f,w,cap,.65f,0xff68727a,light);
        box(pose,out,.025f,0,.33f,.075f,cap,.36f,0xffc5cbd0,light);
        box(pose,out,w-.075f,0,.33f,w-.025f,cap,.36f,0xffc5cbd0,light);
        // Continuous backing prevents holes between articulated slats.
        if (raised < cap) box(pose,out,.1f,raised,.48f,w-.1f,cap,.52f,0xff626b72,light);
        for (float y=raised; y<cap-.001f; y+=.25f) {
            float top=Math.min(y+.225f,cap);
            box(pose,out,.1f,y,.425f,w-.1f,top,.575f,0xffbdc6cc,light);
            if(top-y>.055f) box(pose,out,.1f,top-.035f,.415f,w-.1f,top,.585f,0xffe0e4e7,light);
        }
        if (raised < cap-.05f) {
            box(pose,out,.09f,raised,.4f,w-.09f,Math.min(raised+.07f,cap),.6f,0xff454f57,light);
            if (w>=2) box(pose,out,w/2-.22f,raised+.12f,.38f,w/2+.22f,Math.min(raised+.19f,cap),.425f,0xff454f57,light);
        }
        // Backing and bearings form the housing; the exposed roll makes its motion readable.
        box(pose,out,0,cap,.72f,w,h,.8f,0xff66717b,light);
        box(pose,out,0,cap,.2f,.12f,h,.8f,0xff717d87,light);
        box(pose,out,w-.12f,cap,.2f,w,h,.8f,0xff717d87,light);
        float radius=.15f+.075f*(raised/Math.max(.5f,cap));
        float angle=raised*4;
        for(int i=0;i<12;i++) {
            double a=i*Math.PI/6+angle,b=(i+1)*Math.PI/6+angle;
            float ay=h-.25f+(float)Math.sin(a)*radius,az=.5f+(float)Math.cos(a)*radius;
            float by=h-.25f+(float)Math.sin(b)*radius,bz=.5f+(float)Math.cos(b)*radius;
            float ny=(float)Math.sin((a+b)/2),nz=(float)Math.cos((a+b)/2);
            quad(pose,out,.12f,ay,az,w-.12f,ay,az,w-.12f,by,bz,.12f,by,bz,0,ny,nz,
                    i%2==0?0xffadb7c0:0xff7f8b94,light);
        }
        pose.popPose();
    }
    private static void box(PoseStack p,VertexConsumer o,float x0,float y0,float z0,float x1,float y1,float z1,int c,int l) {
        if (x1<=x0||y1<=y0||z1<=z0) return;
        quad(p,o,x0,y1,z0,x1,y1,z0,x1,y0,z0,x0,y0,z0,0,0,-1,c,l);
        quad(p,o,x1,y1,z1,x0,y1,z1,x0,y0,z1,x1,y0,z1,0,0,1,c,l);
        quad(p,o,x0,y1,z1,x0,y1,z0,x0,y0,z0,x0,y0,z1,-1,0,0,c,l);
        quad(p,o,x1,y1,z0,x1,y1,z1,x1,y0,z1,x1,y0,z0,1,0,0,c,l);
        quad(p,o,x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0,0,1,0,c,l);
        quad(p,o,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1,0,-1,0,c,l);
    }
    private static void quad(PoseStack p,VertexConsumer o,float ax,float ay,float az,float bx,float by,float bz,
            float cx,float cy,float cz,float dx,float dy,float dz,float nx,float ny,float nz,int c,int l) {
        vertex(p,o,ax,ay,az,0,0,nx,ny,nz,c,l); vertex(p,o,bx,by,bz,1,0,nx,ny,nz,c,l);
        vertex(p,o,cx,cy,cz,1,1,nx,ny,nz,c,l); vertex(p,o,dx,dy,dz,0,1,nx,ny,nz,c,l);
    }
    private static void vertex(PoseStack p,VertexConsumer o,float x,float y,float z,float u,float v,float nx,float ny,float nz,int c,int l) {
        var m=p.last().pose(); var n=p.last().normal();
        o.addVertex(m.m00()*x+m.m10()*y+m.m20()*z+m.m30(),m.m01()*x+m.m11()*y+m.m21()*z+m.m31(),
                m.m02()*x+m.m12()*y+m.m22()*z+m.m32(),c,u,v,OverlayTexture.NO_OVERLAY,l,
                n.m00()*nx+n.m10()*ny+n.m20()*nz,n.m01()*nx+n.m11()*ny+n.m21()*nz,n.m02()*nx+n.m12()*ny+n.m22()*nz);
    }
    @Override public AABB getRenderBoundingBox(GarageDoorBlockEntity door) { return door.renderBounds(); }
    @Override public boolean shouldRenderOffScreen(GarageDoorBlockEntity door) { return door.renders(); }
    @Override public int getViewDistance() { return 96; }
}
