package chat.service;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.util.List;

public class GroupService {

    private final ZooKeeper zk;

    public GroupService(ZooKeeper zk) {
        this.zk = zk;
    }

    private String groupPath(String group) {
        return "/chat/groups/" + group;
    }

    public void createGroup(String group, String owner) throws Exception {

        String root = groupPath(group);

        ensure(root);
        ensure(root + "/members");
        ensure(root + "/messages");

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

        System.out.println("Grupo criado: " + group);
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

    public List<String> listMembers(String group) throws Exception {

        String path = groupPath(group) + "/members";

        return zk.getChildren(path, false);
    }

    public List<String> listGroups() throws Exception {
        String path = "/chat/groups";
        ensure(path);
        return zk.getChildren(path, false);
    }

    public List<String> listMyGroups(String user) throws Exception {
        List<String> allGroups = listGroups();
        List<String> myGroups = new java.util.ArrayList<>();
        for (String g : allGroups) {
            try {
                List<String> members = listMembers(g);
                if (members.contains(user)) {
                    myGroups.add(g);
                }
            } catch (Exception e) {
                // Ignore if group or members node got deleted in the meantime
            }
        }
        return myGroups;
    }


    public void sendMessage(String group, String from, String message) throws Exception {

        String path = groupPath(group) + "/messages/msg";

        zk.create(
                path,
                (from + ":" + message).getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT_SEQUENTIAL
        );

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
                            CreateMode.PERSISTENT
                    );
                } catch (org.apache.zookeeper.KeeperException.NodeExistsException e) {
                    // Ignore, already created
                }
            }
        }
    }
}