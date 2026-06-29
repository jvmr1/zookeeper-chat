package chat.cli;

import chat.service.*;

import java.util.List;
import java.util.Scanner;

public class CommandLoop {

    private final Scanner scanner;
    private final PresenceService presence;
    private final MessageService messageService;
    private final MessageWatcherService watcher;
    private final GroupService groupService;
    private final GroupWatcherService groupWatcher;
    private final String username;

    private boolean running = true;

    public CommandLoop(
            Scanner scanner,
            PresenceService presence,
            MessageService messageService,
            MessageWatcherService watcher,
            GroupService groupService,
            GroupWatcherService groupWatcher,
            String username
    ) {
        this.scanner = scanner;
        this.presence = presence;
        this.messageService = messageService;
        this.watcher = watcher;
        this.groupService = groupService;
        this.groupWatcher = groupWatcher;
        this.username = username;
    }

    public void start() throws Exception {

        System.out.println("\nBem-vindo, " + username);

        // 🔥 ativa watcher global
        watcher.watchAll(username);

        // 🔥 ativa watcher global de grupos
        groupWatcher.watchAllGroups(username);

        while (running) {

            System.out.print("\n> ");
            String input = scanner.nextLine().trim();

            if (input.equals("online")) {
                handleOnline();
            }

            else if (input.startsWith("msg ")) {
                handleMsg(input);
            }

            else if (input.startsWith("abrir ")) {
                handleAbrir(input);
            }

            else if (input.equals("logout")) {
                handleLogout();
            }

            else if (input.equals("grupo listar")) {
                List<String> groups = groupService.listGroups();
                if (groups.isEmpty()) {
                    System.out.println("Nenhum grupo cadastrado.");
                } else {
                    System.out.println("Grupos no sistema: " + String.join(", ", groups));
                }
            }

            else if (input.equals("grupo meus")) {
                List<String> groups = groupService.listMyGroups(username);
                if (groups.isEmpty()) {
                    System.out.println("Você não está em nenhum grupo.");
                } else {
                    System.out.println("Meus grupos: " + String.join(", ", groups));
                }
            }

            else if (input.startsWith("grupo criar ")) {

                String group = input.split(" ")[2];

                groupService.createGroup(group, username);
                groupWatcher.watch(group, username); // observa o grupo recém-criado
            }

            else if (input.startsWith("grupo add ")) {

                String[] p = input.split(" ");
                String targetGroup = p[2];
                String targetUser  = p[3];

                groupService.addMember(targetGroup, targetUser);

                // se o próprio usuário entrou, ativa o watcher imediatamente
                if (targetUser.equals(username)) {
                    groupWatcher.watch(targetGroup, username);
                }
            }

            else if (input.startsWith("grupo msg ")) {

                String[] p = input.split(" ", 4);

                groupService.sendMessage(p[2], username, p[3]);
            }

            else if (input.startsWith("grupo sair ")) {

                String group = input.split(" ")[2];

                groupService.leaveGroup(group, username);
            }

            else if (input.startsWith("grupo membros ")) {

                String group = input.split(" ")[2];

                System.out.println(groupService.listMembers(group));
            }

            else {
                System.out.println("Comando desconhecido");
            }
        }
    }

    private void handleOnline() throws Exception {

        List<String> users = presence.listOnline();

        System.out.println(String.join(", ", users));
    }

    private void handleMsg(String input) throws Exception {

        String[] parts = input.split(" ", 3);

        String to = parts[1];
        String msg = parts[2];

        messageService.send(username, to, msg);
    }

    private void handleAbrir(String input) throws Exception {

        String to = input.split(" ")[1];

        messageService.list(username, to);
    }

    private void handleLogout() throws Exception {

        presence.logout(username);
        running = false;
    }
}