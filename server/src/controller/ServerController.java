package controller;

/**
 * ServerController handles the over all logic on the server side.
 * The class contains the main method that starts the program and makes it possible for a client
 * to obtain a connection by creating an instance of ServerNetwork.
 * version 1.0 2020-03-23
 *
 * @autor Angelica Asplund, Emma Svensson and Theresa Dannberg
 */


import model.RegisteredGroups;
import model.RegisteredUsers;

import shared.transferable.*;


import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class ServerController implements ClientListener {

    private RegisteredUsers registeredUsers;
    private RegisteredGroups registeredGroups;
    private ServerNetwork network;
    private LinkedBlockingQueue<Message> clientTaskBuffer; //TODO: här läggs alla inkommande arraylists från klienterna.
    private MessageHandler messageHandler;
    private ConcurrentHashMap<User, ClientHandler> onlineClients = new ConcurrentHashMap<>();

    public ServerController() {
        registeredUsers = new RegisteredUsers();
        registeredGroups = new RegisteredGroups();
        clientTaskBuffer = new LinkedBlockingQueue<>();
        network = new ServerNetwork(this, 6583);
        messageHandler = new MessageHandler();
        Thread t1 = new Thread(messageHandler);
        t1.start();

    }

    @Override
    public void sendMessage(Message msg) {
        clientTaskBuffer.add(msg);

    }

    public void addOnlineClient(User user, ClientHandler client) {
        onlineClients.put(user, client);
    }

    public void removeOnlineClient(User user) {
        onlineClients.remove(user);
    }

    public void sendReply(Message reply) {
        ClientHandler client = onlineClients.get(reply.getUser());
        client.addToOutgoingMessages(reply);
    }

    public void handleClientTask(Message msg) {

        //TODO: tanke - ska vi skicka replymeddelanden härifrån istället för själva metoden? För här finns ju redan usern som ska ha svaret?

        NetCommands command = msg.getCommand();
        User user = msg.getUser();

        switch (command) {
            case registerUser:
                registerUser(msg);
                break;
            case registerNewGroup:
                updateGroup(msg);
                break;
            case addNewChore:
                addNewChore(msg);
                break;
            case addNewReward:
                addNewReward(msg);
                break;
            default:
                //TODO:  kod för default case. Vad kan man skriva här?
                break;
        }
    }

    /**
     * Adds the incoming user to RegisteredUsers
     *
     * @param request
     */


    public void registerUser(Message request) {
        Message reply = null;

        if (registeredUsers.userNameAvailable(request.getUser().getUsername())) {
            if (request.getUser().getPassword() != "") {
                registeredUsers.writeUserToFile(request.getUser());

                reply = new Message(NetCommands.registrationOk, request.getUser(), new ArrayList<>());
                sendReply(reply);
            }
        } else {
            ErrorMessage errorMessage = new ErrorMessage("Användarnamnet är upptaget, välj ett annat.");
            reply = new Message(NetCommands.registrationDenied, request.getUser(), errorMessage);

            sendReply(reply);
            onlineClients.get(request.getUser()).throwOut();
        }
    }


    /**
     * Registers a new group to the server and updates all the members of that group with the new group membership
     *
     * @param request
     */

    public void updateGroup (Message request) {
        Message reply = null;

        if (registeredGroups.groupIdAvailable(request.getGroup().getGroupID())) {
            registeredGroups.updateGroup(request.getGroup());
            ArrayList<User> members = request.getGroup().getUsers();
            GenericID groupID = request.getGroup().getGroupID();
            for (User u : members) {
                u.addGroupMembership(groupID);
            }
            reply = new Message(NetCommands.newGroupOk, request.getUser(), new ArrayList<>());

        } else {
            ErrorMessage errorMessage = new ErrorMessage("Vad kan gå fel vid skapande av grupp?"); //FixMe: felmeddelandetext?
            reply = new Message(NetCommands.newGroupDenied, request.getUser(), errorMessage);

        }

        sendReply(reply);

    }

    //TODO: skicka svar till klienten
    public void addNewChore(Message request) {
        request.getGroup().addChore(request.getChore()); //TODO: Här la jag till en getChore i Message och hämtar, FEL?
        registeredGroups.updateGroup(request.getGroup());
    }

    //TODO: skicka svar till klienten
    public void addNewReward(Message request) {
        request.getGroup().addReward(request.getReward()); //TODO: Här la jag till en getReward i Message och hämtar, FEL?
        registeredGroups.updateGroup(request.getGroup());
    }

    /**
     * Inner class MessageHandler handles the incoming messages from the client one at a time.
     */

    private class MessageHandler implements Runnable {

        public MessageHandler() {

        }

        public void run() {
            ArrayList<Transferable> list;
            while (true) {
                try {
                    Message message = clientTaskBuffer.take();
                    handleClientTask(message);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

        }

    }

}
