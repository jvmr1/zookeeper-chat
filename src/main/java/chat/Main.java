package chat;

import chat.cli.CommandLoop;
import chat.service.*;
import org.apache.zookeeper.ZooKeeper;

import java.util.Scanner;


public class Main {

    public static void main(String[] args) throws Exception {

        ZKClient client = new ZKClient("localhost:2181");
        ZooKeeper zk = client.get();

        PresenceService presence = new PresenceService(zk);
        MessageService messageService = new MessageService(zk);
        MessageWatcherService watcher = new MessageWatcherService(zk);
        GroupService groupService = new GroupService(zk);
        GroupWatcherService groupWatcher = new GroupWatcherService(zk);

        Scanner scanner = new Scanner(System.in);

        System.out.print("Usuário: ");
        String username = scanner.nextLine();

        presence.login(username);

        CommandLoop cli =
                new CommandLoop(scanner, presence, messageService, watcher, groupService, groupWatcher, username);

        cli.start();
    }
}