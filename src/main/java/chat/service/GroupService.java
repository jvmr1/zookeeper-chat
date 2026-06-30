package chat.service;

import java.util.List;
import java.util.ArrayList;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

public class GroupService {

    private final ZooKeeper zk;

    public GroupService(ZooKeeper zk) {
        this.zk = zk;
    }

    private String groupPath(String group) {
        return "/chat/groups/" + group;
    }

    public static class GroupMeta {
        public String owner;
        public boolean isPublic;
    }

    public GroupMeta getMetadata(String group) {
        try {
            byte[] data = zk.getData(groupPath(group), false, null);
            GroupMeta meta = new GroupMeta();
            if (data == null || data.length == 0) {
                // Compatibilidade com grupos antigos: padrão é público e dono desconhecido
                meta.owner = "desconhecido";
                meta.isPublic = true;
                return meta;
            }
            String s = new String(data);
            for (String part : s.split(";")) {
                String[] kv = part.split(":", 2);
                if (kv.length == 2) {
                    if (kv[0].equals("owner"))
                        meta.owner = kv[1];
                    if (kv[0].equals("type"))
                        meta.isPublic = kv[1].equals("public");
                }
            }
            if (meta.owner == null) {
                meta.owner = "desconhecido";
            }
            return meta;
        } catch (Exception e) {
            return null;
        }
    }

    public void createGroup(String group, String owner, boolean isPublic) throws Exception {

        String root = groupPath(group);

        ensure(root);
        ensure(root + "/members");
        ensure(root + "/messages");
        ensure(root + "/requests");

        // Salva os metadados no nó do grupo
        String metaData = "owner:" + owner + ";type:" + (isPublic ? "public" : "private");
        zk.setData(root, metaData.getBytes(), -1);

        try {
            zk.create(
                    root + "/members/" + owner,
                    new byte[0],
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT // membro persiste entre sessões
            );
        } catch (org.apache.zookeeper.KeeperException.NodeExistsException e) {
            // dono já é membro (grupo pré-existente) — ok
        }

        System.out.println("Grupo " + (isPublic ? "público" : "privado") + " criado: " + group);
    }

    public void addMember(String group, String user) throws Exception {

        String path = groupPath(group) + "/members/" + user;

        try {
            zk.create(
                    path,
                    new byte[0],
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT // membro persiste entre sessões
            );
        } catch (org.apache.zookeeper.KeeperException.NodeExistsException e) {
            System.out.println(user + " já é membro do grupo " + group);
            return;
        }

        System.out.println(user + " entrou no grupo " + group);
    }

    public void leaveGroup(String group, String user) throws Exception {

        String path = groupPath(group) + "/members/" + user;

        if (zk.exists(path, false) != null) {
            zk.delete(path, -1);
            System.out.println(user + " saiu do grupo " + group);
        } else {
            System.out.println(user + " não é membro do grupo " + group);
        }
    }

    public void requestJoin(String group, String user) throws Exception {
        GroupMeta meta = getMetadata(group);
        if (meta == null) {
            System.out.println("Grupo não existe.");
            return;
        }
        if (!meta.isPublic) {
            System.out.println("Não é possível solicitar entrada em um grupo privado.");
            return;
        }
        if (zk.exists(groupPath(group) + "/members/" + user, false) != null) {
            System.out.println("Você já é membro deste grupo.");
            return;
        }
        String path = groupPath(group) + "/requests/" + user;
        try {
            zk.create(
                    path,
                    new byte[0],
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT);
            System.out.println("Solicitação enviada para entrar no grupo " + group);
        } catch (org.apache.zookeeper.KeeperException.NodeExistsException e) {
            System.out.println("Você já possui uma solicitação pendente para este grupo.");
        }
    }

    public void approveRequest(String group, String admin, String user) throws Exception {
        GroupMeta meta = getMetadata(group);
        if (meta == null || !admin.equals(meta.owner)) {
            System.out.println(
                    "Apenas o administrador do grupo (" + (meta != null ? meta.owner : "nenhum") + ") pode aprovar.");
            return;
        }
        String reqPath = groupPath(group) + "/requests/" + user;
        if (zk.exists(reqPath, false) == null) {
            System.out.println("Nenhuma solicitação pendente encontrada para o usuário " + user);
            return;
        }
        addMember(group, user);
        zk.delete(reqPath, -1);
        System.out.println("Solicitação de " + user + " aprovada.");
    }

    public void rejectRequest(String group, String admin, String user) throws Exception {
        GroupMeta meta = getMetadata(group);
        if (meta == null || !admin.equals(meta.owner)) {
            System.out.println("Apenas o administrador do grupo pode reprovar.");
            return;
        }
        String reqPath = groupPath(group) + "/requests/" + user;
        if (zk.exists(reqPath, false) == null) {
            System.out.println("Nenhuma solicitação pendente encontrada para o usuário " + user);
            return;
        }
        zk.delete(reqPath, -1);
        System.out.println("Solicitação de " + user + " rejeitada.");
    }

    public List<String> listMembers(String group) throws Exception {

        String path = groupPath(group) + "/members";

        return zk.getChildren(path, false);
    }

    public List<String> listGroups(String user) throws Exception {
        String path = "/chat/groups";
        ensure(path);
        List<String> all = zk.getChildren(path, false);
        List<String> visible = new ArrayList<>();
        for (String g : all) {
            GroupMeta meta = getMetadata(g);
            if (meta == null)
                continue;
            // Público: qualquer um vê. Privado: só quem é membro vê.
            if (meta.isPublic || zk.exists(groupPath(g) + "/members/" + user, false) != null) {
                visible.add(g);
            }
        }
        return visible;
    }

    public List<String> listMyGroups(String user) throws Exception {
        List<String> allGroups = listGroups(user);
        List<String> myGroups = new ArrayList<>();
        for (String g : allGroups) {
            try {
                List<String> members = listMembers(g);
                if (members.contains(user)) {
                    myGroups.add(g);
                }
            } catch (Exception e) {
                // grupo ou membros deletados concorrentemente — ignorar
            }
        }
        return myGroups;
    }

    public void listMessages(String group) throws Exception {

        String path = groupPath(group) + "/messages";

        List<String> msgs = zk.getChildren(path, false);
        msgs.sort(String::compareTo);

        for (String m : msgs) {
            byte[] data = zk.getData(path + "/" + m, false, null);
            String raw = new String(data);
            String[] parts = raw.split(":", 2);
            String sender = parts[0];
            String msg = parts.length > 1 ? parts[1] : raw;
            System.out.println(m + " [" + sender + "] -> " + msg);
        }
    }

    public void sendMessage(String group, String from, String message) throws Exception {

        String path = groupPath(group) + "/messages/msg";

        zk.create(
                path,
                (from + ":" + message).getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT_SEQUENTIAL);

        System.out.println("Mensagem enviada no grupo " + group);
    }

    private void ensure(String path) throws Exception {

        String[] parts = path.split("/");
        StringBuilder current = new StringBuilder();

        for (int i = 1; i < parts.length; i++) {

            current.append("/").append(parts[i]);
            String currentPath = current.toString();

            if (zk.exists(currentPath, false) == null) {
                try {
                    zk.create(
                            currentPath,
                            new byte[0],
                            ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.PERSISTENT);
                } catch (org.apache.zookeeper.KeeperException.NodeExistsException e) {
                    // já existe, criado concorrentemente — ok
                }
            }
        }
    }
}