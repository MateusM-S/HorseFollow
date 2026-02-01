package com.horsefollow.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Define o nameplate (nome exibido) de uma entidade.
 * Tenta primeiro o componente Nameplate da API; se falhar, executa o comando /entity nameplate.
 */
public final class MountNameplateHelper {

    private MountNameplateHelper() {}

    /**
     * Define o texto do nameplate da entidade (montaria).
     * @param store store do mundo
     * @param entityRef ref da entidade (montaria)
     * @param text novo nome (não nulo; pode ser vazio para remover)
     * @param playerRef ref do jogador (para executar comando em nome dele, se necessário)
     * @param runCommand função que executa um comando como se fosse o jogador (ex.: "entity nameplate \"Nome\" --entity <uuid>")
     * @return true se o nameplate foi alterado (por componente ou comando)
     */
    public static boolean setNameplate(Store<EntityStore> store,
                                       Ref<EntityStore> entityRef,
                                       String text,
                                       Ref<EntityStore> playerRef,
                                       java.util.function.BiConsumer<String, UUID> runCommand) {
        if (store == null || entityRef == null || !entityRef.isValid()) return false;
        String safeText = text != null ? text : "";

        if (setNameplateViaComponent(store, entityRef, safeText)) return true;

        UUID entityUuid = tryReadUuid(store, entityRef);
        if (entityUuid != null && runCommand != null) {
            runCommand.accept(safeText, entityUuid);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean setNameplateViaComponent(Store<EntityStore> store, Ref<EntityStore> entityRef, String text) {
        try {
            Class<?> nameplateClass = Class.forName("com.hypixel.hytale.server.core.entity.nameplate.Nameplate");
            Method getComponentType = nameplateClass.getMethod("getComponentType");
            Object componentType = getComponentType.invoke(null);
            if (componentType == null) return false;

            // 1) Tenta ensureAndGetComponent (cria componente padrão se não existir)
            try {
                Method ensureAndGet = store.getClass().getMethod("ensureAndGetComponent", Ref.class, componentType.getClass());
                Object nameplate = ensureAndGet.invoke(store, entityRef, componentType);
                if (nameplate != null) {
                    Method setText = nameplate.getClass().getMethod("setText", String.class);
                    setText.invoke(nameplate, text);
                    return true;
                }
            } catch (NoSuchMethodException ignored) {}

            // 2) getComponent + setText (entidade que já tem nameplate)
            Object nameplate = store.getComponent(entityRef, (com.hypixel.hytale.component.ComponentType) componentType);
            if (nameplate != null) {
                Method setText = nameplate.getClass().getMethod("setText", String.class);
                setText.invoke(nameplate, text);
                return true;
            }

            // 3) Entidade sem nameplate: adiciona com putComponent(Ref, ComponentType, T)
            Object newNameplate = nameplateClass.getConstructor(String.class).newInstance(text);
            for (Method m : store.getClass().getMethods()) {
                if (!"putComponent".equals(m.getName()) || m.getParameterCount() != 3) continue;
                try {
                    m.invoke(store, entityRef, componentType, newNameplate);
                    return true;
                } catch (Exception ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }


    private static UUID tryReadUuid(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null) return null;
        try {
            Class<?> uuidComponent = Class.forName("com.hypixel.hytale.server.core.entity.UUIDComponent");
            Method getType = uuidComponent.getMethod("getComponentType");
            Object type = getType.invoke(null);
            Object comp = store.getComponent(entityRef, (com.hypixel.hytale.component.ComponentType) type);
            if (comp != null) {
                Method getUuid = comp.getClass().getMethod("getUuid");
                Object u = getUuid.invoke(comp);
                if (u instanceof UUID) return (UUID) u;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Obtém o texto atual do nameplate da entidade (montaria), se existir.
     * @return texto do nameplate ou string vazia se não houver componente ou falha
     */
    public static String getNameplateText(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null || !entityRef.isValid()) return "";
        try {
            Class<?> nameplateClass = Class.forName("com.hypixel.hytale.server.core.entity.nameplate.Nameplate");
            Method getComponentType = nameplateClass.getMethod("getComponentType");
            Object componentType = getComponentType.invoke(null);
            if (componentType == null) return "";
            Object nameplate = store.getComponent(entityRef, (com.hypixel.hytale.component.ComponentType) componentType);
            if (nameplate != null) {
                Method getText = nameplate.getClass().getMethod("getText");
                Object text = getText.invoke(nameplate);
                return text != null ? text.toString().trim() : "";
            }
        } catch (Throwable ignored) {}
        return "";
    }

    /**
     * Monta a string do comando /entity nameplate para ser executada pelo servidor/jogador.
     * Formato: entity nameplate "Texto" --entity <uuid>
     */
    public static String buildNameplateCommand(String text, UUID entityUuid) {
        if (entityUuid == null) return "";
        String escaped = (text != null ? text : "").replace("\\", "\\\\").replace("\"", "\\\"");
        return "entity nameplate \"" + escaped + "\" --entity " + entityUuid.toString();
    }
}
