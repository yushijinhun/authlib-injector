package moe.yushi.authlibinjector.transform.support;

import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.util.WeakIdentityHashMap;

public class MC52974_1710Workaround implements TransformUnit {

	// Empty GameProfile -> Filled GameProfile?
	private static final Map<Object, Optional<Object>> markedGameProfiles = new WeakIdentityHashMap<>();

	public static void markGameProfile(Object gp) {
		synchronized (markedGameProfiles) {
			markedGameProfiles.putIfAbsent(gp, Optional.empty());
		}
	}

	public static Object accessGameProfile(Object gp, Object minecraftServer) {
		synchronized (markedGameProfiles) {
			Optional<Object> value = markedGameProfiles.get(gp);
			if (value != null) {
				if (value.isPresent()) {
					return value.get();
				}

				// query it
				if (minecraftServer != null) {
					Logging.TRANSFORM.info("Filling properties for " + gp);
					try {
						Class<?> classGameProfile = gp.getClass();
						Object gameProfile = classGameProfile.getConstructor(UUID.class, String.class)
								.newInstance(
										classGameProfile.getMethod("getId").invoke(gp),
										classGameProfile.getMethod("getName").invoke(gp));

						// MD: net/minecraft/server/MinecraftServer/av ()Lcom/mojang/authlib/minecraft/MinecraftSessionService; net/minecraft/server/MinecraftServer/func_147130_as ()Lcom/mojang/authlib/minecraft/MinecraftSessionService;
						Object minecraftSessionService = minecraftServer.getClass().getMethod("av").invoke(minecraftServer);
						Object filledGameProfile = minecraftSessionService.getClass().getMethod("fillProfileProperties", classGameProfile, boolean.class)
								.invoke(minecraftSessionService, gameProfile, true);

						markedGameProfiles.put(gp, Optional.of(filledGameProfile));
						return filledGameProfile;
					} catch (ReflectiveOperationException e) {
						Logging.TRANSFORM.log(Level.WARNING, "Failed to inject GameProfile properties", e);
					}
				}
			}
		}
		return gp;
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, Runnable modifiedCallback) {
		if ("bbs".equals(className)) {
			// CL: bbs net/minecraft/util/Session
			return Optional.of(new ClassVisitor(ASM7, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if ("e".equals(name) && "()Lcom/mojang/authlib/GameProfile;".equals(descriptor)) {
						// MD: bbs/e ()Lcom/mojang/authlib/GameProfile; net/minecraft/util/Session/func_148256_e ()Lcom/mojang/authlib/GameProfile;
						return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
							@Override
							public void visitInsn(int opcode) {
								if (opcode == ARETURN) {
									modifiedCallback.run();
									super.visitInsn(DUP);
									super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(MC52974_1710Workaround.class), "markGameProfile", "(Ljava/lang/Object;)V", false);
								}
								super.visitInsn(opcode);
							}
						};
					} else {
						return super.visitMethod(access, name, descriptor, signature, exceptions);
					}
				}
			});

		} else if ("gb".equals(className)) {
			// CL: gb net/minecraft/network/play/server/S0CPacketSpawnPlayer
			return Optional.of(new ClassVisitor(ASM7, writer) {

				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if ("b".equals(name) && "(Let;)V".equals(descriptor)) {
						// MD: gb/b (Let;)V net/minecraft/network/play/server/S0CPacketSpawnPlayer/func_148840_b (Lnet/minecraft/network/PacketBuffer;)V
						// func_148840_b,writePacketData,0,Writes the raw packet data to the data stream.
						return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
							@Override
							public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
								super.visitFieldInsn(opcode, owner, name, descriptor);
								// FD: gb/b net/minecraft/network/play/server/S0CPacketSpawnPlayer/field_148955_b
								if (opcode == GETFIELD && "gb".equals(owner) && "b".equals(name) && "Lcom/mojang/authlib/GameProfile;".equals(descriptor)) {
									modifiedCallback.run();
									// MD: net/minecraft/server/MinecraftServer/I ()Lnet/minecraft/server/MinecraftServer; net/minecraft/server/MinecraftServer/func_71276_C ()Lnet/minecraft/server/MinecraftServer;
									// func_71276_C,getServer,0,Gets mcServer.
									super.visitMethodInsn(INVOKESTATIC, "net/minecraft/server/MinecraftServer", "I", "()Lnet/minecraft/server/MinecraftServer;", false);
									super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(MC52974_1710Workaround.class), "accessGameProfile", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
									super.visitTypeInsn(CHECKCAST, "com/mojang/authlib/GameProfile");
								}
							}
						};
					} else {
						return super.visitMethod(access, name, descriptor, signature, exceptions);
					}
				}
			});
		}
		return Optional.empty();
	}

	@Override
	public String toString() {
		return "MC-52974 Workaround for 1.7.10";
	}
}
